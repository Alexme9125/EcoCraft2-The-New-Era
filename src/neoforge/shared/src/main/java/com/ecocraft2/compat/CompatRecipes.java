package com.ecocraft2.compat;

import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.CultureIncubatorEngine;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.common.giant.GiantMachineEngine;
import com.ecocraft2.common.giant.GiantMachineSpec;
import com.ecocraft2.common.solar.SolarTier;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.gas.Ecc2Fluids;
import com.ecocraft2.giant.GiantGasModule;
import com.ecocraft2.giant.GiantGasPlatform;
import com.ecocraft2.giant.GiantMachineType;
import com.ecocraft2.solar.Ecc2DataSync;
import com.ecocraft2.solar.SolarKineticModule;
import com.ecocraft2.solar.SolarPlatform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Shared recipe models and builders for EcoCraft 2's JEI and EMI recipe viewer plugins.
 *
 * <p>Has zero imports from JEI or EMI so all recipe discovery, food scanning, and IC2 bridge recipe
 * extraction stays unified across NeoForge 1.21.1, 26.1.2, and 26.2.
 */
public final class CompatRecipes {

    private CompatRecipes() {}

    /** Display entry for a single active {@code ecc2:molecular_transformer} recipe. */
    public record MtEntry(String id, List<ItemStack> inputs, ItemStack output, long energyEu) {}

    /** Display entry for Water Gas Machine or Culture Incubator fluid synthesis. */
    public record FluidProcessEntry(
            String id,
            List<ItemStack> inputItems,
            Fluid inputFluid,
            int inputFluidMb,
            Fluid outputFluid,
            int outputFluidMb,
            int euCost,
            int ticks,
            Component note) {}

    /** Display entry for one of the 3 Gas Furnace fuels (50 mB / 100t per item). */
    public record GasFurnaceFuelEntry(
            String id, GasFuelSpec spec, Fluid fluid, int mbPerOp, int baseTicks) {}

    /** Display entry for one of the 3 Gas Generator fuels (EU / 1,000 mB @ EU/t). */
    public record GasGenFuelEntry(
            String id, GasFuelSpec spec, Fluid fluid, int amountMb, int euPerBucket, int euPerTick) {}

    /** Display entry for fermenting an edible item in the Organic Fermenter. */
    public record FermenterEntry(
            String id,
            ItemStack food,
            int nutrition,
            ItemStack containerRemainder,
            Fluid cultureFluid,
            int cultureMb,
            Fluid biogasFluid,
            int biogasMb,
            int euCost,
            int ticks) {}

    /**
     * Display entry for an EcoCraft 2 recipe bridged into IC2NE machines (Methane Ice Canning in
     * IC2 Canners + ASP Compressor &amp; Metal Former Extruder recipes).
     */
    public record Ic2BridgeEntry(
            String id,
            Component machineLabel,
            List<List<ItemStack>> inputSlots,
            ItemStack output,
            List<ItemStack> catalystMachines) {}

    /** Display entry documenting one 3x3x3 Giant Machine multiblock and its throughput specs. */
    public record GiantMachineEntry(
            String id,
            GiantMachineType type,
            ItemStack unitCubeStack,
            ItemStack sourceMachineIcon,
            int lanes,
            int batchPerLane,
            int baseTier,
            int maxPacketEu,
            int baseEuPerTick,
            int baseTicks,
            int waterPerOpMb,
            int waterCapacityMb,
            int scrapChanceDenom) {}

    /** Ingredient information page entry shared between JEI info and EMI {@code EmiInfoRecipe}. */
    public record InfoPage(String id, List<ItemStack> items, List<Component> lines) {}

    /** Resolves all currently active Molecular Transformer recipes. */
    public static List<MtEntry> molecularTransformerRecipes(Level level) {
        var result = new ArrayList<MtEntry>();
        for (SolarKineticModule.MatchedMtRecipe matched :
                SolarKineticModule.allMolecularTransformerRecipes(level)) {
            var recipe = matched.recipe();
            if (!recipe.isAvailable()) {
                continue;
            }
            List<ItemStack> inputs = recipe.resolveInputExamples();
            ItemStack output = recipe.resolveOutput();
            if (!inputs.isEmpty() && !output.isEmpty() && recipe.energy() > 0L) {
                result.add(new MtEntry(matched.id(), inputs, output, recipe.energy()));
            }
        }
        return result;
    }

