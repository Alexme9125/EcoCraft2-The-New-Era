package com.ecocraft2.common.config;

import java.util.List;

/**
 * Canonical registry paths, resource IDs, and tags for EcoCraft 2: The New Era ({@code ecc2}).
 *
 * <p>All paths strictly match {@code spec/CONTRACTS.md} and use lowercase {@code [a-z0-9/._-]}.
 */
public final class Ecc2Ids {

    /** Mod namespace for EcoCraft 2. */
    public static final String MOD_ID = "ecc2";

    /** Host mod namespace for IndustrialCraft 2: The New Era. */
    public static final String IC2_MOD_ID = "ic2";

    /** Vanilla Minecraft namespace. */
    public static final String MINECRAFT_MOD_ID = "minecraft";

    // --- Placeable Block Paths (23) ---

    public static final String ADVANCED_SOLAR_PANEL = "advanced_solar_panel";
    public static final String HYBRID_SOLAR_PANEL = "hybrid_solar_panel";
    public static final String ULTIMATE_HYBRID_SOLAR_PANEL = "ultimate_hybrid_solar_panel";
    public static final String QUANTUM_SOLAR_PANEL = "quantum_solar_panel";
    public static final String QUANTUM_GENERATOR = "quantum_generator";
    public static final String MOLECULAR_TRANSFORMER = "molecular_transformer";

    public static final String DURABLE_WIND_KINETIC_GENERATOR = "durable_wind_kinetic_generator";
    public static final String OVERCLOCKED_WIND_KINETIC_GENERATOR = "overclocked_wind_kinetic_generator";
    public static final String PRECISION_WIND_KINETIC_GENERATOR = "precision_wind_kinetic_generator";
    public static final String DURABLE_WATER_KINETIC_GENERATOR = "durable_water_kinetic_generator";
    public static final String OVERCLOCKED_WATER_KINETIC_GENERATOR = "overclocked_water_kinetic_generator";
    public static final String PRECISION_WATER_KINETIC_GENERATOR = "precision_water_kinetic_generator";

    public static final String GIANT_MACERATOR_UNIT = "giant_macerator_unit";
    public static final String GIANT_COMPRESSOR_UNIT = "giant_compressor_unit";
    public static final String GIANT_RECYCLER_UNIT = "giant_recycler_unit";
    public static final String GIANT_EXTRACTOR_UNIT = "giant_extractor_unit";
    public static final String GIANT_ORE_WASHER_UNIT = "giant_ore_washer_unit";

    public static final String WATER_GAS_MACHINE = "water_gas_machine";
    public static final String GAS_FURNACE = "gas_furnace";
    public static final String GAS_GENERATOR = "gas_generator";
    public static final String METHANE_ICE_CLUSTER = "methane_ice_cluster";
    public static final String CULTURE_INCUBATOR = "culture_incubator";
    public static final String ORGANIC_FERMENTER = "organic_fermenter";

    // --- Fluid Paths (3 new + 1 reused IC2 fluid) ---

    public static final String WATER_GAS = "water_gas";
    public static final String FLOWING_WATER_GAS = "flowing_water_gas";
    public static final String NATURAL_GAS = "natural_gas";
    public static final String FLOWING_NATURAL_GAS = "flowing_natural_gas";
    public static final String CULTURE_MEDIUM = "culture_medium";
    public static final String FLOWING_CULTURE_MEDIUM = "flowing_culture_medium";

    // --- Full Fluid Resource IDs ---

    public static final String WATER_GAS_ID = ecc2(WATER_GAS);
    public static final String NATURAL_GAS_ID = ecc2(NATURAL_GAS);
    public static final String CULTURE_MEDIUM_ID = ecc2(CULTURE_MEDIUM);
    public static final String IC2_BIOGAS_ID = ic2("biogas");
    public static final String MINECRAFT_WATER_ID = MINECRAFT_MOD_ID + ":water";

    // --- Standalone Item Paths (26) ---

