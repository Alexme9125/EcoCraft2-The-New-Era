package com.ecocraft2.common.kinetic;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;

import java.util.Objects;

/**
 * Pure kinetic power and over-wind calculations for standard and advanced Wind/Water Kinetic Generators.
 */
public final class KineticCalculation {

    private KineticCalculation() {
    }

    /**
     * Scales a base wind or water KU value by the machine tier's output multiplier and rounds to integer KU.
     *
     * @param baseKu base kinetic output in KU
     * @param tier   kinetic machine tier
     * @return scaled integer KU ({@code >= 0})
     */
    public static int scaleOutput(double baseKu, KineticMachineTier tier) {
        Objects.requireNonNull(tier, "tier");
        if (baseKu <= 0.0 || !Double.isFinite(baseKu)) {
            return 0;
        }
        return (int) Math.round(baseKu * tier.outputMultiplier());
    }

    /**
     * Scales a base wind or water KU value by the machine tier's output multiplier without integer rounding.
     *
     * @param baseKu base kinetic output in KU
     * @param tier   kinetic machine tier
     * @return exact scaled KU ({@code >= 0.0})
     */
    public static double scaleOutputExact(double baseKu, KineticMachineTier tier) {
        Objects.requireNonNull(tier, "tier");
        if (baseKu <= 0.0 || !Double.isFinite(baseKu)) {
            return 0.0;
        }
        return baseKu * tier.outputMultiplier();
    }

    /**
     * Tests whether wind strength exceeds the rotor's maximum safe MCW limit (triggering 4x wear).
     *
     * @param windStrengthMcw current wind strength in MCW
     * @param rotor           installed rotor specification
     * @return {@code true} iff {@code windStrengthMcw > rotor.maxSafeWind()}
     */
    public static boolean isOverWind(double windStrengthMcw, RotorSpec rotor) {
        Objects.requireNonNull(rotor, "rotor");
        return Double.isFinite(windStrengthMcw) && windStrengthMcw > rotor.maxSafeWind();
    }

    /**
     * Returns whether the given rotor is permitted inside a Water Kinetic Generator.
     *
     * @param rotor rotor specification
     * @return {@code true} for bronze/iron/steel/carbon; {@code false} for wooden rotor
     */
    public static boolean canUseInWater(RotorSpec rotor) {
        return Objects.requireNonNull(rotor, "rotor").waterAllowed();
    }

    /**
     * Converts kinetic units (KU) to electrical units (EU) using the IC2 standard 4 KU = 1 EU ratio.
     *
     * @param ku kinetic output in KU
     * @return equivalent EU ({@code >= 0.0})
     */
    public static double kuToEu(double ku) {
        if (ku <= 0.0 || !Double.isFinite(ku)) {
            return 0.0;
        }
        return ku / Ecc2ConfigDefaults.KU_PER_EU;
    }

    /**
     * Computes the effective wear rate per 20 active ticks for a given over-wind state and machine tier.
     *
     * @param overWind whether wind exceeds safe rotor threshold
     * @param tier     kinetic machine tier
     * @return durability points per 20 active ticks
     */
    public static double effectiveWearPerSecond(boolean overWind, KineticMachineTier tier) {
        Objects.requireNonNull(tier, "tier");
        double base = overWind ? RotorWearAccumulator.OVERWIND_BASE_WEAR : RotorWearAccumulator.NORMAL_BASE_WEAR;
        return base * tier.wearMultiplier();
    }
}