    /** Builds the canonical Water Gas Synthesis recipe entry. */
    public static List<FluidProcessEntry> waterGasRecipes() {
        List<ItemStack> coalInputs = collectCoalDustInputs();
        Fluid waterGas = Ecc2Fluids.WATER_GAS_SOURCE.get();
        return List.of(
                new FluidProcessEntry(
                        "ecera:water_gas/coal_dust",
                        coalInputs,
                        Fluids.WATER,
                        WaterGasEngine.WATER_PER_OP_MB,
                        waterGas,
                        WaterGasEngine.GAS_OUTPUT_PER_OP_MB,
                        WaterGasEngine.BASE_EU_PER_OP,
                        WaterGasEngine.BASE_TICKS_PER_OP,
                        Component.translatable(
                                "jei.ecera.water_gas.note",
                                Ecc2DataSync.formatInt(Ecc2Config.waterGasSolarEuPerTick()))));
    }

    /** Builds the 3 Gas Furnace fuel entries (configured mB / base ticks per smelt). */
    public static List<GasFurnaceFuelEntry> gasFurnaceFuelRecipes() {
        var list = new ArrayList<GasFurnaceFuelEntry>(3);
        int mbPerItem = Ecc2Config.gasFurnaceGasPerItem();
        int baseTicks = Ecc2Config.gasFurnaceBaseTicks();
        for (GasFuelSpec spec : GasFuelSpec.values()) {
            Fluid fluid = GiantGasPlatform.findFluid(spec.fluidId());
            if (fluid != Fluids.EMPTY) {
                list.add(
                        new GasFurnaceFuelEntry(
                                "ecera:gas_furnace/" + spec.name().toLowerCase(Locale.ROOT),
                                spec,
                                fluid,
                                mbPerItem,
                                baseTicks));
            }
        }
        return list;
    }

    /** Builds the 3 Gas Generator fuel entries (configured EU / 1,000 mB and EU/t). */
    public static List<GasGenFuelEntry> gasGeneratorFuelRecipes() {
        var list = new ArrayList<GasGenFuelEntry>(3);
        for (GasFuelSpec spec : GasFuelSpec.values()) {
            Fluid fluid = GiantGasPlatform.findFluid(spec.fluidId());
            if (fluid != Fluids.EMPTY) {
                int euPerBucket =
                        switch (spec) {
                            case WATER_GAS -> Ecc2Config.waterGasEnergyPerBucket();
                            case NATURAL_GAS -> Ecc2Config.naturalGasEnergyPerBucket();
                            case BIOGAS -> Ecc2Config.biogasEnergyPerBucket();
                        };
                int euPerTick =
                        switch (spec) {
                            case WATER_GAS -> Ecc2Config.waterGasOutputEuPerTick();
                            case NATURAL_GAS -> Ecc2Config.naturalGasOutputEuPerTick();
                            case BIOGAS -> Ecc2Config.biogasOutputEuPerTick();
                        };
                list.add(
                        new GasGenFuelEntry(
                                "ecera:gas_generator/" + spec.name().toLowerCase(Locale.ROOT),
                                spec,
                                fluid,
                                1000,
                                euPerBucket,
                                euPerTick));
            }
        }
        return list;
    }

    /** Builds the canonical Culture Incubator recipe entry from {@code #ecc2:culture_nutrients}. */
    public static List<FluidProcessEntry> cultureIncubatorRecipes() {
        Fluid culture = Ecc2Fluids.CULTURE_MEDIUM_SOURCE.get();
        Component note = Component.translatable("jei.ecera.culture_incubator.note");
        return List.of(
                new FluidProcessEntry(
                        "ecera:culture_incubator/nutrients",
                        collectCultureNutrientInputs(),
                        Fluids.WATER,
                        CultureIncubatorEngine.WATER_PER_OP_MB,
                        culture,
                        CultureIncubatorEngine.CULTURE_OUTPUT_PER_OP_MB,
                        CultureIncubatorEngine.BASE_EU_PER_OP,
                        CultureIncubatorEngine.BASE_TICKS_PER_OP,
                        note));
    }

