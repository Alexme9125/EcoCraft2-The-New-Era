package com.ecocraft2.common.kinetic;

import com.ecocraft2.common.config.Ecc2Ids;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Classic IndustrialCraft 2 rotor durability and operating specifications.
 *
 * <p>Durability is measured in running seconds (1 durability point deducted per 20 active ticks
 * under 1.0x normal wear, or 4 points per 20 ticks when wind exceeds {@link #maxSafeWind()}).
 */
public enum RotorSpec {

    /** Wooden Rotor: 10,800s (3h), safe wind 60 MCW, rating 70, wind-only. */
    WOOD(Ecc2Ids.IC2_WOODEN_ROTOR, 10_800, 60, 70, 5, 4, false),

    /** Bronze Rotor: 86,400s (24h), safe wind 75 MCW, rating 100, wind & water. */
    BRONZE(Ecc2Ids.IC2_BRONZE_ROTOR, 86_400, 75, 100, 7, 4, true),

    /** Iron Rotor: 86,400s (24h), safe wind 75 MCW, rating 100, wind & water. */
    IRON(Ecc2Ids.IC2_IRON_ROTOR, 86_400, 75, 100, 7, 4, true),

    /** Steel Rotor: 172,800s (48h), safe wind 90 MCW, rating 120, wind & water. */
    STEEL(Ecc2Ids.IC2_STEEL_ROTOR, 172_800, 90, 120, 9, 6, true),

    /** Carbon Rotor: 604,800s (168h / 7d), safe wind 110 MCW, rating 150, wind & water. */
    CARBON(Ecc2Ids.IC2_CARBON_ROTOR, 604_800, 110, 150, 11, 8, true);

    private static final Map<String, RotorSpec> BY_ITEM_ID;

    static {
        Map<String, RotorSpec> map = new HashMap<>();
        for (RotorSpec spec : values()) {
            map.put(spec.itemId, spec);
        }
        BY_ITEM_ID = Collections.unmodifiableMap(map);
    }

    private final String itemId;
    private final int maxDurability;
    private final int maxSafeWind;
    private final int rating;
    private final int windDiameter;
    private final int waterDiameter;
    private final boolean waterAllowed;

    RotorSpec(
            String itemId,
            int maxDurability,
            int maxSafeWind,
            int rating,
            int windDiameter,
            int waterDiameter,
            boolean waterAllowed
    ) {
        this.itemId = itemId;
        this.maxDurability = maxDurability;
        this.maxSafeWind = maxSafeWind;
        this.rating = rating;
        this.windDiameter = windDiameter;
        this.waterDiameter = waterDiameter;
        this.waterAllowed = waterAllowed;
    }

    /** Returns the full IC2 item resource ID (e.g. {@code "ic2:carbon_rotor"}). */
    public String itemId() {
        return itemId;
    }

    /** Returns classic max durability in normal operating seconds. */
    public int maxDurability() {
        return maxDurability;
    }

    /** Returns maximum safe wind strength in MCW before 4x over-wind wear triggers. */
    public int maxSafeWind() {
        return maxSafeWind;
    }

    /** Returns the rotor efficiency rating percentage. */
    public int rating() {
        return rating;
    }

    /** Returns the wind rotor blade diameter in blocks. */
    public int windDiameter() {
        return windDiameter;
    }

    /** Returns the water rotor blade diameter in blocks. */
    public int waterDiameter() {
        return waterDiameter;
    }

    /** Returns {@code true} if this rotor may be installed in water kinetic generators. */
    public boolean waterAllowed() {
        return waterAllowed;
    }

    /** Returns nominal continuous operating lifespan in hours at 1.0x wear. */
    public double nominalLifespanHours() {
        return maxDurability / 3600.0;
    }

    /**
     * Computes remaining durability percentage in {@code [0.0, 100.0]}.
     *
     * @param remainingDurability remaining durability points
     * @return clamped percentage
     */
    public double remainingPercent(int remainingDurability) {
        if (remainingDurability <= 0) {
            return 0.0;
        }
        if (remainingDurability >= maxDurability) {
            return 100.0;
        }
        return (remainingDurability * 100.0) / maxDurability;
    }

    /**
     * Estimates remaining real-time hours at a given wear multiplier.
     *
     * @param remainingDurability remaining durability points
     * @param wearMultiplier      effective wear rate multiplier (must be {@code > 0})
     * @return estimated remaining hours, or {@code 0.0} if depleted
     */
    public double estimatedRemainingHours(int remainingDurability, double wearMultiplier) {
        if (remainingDurability <= 0 || wearMultiplier <= 0.0 || !Double.isFinite(wearMultiplier)) {
            return 0.0;
        }
        int clamped = Math.min(remainingDurability, maxDurability);
        return (clamped / wearMultiplier) / 3600.0;
    }

    /**
     * Looks up a {@link RotorSpec} by its full IC2 item ID (e.g. {@code "ic2:steel_rotor"}).
     *
     * @param itemId full namespaced item ID
     * @return matching spec, or {@link Optional#empty()} if unrecognized
     */
    public static Optional<RotorSpec> byItemId(String itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_ITEM_ID.get(itemId));
    }
}