    // ASP Crafting Materials (14)
    public static final String SUNNARIUM = "sunnarium";
    public static final String SUNNARIUM_PART = "sunnarium_part";
    public static final String SUNNARIUM_ALLOY = "sunnarium_alloy";
    public static final String IRRADIANT_URANIUM = "irradiant_uranium";
    public static final String ENRICHED_SUNNARIUM = "enriched_sunnarium";
    public static final String ENRICHED_SUNNARIUM_ALLOY = "enriched_sunnarium_alloy";
    public static final String IRRADIANT_GLASS_PANE = "irradiant_glass_pane";
    public static final String IRIDIUM_IRON_PLATE = "iridium_iron_plate";
    public static final String REINFORCED_IRIDIUM_IRON_PLATE = "reinforced_iridium_iron_plate";
    public static final String IRRADIANT_REINFORCED_PLATE = "irradiant_reinforced_plate";
    public static final String IRIDIUM_INGOT = "iridium_ingot";
    public static final String URANIUM_INGOT = "uranium_ingot";
    public static final String MT_CORE = "mt_core";
    public static final String QUANTUM_CORE = "quantum_core";

    // ASP Solar Helmets (3)
    public static final String ADVANCED_SOLAR_HELMET = "advanced_solar_helmet";
    public static final String HYBRID_SOLAR_HELMET = "hybrid_solar_helmet";
    public static final String ULTIMATE_HYBRID_SOLAR_HELMET = "ultimate_hybrid_solar_helmet";

    // Giant Machine Component (1)
    public static final String MEGASTRUCTURE_COMPONENT = "megastructure_component";

    // Named Fluid Cells (5)
    public static final String WATER_GAS_CELL = "water_gas_cell";
    public static final String NATURAL_GAS_CELL = "natural_gas_cell";
    public static final String CULTURE_MEDIUM_CELL = "culture_medium_cell";
    public static final String BASIC_CULTURE_MEDIUM_CELL = "basic_culture_medium_cell";
    public static final String BIOGAS_CELL = "biogas_cell";

    // Fluid Buckets (3)
    public static final String WATER_GAS_BUCKET = "water_gas_bucket";
    public static final String NATURAL_GAS_BUCKET = "natural_gas_bucket";
    public static final String CULTURE_MEDIUM_BUCKET = "culture_medium_bucket";

    // --- External IC2NE Resource IDs ---

    public static final String IC2_WOODEN_ROTOR = ic2("wooden_rotor");
    public static final String IC2_BRONZE_ROTOR = ic2("bronze_rotor");
    public static final String IC2_IRON_ROTOR = ic2("iron_rotor");
    public static final String IC2_STEEL_ROTOR = ic2("steel_rotor");
    public static final String IC2_CARBON_ROTOR = ic2("carbon_rotor");

    public static final String IC2_EMPTY_CELL = ic2("empty_cell");
    public static final String IC2_FLUID_CELL = ic2("fluid_cell");
    public static final String IC2_BIOGAS_BUCKET = ic2("biogas_bucket");

    public static final String IC2_MACERATOR = ic2("macerator");
    public static final String IC2_COMPRESSOR = ic2("compressor");
    public static final String IC2_RECYCLER = ic2("recycler");
    public static final String IC2_EXTRACTOR = ic2("extractor");
    public static final String IC2_ORE_WASHING_PLANT = ic2("ore_washing_plant");
    public static final String IC2_KINETIC_GENERATOR = ic2("kinetic_generator");

    // --- Recipe Type & Tag IDs ---

    public static final String RECIPE_MOLECULAR_TRANSFORMER = "molecular_transformer";

    public static final String TAG_GAS_FUEL = ecc2("gas_fuel");
    public static final String TAG_WATER_GAS = "c:water_gas";
    public static final String TAG_NATURAL_GAS = "c:natural_gas";
    public static final String TAG_CULTURE_MEDIUM = "c:culture_medium";
    public static final String TAG_BIOGAS = "c:biogas";
    public static final String TAG_INGOTS_IRIDIUM = "c:ingots/iridium";
    public static final String TAG_INGOTS_URANIUM = "c:ingots/uranium";

    // --- Fluid Properties (matching spec/CONTRACTS.md §2.3) ---

    public static final int WATER_GAS_DENSITY = -400;
    public static final int WATER_GAS_VISCOSITY = 400;
    public static final int WATER_GAS_TEMPERATURE = 310;
    public static final int WATER_GAS_LIGHT = 2;
    public static final int WATER_GAS_TINT_ARGB = 0xD89BD0D6;

