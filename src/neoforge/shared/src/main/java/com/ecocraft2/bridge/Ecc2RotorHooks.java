package com.ecocraft2.bridge;

import com.ecocraft2.config.Ecc2Config;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Classic IC2 rotor durability specifications, baseline wear hooks for IC2NE's vanilla wind/water
 * kinetic generators, and rotor lifespan tooltip formatting.
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
                    new RotorEntry("wooden_rotor", "ic2:wooden_rotor", 10_800, 60, 70, 5, 4, false),
                    "bronze_rotor",
                    new RotorEntry("bronze_rotor", "ic2:bronze_rotor", 86_400, 75, 100, 7, 4, true),
                    "iron_rotor",
                    new RotorEntry("iron_rotor", "ic2:iron_rotor", 86_400, 75, 100, 7, 4, true),
                    "steel_rotor",
                    new RotorEntry("steel_rotor", "ic2:steel_rotor", 172_800, 90, 120, 9, 6, true),
                    "carbon_rotor",
                    new RotorEntry(
                            "carbon_rotor", "ic2:carbon_rotor", 604_800, 110, 150, 11, 8, true));

    private static final Map<String, RotorEntry> BY_ITEM_ID =
            Map.of(
                    "ic2:wooden_rotor", BY_PATH.get("wooden_rotor"),
                    "ic2:bronze_rotor", BY_PATH.get("bronze_rotor"),
                    "ic2:iron_rotor", BY_PATH.get("iron_rotor"),
                    "ic2:steel_rotor", BY_PATH.get("steel_rotor"),
                    "ic2:carbon_rotor", BY_PATH.get("carbon_rotor"));

    private static final Map<Object, WearState> VANILLA_WEAR =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final class WearState {
        int activeTicks;
        double fractionalWear;
    }

    private Ecc2RotorHooks() {}

    /**
     * Returns the classic durability for an unqualified IC2 item path (e.g. {@code "wooden_rotor"}),
     * or {@code 0} if the path is not a rotor.
     */
    public static int durabilityForPath(String unqualifiedPath) {
        if (unqualifiedPath == null) {
            return 0;
        }
        var entry = BY_PATH.get(unqualifiedPath);
        return entry == null ? 0 : entry.maxDurability();
    }

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
     * Applies 1.0x baseline classic rotor wear for vanilla IC2NE {@code wind_kinetic_generator}
     * and {@code water_kinetic_generator} block entities when producing positive KU.
     *
     * <p>Every 20 active ticks deducts {@code (overWind ? 4.0 : 1.0) * wearMultiplier} durability
     * points; breaks and removes the rotor when durability reaches zero.
     */
    public static void tickVanillaRotorWear(
            Object nodeKey,
            ServerLevel level,
            Container inventory,
            boolean isWind,
            int windStrength) {
        if (!Ecc2Config.enableVanillaRotorWear()) {
            return;
        }
        var rotor = inventory.getItem(0);
        var entry = lookup(rotor);
        if (entry == null) {
            return;
        }
        WearState state;
        synchronized (VANILLA_WEAR) {
            state = VANILLA_WEAR.computeIfAbsent(nodeKey, ignored -> new WearState());
        }
        state.activeTicks++;
        if (state.activeTicks < 20) {
            return;
        }
        state.activeTicks = 0;
        boolean overWind = isWind && windStrength > entry.maxSafeWind();
        double baseWear = overWind ? 4.0 : 1.0;
        state.fractionalWear += baseWear * Ecc2Config.vanillaRotorWearMultiplier();
        int wholeDamage = (int) Math.floor(state.fractionalWear);
        if (wholeDamage > 0) {
            state.fractionalWear -= wholeDamage;
            applyRotorDamage(inventory, 0, wholeDamage);
        }
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

    /** Appends remaining durability percentage, estimated lifespan, and max safe wind tooltips. */
    public static void appendRotorTooltip(ItemStack stack, List<Component> tooltip) {
        var entry = lookup(stack);
        if (entry == null) {
            return;
        }
        int max = maxDurability(stack);
        int remaining = Math.max(0, max - stack.getDamageValue());
        double percent = max > 0 ? (remaining * 100.0) / max : 0.0;
        double remainingHours = remaining / 3600.0;
        tooltip.add(
                Component.translatable(
                                "tooltip.ecc2.rotor.durability",
                                remaining,
                                max,
                                String.format(Locale.ROOT, "%.1f", percent))
                        .withStyle(percent > 25.0 ? ChatFormatting.GRAY : ChatFormatting.RED));
        tooltip.add(
                Component.translatable(
                                "tooltip.ecc2.rotor.remaining_hours",
                                String.format(Locale.ROOT, "%.1f", remainingHours))
                        .withStyle(ChatFormatting.AQUA));
        tooltip.add(
                Component.translatable("tooltip.ecc2.rotor.max_safe_wind", entry.maxSafeWind())
                        .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(
                Component.translatable(
                                entry.waterAllowed()
                                        ? "tooltip.ecc2.rotor.water_allowed"
                                        : "tooltip.ecc2.rotor.wind_only")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }
}
