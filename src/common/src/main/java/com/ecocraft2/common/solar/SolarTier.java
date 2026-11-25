package com.ecocraft2.common.solar;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.config.Ecc2Ids;

/**
 * Canonical Advanced Solar Panels 2 tiers and IC2 voltage tier packet sizing.
 */
public enum SolarTier {

    /** Advanced Solar Panel: 8/1 EU/t, 32,000 EU, Tier 1 (LV, 32 EU/t). */
    ADVANCED(
            Ecc2Ids.ADVANCED_SOLAR_PANEL,
            Ecc2ConfigDefaults.ADV_SOLAR_GEN_DAY,
            Ecc2ConfigDefaults.ADV_SOLAR_GEN_NIGHT,
            Ecc2ConfigDefaults.ADV_SOLAR_STORAGE,
            Ecc2ConfigDefaults.ADV_SOLAR_TIER
    ),

    /** Hybrid Solar Panel: 64/8 EU/t, 100,000 EU, Tier 2 (MV, 128 EU/t). */
    HYBRID(
            Ecc2Ids.HYBRID_SOLAR_PANEL,
            Ecc2ConfigDefaults.HYBRID_SOLAR_GEN_DAY,
            Ecc2ConfigDefaults.HYBRID_SOLAR_GEN_NIGHT,
            Ecc2ConfigDefaults.HYBRID_SOLAR_STORAGE,
            Ecc2ConfigDefaults.HYBRID_SOLAR_TIER
    ),

    /** Ultimate Hybrid Solar Panel: 512/64 EU/t, 1,000,000 EU, Tier 3 (HV, 512 EU/t). */
    ULTIMATE_HYBRID(
            Ecc2Ids.ULTIMATE_HYBRID_SOLAR_PANEL,
            Ecc2ConfigDefaults.ULTIMATE_SOLAR_GEN_DAY,
            Ecc2ConfigDefaults.ULTIMATE_SOLAR_GEN_NIGHT,
            Ecc2ConfigDefaults.ULTIMATE_SOLAR_STORAGE,
            Ecc2ConfigDefaults.ULTIMATE_SOLAR_TIER
    ),

    /** Quantum Solar Panel: 4,096/2,048 EU/t, 10,000,000 EU, Tier 5 (IV, 8,192 EU/t). */
    QUANTUM(
            Ecc2Ids.QUANTUM_SOLAR_PANEL,
            Ecc2ConfigDefaults.QUANTUM_SOLAR_GEN_DAY,
            Ecc2ConfigDefaults.QUANTUM_SOLAR_GEN_NIGHT,
            Ecc2ConfigDefaults.QUANTUM_SOLAR_STORAGE,
            Ecc2ConfigDefaults.QUANTUM_SOLAR_TIER
    );

    private final String blockPath;
    private final int defaultDayPower;
    private final int defaultNightPower;
    private final int defaultMaxStorage;
    private final int defaultTier;

    SolarTier(String blockPath, int defaultDayPower, int defaultNightPower, int defaultMaxStorage, int defaultTier) {
        this.blockPath = blockPath;
        this.defaultDayPower = defaultDayPower;
        this.defaultNightPower = defaultNightPower;
        this.defaultMaxStorage = defaultMaxStorage;
        this.defaultTier = defaultTier;
    }

    /** Returns the canonical block registry path (without namespace). */
    public String blockPath() {
        return blockPath;
    }

    /** Returns the default daytime generation rate in EU/t. */
    public int defaultDayPower() {
        return defaultDayPower;
    }

    /** Returns the default nighttime/rain generation rate in EU/t. */
    public int defaultNightPower() {
        return defaultNightPower;
    }

    /** Returns the default internal EU storage capacity. */
    public int defaultMaxStorage() {
        return defaultMaxStorage;
    }

    /** Returns the default IC2 output power tier. */
    public int defaultTier() {
        return defaultTier;
    }

    /** Returns the default maximum single-packet EU output for this solar tier. */
    public double defaultPacketSize() {
        return packetSize(defaultTier);
    }

    /**
     * Computes the IC2 voltage packet size for a power tier using
     * {@code Math.scalb(8.0, 2 * Math.min(tier, 30))}.
     *
     * <p>Examples: tier 1 = 32, tier 2 = 128, tier 3 = 512, tier 4 = 2048,
     * tier 5 = 8192, tier 6 = 32768.
     *
     * @param tier IC2 voltage tier
     * @return maximum EU per packet, or {@code 0.0} if {@code tier < 0}
     */
    public static double packetSize(int tier) {
        if (tier < 0) {
            return 0.0;
        }
        return Math.scalb(8.0, 2 * Math.min(tier, 30));
    }
}