    public static final int NATURAL_GAS_DENSITY = -600;
    public static final int NATURAL_GAS_VISCOSITY = 350;
    public static final int NATURAL_GAS_TEMPERATURE = 290;
    public static final int NATURAL_GAS_LIGHT = 0;
    public static final int NATURAL_GAS_TINT_ARGB = 0xC888D4FF;

    public static final int CULTURE_MEDIUM_DENSITY = 1020;
    public static final int CULTURE_MEDIUM_VISCOSITY = 1800;
    public static final int CULTURE_MEDIUM_TEMPERATURE = 300;
    public static final int CULTURE_MEDIUM_LIGHT = 1;
    public static final int CULTURE_MEDIUM_TINT_ARGB = 0xE84CC77A;

    /** Immutable list of all 23 placeable block registry paths. */
    public static final List<String> PLACEABLE_BLOCKS = List.of(
            ADVANCED_SOLAR_PANEL,
            HYBRID_SOLAR_PANEL,
            ULTIMATE_HYBRID_SOLAR_PANEL,
            QUANTUM_SOLAR_PANEL,
            QUANTUM_GENERATOR,
            MOLECULAR_TRANSFORMER,
            DURABLE_WIND_KINETIC_GENERATOR,
            OVERCLOCKED_WIND_KINETIC_GENERATOR,
            PRECISION_WIND_KINETIC_GENERATOR,
            DURABLE_WATER_KINETIC_GENERATOR,
            OVERCLOCKED_WATER_KINETIC_GENERATOR,
            PRECISION_WATER_KINETIC_GENERATOR,
            GIANT_MACERATOR_UNIT,
            GIANT_COMPRESSOR_UNIT,
            GIANT_RECYCLER_UNIT,
            GIANT_EXTRACTOR_UNIT,
            GIANT_ORE_WASHER_UNIT,
            WATER_GAS_MACHINE,
            GAS_FURNACE,
            GAS_GENERATOR,
            METHANE_ICE_CLUSTER,
            CULTURE_INCUBATOR,
            ORGANIC_FERMENTER
    );

    /** Immutable list of all 3 new fluid block registry paths. */
    public static final List<String> FLUID_BLOCKS = List.of(
            WATER_GAS,
            NATURAL_GAS,
            CULTURE_MEDIUM
    );

    /** Immutable list of all 26 standalone item registry paths. */
    public static final List<String> STANDALONE_ITEMS = List.of(
            SUNNARIUM,
            SUNNARIUM_PART,
            SUNNARIUM_ALLOY,
            IRRADIANT_URANIUM,
            ENRICHED_SUNNARIUM,
            ENRICHED_SUNNARIUM_ALLOY,
            IRRADIANT_GLASS_PANE,
            IRIDIUM_IRON_PLATE,
            REINFORCED_IRIDIUM_IRON_PLATE,
            IRRADIANT_REINFORCED_PLATE,
            IRIDIUM_INGOT,
            URANIUM_INGOT,
            MT_CORE,
            QUANTUM_CORE,
            ADVANCED_SOLAR_HELMET,
            HYBRID_SOLAR_HELMET,
            ULTIMATE_HYBRID_SOLAR_HELMET,
            MEGASTRUCTURE_COMPONENT,
            WATER_GAS_CELL,
            NATURAL_GAS_CELL,
            CULTURE_MEDIUM_CELL,
            BASIC_CULTURE_MEDIUM_CELL,
            BIOGAS_CELL,
            WATER_GAS_BUCKET,
            NATURAL_GAS_BUCKET,
            CULTURE_MEDIUM_BUCKET
    );

    private Ecc2Ids() {
    }

    /**
     * Qualifies a path under the {@code ecc2} namespace.
     *
     * @param path lowercase registry path
     * @return {@code "ecc2:" + path}
     */
    public static String ecc2(String path) {
        return MOD_ID + ":" + path;
    }

    /**
     * Qualifies a path under the {@code ic2} namespace.
     *
     * @param path lowercase registry path
     * @return {@code "ic2:" + path}
     */
    public static String ic2(String path) {
        return IC2_MOD_ID + ":" + path;
    }
}
