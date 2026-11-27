package com.ecocraft2.common.gas;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.config.Ecc2Ids;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Combustible gas fuels supported by the Gas Furnace and Gas Generator.
 */
public enum GasFuelSpec {

    /** Water Gas ({@code ecc2:water_gas}): 10,000 EU per 1,000 mB (10 EU/mB) at 32 EU/t. */
    WATER_GAS(
            Ecc2Ids.WATER_GAS_ID,
            Ecc2Ids.COMMON_TAG_WATER_GAS,
            Ecc2ConfigDefaults.WATER_GAS_EU_PER_BUCKET,
            Ecc2ConfigDefaults.WATER_GAS_GEN_EU_PER_TICK
    ),

    /** Natural Gas ({@code ecc2:natural_gas}): 10,000 EU per 1,000 mB (10 EU/mB) at 64 EU/t. */
    NATURAL_GAS(
            Ecc2Ids.NATURAL_GAS_ID,
            Ecc2Ids.COMMON_TAG_NATURAL_GAS,
            Ecc2ConfigDefaults.NATURAL_GAS_EU_PER_BUCKET,
            Ecc2ConfigDefaults.NATURAL_GAS_GEN_EU_PER_TICK
    ),

    /** IC2 Biogas ({@code ic2:biogas}): 16,000 EU per 1,000 mB (16 EU/mB) at 32 EU/t. */
    BIOGAS(
            Ecc2Ids.IC2_BIOGAS_ID,
            Ecc2Ids.COMMON_TAG_BIOGAS,
            Ecc2ConfigDefaults.BIOGAS_EU_PER_BUCKET,
            Ecc2ConfigDefaults.BIOGAS_GEN_EU_PER_TICK
    );

    private static final Map<String, GasFuelSpec> BY_FLUID_ID;

    static {
        Map<String, GasFuelSpec> map = new HashMap<>();
        for (GasFuelSpec spec : values()) {
            map.put(spec.fluidId, spec);
        }
        BY_FLUID_ID = Collections.unmodifiableMap(map);
    }

    private final String fluidId;
    private final String commonTagPath;
    private final int euPerBucket;
    private final int euPerTick;

    GasFuelSpec(String fluidId, String commonTagPath, int euPerBucket, int euPerTick) {
        this.fluidId = fluidId;
        this.commonTagPath = commonTagPath;
        this.euPerBucket = euPerBucket;
        this.euPerTick = euPerTick;
    }

    /** Returns the full fluid resource ID (e.g. {@code "ecc2:water_gas"} or {@code "ic2:biogas"}). */
    public String fluidId() {
        return fluidId;
    }

    /** Returns the platform-neutral common fluid tag path (e.g. {@code "water_gas"}). */
    public String commonTagPath() {
        return commonTagPath;
    }

    /** Returns total EU generated per 1,000 mB (1 bucket) of this gas. */
    public int euPerBucket() {
        return euPerBucket;
    }

    /** Returns nominal generator output rate in EU/t when burning this gas. */
    public int euPerTick() {
        return euPerTick;
    }

    /** Returns exact EU generated per 1 mB of gas ({@code euPerBucket / 1000.0}). */
    public double euPerMb() {
        return euPerBucket / 1000.0;
    }

    /** Returns nominal mB consumed per tick at full generator output ({@code euPerTick / euPerMb()}). */
    public double mbPerTick() {
        return euPerTick / euPerMb();
    }

    /**
     * Looks up a {@link GasFuelSpec} by its full fluid resource ID.
     *
     * @param fluidId full namespaced fluid ID
     * @return matching fuel spec, or {@link Optional#empty()} if not a supported gas fuel
     */
    public static Optional<GasFuelSpec> byFluidId(String fluidId) {
        if (fluidId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_FLUID_ID.get(fluidId));
    }

    /**
     * Resolves a {@link GasFuelSpec} by exact fluid ID first, then by common fluid tag
     * ({@code water_gas}, {@code natural_gas}, {@code biogas}), and finally falls back to
     * {@link #WATER_GAS} if the fluid belongs to {@code ecc2:gas_fuel}.
     *
     * @param fluidId full namespaced fluid ID
     * @param commonNamespace platform common tag namespace ({@code "c"} or {@code "forge"})
     * @param hasFluidTag predicate evaluated against full tag IDs (without leading {@code '#'})
     * @return matching fuel spec, or {@link Optional#empty()} if not a supported gas fuel
     */
    public static Optional<GasFuelSpec> resolve(
            String fluidId, String commonNamespace, Predicate<String> hasFluidTag) {
        Optional<GasFuelSpec> exact = byFluidId(fluidId);
        if (exact.isPresent()) {
            return exact;
        }
        if (hasFluidTag == null || commonNamespace == null || commonNamespace.isEmpty()) {
            return Optional.empty();
        }
        for (GasFuelSpec spec : values()) {
            if (hasFluidTag.test(Ecc2Ids.commonTag(commonNamespace, spec.commonTagPath))) {
                return Optional.of(spec);
            }
        }
        if (hasFluidTag.test(Ecc2Ids.TAG_GAS_FUEL)) {
            return Optional.of(WATER_GAS);
        }
        return Optional.empty();
    }
}
