package com.ecocraft2.common.kinetic;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;

import java.util.Objects;

/**
 * Lossless fractional rotor wear accumulator for wind and water kinetic generators.
 *
 * <p>Wear accumulates ONLY while the rotor is actively spinning and producing KU ({@code active == true}).
 * Every {@value #WEAR_INTERVAL_TICKS} active ticks, adds {@code (overWind ? 4.0 : 1.0) * wearMultiplier}
 * to the internal fractional accumulator and emits the whole integer durability points to deduct,
 * preserving the fractional remainder across ticks and NBT saves so {@code 0.5x} wear never loses precision.
 */
public final class RotorWearAccumulator {

    /** Number of active ticks per wear evaluation step (1 second = 20 ticks). */
    public static final int WEAR_INTERVAL_TICKS = Ecc2ConfigDefaults.ROTOR_WEAR_INTERVAL_TICKS;

    /** Base wear per interval during safe wind/water operation. */
    public static final double NORMAL_BASE_WEAR = 1.0;

    /** Base wear per interval when wind exceeds the rotor's {@link RotorSpec#maxSafeWind()}. */
    public static final double OVERWIND_BASE_WEAR = Ecc2ConfigDefaults.ROTOR_OVERWIND_WEAR_MULTIPLIER;

    private static final double WHOLE_EPSILON = 1e-12;

    private int activeTickCounter;
    private double fractionalWear;

    /**
     * Records a single server tick for a kinetic generator.
     *
     * @param active   {@code true} only when a rotor is installed, spinning, and producing {@code KU > 0}
     * @param overWind {@code true} if current wind exceeds {@link RotorSpec#maxSafeWind()}
     * @param tier     kinetic machine tier providing the wear multiplier
     * @return whole durability points to deduct from the rotor on this tick ({@code >= 0})
     */
    public int recordTick(boolean active, boolean overWind, KineticMachineTier tier) {
        return recordTick(active, overWind, Objects.requireNonNull(tier, "tier").wearMultiplier());
    }

    /**
     * Records a single server tick with an explicit wear multiplier.
     *
     * @param active         {@code true} only when producing {@code KU > 0} and spinning
     * @param overWind       {@code true} if wind exceeds safe rotor limit
     * @param wearMultiplier machine wear multiplier ({@code >= 0})
     * @return whole durability points to deduct on this tick ({@code >= 0})
     */
    public int recordTick(boolean active, boolean overWind, double wearMultiplier) {
        if (!active || wearMultiplier <= 0.0 || !Double.isFinite(wearMultiplier)) {
            return 0;
        }
        activeTickCounter++;
        if (activeTickCounter < WEAR_INTERVAL_TICKS) {
            return 0;
        }
        activeTickCounter -= WEAR_INTERVAL_TICKS;
        return recordWearInterval(overWind, wearMultiplier);
    }

    /**
     * Applies one complete 20-tick wear interval directly to the fractional accumulator.
     *
     * @param overWind       {@code true} if wind exceeds safe rotor limit
     * @param wearMultiplier machine wear multiplier ({@code >= 0})
     * @return whole durability points to deduct ({@code >= 0})
     */
    public int recordWearInterval(boolean overWind, double wearMultiplier) {
        if (wearMultiplier <= 0.0 || !Double.isFinite(wearMultiplier)) {
            return 0;
        }
        double baseWear = overWind ? OVERWIND_BASE_WEAR : NORMAL_BASE_WEAR;
        fractionalWear += baseWear * wearMultiplier;
        int wholeDamage = (int) Math.floor(fractionalWear + WHOLE_EPSILON);
        if (wholeDamage > 0) {
            fractionalWear = Math.max(0.0, fractionalWear - wholeDamage);
            if (fractionalWear < WHOLE_EPSILON) {
                fractionalWear = 0.0;
            }
        }
        return wholeDamage;
    }

    /** Returns the active ticks accumulated toward the next 20-tick wear interval ({@code 0..19}). */
    public int activeTickCounter() {
        return activeTickCounter;
    }

    /** Returns the persisted fractional wear remainder in {@code [0.0, 1.0)}. */
    public double fractionalWear() {
        return fractionalWear;
    }

    /**
     * Restores persisted accumulator state from NBT/serialization.
     *
     * @param activeTickCounter saved active tick counter
     * @param fractionalWear    saved fractional wear remainder
     */
    public void restore(int activeTickCounter, double fractionalWear) {
        this.activeTickCounter = Math.floorMod(Math.max(0, activeTickCounter), WEAR_INTERVAL_TICKS);
        if (!Double.isFinite(fractionalWear) || fractionalWear < 0.0) {
            this.fractionalWear = 0.0;
        } else {
            this.fractionalWear = fractionalWear;
        }
    }

    /** Resets both tick counter and fractional remainder to zero. */
    public void reset() {
        this.activeTickCounter = 0;
        this.fractionalWear = 0.0;
    }
}
