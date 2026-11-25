package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2CellHelper;
import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import com.industrialage.ia2.logistics.CellFamily;
import com.industrialage.ia2.logistics.CellItem;
import com.industrialage.ia2.logistics.TransportPlatform;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integrates EcoCraft 2's named fluid cells with IC2NE Forge 1.19.2's {@link TransportPlatform}
 * container helpers so tanks, pipes, canners, and crafting remainders recognize them natively.
 */
@Mixin(value = TransportPlatform.class, remap = false)
public final class TransportPlatformMixin {

    @Shadow
    private static CellFamily.Contents measure(FluidStack fluid) {
        throw new AssertionError();
    }

    @Inject(method = "cellContents", at = @At("HEAD"), cancellable = true)
    private static void ecc2$cellContentsForExternalNamedCell(
            ItemStack cell, CallbackInfoReturnable<CellFamily.Contents> cir) {
        if (!cell.isEmpty()
                && !(cell.getItem() instanceof CellItem)
                && Ecc2ExternalRecipes.isNamedCell(Ecc2BridgePlatform.itemId(cell))) {
            cir.setReturnValue(measure(Ecc2CellHelper.readContents(cell)));
        }
    }

    @Inject(method = "emptyCellRemainder", at = @At("HEAD"), cancellable = true)
    private static void ecc2$emptyRemainderForExternalNamedCell(
            ItemStack cell, CallbackInfoReturnable<ItemStack> cir) {
        if (!cell.isEmpty()
                && !(cell.getItem() instanceof CellItem)
                && Ecc2ExternalRecipes.isNamedCell(Ecc2BridgePlatform.itemId(cell))) {
            ItemStack single = Ecc2BridgePlatform.copyWithCount(cell, 1);
            cir.setReturnValue(Ecc2CellHelper.withContents(single, FluidStack.EMPTY));
        }
    }
}
