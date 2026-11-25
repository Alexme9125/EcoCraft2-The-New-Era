package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import com.industrialage.ia2.logistics.CellFamily;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends IC2NE's {@link CellFamily} lookup, admission, and full-cell mapping tables to support
 * EcoCraft 2's named basic and universal fluid cells without mutating IC2NE's own item registration
 * loop.
 */
@Mixin(value = CellFamily.class, remap = false)
public abstract class CellFamilyMixin {

    @Inject(method = "itemPath", at = @At("HEAD"), cancellable = true)
    private static void ecc2$preserveExternalCellId(
            String id, CallbackInfoReturnable<String> cir) {
        if (Ecc2ExternalRecipes.isNamedCell(id)) {
            cir.setReturnValue(id);
        }
    }

    @Inject(method = "of", at = @At("HEAD"), cancellable = true)
    private static void ecc2$resolveExternalCellFamily(
            String path, CallbackInfoReturnable<CellFamily> cir) {
        Boolean basic = Ecc2ExternalRecipes.isBasicNamedCell(path);
        if (basic != null) {
            cir.setReturnValue(basic ? CellFamily.BASIC : CellFamily.UNIVERSAL);
        }
    }

    @Inject(method = "admits", at = @At("RETURN"), cancellable = true)
    private void ecc2$admitExternalBasicFluids(
            String liquid, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                && (Object) this == CellFamily.BASIC
                && Ecc2ExternalRecipes.basicCellAdmits(liquid)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "resultingPath", at = @At("HEAD"), cancellable = true)
    private void ecc2$resolveExternalNamedCellPath(
            String liquid, int amount, CallbackInfoReturnable<String> cir) {
        if (amount == CellFamily.CAPACITY) {
            String cellId =
                    Ecc2ExternalRecipes.namedCellForFluid(
                            (Object) this == CellFamily.BASIC, liquid);
            if (cellId != null) {
                cir.setReturnValue(cellId);
            }
        }
    }
}
