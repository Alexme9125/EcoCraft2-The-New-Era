package com.ecocraft2.bridge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge 26.x platform helpers used by shared EcoCraft 2 Mixins and bridge classes. */
public final class Ecc2BridgePlatform {

    private Ecc2BridgePlatform() {}

    /** Looks up a registered item by its namespaced ID, or {@code null} if not registered. */
    public static Item findItem(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        var key = Identifier.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    /** Returns the namespaced item registry ID of {@code stack}, or {@code ""} if empty. */
    public static String itemId(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? ""
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Registers a damageable unstackable item via NeoForge 26.x's {@code DeferredRegister}. */
    public static void registerDurableSimpleItem(
            DeferredRegister.Items items, String path, int durability) {
        items.registerSimpleItem(path, () -> new Item.Properties().durability(durability));
    }
}
