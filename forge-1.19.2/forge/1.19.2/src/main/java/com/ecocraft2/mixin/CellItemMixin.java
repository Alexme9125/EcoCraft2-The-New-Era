package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import com.industrialage.ia2.logistics.CellFamily;
import com.industrialage.ia2.logistics.CellItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integrates EcoCraft 2's named 1000 mB fluid cells with IC2NE Forge 1.19.2's {@link CellItem},
 * preserving full bidirectional conversion with {@code ic2:fluid_cell} and {@code ic2:empty_cell}.
 */
@Mixin(value = CellItem.class, remap = false)
public final class CellItemMixin {

    @Inject(method = "path", at = @At("HEAD"), cancellable = true)
    private static void ecc2$externalCellPath(ItemStack stack, CallbackInfoReturnable<String> cir) {
        if (!(stack.getItem() instanceof CellItem)) {
            String id = Ecc2BridgePlatform.itemId(stack);
            if (Ecc2ExternalRecipes.isNamedCell(id)) {
                cir.setReturnValue(id);
            }
        }
    }

    @Inject(method = "contents", at = @At("RETURN"), cancellable = true)
    private static void ecc2$readExternalNamedCellFluid(
            ItemStack stack, CallbackInfoReturnable<FluidStack> cir) {
        if (!cir.getReturnValue().isEmpty()) {
            return;
        }
        String fluidId = Ecc2ExternalRecipes.fluidForNamedCell(Ecc2BridgePlatform.itemId(stack));
        if (fluidId != null) {
            cir.setReturnValue(Ecc2BridgePlatform.fluidStack(fluidId, CellFamily.CAPACITY));
        }
    }

    @Inject(method = "withContents", at = @At("RETURN"))
    private static void ecc2$stripRedundantNamedCellNbt(
            ItemStack stack, FluidStack fluid, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty()
                || fluid.isEmpty()
                || fluid.hasTag()
                || fluid.getAmount() != CellFamily.CAPACITY) {
            return;
        }
        String expectedFluid =
                Ecc2ExternalRecipes.fluidForNamedCell(Ecc2BridgePlatform.itemId(result));
        if (expectedFluid != null && expectedFluid.equals(Ecc2BridgePlatform.fluidId(fluid))) {
            if (result.getTag() != null) {
                result.removeTagKey("Fluid");
            }
        }
    }

    @Inject(method = "retype", at = @At("HEAD"), cancellable = true)
    private static void ecc2$retypeExternalNamedCell(
            ItemStack stack, String target, CallbackInfoReturnable<ItemStack> cir) {
        if (target == null || target.indexOf(':') < 0) {
            return;
        }
        CompoundTag saved = stack.save(new CompoundTag());
        saved.putString("id", target);
        saved.putByte("Count", (byte) 1);
        cir.setReturnValue(ItemStack.of(saved));
    }
}
