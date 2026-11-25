package com.ecocraft2.common.giant;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.config.Ecc2Ids;

/**
 * Built-in Giant Machine kinds shipped in EcoCraft 2.
 */
public enum GiantMachineKind {

    /** Giant Macerator: 300t / 2 EU/t per op, HV base tier. */
    MACERATOR(new GiantMachineSpec(
            "macerator",
            Ecc2Ids.GIANT_MACERATOR_UNIT,
            Ecc2Ids.IC2_MACERATOR,
            300,
            2,
            Ecc2ConfigDefaults.GIANT_BASE_TIER,
            0,
            0,
            0
    )),

    /** Giant Compressor: 300t / 2 EU/t per op, HV base tier. */
    COMPRESSOR(new GiantMachineSpec(
            "compressor",
            Ecc2Ids.GIANT_COMPRESSOR_UNIT,
            Ecc2Ids.IC2_COMPRESSOR,
            300,
            2,
            Ecc2ConfigDefaults.GIANT_BASE_TIER,
            0,
            0,
            0
    )),

    /** Giant Recycler: 45t / 1 EU/t per op, independent 1/8 scrap roll per op, HV base tier. */
    RECYCLER(new GiantMachineSpec(
            "recycler",
            Ecc2Ids.GIANT_RECYCLER_UNIT,
            Ecc2Ids.IC2_RECYCLER,
            45,
            1,
            Ecc2ConfigDefaults.GIANT_BASE_TIER,
            0,
            0,
            Ecc2ConfigDefaults.GIANT_RECYCLER_SCRAP_DENOMINATOR
    )),

    /** Giant Extractor: 300t / 2 EU/t per op, HV base tier. */
    EXTRACTOR(new GiantMachineSpec(
            "extractor",
            Ecc2Ids.GIANT_EXTRACTOR_UNIT,
            Ecc2Ids.IC2_EXTRACTOR,
            300,
            2,
            Ecc2ConfigDefaults.GIANT_BASE_TIER,
            0,
            0,
            0
    )),

    /** Giant Ore Washing Plant: 500t / 16 EU/t + 1,000 mB water per op, 64,000 mB tank, HV base tier. */
    ORE_WASHER(new GiantMachineSpec(
            "ore_washer",
            Ecc2Ids.GIANT_ORE_WASHER_UNIT,
            Ecc2Ids.IC2_ORE_WASHING_PLANT,
            500,
            16,
            Ecc2ConfigDefaults.GIANT_BASE_TIER,
            Ecc2ConfigDefaults.GIANT_ORE_WASHER_TANK_CAPACITY_MB,
            Ecc2ConfigDefaults.GIANT_ORE_WASHER_WATER_PER_OP_MB,
            0
    ));

    private final GiantMachineSpec spec;

    GiantMachineKind(GiantMachineSpec spec) {
        this.spec = spec;
    }

    /** Returns the immutable {@link GiantMachineSpec} for this built-in kind. */
    public GiantMachineSpec spec() {
        return spec;
    }

    /** Returns the type ID (e.g. {@code "macerator"}). */
    public String id() {
        return spec.id();
    }

    /** Returns the canonical unit block registry path (e.g. {@code "giant_macerator_unit"}). */
    public String unitBlockPath() {
        return spec.unitBlockPath();
    }
}
