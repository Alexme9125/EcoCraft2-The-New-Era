package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import com.industrialage.ia2.industrial.WorkshopPlatform;
import com.industrialage.ia2.logistics.CellFamily;
import com.industrialage.ia2.logistics.CellItem;
import com.industrialage.ia2.logistics.TransportPlatform;
import java.util.Arrays;
import java.util.LinkedHashSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integrates EcoCraft 2's named fluid cells with IC2NE's {@link TransportPlatform}, enabling
 * bidirectional filling/emptying, {@code retype} conversion to/from {@code ic2:fluid_cell} and
 * {@code ic2:empty_cell}, and automatic NeoForge fluid capability registration via {@code cells()}.
 */
@Mixin(value = TransportPlatform.class, remap = false)
public final class TransportPlatformMixin {

    @Shadow
    private static FluidStack readCellFluid(ItemStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private static ItemStack withCellContents(ItemStack stack, FluidStack fluid) {
        throw new AssertionError();
    }

    @Shadow
    private static String itemId(ItemStack stack) {
        throw new AssertionError();
    }

    @Shadow
    private static String fluidId(FluidStack fluid) {
        throw new AssertionError();
    }

    @Shadow
    private static CellFamily.Contents measure(FluidStack fluid) {
        throw new AssertionError();
    }

    @Inject(method = "cellContents", at = @At("HEAD"), cancellable = true)
    private static void ecc2$cellContentsForExternalNamedCell(
            ItemStack cell, CallbackInfoReturnable<CellFamily.Contents> cir) {
        if (!cell.isEmpty()
                && !(cell.getItem() instanceof CellItem)
                && Ecc2ExternalRecipes.isNamedCell(itemId(cell))) {
            cir.setReturnValue(measure(readCellFluid(cell)));
        }
    }

    @Inject(method = "emptyCellRemainder", at = @At("HEAD"), cancellable = true)
    private static void ecc2$emptyRemainderForExternalNamedCell(
            ItemStack cell, CallbackInfoReturnable<ItemStack> cir) {
        if (!cell.isEmpty()
                && !(cell.getItem() instanceof CellItem)
                && Ecc2ExternalRecipes.isNamedCell(itemId(cell))) {
            cir.setReturnValue(withCellContents(cell.copyWithCount(1), FluidStack.EMPTY));
        }
    }

    @Inject(method = "readCellFluid", at = @At("RETURN"), cancellable = true)
    private static void ecc2$readExternalNamedCellFluid(
            ItemStack stack, CallbackInfoReturnable<FluidStack> cir) {
        if (!cir.getReturnValue().isEmpty()) {
            return;
        }
        String fluid = Ecc2ExternalRecipes.fluidForNamedCell(itemId(stack));
        if (fluid != null) {
            cir.setReturnValue(WorkshopPlatform.fluid(fluid, CellFamily.CAPACITY));
        }
    }

    @Inject(method = "withCellContents", at = @At("RETURN"))
    private static void ecc2$stripRedundantCellFluidComponent(
            ItemStack stack, FluidStack fluid, CallbackInfoReturnable<ItemStack> cir) {
        var result = cir.getReturnValue();
        if (result.isEmpty()
                || fluid.isEmpty()
                || !fluid.getComponentsPatch().isEmpty()
                || fluid.getAmount() != CellFamily.CAPACITY) {
            return;
        }
        String expectedFluid = Ecc2ExternalRecipes.fluidForNamedCell(itemId(result));
        if (expectedFluid != null && expectedFluid.equals(fluidId(fluid))) {
            result.remove(TransportPlatform.cellFluidComponent());
        }
    }

    @Inject(method = "retype", at = @At("HEAD"), cancellable = true)
    private static void ecc2$retypeToExternalNamedCell(
            ItemStack stack, String target, CallbackInfoReturnable<ItemStack> cir) {
        if (target == null || target.indexOf(':') < 0) {
            return;
        }
        Item external = Ecc2BridgePlatform.findItem(target);
        if (external != null) {
            var replacement = new ItemStack(external);
            replacement.applyComponents(stack.getComponentsPatch());
            cir.setReturnValue(replacement);
        } else {
            cir.setReturnValue(stack.copy());
        }
    }

    @Inject(method = "cells", at = @At("RETURN"), cancellable = true)
    private static void ecc2$attachCapabilitiesToExternalCells(
            CallbackInfoReturnable<Item[]> cir) {
        var extraIds = Ecc2ExternalRecipes.namedCellItemIds();
        if (extraIds.isEmpty()) {
            return;
        }
        var combined = new LinkedHashSet<>(Arrays.asList(cir.getReturnValue()));
        boolean changed = false;
        for (String id : extraIds) {
            Item item = Ecc2BridgePlatform.findItem(id);
            if (item != null && combined.add(item)) {
                changed = true;
            }
        }
        if (changed) {
            cir.setReturnValue(combined.toArray(Item[]::new));
        }
    }
}
