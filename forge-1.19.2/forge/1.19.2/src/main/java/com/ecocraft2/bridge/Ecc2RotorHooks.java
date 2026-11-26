package com.ecocraft2.bridge;

import com.ecocraft2.common.kinetic.RotorSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Rotor specification lookups, wind sampling, and damage application helpers used by EcoCraft 2's
 * six Advanced Wind/Water Kinetic Generators in Forge 1.19.2.
 *
 * <p>Vanilla IC2NE wind/water rotor durability registration, wear ticking, and item tooltips are
 * implemented natively by base IC2NE.
 */
public final class Ecc2RotorHooks {

    private Ecc2RotorHooks() {}

    /** Looks up the {@link RotorSpec} for an item stack, or {@code null} if not a rotor. */
    public static RotorSpec lookup(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return RotorSpec.byItemId(Ecc2BridgePlatform.itemId(stack)).orElse(null);
    }

    public static boolean isRotor(ItemStack stack) {
        return lookup(stack) != null;
    }

    public static boolean isWaterRotor(ItemStack stack) {
        var spec = lookup(stack);
        return spec != null && spec.waterAllowed();
    }

    public static int maxDurability(ItemStack stack) {
        var spec = lookup(stack);
        if (spec == null) {
            return 0;
        }
        int itemMax = stack.getMaxDamage();
        return itemMax > 0 ? itemMax : spec.maxDurability();
    }

    public static int maxSafeWind(ItemStack stack) {
        var spec = lookup(stack);
        return spec == null ? 0 : spec.maxSafeWind();
    }

    /**
     * Computes IC2NE's raw wind strength (MCW) at the rotor center for a wind kinetic generator.
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
