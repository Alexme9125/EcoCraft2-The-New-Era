package com.ecocraft2.common.config;

/**
 * Canonical default configuration values for EcoCraft 2: The New Era.
 *
 * <p>Covers all Advanced Solar Panels 2 settings (power, storage, tier, quantum generator,
 * solar helmets, recipe toggles, and client visual flags) plus kinetic, giant machine,
 * and gas/biogas balance defaults from {@code spec/DESIGN.md} and {@code spec/CONTRACTS.md}.
 */
public final class Ecc2ConfigDefaults {

    // --- ASP Solar Panel Defaults (16) ---

    public static final int ADV_SOLAR_GEN_DAY = 8;
    public static final int ADV_SOLAR_GEN_NIGHT = 1;
    public static final int ADV_SOLAR_STORAGE = 32_000;
    public static final int ADV_SOLAR_TIER = 1;

    public static final int HYBRID_SOLAR_GEN_DAY = 64;
    public static final int HYBRID_SOLAR_GEN_NIGHT = 8;
    public static final int HYBRID_SOLAR_STORAGE = 100_000;
    public static final int HYBRID_SOLAR_TIER = 2;

    public static final int ULTIMATE_SOLAR_GEN_DAY = 512;
    public static final int ULTIMATE_SOLAR_GEN_NIGHT = 64;
    public static final int ULTIMATE_SOLAR_STORAGE = 1_000_000;
    public static final int ULTIMATE_SOLAR_TIER = 3;

    public static final int QUANTUM_SOLAR_GEN_DAY = 4_096;
    public static final int QUANTUM_SOLAR_GEN_NIGHT = 2_048;
    public static final int QUANTUM_SOLAR_STORAGE = 10_000_000;
    public static final int QUANTUM_SOLAR_TIER = 5;

    public static final int SOLAR_SKY_CHECK_INTERVAL_TICKS = 128;

    // --- ASP Quantum Generator Defaults (2) ---

    public static final int QUANTUM_GENERATOR_DEFAULT_PRODUCTION = 512;
    public static final int QUANTUM_GENERATOR_DEFAULT_TIER = 3;

    // --- ASP Molecular Transformer Defaults ---

    public static final int MOLECULAR_TRANSFORMER_INPUT_TIER = 14;
    public static final int MOLECULAR_TRANSFORMER_MAX_IDLE_TICKS = 40;
    public static final boolean DRAW_MOLECULAR_TRANSFORMER_CORE = true;

    // --- ASP Solar Helmet & Recipe Toggles (13+) ---

    /**
     * When {@code true}, solar helmets only charge worn armor slots (and self) instead of
     * also charging offhand and main inventory items. Default is {@code false} (full charging),
     * fixing the inverted upstream key semantics.
     */
    public static final boolean SOLAR_HELMET_CHARGE_ARMOR_ONLY = false;

    public static final boolean HARD_RECIPES = true;
    public static final boolean EASY_ASP_RECIPE = false;
    public static final boolean DISABLE_ADVANCED_SOLAR_RECIPE = false;
    public static final boolean DISABLE_HYBRID_SOLAR_RECIPE = false;
    public static final boolean DISABLE_ULTIMATE_HYBRID_SOLAR_RECIPE = false;
    public static final boolean DISABLE_QUANTUM_SOLAR_RECIPE = false;
    public static final boolean DISABLE_ADVANCED_SOLAR_HELMET_RECIPE = false;
    public static final boolean DISABLE_HYBRID_SOLAR_HELMET_RECIPE = false;
    public static final boolean DISABLE_ULTIMATE_HYBRID_SOLAR_HELMET_RECIPE = false;
    public static final boolean DISABLE_MOLECULAR_TRANSFORMER_RECIPE = false;
    public static final boolean DISABLE_DOUBLE_SLAB_RECIPE = true;
    public static final boolean ENABLE_SIMPLE_ADVANCED_SOLAR_RECIPE = false;
    public static final boolean ENABLE_SIMPLE_HYBRID_SOLAR_RECIPE = false;
    public static final boolean ENABLE_SIMPLE_ULTIMATE_SOLAR_RECIPE = false;

    // --- Advanced Wind & Water Kinetic Defaults ---

    public static final boolean ENABLE_CLASSIC_ROTOR_WEAR = true;
    public static final int ROTOR_WEAR_INTERVAL_TICKS = 20;
    public static final double ROTOR_OVERWIND_WEAR_MULTIPLIER = 4.0;
    public static final double KU_PER_EU = 4.0;

