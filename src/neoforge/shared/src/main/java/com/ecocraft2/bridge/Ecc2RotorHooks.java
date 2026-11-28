package com.ecocraft2.bridge;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Rotor specification lookups, wind sampling, and damage application helpers used by EcoCraft 2's
 * six Advanced Wind/Water Kinetic Generators.
 *
 * <p>Vanilla IC2NE wind/water rotor durability registration, wear ticking, and item tooltips are
 * implemented natively by base IC2NE.
 */
public final class Ecc2RotorHooks {

    /** Classic rotor durability and safe operating envelope. */
    public record RotorEntry(
            String path,
            String itemId,
            int maxDurability,
            int maxSafeWind,
            int rating,
            int windDiameter,
            int waterDiameter,
            boolean waterAllowed) {}

    private static final Map<String, RotorEntry> BY_PATH =
            Map.of(
                    "wooden_rotor",
                    new RotorEntry("wooden_rotor", "mhera:wooden_rotor", 10_800, 60, 70, 5, 4, false),
                    "bronze_rotor",
                    new RotorEntry("bronze_rotor", "mhera:bronze_rotor", 86_400, 75, 100, 7, 4, true),
                    "iron_rotor",
                    new RotorEntry("iron_rotor", "mhera:iron_rotor", 86_400, 75, 100, 7, 4, true),
                    "steel_rotor",
                    new RotorEntry("steel_rotor", "mhera:steel_rotor", 172_800, 90, 120, 9, 6, true),
                    "carbon_rotor",
                    new RotorEntry(
                            "carbon_rotor", "mhera:carbon_rotor", 604_800, 110, 150, 11, 8, true));

    private static final Map<String, RotorEntry> BY_ITEM_ID =
            Map.of(
                    "mhera:wooden_rotor", BY_PATH.get("wooden_rotor"),
                    "mhera:bronze_rotor", BY_PATH.get("bronze_rotor"),
                    "mhera:iron_rotor", BY_PATH.get("iron_rotor"),
                    "mhera:steel_rotor", BY_PATH.get("steel_rotor"),
                    "mhera:carbon_rotor", BY_PATH.get("carbon_rotor"));

    private Ecc2RotorHooks() {}

    /** Looks up the classic rotor entry for an item stack, or {@code null} if not a rotor. */
    public static RotorEntry lookup(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return BY_ITEM_ID.get(id);
    }

    /** Returns {@code true} if {@code stack} is one of IC2NE's five kinetic rotors. */
    public static boolean isRotor(ItemStack stack) {
        return lookup(stack) != null;
    }

    /** Returns {@code true} if {@code stack} is a rotor allowed inside water kinetic generators. */
    public static boolean isWaterRotor(ItemStack stack) {
        var entry = lookup(stack);
        return entry != null && entry.waterAllowed();
    }

    /** Returns the classic max durability of a rotor stack, or {@code 0} if not a rotor. */
    public static int maxDurability(ItemStack stack) {
        var entry = lookup(stack);
        if (entry == null) {
            return 0;
        }
        int itemMax = stack.getMaxDamage();
        return itemMax > 0 ? itemMax : entry.maxDurability();
    }

    /** Returns the safe maximum wind strength (MCW) before 4x over-wind wear applies. */
    public static int maxSafeWind(ItemStack stack) {
        var entry = lookup(stack);
        return entry == null ? 0 : entry.maxSafeWind();
    }

    /**
     * Computes IC2NE's raw wind strength (MCW) at the rotor center for a wind kinetic generator.
     * Matches {@code WorkshopKinetic#sampleWind} exactly.
     */
    public static int computeWindStrength(ServerLevel level, BlockPos pos, Direction facing) {
        var center = pos.relative(facing.getOpposite());
        long sampled = level.getGameTime() / 200L;
        int variation = 10 * Math.floorMod(sampled + pos.getX() + pos.getZ(), 4) - 10;
        int altitude = Math.max(0, Math.min(200, center.getY() - 64));
        return Math.max(0, altitude + variation);
    }

    /**
     * Deducts {@code damagePoints} from a rotor in {@code inventory[slot]}, breaking and removing
     * the rotor if its remaining durability reaches zero.
     *
     * @return {@code true} if the rotor broke and was cleared from the slot
     */
    public static boolean applyRotorDamage(Container inventory, int slot, int damagePoints) {
        if (damagePoints <= 0) {
            return false;
        }
        var rotor = inventory.getItem(slot);
        int max = maxDurability(rotor);
        if (max <= 0) {
            return false;
        }
        int nextDamage = rotor.getDamageValue() + damagePoints;
        if (nextDamage >= max) {
            inventory.setItem(slot, ItemStack.EMPTY);
            inventory.setChanged();
            return true;
        }
        rotor.setDamageValue(nextDamage);
        inventory.setChanged();
        return false;
    }
}
