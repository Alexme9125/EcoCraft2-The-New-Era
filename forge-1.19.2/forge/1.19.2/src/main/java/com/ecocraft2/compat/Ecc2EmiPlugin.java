package com.ecocraft2.compat;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.gas.GasRegistry;
import com.ecocraft2.solar.MolecularTransformerRecipe;
import com.ecocraft2.solar.SolarKineticRegistry;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Optional EMI plugin for EcoCraft 2 (discovered automatically by EMI when present). */
@EmiEntrypoint
public final class Ecc2EmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeCategory mtCategory =
                new EmiRecipeCategory(
                        Ecc2BridgePlatform.rl("molecular_transformer"),
                        EmiStack.of(SolarKineticRegistry.MOLECULAR_TRANSFORMER.get()));
        EmiRecipeCategory waterGasCategory =
                new EmiRecipeCategory(
                        Ecc2BridgePlatform.rl("water_gas"),
                        EmiStack.of(GasRegistry.WATER_GAS_MACHINE.get()));
        EmiRecipeCategory gasGenCategory =
                new EmiRecipeCategory(
                        Ecc2BridgePlatform.rl("gas_generator"),
                        EmiStack.of(GasRegistry.GAS_GENERATOR.get()));
        EmiRecipeCategory cultureCategory =
                new EmiRecipeCategory(
                        Ecc2BridgePlatform.rl("culture_incubator"),
                        EmiStack.of(GasRegistry.CULTURE_INCUBATOR.get()));
        EmiRecipeCategory fermenterCategory =
                new EmiRecipeCategory(
                        Ecc2BridgePlatform.rl("organic_fermenter"),
                        EmiStack.of(GasRegistry.ORGANIC_FERMENTER.get()));

        registry.addCategory(mtCategory);
        registry.addWorkstation(
                mtCategory, EmiStack.of(SolarKineticRegistry.MOLECULAR_TRANSFORMER.get()));

        registry.addCategory(waterGasCategory);
        registry.addWorkstation(
                waterGasCategory, EmiStack.of(GasRegistry.WATER_GAS_MACHINE.get()));

        registry.addWorkstation(
                VanillaEmiRecipeCategories.SMELTING, EmiStack.of(GasRegistry.GAS_FURNACE.get()));

        registry.addCategory(gasGenCategory);
        registry.addWorkstation(gasGenCategory, EmiStack.of(GasRegistry.GAS_GENERATOR.get()));

        registry.addCategory(cultureCategory);
        registry.addWorkstation(
                cultureCategory, EmiStack.of(GasRegistry.CULTURE_INCUBATOR.get()));

        registry.addCategory(fermenterCategory);
        registry.addWorkstation(
                fermenterCategory, EmiStack.of(GasRegistry.ORGANIC_FERMENTER.get()));

        for (MolecularTransformerRecipe recipe :
                registry.getRecipeManager()
                        .getAllRecipesFor(SolarKineticRegistry.MT_RECIPE_TYPE.get())) {
            if (recipe.isAvailable()) {
                registry.addRecipe(new MtEmiRecipe(mtCategory, recipe));
            }
        }

        registry.addRecipe(
                new ProcessEmiRecipe(
                        waterGasCategory,
                        Ecc2BridgePlatform.rl("emi/water_gas/coal_dust"),
                        itemChoices(collectCoalDustInputs()),
                        EmiStack.of(Fluids.WATER, 100),
                        EmiStack.of(GasRegistry.WATER_GAS.get(), 1000),
                        1000));

        registry.addRecipe(
                new ProcessEmiRecipe(
                        cultureCategory,
                        Ecc2BridgePlatform.rl("emi/culture_incubator/nutrients"),
                        itemChoices(collectCultureNutrientInputs()),
                        EmiStack.of(Fluids.WATER, 100),
                        EmiStack.of(GasRegistry.CULTURE_MEDIUM.get(), 100),
                        200));

        Fluid biogas = Ecc2BridgePlatform.findFluid(Ecc2Ids.IC2_BIOGAS_ID);
        if (biogas != Fluids.EMPTY) {
            int cultureCost = Ecc2Config.organicFermenterCulturePerOp();
            int perHunger = Ecc2Config.organicFermenterBiogasPerHunger();
            registry.addRecipe(
                    new ProcessEmiRecipe(
                            fermenterCategory,
                            Ecc2BridgePlatform.rl("emi/organic_fermenter/apple"),
                            EmiStack.of(Items.APPLE),
                            EmiStack.of(GasRegistry.CULTURE_MEDIUM.get(), cultureCost),
                            EmiStack.of(biogas, 4 * perHunger),
                            200));
            registry.addRecipe(
                    new ProcessEmiRecipe(
                            fermenterCategory,
                            Ecc2BridgePlatform.rl("emi/organic_fermenter/bread"),
                            EmiStack.of(Items.BREAD),
                            EmiStack.of(GasRegistry.CULTURE_MEDIUM.get(), cultureCost),
                            EmiStack.of(biogas, 5 * perHunger),
                            200));
            registry.addRecipe(
                    new ProcessEmiRecipe(
                            fermenterCategory,
                            Ecc2BridgePlatform.rl("emi/organic_fermenter/cooked_beef"),
                            EmiStack.of(Items.COOKED_BEEF),
                            EmiStack.of(GasRegistry.CULTURE_MEDIUM.get(), cultureCost),
                            EmiStack.of(biogas, 8 * perHunger),
                            200));
        }

        for (GasFuelSpec spec : GasFuelSpec.values()) {
            Fluid fluid = Ecc2BridgePlatform.findFluid(spec.fluidId());
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
                registry.addRecipe(
                        new GasGenEmiRecipe(
                                gasGenCategory,
                                Ecc2BridgePlatform.rl(
                                        "emi/gas_generator/" + spec.name().toLowerCase(Locale.ROOT)),
                                EmiStack.of(fluid, 1000),
                                euPerBucket,
                                euPerTick));
            }
        }
    }

    private static final class MtEmiRecipe extends BasicEmiRecipe {
        private final long energy;

        MtEmiRecipe(EmiRecipeCategory category, MolecularTransformerRecipe recipe) {
            super(category, recipe.getId(), 148, 38);
            this.energy = recipe.energy();
            this.inputs.add(EmiIngredient.of(recipe.input(), recipe.inputCount()));
            this.outputs.add(EmiStack.of(recipe.resolveOutput()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(inputs.get(0), 8, 10);
            widgets.addText(
                    Component.translatable("jei.ecera.energy_cost", energy), 34, 15, 0xFF334455, false);
            widgets.addSlot(outputs.get(0), 120, 10).recipeContext(this);
        }
    }

    private static EmiIngredient itemChoices(List<ItemStack> stacks) {
        if (stacks.size() == 1) {
            return EmiStack.of(stacks.get(0));
        }
        return EmiIngredient.of(Ingredient.of(stacks.toArray(ItemStack[]::new)));
    }

    private static List<ItemStack> collectCoalDustInputs() {
        Map<Item, ItemStack> unique = new LinkedHashMap<>();
        Item coalDust = Ecc2BridgePlatform.findItem("mhera:coal_dust");
        if (coalDust != null && coalDust != Items.AIR) {
            unique.put(coalDust, new ItemStack(coalDust));
        }
        for (ItemStack tagged :
                Ecc2BridgePlatform.allItemsInTag(
                        Ecc2Ids.commonTag("forge", Ecc2Ids.COMMON_TAG_DUSTS_COAL), 1)) {
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
        for (ItemStack tagged :
                Ecc2BridgePlatform.allItemsInTag(Ecc2Ids.TAG_CULTURE_NUTRIENTS, 1)) {
            if (!tagged.isEmpty()) {
                unique.putIfAbsent(tagged.getItem(), tagged);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static final class ProcessEmiRecipe extends BasicEmiRecipe {
        private final int euCost;

        ProcessEmiRecipe(
                EmiRecipeCategory cat,
                ResourceLocation id,
                EmiIngredient itemIn,
                EmiStack fluidIn,
                EmiStack fluidOut,
                int euCost) {
            super(cat, id, 148, 38);
            this.euCost = euCost;
            this.inputs.add(itemIn);
            this.inputs.add(fluidIn);
            this.outputs.add(fluidOut);
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(inputs.get(0), 8, 10);
            widgets.addSlot(inputs.get(1), 30, 10);
            widgets.addText(
                    Component.translatable("jei.ecera.energy_cost", euCost), 54, 15, 0xFF334455, false);
            widgets.addSlot(outputs.get(0), 120, 10).recipeContext(this);
        }
    }

    private static final class GasGenEmiRecipe extends BasicEmiRecipe {
        private final int euPerBucket;
        private final int euPerTick;

        GasGenEmiRecipe(
                EmiRecipeCategory category,
                ResourceLocation id,
                EmiStack fluid,
                int euPerBucket,
                int euPerTick) {
            super(category, id, 160, 34);
            this.euPerBucket = euPerBucket;
            this.euPerTick = euPerTick;
            this.inputs.add(fluid);
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(inputs.get(0), 8, 8);
            widgets.addText(
                    Component.translatable("jei.ecera.fuel_output", euPerBucket, euPerTick),
                    32,
                    13,
                    0xFF334455,
                    false);
        }
    }
}
