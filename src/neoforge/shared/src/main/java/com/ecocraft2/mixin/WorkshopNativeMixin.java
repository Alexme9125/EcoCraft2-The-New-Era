package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2RotorHooks;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers IC2NE's five kinetic rotor items with exact classic IC2 durability (10,800 / 86,400 /
 * 86,400 / 172,800 / 604,800) so they natively track damage via {@code DataComponents.MAX_DAMAGE}
 * and display durability bars across Minecraft 1.21.1 and 26.x.
 */
@Mixin(targets = "com.industrialage.ia2.industrial.WorkshopNative", remap = false)
public final class WorkshopNativeMixin {

    @Inject(method = "unstackable", at = @At("HEAD"), cancellable = true)
    private static void ecc2$registerRotorWithDurability(
            DeferredRegister.Items items, String path, CallbackInfo ci) {
        int durability = Ecc2RotorHooks.durabilityForPath(path);
        if (durability > 0) {
            Ecc2BridgePlatform.registerDurableSimpleItem(items, path, durability);
            ci.cancel();
        }
    }
}
