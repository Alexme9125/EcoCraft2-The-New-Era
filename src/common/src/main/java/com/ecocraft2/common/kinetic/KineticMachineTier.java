package com.ecocraft2.common.kinetic;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;

/**
 * Operating tiers for standard and advanced Wind/Water Kinetic Generators.
 */
public enum KineticMachineTier {

    /** Vanilla IC2 wind/water kinetic generator: 100% KU output, 100% rotor wear. */
    STANDARD(
            Ecc2ConfigDefaults.STANDARD_KINETIC_OUTPUT_MULT,
            Ecc2ConfigDefaults.STANDARD_KINETIC_WEAR_MULT
    ),

    /** Durable variant: 80% KU output (-20%), 50% rotor wear (-50%). */
    DURABLE(
            Ecc2ConfigDefaults.DURABLE_KINETIC_OUTPUT_MULT,
            Ecc2ConfigDefaults.DURABLE_KINETIC_WEAR_MULT
    ),

    /** Overclocked variant: 220% KU output (+120%), 200% rotor wear (+100%). */
    OVERCLOCKED(
            Ecc2ConfigDefaults.OVERCLOCKED_KINETIC_OUTPUT_MULT,
            Ecc2ConfigDefaults.OVERCLOCKED_KINETIC_WEAR_MULT
    ),

    /** Precision variant: 240% KU output (+140%), 50% rotor wear (-50%). */
    PRECISION(
            Ecc2ConfigDefaults.PRECISION_KINETIC_OUTPUT_MULT,
            Ecc2ConfigDefaults.PRECISION_KINETIC_WEAR_MULT
    );

    private final double outputMultiplier;
    private final double wearMultiplier;

    KineticMachineTier(double outputMultiplier, double wearMultiplier) {
        this.outputMultiplier = outputMultiplier;
        this.wearMultiplier = wearMultiplier;
    }

    /** Returns the KU output multiplier applied to base wind/water kinetic output. */
    public double outputMultiplier() {
        return outputMultiplier;
    }

    /** Returns the rotor durability wear multiplier applied every 20 active ticks. */
    public double wearMultiplier() {
        return wearMultiplier;
    }
}