    /**
     * Scans {@link BuiltInRegistries#ITEM} for edible foods ({@code FoodProperties.nutrition() >
     * 0}) and produces Organic Fermenter recipes yielding {@code 30 * H mB ic2:biogas}.
     */
    public static List<FermenterEntry> organicFermenterRecipes() {
        Fluid culture = Ecc2Fluids.CULTURE_MEDIUM_SOURCE.get();
        Fluid biogas = GiantGasPlatform.findFluid(Ecc2Ids.IC2_BIOGAS_ID);
        if (biogas == Fluids.EMPTY) {
            return List.of();
        }
        int biogasPerHunger = Ecc2Config.organicFermenterBiogasPerHunger();
        int culturePerOp = Ecc2Config.organicFermenterCulturePerOp();
        var list = new ArrayList<FermenterEntry>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            int nutrition = GiantGasPlatform.foodNutrition(stack);
            if (nutrition <= 0) {
                continue;
            }
            int yieldMb =
                    (int)
                            Math.min(
                                    OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB,
                                    (long) nutrition * biogasPerHunger);
            if (yieldMb <= 0) {
                continue;
            }
            ItemStack remainder = GiantGasPlatform.foodContainerRemainder(stack);
            String itemKey = GiantGasPlatform.itemId(stack).replace(':', '_');
            list.add(
                    new FermenterEntry(
                            "ecera:organic_fermenter/" + itemKey,
                            stack,
                            nutrition,
                            remainder,
                            culture,
                            culturePerOp,
                            biogas,
                            yieldMb,
                            OrganicFermenterEngine.BASE_EU_PER_OP,
                            OrganicFermenterEngine.BASE_TICKS_PER_OP));
        }
        list.sort(
                Comparator.comparingInt(FermenterEntry::nutrition)
                        .reversed()
                        .thenComparing(FermenterEntry::id));
        return list;
    }

    /**
     * Builds all EcoCraft 2 -> IC2NE bridge recipes (Methane Ice Canning + 6 ASP Compressor recipes
     * + ASP Metal Former Extruder recipe) directly from {@link Ecc2ExternalRecipes}.
     */
    public static List<Ic2BridgeEntry> ic2BridgeRecipes() {
        var list = new ArrayList<Ic2BridgeEntry>();

        List<ItemStack> canners =
                resolveItemStacks("mhera:canner", "mhera:solid_canner", "mhera:classic_canner");
        int canIdx = 0;
        for (var entry : Ecc2ExternalRecipes.canningBottleRecipes()) {
            List<ItemStack> ingredient =
                    resolveSpecStacks(entry.ingredientId(), entry.ingredientCount());
            List<ItemStack> container =
                    resolveSpecStacks(entry.containerId(), entry.containerCount());
            ItemStack output = GiantGasPlatform.itemStack(entry.outputId(), entry.outputCount());
            if (!ingredient.isEmpty() && !container.isEmpty() && !output.isEmpty()) {
                list.add(
                        new Ic2BridgeEntry(
                                "ecera:mhera_recipes/canning_" + (canIdx++),
                                Component.translatable("jei.ecera.mhera_bridge.canning"),
                                List.of(ingredient, container),
                                output,
                                canners));
            }
        }

        List<ItemStack> compressors = new ArrayList<>(resolveItemStacks("mhera:compressor"));
        compressors.add(new ItemStack(GiantGasModule.GIANT_COMPRESSOR_UNIT.get()));
        int compIdx = 0;
        for (var entry : Ecc2ExternalRecipes.compressorRecipes()) {
            List<ItemStack> input = resolveSpecStacks(entry.inputId(), entry.inputCount());
            ItemStack output = GiantGasPlatform.itemStack(entry.outputId(), entry.outputCount());
            if (!input.isEmpty() && !output.isEmpty()) {
                list.add(
                        new Ic2BridgeEntry(
                                "ecera:mhera_recipes/compressor_" + (compIdx++),
                                Component.translatable("jei.ecera.mhera_bridge.compressor"),
                                List.of(input),
                                output,
                                compressors));
            }
        }

        List<ItemStack> formers = resolveItemStacks("mhera:metal_former");
        int extIdx = 0;
        for (var entry : Ecc2ExternalRecipes.extruderRecipes()) {
            List<ItemStack> input = resolveSpecStacks(entry.inputId(), entry.inputCount());
            ItemStack output = GiantGasPlatform.itemStack(entry.outputId(), entry.outputCount());
            if (!input.isEmpty() && !output.isEmpty()) {
                list.add(
                        new Ic2BridgeEntry(
                                "ecera:mhera_recipes/extruder_" + (extIdx++),
                                Component.translatable("jei.ecera.mhera_bridge.extruder"),
                                List.of(input),
                                output,
                                formers));
            }
        }
        return list;
    }

    /** Returns all IC2NE workstation stacks relevant to {@link #ic2BridgeRecipes()}. */
    public static List<ItemStack> ic2BridgeCatalysts() {
        var list =
                new ArrayList<ItemStack>(
                        resolveItemStacks(
                                "mhera:canner",
                                "mhera:solid_canner",
                                "mhera:classic_canner",
                                "mhera:compressor",
                                "mhera:metal_former"));
        list.add(new ItemStack(GiantGasModule.GIANT_COMPRESSOR_UNIT.get()));
        return list;
    }

    /** Builds the 5 Giant Machine (3x3x3 multiblock) overview entries. */
    public static List<GiantMachineEntry> giantMachineEntries() {
        var list = new ArrayList<GiantMachineEntry>();
        for (GiantMachineType type : GiantMachineType.all()) {
            var blockHolder = GiantGasModule.giantUnitBlock(type);
            if (blockHolder == null) {
                continue;
            }
            GiantMachineSpec spec = type.effectiveSpec();
            ItemStack unitCube = new ItemStack(blockHolder.get(), 27);
            ItemStack sourceIcon = GiantGasPlatform.itemStack(spec.sourceMachineId(), 1);
            if (sourceIcon.isEmpty()) {
                sourceIcon = new ItemStack(blockHolder.get(), 1);
            }
            list.add(
                    new GiantMachineEntry(
                            "ecera:giant_machine/" + spec.id(),
                            type,
                            unitCube,
                            sourceIcon,
                            GiantMachineEngine.LANE_COUNT,
                            Ecc2Config.giantMaxBatchPerLane(),
                            spec.baseTier(),
                            (int) Math.round(SolarTier.packetSize(spec.baseTier())),
                            spec.baseEuPerTick(),
                            spec.baseOperationTicks(),
                            spec.waterPerOperationMb(),
                            spec.waterTankCapacityMb(),
                            spec.scrapChanceDenominator()));
        }
        return list;
    }

    /** Returns the 5 Giant Unit block stacks as catalysts/workstations. */
    public static List<ItemStack> giantMachineCatalysts() {
        var list = new ArrayList<ItemStack>();
        for (GiantMachineType type : GiantMachineType.all()) {
            var blockHolder = GiantGasModule.giantUnitBlock(type);
            if (blockHolder != null) {
                list.add(new ItemStack(blockHolder.get()));
            }
        }
        return list;
    }

    /** Builds informative JEI / EMI info pages for key EcoCraft 2 items and multiblocks. */
    public static List<InfoPage> infoPages() {
        var pages = new ArrayList<InfoPage>();
        pages.add(
                new InfoPage(
                        "ecera:info/methane_ice",
                        List.of(
                                new ItemStack(GiantGasModule.METHANE_ICE_CLUSTER_ITEM.get()),
                                new ItemStack(Ecc2Fluids.NATURAL_GAS_CELL.get())),
                        List.of(
                                Component.translatable("tooltip.ecera.methane_ice_cluster"),
                                Component.translatable("jei.ecera.info.methane_ice.canning"),
                                Component.translatable(
                                        "jei.ecera.info.methane_ice.fuel",
                                        Ecc2DataSync.formatInt(Ecc2Config.gasFurnaceGasPerItem()),
                                        Ecc2DataSync.formatInt(
                                                Ecc2Config.naturalGasEnergyPerBucket()),
                                        Ecc2DataSync.formatInt(
                                                Ecc2Config.naturalGasOutputEuPerTick())))));
        pages.add(
                new InfoPage(
                        "ecera:info/giant_machines",
                        giantMachineCatalysts(),
                        List.of(
                                Component.translatable("tooltip.ecera.giant_unit"),
                                Component.translatable("tooltip.ecera.giant_unit.ports"),
                                Component.translatable("jei.ecera.info.giant_machines.usage"))));
        var cultureCells = new ArrayList<ItemStack>();
        cultureCells.add(new ItemStack(Ecc2Fluids.CULTURE_MEDIUM_CELL.get()));
        cultureCells.add(new ItemStack(Ecc2Fluids.BASIC_CULTURE_MEDIUM_CELL.get()));
        cultureCells.add(new ItemStack(Ecc2Fluids.WATER_GAS_CELL.get()));
        ItemStack ic2BiogasCell = GiantGasPlatform.itemStack(Ecc2Ids.IC2_BIOGAS_CELL, 1);
        if (!ic2BiogasCell.isEmpty()) {
            cultureCells.add(ic2BiogasCell);
        }
        pages.add(
                new InfoPage(
                        "ecera:info/culture_cells",
                        cultureCells,
                        List.of(
                                Component.translatable("tooltip.ecera.water_gas_machine"),
                                Component.translatable("tooltip.ecera.culture_incubator"),
                                Component.translatable("tooltip.ecera.organic_fermenter"))));
        pages.add(
                new InfoPage(
                        "ecera:info/pv_power_upgrade",
                        List.of(
                                new ItemStack(GiantGasModule.PV_POWER_UPGRADE.get()),
                                new ItemStack(GiantGasModule.WATER_GAS_MACHINE_ITEM.get())),
                        List.of(
                                Component.translatable("tooltip.ecera.pv_power_upgrade"),
                                Component.translatable("tooltip.ecera.water_gas_machine"))));
        return pages;
    }

    /** Formats a Giant Machine's compact spec line for JEI/EMI category views. */
    public static Component giantSpecLine(GiantMachineEntry entry) {
        if (entry.waterPerOpMb() > 0) {
            return Component.translatable(
                    "jei.ecera.giant.spec_water",
                    entry.lanes(),
                    entry.batchPerLane(),
                    entry.baseEuPerTick(),
                    entry.baseTicks(),
                    Ecc2DataSync.formatInt(entry.waterPerOpMb()));
        }
        if (entry.scrapChanceDenom() > 0) {
            return Component.translatable(
                    "jei.ecera.giant.spec_scrap",
                    entry.lanes(),
                    entry.batchPerLane(),
                    entry.baseEuPerTick(),
                    entry.baseTicks(),
                    entry.scrapChanceDenom());
        }
        return Component.translatable(
                "jei.ecera.giant.spec",
                entry.lanes(),
                entry.batchPerLane(),
                entry.baseEuPerTick(),
                entry.baseTicks());
    }

    /** Formats a Giant Machine's power tier and port summary line. */
    public static Component giantPowerLine(GiantMachineEntry entry) {
        return Component.translatable(
                "jei.ecera.giant.power",
                entry.baseTier(),
                Ecc2DataSync.formatInt(entry.maxPacketEu()));
    }

    private static List<ItemStack> collectCoalDustInputs() {
        Map<Item, ItemStack> unique = new LinkedHashMap<>();
        Item ic2CoalDust = GiantGasPlatform.findItem("mhera:coal_dust");
        if (ic2CoalDust != null && ic2CoalDust != Items.AIR) {
            unique.put(ic2CoalDust, new ItemStack(ic2CoalDust));
        }
        for (ItemStack tagged :
                SolarPlatform.allItemsInTag(
                        Ecc2Ids.commonTag("c", Ecc2Ids.COMMON_TAG_DUSTS_COAL), 1)) {
            if (!tagged.isEmpty()) {
                unique.putIfAbsent(tagged.getItem(), tagged);
            }
        }
        if (unique.isEmpty()) {
            unique.put(Items.COAL, new ItemStack(Items.COAL));
        }
        return new ArrayList<>(unique.values());
    }

    private static List<ItemStack> collectCultureNutrientInputs() {
        Map<Item, ItemStack> unique = new LinkedHashMap<>();
        unique.put(Items.BONE_MEAL, new ItemStack(Items.BONE_MEAL));
        unique.put(Items.ROTTEN_FLESH, new ItemStack(Items.ROTTEN_FLESH));
        for (ItemStack tagged : SolarPlatform.allItemsInTag(Ecc2Ids.TAG_CULTURE_NUTRIENTS, 1)) {
            if (!tagged.isEmpty()) {
                unique.putIfAbsent(tagged.getItem(), tagged);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static List<ItemStack> resolveSpecStacks(String spec, int count) {
        if (spec == null || spec.isEmpty() || count <= 0) {
            return List.of();
        }
        if (spec.startsWith("#")) {
            return SolarPlatform.allItemsInTag(spec.substring(1), count);
        }
        ItemStack stack = GiantGasPlatform.itemStack(spec, count);
        return stack.isEmpty() ? List.of() : List.of(stack);
    }

    private static List<ItemStack> resolveItemStacks(String... ids) {
        var list = new ArrayList<ItemStack>(ids.length);
        for (String id : ids) {
            ItemStack stack = GiantGasPlatform.itemStack(id, 1);
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }
}
