package com.ecocraft2.common.giant;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;

import java.util.Objects;

/**
 * Extensible specification describing a 3x3x3 Giant Machine type.
 *
 * <p>Third-party addons or future EcoCraft 2 expansions can construct custom {@code GiantMachineSpec}
 * instances and register them in {@link GiantMachineRegistry} without modifying engine code.
 *
 * @param id                     unique lowercase type identifier (e.g. {@code "macerator"})
 * @param unitBlockPath          block registry path of the unit block (e.g. {@code "giant_macerator_unit"})
 * @param sourceMachineId        corresponding single-block IC2 machine ID (e.g. {@code "ic2:macerator"})
 * @param baseOperationTicks     base ticks for one operation before overclockers ({@code > 0})
 * @param baseEuPerTick          base EU/t per active operation before overclockers ({@code > 0})
 * @param baseTier               base IC2 input voltage tier (default 3 = HV = 512 EU/t, {@code >= 1})
 * @param waterTankCapacityMb    internal water tank capacity in mB ({@code 0} if machine does not use water)
 * @param waterPerOperationMb    water consumed per single operation in mB ({@code 0} if machine does not use water)
 * @param scrapChanceDenominator denominator for independent scrap chance ({@code 8} for 1/8 recycler, {@code 0} if unused)
 */
public record GiantMachineSpec(
        String id,
        String unitBlockPath,
        String sourceMachineId,
        int baseOperationTicks,
        int baseEuPerTick,
        int baseTier,
        int waterTankCapacityMb,
        int waterPerOperationMb,
        int scrapChanceDenominator
) {

    public GiantMachineSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(unitBlockPath, "unitBlockPath");
        Objects.requireNonNull(sourceMachineId, "sourceMachineId");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (unitBlockPath.isBlank()) {
            throw new IllegalArgumentException("unitBlockPath must not be blank");
        }
        if (sourceMachineId.isBlank()) {
            throw new IllegalArgumentException("sourceMachineId must not be blank");
        }
        if (baseOperationTicks <= 0) {
            throw new IllegalArgumentException("baseOperationTicks must be > 0");
        }
        if (baseEuPerTick <= 0) {
            throw new IllegalArgumentException("baseEuPerTick must be > 0");
        }
        if (baseTier < 1) {
            throw new IllegalArgumentException("baseTier must be >= 1");
        }
        if (waterTankCapacityMb < 0) {
            throw new IllegalArgumentException("waterTankCapacityMb must be >= 0");
        }
        if (waterPerOperationMb < 0) {
            throw new IllegalArgumentException("waterPerOperationMb must be >= 0");
        }
        if (waterPerOperationMb > 0 && waterTankCapacityMb < waterPerOperationMb) {
            throw new IllegalArgumentException("waterTankCapacityMb must be >= waterPerOperationMb");
        }
        if (scrapChanceDenominator < 0) {
            throw new IllegalArgumentException("scrapChanceDenominator must be >= 0");
        }
    }

    /** Returns {@code true} if this giant machine requires water per operation (e.g. Ore Washer). */
    public boolean usesWater() {
        return waterPerOperationMb > 0 && waterTankCapacityMb > 0;
    }

    /** Returns {@code true} if this giant machine produces probabilistic recycler scrap. */
    public boolean isRecycler() {
        return scrapChanceDenominator > 0;
    }

    /** Returns total EU required for a single un-upgraded operation ({@code baseOperationTicks * baseEuPerTick}). */
    public int singleOperationReserveEu() {
        return Math.multiplyExact(baseOperationTicks, baseEuPerTick);
    }

    /**
     * Returns base internal EU storage capacity before Energy Storage Upgrades:
     * {@code max(32_000, 27 * singleOperationReserveEu())}.
     */
    public int baseEnergyCapacity() {
        long fullParallelReserve = (long) Ecc2ConfigDefaults.GIANT_LANE_COUNT
                * Ecc2ConfigDefaults.GIANT_MAX_BATCH_PER_LANE
                * singleOperationReserveEu();
        return (int) Math.max(Ecc2ConfigDefaults.GIANT_MIN_BASE_STORAGE, fullParallelReserve);
    }
}