    public static final double STANDARD_KINETIC_OUTPUT_MULT = 1.00;
    public static final double STANDARD_KINETIC_WEAR_MULT = 1.00;
    public static final double DURABLE_KINETIC_OUTPUT_MULT = 0.80;
    public static final double DURABLE_KINETIC_WEAR_MULT = 0.50;
    public static final double OVERCLOCKED_KINETIC_OUTPUT_MULT = 2.20;
    public static final double OVERCLOCKED_KINETIC_WEAR_MULT = 2.00;
    public static final double PRECISION_KINETIC_OUTPUT_MULT = 2.40;
    public static final double PRECISION_KINETIC_WEAR_MULT = 0.50;

    // --- Giant Machine Defaults ---

    public static final int GIANT_LANE_COUNT = 9;
    public static final int GIANT_MAX_BATCH_PER_LANE = 3;
    public static final int GIANT_BASE_TIER = 3;
    public static final int GIANT_MIN_BASE_STORAGE = 32_000;
    public static final int GIANT_STORAGE_UPGRADE_BONUS = 10_000;
    public static final int GIANT_ORE_WASHER_TANK_CAPACITY_MB = 64_000;
    public static final int GIANT_ORE_WASHER_WATER_PER_OP_MB = 1_000;
    public static final int GIANT_RECYCLER_SCRAP_DENOMINATOR = 8;

    // --- Gas & Biogas Industry Defaults ---

    public static final int WATER_GAS_SOLAR_EU_PER_TICK = 2;
    public static final int WATER_GAS_EU_PER_OP = 1_000;
    public static final int WATER_GAS_TICKS_PER_OP = 200;
    public static final int WATER_GAS_WATER_PER_OP_MB = 100;
    public static final int WATER_GAS_OUTPUT_PER_OP_MB = 1_000;
    public static final int WATER_GAS_TANK_CAPACITY_MB = 8_000;
    public static final int WATER_GAS_INPUT_TIER = 1;
    public static final int WATER_GAS_BASE_EU_CAPACITY = 4_000;

    public static final int GAS_FURNACE_MB_PER_ITEM = 50;
    public static final int GAS_FURNACE_BASE_TICKS = 100;
    public static final int GAS_FURNACE_TANK_CAPACITY_MB = 8_000;

    public static final int GAS_GENERATOR_TANK_CAPACITY_MB = 16_000;
    public static final int GAS_GENERATOR_EU_CAPACITY = 16_000;
    public static final int GAS_GENERATOR_OUTPUT_TIER = 2;

    public static final int WATER_GAS_EU_PER_BUCKET = 10_000;
    public static final int WATER_GAS_GEN_EU_PER_TICK = 32;
    public static final int NATURAL_GAS_EU_PER_BUCKET = 10_000;
    public static final int NATURAL_GAS_GEN_EU_PER_TICK = 64;
    public static final int BIOGAS_EU_PER_BUCKET = 16_000;
    public static final int BIOGAS_GEN_EU_PER_TICK = 32;

    public static final int CULTURE_INCUBATOR_EU_PER_OP = 200;
    public static final int CULTURE_INCUBATOR_TICKS_PER_OP = 100;
    public static final int CULTURE_INCUBATOR_WATER_PER_OP_MB = 100;
    public static final int CULTURE_INCUBATOR_OUTPUT_PER_OP_MB = 100;
    public static final int CULTURE_INCUBATOR_TANK_CAPACITY_MB = 8_000;
    public static final int CULTURE_INCUBATOR_INPUT_TIER = 1;
    public static final int CULTURE_INCUBATOR_BASE_EU_CAPACITY = 4_000;

    public static final int ORGANIC_FERMENTER_EU_PER_OP = 200;
    public static final int ORGANIC_FERMENTER_TICKS_PER_OP = 100;
    public static final int ORGANIC_FERMENTER_CULTURE_PER_OP_MB = 50;
    public static final int ORGANIC_FERMENTER_BIOGAS_PER_NUTRITION_MB = 20;
    public static final int ORGANIC_FERMENTER_CULTURE_TANK_MB = 8_000;
    public static final int ORGANIC_FERMENTER_BIOGAS_TANK_MB = 16_000;
    public static final int ORGANIC_FERMENTER_INPUT_TIER = 1;
    public static final int ORGANIC_FERMENTER_BASE_EU_CAPACITY = 4_000;

    private Ecc2ConfigDefaults() {
    }
}
