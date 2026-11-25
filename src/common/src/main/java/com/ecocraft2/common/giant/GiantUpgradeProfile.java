package com.ecocraft2.common.giant;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;

/**
 * IC2 machine upgrade counts and scaling math (Overclockers, Transformers, Energy Storage).
 *
 * @param overclockers    number of Overclocker Upgrades ({@code >= 0})
 * @param transformers    number of Transformer Upgrades ({@code >= 0})
 * @param storageUpgrades number of Energy Storage Upgrades ({@code >= 0})
 */
public record GiantUpgradeProfile(int overclockers, int transformers, int storageUpgrades) {

    /** Profile with zero upgrades installed. */
    public static final GiantUpgradeProfile NONE = new GiantUpgradeProfile(0, 0, 0);

    /** Per-overclocker processing time multiplier ({@code 0.7^n}). */
    public static final double OVERCLOCKER_TIME_FACTOR = 0.7;

    /** Per-overclocker energy consumption multiplier ({@code 1.6^n}). */
    public static final double OVERCLOCKER_ENERGY_FACTOR = 1.6;

    /** Maximum effective voltage tier achievable with transformer upgrades. */
    public static final int MAX_EFFECTIVE_TIER = 14;

    public GiantUpgradeProfile {
        if (overclockers < 0) {
            throw new IllegalArgumentException("overclockers must be >= 0");
        }
        if (transformers < 0) {
            throw new IllegalArgumentException("transformers must be >= 0");
        }
        if (storageUpgrades < 0) {
            throw new IllegalArgumentException("storageUpgrades must be >= 0");
        }
    }

    /**
     * Computes scaled operation duration in ticks: {@code max(1, round(baseTicks * 0.7^overclockers))}.
     *
     * @param baseTicks base un-upgraded ticks ({@code > 0})
     * @return upgraded operation ticks ({@code >= 1})
     */
    public int operationTicks(int baseTicks) {
        if (baseTicks <= 0) {
            throw new IllegalArgumentException("baseTicks must be > 0");
        }
        if (overclockers == 0) {
            return baseTicks;
        }
        double scaled = baseTicks * Math.pow(OVERCLOCKER_TIME_FACTOR, overclockers);
        return Math.max(1, (int) Math.round(scaled));
    }

    /**
     * Computes scaled EU/t per single active operation: {@code baseEuPerTick * 1.6^overclockers}.
     *
     * @param baseEuPerTick base EU/t ({@code > 0})
     * @return upgraded EU/t per operation
     */
    public double euPerTickPerOp(int baseEuPerTick) {
        if (baseEuPerTick <= 0) {
            throw new IllegalArgumentException("baseEuPerTick must be > 0");
        }
        if (overclockers == 0) {
            return baseEuPerTick;
        }
        return baseEuPerTick * Math.pow(OVERCLOCKER_ENERGY_FACTOR, overclockers);
    }

    /**
     * Computes effective IC2 input tier: {@code min(MAX_EFFECTIVE_TIER, baseTier + transformers)}.
     *
     * @param baseTier base tier ({@code >= 1})
     * @return effective voltage tier
     */
    public int effectiveTier(int baseTier) {
        if (baseTier < 1) {
            throw new IllegalArgumentException("baseTier must be >= 1");
        }
        return Math.min(MAX_EFFECTIVE_TIER, baseTier + transformers);
    }

    /**
     * Computes total EU storage capacity: {@code baseCapacity + 10_000 * storageUpgrades}.
     *
     * @param baseCapacity base machine EU capacity ({@code >= 0})
     * @return upgraded EU capacity
     */
    public double energyCapacity(int baseCapacity) {
        if (baseCapacity < 0) {
            throw new IllegalArgumentException("baseCapacity must be >= 0");
        }
        return baseCapacity + (double) Ecc2ConfigDefaults.GIANT_STORAGE_UPGRADE_BONUS * storageUpgrades;
    }
}
