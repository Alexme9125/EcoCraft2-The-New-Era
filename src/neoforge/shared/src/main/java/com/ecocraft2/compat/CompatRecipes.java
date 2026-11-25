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
            List<ItemStack> inputs,
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
                        "ecc2:water_gas/coal_dust",
                        coalInputs,
                        Fluids.WATER,
                        WaterGasEngine.WATER_PER_OP_MB,
                        waterGas,
                        WaterGasEngine.GAS_OUTPUT_PER_OP_MB,
                        WaterGasEngine.BASE_EU_PER_OP,
                        WaterGasEngine.BASE_TICKS_PER_OP,
                        Component.literal("Solar +2 EU/t (Day) + LV (Tier 1)")));
    }

    /** Builds the 3 Gas Furnace fuel entries (50 mB / 100t per smelt). */
    public static List<GasFurnaceFuelEntry> gasFurnaceFuelRecipes() {
        var list = new ArrayList<GasFurnaceFuelEntry>(3);
        for (GasFuelSpec spec : GasFuelSpec.values()) {
            Fluid fluid = GiantGasPlatform.findFluid(spec.fluidId());
            if (fluid != Fluids.EMPTY) {
                list.add(
                        new GasFurnaceFuelEntry(
                                "ecc2:gas_furnace/" + spec.name().toLowerCase(Locale.ROOT),
                                spec,
                                fluid,
                                GasFurnaceEngine.GAS_PER_ITEM_MB,
                                GasFurnaceEngine.BASE_SMELT_TICKS));
            }
        }
        return list;
    }

    /** Builds the 3 Gas Generator fuel entries (exact EU / 1,000 mB and EU/t). */
    public static List<GasGenFuelEntry> gasGeneratorFuelRecipes() {
        var list = new ArrayList<GasGenFuelEntry>(3);
        for (GasFuelSpec spec : GasFuelSpec.values()) {
            Fluid fluid = GiantGasPlatform.findFluid(spec.fluidId());
            if (fluid != Fluids.EMPTY) {
                list.add(
                        new GasGenFuelEntry(
                                "ecc2:gas_generator/" + spec.name().toLowerCase(Locale.ROOT),
                                spec,
                                fluid,
                                1000,
                                spec.euPerBucket(),
                                spec.euPerTick()));
            }
        }
        return list;
    }

    /** Builds the canonical Culture Incubator recipe entry. */
    public static List<FluidProcessEntry> cultureIncubatorRecipes() {
        Fluid culture = Ecc2Fluids.CULTURE_MEDIUM_SOURCE.get();
        return List.of(
                new FluidProcessEntry(
                        "ecc2:culture_incubator/bone_meal",
                        List.of(new ItemStack(Items.BONE_MEAL)),
                        Fluids.WATER,
                        CultureIncubatorEngine.WATER_PER_OP_MB,
                        culture,
                        CultureIncubatorEngine.CULTURE_OUTPUT_PER_OP_MB,
                        CultureIncubatorEngine.BASE_EU_PER_OP,
                        CultureIncubatorEngine.BASE_TICKS_PER_OP,
                        Component.literal("LV (Tier 1) · Fills Universal & Basic Cells")));
    }

    /**
     * Scans {@link BuiltInRegistries#ITEM} for edible foods ({@code FoodProperties.nutrition() >
     * 0}) and produces Organic Fermenter recipes yielding {@code 20 * H mB ic2:biogas}.
     */
    public static List<FermenterEntry> organicFermenterRecipes() {
        Fluid culture = Ecc2Fluids.CULTURE_MEDIUM_SOURCE.get();
        Fluid biogas = GiantGasPlatform.findFluid(Ecc2Ids.IC2_BIOGAS_ID);
        if (biogas == Fluids.EMPTY) {
            return List.of();
        }
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
            int yieldMb = OrganicFermenterEngine.biogasYieldForNutrition(nutrition);
            if (yieldMb <= 0) {
                continue;
            }
            ItemStack remainder = GiantGasPlatform.foodContainerRemainder(stack);
            String itemKey = GiantGasPlatform.itemId(stack).replace(':', '_');
            list.add(
                    new FermenterEntry(
                            "ecc2:organic_fermenter/" + itemKey,
                            stack,
                            nutrition,
                            remainder,
                            culture,
                            OrganicFermenterEngine.CULTURE_PER_OP_MB,
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
                resolveItemStacks("ic2:canner", "ic2:solid_canner", "ic2:classic_canner");
        int canIdx = 0;
        for (var entry : Ecc2ExternalRecipes.canningBottleRecipes()) {
            ItemStack ingredient =
                    GiantGasPlatform.itemStack(entry.ingredientId(), entry.ingredientCount());
            ItemStack container =
                    GiantGasPlatform.itemStack(entry.containerId(), entry.containerCount());
            ItemStack output = GiantGasPlatform.itemStack(entry.outputId(), entry.outputCount());
            if (!ingredient.isEmpty() && !container.isEmpty() && !output.isEmpty()) {
                list.add(
                        new Ic2BridgeEntry(
                                "ecc2:ic2_recipes/canning_" + (canIdx++),
                                Component.literal("IC2 Canning Machine (Bottle Mode)"),
                                List.of(ingredient, container),
                                output,
                                canners));
            }
        }

        List<ItemStack> compressors = new ArrayList<>(resolveItemStacks("ic2:compressor"));
        compressors.add(new ItemStack(GiantGasModule.GIANT_COMPRESSOR_UNIT.get()));
        int compIdx = 0;
        for (var entry : Ecc2ExternalRecipes.compressorRecipes()) {
            ItemStack input = GiantGasPlatform.itemStack(entry.inputId(), entry.inputCount());
            ItemStack output = GiantGasPlatform.itemStack(entry.outputId(), entry.outputCount());
            if (!input.isEmpty() && !output.isEmpty()) {
                list.add(
                        new Ic2BridgeEntry(
                                "ecc2:ic2_recipes/compressor_" + (compIdx++),
                                Component.literal("IC2 Compressor / Giant Compressor"),
                                List.of(input),
                                output,
                                compressors));
            }
        }

        List<ItemStack> formers = resolveItemStacks("ic2:metal_former");
        int extIdx = 0;
        for (var entry : Ecc2ExternalRecipes.extruderRecipes()) {
            ItemStack input = GiantGasPlatform.itemStack(entry.inputId(), entry.inputCount());
            ItemStack output = GiantGasPlatform.itemStack(entry.outputId(), entry.outputCount());
            if (!input.isEmpty() && !output.isEmpty()) {
                list.add(
                        new Ic2BridgeEntry(
                                "ecc2:ic2_recipes/extruder_" + (extIdx++),
                                Component.literal("IC2 Metal Former (Extruding)"),
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
                                "ic2:canner",
                                "ic2:solid_canner",
                                "ic2:classic_canner",
                                "ic2:compressor",
                                "ic2:metal_former"));
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
                            "ecc2:giant_machine/" + spec.id(),
                            type,
                            unitCube,
                            sourceIcon,
                            GiantMachineEngine.LANE_COUNT,
                            GiantMachineEngine.MAX_BATCH_PER_LANE,
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
                        "ecc2:info/methane_ice",
                        List.of(
                                new ItemStack(GiantGasModule.METHANE_ICE_CLUSTER_ITEM.get()),
                                new ItemStack(Ecc2Fluids.NATURAL_GAS_CELL.get())),
                        List.of(
                                Component.translatable("tooltip.ecc2.methane_ice_cluster"),
                                Component.literal(
                                        "Can 3x Methane Ice Cluster + 1x Universal Fluid Cell -> 1x Natural Gas Cell in any IC2 Canner."),
                                Component.literal(
                                        "Natural Gas fuels Gas Furnaces (50 mB/item) and Gas Generators (10,000 EU/B @ 64 EU/t)."))));
        pages.add(
                new InfoPage(
                        "ecc2:info/giant_machines",
                        giantMachineCatalysts(),
                        List.of(
                                Component.translatable("tooltip.ecc2.giant_unit"),
                                Component.translatable("tooltip.ecc2.giant_unit.ports"),
                                Component.literal(
                                        "Right-click any formed block to open the 9-lane parallel GUI and configure the 8 lower-corner I/O ports (Full / Input / Output / Blocked)."))));
        pages.add(
                new InfoPage(
                        "ecc2:info/culture_cells",
                        List.of(
                                new ItemStack(Ecc2Fluids.CULTURE_MEDIUM_CELL.get()),
                                new ItemStack(Ecc2Fluids.BASIC_CULTURE_MEDIUM_CELL.get()),
                                new ItemStack(Ecc2Fluids.WATER_GAS_CELL.get()),
                                new ItemStack(Ecc2Fluids.BIOGAS_CELL.get())),
                        List.of(
                                Component.translatable("tooltip.ecc2.water_gas_machine"),
                                Component.translatable("tooltip.ecc2.culture_incubator"),
                                Component.translatable("tooltip.ecc2.organic_fermenter"))));
        return pages;
    }

    /** Formats a Giant Machine's compact spec line for JEI/EMI category views. */
    public static Component giantSpecLine(GiantMachineEntry entry) {
        String base =
                entry.lanes()
                        + "×"
                        + entry.batchPerLane()
                        + " ops · "
                        + entry.baseEuPerTick()
                        + " EU/t · "
                        + entry.baseTicks()
                        + "t";
        if (entry.waterPerOpMb() > 0) {
            return Component.literal(
                    base
                            + " · "
                            + Ecc2DataSync.formatInt(entry.waterPerOpMb())
                            + " mB Water");
        }
        if (entry.scrapChanceDenom() > 0) {
            return Component.literal(base + " · 1/" + entry.scrapChanceDenom() + " Scrap");
        }
        return Component.literal(base);
    }

    /** Formats a Giant Machine's power tier and port summary line. */
    public static Component giantPowerLine(GiantMachineEntry entry) {
        return Component.literal(
                "3×3×3 Cube · Tier "
                        + entry.baseTier()
                        + " ("
                        + Ecc2DataSync.formatInt(entry.maxPacketEu())
                        + " EU/t) · 8 Corner Ports");
    }

    private static List<ItemStack> collectCoalDustInputs() {
        Map<Item, ItemStack> unique = new LinkedHashMap<>();
        Item ic2CoalDust = GiantGasPlatform.findItem("ic2:coal_dust");
        if (ic2CoalDust != null && ic2CoalDust != Items.AIR) {
            unique.put(ic2CoalDust, new ItemStack(ic2CoalDust));
        }
        for (ItemStack tagged : SolarPlatform.allItemsInTag("c:dusts/coal", 1)) {
            if (!tagged.isEmpty()) {
                unique.putIfAbsent(tagged.getItem(), tagged);
            }
        }
        if (unique.isEmpty()) {
            unique.put(Items.COAL, new ItemStack(Items.COAL));
        }
        return new ArrayList<>(unique.values());
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
