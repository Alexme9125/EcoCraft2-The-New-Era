package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.industrialage.ia2.logistics.TransportPlatform;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Guards IC2NE's {@code WorkshopPlans#matches} so a universal fluid cell ({@code ic2:fluid_cell})
 * that already holds encoded fluid cannot be accepted or consumed as an empty cell container in
 * solid/classic/bottle canning recipes.
 */
@Mixin(targets = "com.industrialage.ia2.industrial.WorkshopPlans", remap = false)
public final class WorkshopPlansMixin {

    @Inject(method = "matches", at = @At("RETURN"), cancellable = true)
    private static void ecc2$requireEmptyUniversalCellForCanning(
            ItemStack item, @Coerce Object expected, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()
                && "ic2:fluid_cell".equals(Ecc2BridgePlatform.itemId(item))
                && TransportPlatform.cellContainsFluid(item)) {
            cir.setReturnValue(false);
        }
    }
}
