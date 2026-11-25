package com.ecocraft2.common.solar;

import com.ecocraft2.common.config.Ecc2Ids;

/**
 * Specifications for the three EcoCraft 2 solar helmets.
 */
public enum SolarHelmetSpec {

    /**
     * Advanced Solar Helmet: 8/1 EU/t, Tier 3, 1,000,000 EU, 3,000 EU/t transfer,
     * 800 EU/damage, 0.9 absorption ratio (Nano Helmet equivalent), not dyeable.
     */
    ADVANCED(
            Ecc2Ids.ADVANCED_SOLAR_HELMET,
            SolarTier.ADVANCED,
            3,
            1_000_000,
            3_000,
            800,
            0.9,
            false,
            false
    ),

    /**
     * Hybrid Solar Helmet: 64/8 EU/t, Tier 4, 10,000,000 EU, 10,000 EU/t transfer,
     * 2,000 EU/damage, 1.0 absorption ratio (Quantum Helmet equivalent), quantum abilities, dyeable.
     */
    HYBRID(
            Ecc2Ids.HYBRID_SOLAR_HELMET,
            SolarTier.HYBRID,
            4,
            10_000_000,
            10_000,
            2_000,
            1.0,
            true,
            true
    ),

    /**
     * Ultimate Hybrid Solar Helmet: 512/64 EU/t, Tier 4, 10,000,000 EU, 10,000 EU/t transfer,
     * 2,000 EU/damage, 1.0 absorption ratio (Quantum Helmet equivalent), quantum abilities, dyeable.
     */
    ULTIMATE_HYBRID(
            Ecc2Ids.ULTIMATE_HYBRID_SOLAR_HELMET,
            SolarTier.ULTIMATE_HYBRID,
            4,
            10_000_000,
            10_000,
            2_000,
            1.0,
            true,
            true
    );

    private final String itemPath;
    private final SolarTier solarTier;
    private final int tier;
    private final int capacity;
    private final int transferLimit;
    private final int euPerDamage;
    private final double damageAbsorptionRatio;
    private final boolean quantumAbilities;
    private final boolean dyeable;

    SolarHelmetSpec(
            String itemPath,
            SolarTier solarTier,
            int tier,
            int capacity,
            int transferLimit,
            int euPerDamage,
            double damageAbsorptionRatio,
            boolean quantumAbilities,
            boolean dyeable
    ) {
        this.itemPath = itemPath;
        this.solarTier = solarTier;
        this.tier = tier;
        this.capacity = capacity;
        this.transferLimit = transferLimit;
        this.euPerDamage = euPerDamage;
        this.damageAbsorptionRatio = damageAbsorptionRatio;
        this.quantumAbilities = quantumAbilities;
        this.dyeable = dyeable;
    }

    /** Returns the canonical item registry path (without namespace). */
    public String itemPath() {
        return itemPath;
    }

    /** Returns the backing {@link SolarTier} determining default day/night EU/t. */
    public SolarTier solarTier() {
        return solarTier;
    }

    /** Returns the default daytime generation in EU/t. */
    public int defaultDayPower() {
        return solarTier.defaultDayPower();
    }

    /** Returns the default nighttime/rain generation in EU/t. */
    public int defaultNightPower() {
        return solarTier.defaultNightPower();
    }

    /** Returns the IC2 electric item voltage tier (3 for Advanced, 4 for Hybrid/Ultimate). */
    public int tier() {
        return tier;
    }

    /** Returns the internal EU battery capacity. */
    public int capacity() {
        return capacity;
    }

    /** Returns the max EU/t charge/discharge transfer limit. */
    public int transferLimit() {
        return transferLimit;
    }

    /** Returns EU consumed per 1.0 point of absorbed damage. */
    public int euPerDamage() {
        return euPerDamage;
    }

    /** Returns armor damage absorption ratio (0.9 for Nano-class, 1.0 for Quantum-class). */
    public double damageAbsorptionRatio() {
        return damageAbsorptionRatio;
    }

    /** Returns {@code true} if this helmet grants full Quantum Helmet abilities. */
    public boolean hasQuantumAbilities() {
        return quantumAbilities;
    }

    /** Returns {@code true} if this helmet supports vanilla leather dyeing and color preservation. */
    public boolean isDyeable() {
        return dyeable;
    }
}
