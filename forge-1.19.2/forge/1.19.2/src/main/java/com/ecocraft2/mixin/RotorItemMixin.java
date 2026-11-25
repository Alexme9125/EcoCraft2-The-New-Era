package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2RotorHooks;
import com.industrialage.ia2.forge.content.ForgeContent;
import com.industrialage.ia2.industrial.WorkshopPlatform;
import java.util.function.Function;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Registers IC2NE's five kinetic rotors with exact classic IC2 durabilities (10,800 / 86,400 /
 * 86,400 / 172,800 / 604,800) so they natively track damage and display durability bars in Forge
 * 1.19.2.
 */
@Mixin(value = WorkshopPlatform.class, remap = false)
public final class RotorItemMixin {

    @Shadow
    @Final
    private static DeferredRegister<Item> ITEMS;

    @Inject(method = "registerItem", at = @At("HEAD"), cancellable = true)
    private static void ecc2$registerRotorWithClassicDurability(
            String path,
            Function<Item.Properties, Item> factory,
            CallbackInfoReturnable<RegistryObject<Item>> cir) {
        int durability = Ecc2RotorHooks.durabilityForPath(path);
        if (durability > 0) {
            cir.setReturnValue(
                    ITEMS.register(
                            path,
                            () -> new Item(ForgeContent.itemProperties(path).durability(durability))));
        }
    }
}
