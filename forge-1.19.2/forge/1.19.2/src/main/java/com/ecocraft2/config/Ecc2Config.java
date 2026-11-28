package com.ecocraft2.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Forge 1.19.2 {@link ForgeConfigSpec} definitions for EcoCraft 2: The New Era (COMMON gameplay/
 * balance settings and CLIENT rendering settings).
 */
public final class Ecc2Config {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        var commonPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();

        var clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }

    private Ecc2Config() {}

    /** Registers both common and client {@link ForgeConfigSpec} files with Forge. */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    private static <T> T read(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<T> value) {
        return spec.isLoaded() ? value.get() : value.getDefault();
    }

    // ---- Solar Panel accessors ----

    public static int advancedSolarGenDay() {
        return read(COMMON_SPEC, COMMON.advancedSolarGenDay);
    }

    public static int advancedSolarGenNight() {
        return read(COMMON_SPEC, COMMON.advancedSolarGenNight);
    }

    public static int advancedSolarStorage() {
        return read(COMMON_SPEC, COMMON.advancedSolarStorage);
    }

    public static int advancedSolarTier() {
        return read(COMMON_SPEC, COMMON.advancedSolarTier);
    }

    public static int hybridSolarGenDay() {
        return read(COMMON_SPEC, COMMON.hybridSolarGenDay);
    }

    public static int hybridSolarGenNight() {
        return read(COMMON_SPEC, COMMON.hybridSolarGenNight);
    }

    public static int hybridSolarStorage() {
        return read(COMMON_SPEC, COMMON.hybridSolarStorage);
    }

    public static int hybridSolarTier() {
        return read(COMMON_SPEC, COMMON.hybridSolarTier);
    }

    public static int ultimateSolarGenDay() {
        return read(COMMON_SPEC, COMMON.ultimateSolarGenDay);
    }

    public static int ultimateSolarGenNight() {
        return read(COMMON_SPEC, COMMON.ultimateSolarGenNight);
    }

    public static int ultimateSolarStorage() {
        return read(COMMON_SPEC, COMMON.ultimateSolarStorage);
    }

    public static int ultimateSolarTier() {
        return read(COMMON_SPEC, COMMON.ultimateSolarTier);
    }

    public static int quantumSolarGenDay() {
        return read(COMMON_SPEC, COMMON.quantumSolarGenDay);
    }

    public static int quantumSolarGenNight() {
        return read(COMMON_SPEC, COMMON.quantumSolarGenNight);
    }

    public static int quantumSolarStorage() {
        return read(COMMON_SPEC, COMMON.quantumSolarStorage);
    }

    public static int quantumSolarTier() {
        return read(COMMON_SPEC, COMMON.quantumSolarTier);
    }

    // ---- Quantum Generator & Helmets accessors ----

    public static int quantumGeneratorDefaultProduction() {
        return read(COMMON_SPEC, COMMON.quantumGeneratorDefaultProduction);
    }

    public static int quantumGeneratorDefaultTier() {
        return read(COMMON_SPEC, COMMON.quantumGeneratorDefaultTier);
    }

    public static boolean helmetsChargeArmorOnly() {
        return read(COMMON_SPEC, COMMON.helmetsChargeArmorOnly);
    }

    public static int advancedHelmetGenDay() {
        return read(COMMON_SPEC, COMMON.advancedHelmetGenDay);
    }

    public static int advancedHelmetGenNight() {
        return read(COMMON_SPEC, COMMON.advancedHelmetGenNight);
    }

    public static int hybridHelmetGenDay() {
        return read(COMMON_SPEC, COMMON.hybridHelmetGenDay);
    }

    public static int hybridHelmetGenNight() {
        return read(COMMON_SPEC, COMMON.hybridHelmetGenNight);
    }

    public static int ultimateHelmetGenDay() {
        return read(COMMON_SPEC, COMMON.ultimateHelmetGenDay);
    }

    public static int ultimateHelmetGenNight() {
        return read(COMMON_SPEC, COMMON.ultimateHelmetGenNight);
    }

    // ---- Recipe toggles ----

    public static boolean hardRecipes() {
        return read(COMMON_SPEC, COMMON.hardRecipes);
    }

    public static boolean easyASPRecipe() {
        return read(COMMON_SPEC, COMMON.easyASPRecipe);
    }

    public static boolean disableAdvancedSolarRecipe() {
        return read(COMMON_SPEC, COMMON.disableAdvancedSolarRecipe);
    }

    public static boolean disableHybridSolarRecipe() {
        return read(COMMON_SPEC, COMMON.disableHybridSolarRecipe);
    }

    public static boolean disableUltimateSolarRecipe() {
        return read(COMMON_SPEC, COMMON.disableUltimateSolarRecipe);
    }

    public static boolean disableQuantumSolarRecipe() {
        return read(COMMON_SPEC, COMMON.disableQuantumSolarRecipe);
    }

    public static boolean disableAdvancedHelmetRecipe() {
        return read(COMMON_SPEC, COMMON.disableAdvancedHelmetRecipe);
    }

    public static boolean disableHybridHelmetRecipe() {
        return read(COMMON_SPEC, COMMON.disableHybridHelmetRecipe);
    }

    public static boolean disableUltimateHelmetRecipe() {
        return read(COMMON_SPEC, COMMON.disableUltimateHelmetRecipe);
    }

    public static boolean disableMolecularTransformerRecipe() {
        return read(COMMON_SPEC, COMMON.disableMolecularTransformerRecipe);
    }

    // ---- Kinetic & Rotor Wear accessors ----

    public static boolean enableVanillaRotorWear() {
        return read(COMMON_SPEC, COMMON.enableVanillaRotorWear);
    }

    public static double vanillaRotorWearMultiplier() {
        return read(COMMON_SPEC, COMMON.vanillaRotorWearMultiplier);
    }

    public static double durableKineticOutputMultiplier() {
        return read(COMMON_SPEC, COMMON.durableKineticOutputMultiplier);
    }

    public static double durableKineticWearMultiplier() {
        return read(COMMON_SPEC, COMMON.durableKineticWearMultiplier);
    }

    public static double overclockedKineticOutputMultiplier() {
        return read(COMMON_SPEC, COMMON.overclockedKineticOutputMultiplier);
    }

    public static double overclockedKineticWearMultiplier() {
        return read(COMMON_SPEC, COMMON.overclockedKineticWearMultiplier);
    }

    public static double precisionKineticOutputMultiplier() {
        return read(COMMON_SPEC, COMMON.precisionKineticOutputMultiplier);
    }

    public static double precisionKineticWearMultiplier() {
        return read(COMMON_SPEC, COMMON.precisionKineticWearMultiplier);
    }

    // ---- Giant Machine accessors ----

    public static int giantBaseTier() {
        return read(COMMON_SPEC, COMMON.giantBaseTier);
    }

    public static int giantMaxBatchPerLane() {
        return read(COMMON_SPEC, COMMON.giantMaxBatchPerLane);
    }

    public static int giantWasherWaterCapacity() {
        return read(COMMON_SPEC, COMMON.giantWasherWaterCapacity);
    }

    // ---- Gas & Biogas accessors ----

    public static int waterGasSolarEuPerTick() {
        return read(COMMON_SPEC, COMMON.waterGasSolarEuPerTick);
    }

    public static int waterGasEnergyPerBucket() {
        return read(COMMON_SPEC, COMMON.waterGasEnergyPerBucket);
    }

    public static int waterGasOutputEuPerTick() {
        return read(COMMON_SPEC, COMMON.waterGasOutputEuPerTick);
    }

    public static int naturalGasEnergyPerBucket() {
        return read(COMMON_SPEC, COMMON.naturalGasEnergyPerBucket);
    }

    public static int naturalGasOutputEuPerTick() {
        return read(COMMON_SPEC, COMMON.naturalGasOutputEuPerTick);
    }

    public static int biogasEnergyPerBucket() {
        return read(COMMON_SPEC, COMMON.biogasEnergyPerBucket);
    }

    public static int biogasOutputEuPerTick() {
        return read(COMMON_SPEC, COMMON.biogasOutputEuPerTick);
    }

    public static int gasFurnaceGasPerItem() {
        return read(COMMON_SPEC, COMMON.gasFurnaceGasPerItem);
    }

    public static int gasFurnaceBaseTicks() {
        return read(COMMON_SPEC, COMMON.gasFurnaceBaseTicks);
    }

    public static int organicFermenterBiogasPerHunger() {
        return read(COMMON_SPEC, COMMON.organicFermenterBiogasPerHunger);
    }

    public static int organicFermenterCulturePerOp() {
        return read(COMMON_SPEC, COMMON.organicFermenterCulturePerOp);
    }

    // ---- Client accessors ----

    public static boolean drawMolecularTransformerCore() {
        return read(CLIENT_SPEC, CLIENT.drawMolecularTransformerCore);
    }

    public static boolean enableRotorRenderAnimation() {
        return read(CLIENT_SPEC, CLIENT.enableRotorRenderAnimation);
    }

    /** Common/server balance configuration schema. */
    public static final class Common {
        public final ForgeConfigSpec.IntValue advancedSolarGenDay;
        public final ForgeConfigSpec.IntValue advancedSolarGenNight;
        public final ForgeConfigSpec.IntValue advancedSolarStorage;
        public final ForgeConfigSpec.IntValue advancedSolarTier;

        public final ForgeConfigSpec.IntValue hybridSolarGenDay;
        public final ForgeConfigSpec.IntValue hybridSolarGenNight;
        public final ForgeConfigSpec.IntValue hybridSolarStorage;
        public final ForgeConfigSpec.IntValue hybridSolarTier;

        public final ForgeConfigSpec.IntValue ultimateSolarGenDay;
        public final ForgeConfigSpec.IntValue ultimateSolarGenNight;
        public final ForgeConfigSpec.IntValue ultimateSolarStorage;
        public final ForgeConfigSpec.IntValue ultimateSolarTier;

        public final ForgeConfigSpec.IntValue quantumSolarGenDay;
        public final ForgeConfigSpec.IntValue quantumSolarGenNight;
        public final ForgeConfigSpec.IntValue quantumSolarStorage;
        public final ForgeConfigSpec.IntValue quantumSolarTier;

        public final ForgeConfigSpec.IntValue quantumGeneratorDefaultProduction;
        public final ForgeConfigSpec.IntValue quantumGeneratorDefaultTier;

        public final ForgeConfigSpec.BooleanValue helmetsChargeArmorOnly;
        public final ForgeConfigSpec.IntValue advancedHelmetGenDay;
        public final ForgeConfigSpec.IntValue advancedHelmetGenNight;
        public final ForgeConfigSpec.IntValue hybridHelmetGenDay;
        public final ForgeConfigSpec.IntValue hybridHelmetGenNight;
        public final ForgeConfigSpec.IntValue ultimateHelmetGenDay;
        public final ForgeConfigSpec.IntValue ultimateHelmetGenNight;

        public final ForgeConfigSpec.BooleanValue hardRecipes;
        public final ForgeConfigSpec.BooleanValue easyASPRecipe;
        public final ForgeConfigSpec.BooleanValue disableAdvancedSolarRecipe;
        public final ForgeConfigSpec.BooleanValue disableHybridSolarRecipe;
        public final ForgeConfigSpec.BooleanValue disableUltimateSolarRecipe;
        public final ForgeConfigSpec.BooleanValue disableQuantumSolarRecipe;
        public final ForgeConfigSpec.BooleanValue disableAdvancedHelmetRecipe;
        public final ForgeConfigSpec.BooleanValue disableHybridHelmetRecipe;
        public final ForgeConfigSpec.BooleanValue disableUltimateHelmetRecipe;
        public final ForgeConfigSpec.BooleanValue disableMolecularTransformerRecipe;

        public final ForgeConfigSpec.BooleanValue enableVanillaRotorWear;
        public final ForgeConfigSpec.DoubleValue vanillaRotorWearMultiplier;
        public final ForgeConfigSpec.DoubleValue durableKineticOutputMultiplier;
        public final ForgeConfigSpec.DoubleValue durableKineticWearMultiplier;
        public final ForgeConfigSpec.DoubleValue overclockedKineticOutputMultiplier;
        public final ForgeConfigSpec.DoubleValue overclockedKineticWearMultiplier;
        public final ForgeConfigSpec.DoubleValue precisionKineticOutputMultiplier;
        public final ForgeConfigSpec.DoubleValue precisionKineticWearMultiplier;

        public final ForgeConfigSpec.IntValue giantBaseTier;
        public final ForgeConfigSpec.IntValue giantMaxBatchPerLane;
        public final ForgeConfigSpec.IntValue giantWasherWaterCapacity;

        public final ForgeConfigSpec.IntValue waterGasSolarEuPerTick;
        public final ForgeConfigSpec.IntValue waterGasEnergyPerBucket;
        public final ForgeConfigSpec.IntValue waterGasOutputEuPerTick;
        public final ForgeConfigSpec.IntValue naturalGasEnergyPerBucket;
        public final ForgeConfigSpec.IntValue naturalGasOutputEuPerTick;
        public final ForgeConfigSpec.IntValue biogasEnergyPerBucket;
        public final ForgeConfigSpec.IntValue biogasOutputEuPerTick;
        public final ForgeConfigSpec.IntValue gasFurnaceGasPerItem;
        public final ForgeConfigSpec.IntValue gasFurnaceBaseTicks;
        public final ForgeConfigSpec.IntValue organicFermenterBiogasPerHunger;
        public final ForgeConfigSpec.IntValue organicFermenterCulturePerOp;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Advanced Solar Panels 2 generation, storage, and voltage tier settings")
                    .push("solar");

            advancedSolarGenDay =
                    builder.comment("Advanced Solar Panel daytime EU/t")
                            .defineInRange("advancedSolarGenDay", 8, 0, 1_000_000);
            advancedSolarGenNight =
                    builder.comment("Advanced Solar Panel nighttime/rain EU/t")
                            .defineInRange("advancedSolarGenNight", 1, 0, 1_000_000);
            advancedSolarStorage =
                    builder.comment("Advanced Solar Panel internal EU capacity")
                            .defineInRange("advancedSolarStorage", 32_000, 1, 1_000_000_000);
            advancedSolarTier =
                    builder.comment("Advanced Solar Panel output voltage tier (1=LV)")
                            .defineInRange("advancedSolarTier", 1, 1, 14);

            hybridSolarGenDay =
                    builder.comment("Hybrid Solar Panel daytime EU/t")
                            .defineInRange("hybridSolarGenDay", 64, 0, 1_000_000);
            hybridSolarGenNight =
                    builder.comment("Hybrid Solar Panel nighttime/rain EU/t")
                            .defineInRange("hybridSolarGenNight", 8, 0, 1_000_000);
            hybridSolarStorage =
                    builder.comment("Hybrid Solar Panel internal EU capacity")
                            .defineInRange("hybridSolarStorage", 100_000, 1, 1_000_000_000);
            hybridSolarTier =
                    builder.comment("Hybrid Solar Panel output voltage tier (2=MV)")
                            .defineInRange("hybridSolarTier", 2, 1, 14);

            ultimateSolarGenDay =
                    builder.comment("Ultimate Hybrid Solar Panel daytime EU/t")
                            .defineInRange("ultimateSolarGenDay", 512, 0, 1_000_000);
            ultimateSolarGenNight =
                    builder.comment("Ultimate Hybrid Solar Panel nighttime/rain EU/t")
                            .defineInRange("ultimateSolarGenNight", 64, 0, 1_000_000);
            ultimateSolarStorage =
                    builder.comment("Ultimate Hybrid Solar Panel internal EU capacity")
                            .defineInRange("ultimateSolarStorage", 1_000_000, 1, 1_000_000_000);
            ultimateSolarTier =
                    builder.comment("Ultimate Hybrid Solar Panel output voltage tier (3=HV)")
                            .defineInRange("ultimateSolarTier", 3, 1, 14);

            quantumSolarGenDay =
                    builder.comment("Quantum Solar Panel daytime EU/t")
                            .defineInRange("quantumSolarGenDay", 4_096, 0, 10_000_000);
            quantumSolarGenNight =
                    builder.comment("Quantum Solar Panel nighttime/rain EU/t")
                            .defineInRange("quantumSolarGenNight", 2_048, 0, 10_000_000);
            quantumSolarStorage =
                    builder.comment("Quantum Solar Panel internal EU capacity")
                            .defineInRange("quantumSolarStorage", 10_000_000, 1, 1_000_000_000);
            quantumSolarTier =
                    builder.comment("Quantum Solar Panel output voltage tier (5=IV)")
                            .defineInRange("quantumSolarTier", 5, 1, 14);

            quantumGeneratorDefaultProduction =
                    builder.comment("Default EU/t production for newly placed Quantum Generators")
                            .defineInRange("quantumGeneratorDefaultProduction", 512, 0, 1_000_000);
            quantumGeneratorDefaultTier =
                    builder.comment("Default voltage tier (1..6, 6=MAX 32768) for Quantum Generators")
                            .defineInRange("quantumGeneratorDefaultTier", 3, 1, 6);

            helmetsChargeArmorOnly =
                    builder.comment(
                                    "When true, Solar Helmets only charge worn armor slots (skipping offhand/inventory)")
                            .define("helmetsChargeArmorOnly", false);
            advancedHelmetGenDay =
                    builder.comment("Advanced Solar Helmet daytime EU/t")
                            .defineInRange("advancedHelmetGenDay", 8, 0, 1_000_000);
            advancedHelmetGenNight =
                    builder.comment("Advanced Solar Helmet nighttime/rain EU/t")
                            .defineInRange("advancedHelmetGenNight", 1, 0, 1_000_000);
            hybridHelmetGenDay =
                    builder.comment("Hybrid Solar Helmet daytime EU/t")
                            .defineInRange("hybridHelmetGenDay", 64, 0, 1_000_000);
            hybridHelmetGenNight =
                    builder.comment("Hybrid Solar Helmet nighttime/rain EU/t")
                            .defineInRange("hybridHelmetGenNight", 8, 0, 1_000_000);
            ultimateHelmetGenDay =
                    builder.comment("Ultimate Hybrid Solar Helmet daytime EU/t")
                            .defineInRange("ultimateHelmetGenDay", 512, 0, 1_000_000);
            ultimateHelmetGenNight =
                    builder.comment("Ultimate Hybrid Solar Helmet nighttime/rain EU/t")
                            .defineInRange("ultimateHelmetGenNight", 64, 0, 1_000_000);
            builder.pop();

            builder.comment("Recipe difficulty and enable/disable switches").push("recipes");
            hardRecipes = builder.define("hardRecipes", true);
            easyASPRecipe = builder.define("easyASPRecipe", false);
            disableAdvancedSolarRecipe = builder.define("disableAdvancedSolarRecipe", false);
            disableHybridSolarRecipe = builder.define("disableHybridSolarRecipe", false);
            disableUltimateSolarRecipe = builder.define("disableUltimateSolarRecipe", false);
            disableQuantumSolarRecipe = builder.define("disableQuantumSolarRecipe", false);
            disableAdvancedHelmetRecipe = builder.define("disableAdvancedHelmetRecipe", false);
            disableHybridHelmetRecipe = builder.define("disableHybridHelmetRecipe", false);
            disableUltimateHelmetRecipe = builder.define("disableUltimateHelmetRecipe", false);
            disableMolecularTransformerRecipe =
                    builder.define("disableMolecularTransformerRecipe", false);
            builder.pop();

            builder.comment("Kinetic wind/water generator multipliers and classic rotor durability wear")
                    .push("kinetic");
            enableVanillaRotorWear = builder.define("enableVanillaRotorWear", true);
            vanillaRotorWearMultiplier =
                    builder.defineInRange("vanillaRotorWearMultiplier", 1.0, 0.0, 100.0);
            durableKineticOutputMultiplier =
                    builder.defineInRange("durableKineticOutputMultiplier", 0.80, 0.05, 100.0);
            durableKineticWearMultiplier =
                    builder.defineInRange("durableKineticWearMultiplier", 0.50, 0.0, 100.0);
            overclockedKineticOutputMultiplier =
                    builder.defineInRange("overclockedKineticOutputMultiplier", 2.20, 0.05, 100.0);
            overclockedKineticWearMultiplier =
                    builder.defineInRange("overclockedKineticWearMultiplier", 2.00, 0.0, 100.0);
            precisionKineticOutputMultiplier =
                    builder.defineInRange("precisionKineticOutputMultiplier", 2.40, 0.05, 100.0);
            precisionKineticWearMultiplier =
                    builder.defineInRange("precisionKineticWearMultiplier", 0.50, 0.0, 100.0);
            builder.pop();

            builder.comment("3x3x3 Giant Machine multiblock balance settings").push("giant");
            giantBaseTier = builder.defineInRange("giantBaseTier", 3, 1, 14);
            giantMaxBatchPerLane = builder.defineInRange("giantMaxBatchPerLane", 3, 1, 64);
            giantWasherWaterCapacity =
                    builder.defineInRange("giantWasherWaterCapacity", 64_000, 1_000, 1_000_000);
            builder.pop();

            builder.comment("Water Gas, Natural Gas, and Biogas industry settings").push("gas");
            waterGasSolarEuPerTick = builder.defineInRange("waterGasSolarEuPerTick", 2, 0, 1_000);
            waterGasEnergyPerBucket =
                    builder.defineInRange("waterGasEnergyPerBucket", 10_000, 100, 10_000_000);
            waterGasOutputEuPerTick =
                    builder.defineInRange("waterGasOutputEuPerTick", 32, 1, 100_000);
            naturalGasEnergyPerBucket =
                    builder.defineInRange("naturalGasEnergyPerBucket", 10_000, 100, 10_000_000);
            naturalGasOutputEuPerTick =
                    builder.defineInRange("naturalGasOutputEuPerTick", 64, 1, 100_000);
            biogasEnergyPerBucket =
                    builder.defineInRange("biogasEnergyPerBucket", 16_000, 100, 10_000_000);
            biogasOutputEuPerTick =
                    builder.defineInRange("biogasOutputEuPerTick", 32, 1, 100_000);
            gasFurnaceGasPerItem = builder.defineInRange("gasFurnaceGasPerItem", 50, 1, 10_000);
            gasFurnaceBaseTicks = builder.defineInRange("gasFurnaceBaseTicks", 100, 1, 10_000);
            organicFermenterBiogasPerHunger =
                    builder.defineInRange("organicFermenterBiogasPerHunger", 30, 1, 1_000);
            organicFermenterCulturePerOp =
                    builder.defineInRange("organicFermenterCulturePerOp", 50, 1, 8_000);
            builder.pop();
        }
    }

    /** Client-only rendering configuration schema. */
    public static final class Client {
        public final ForgeConfigSpec.BooleanValue drawMolecularTransformerCore;
        public final ForgeConfigSpec.BooleanValue enableRotorRenderAnimation;

        Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Ecolera client rendering options").push("rendering");
            drawMolecularTransformerCore =
                    builder.comment("Render animated 3D plasma core inside Molecular Transformers")
                            .define("drawMolecularTransformerCore", true);
            enableRotorRenderAnimation =
                    builder.comment(
                                    "Render animated 3D rotors on advanced wind and water kinetic generators")
                            .define("enableRotorRenderAnimation", true);
            builder.pop();
        }
    }
}
