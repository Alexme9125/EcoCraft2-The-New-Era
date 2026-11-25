package com.ecocraft2.compat;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.giant.GiantGasModule;
import com.ecocraft2.solar.Ecc2DataSync;
import com.ecocraft2.solar.MolecularTransformerRecipe;
import com.ecocraft2.solar.SolarKineticModule;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * NeoForge 1.21.1 EMI ({@code @EmiEntrypoint}) plugin for EcoCraft 2.
 *
 * <p>Present exclusively in the 1.21.1 source set where {@code hasEmiCompat == true}, and loaded
 * solely by EMI when EMI is installed.
 */
@EmiEntrypoint
public final class Ecc2EmiPlugin implements EmiPlugin {

    private static final ResourceLocation TEX_MT = rl("textures/gui/molecular_transformer.png");

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeCategory mtCategory =
                new EmiRecipeCategory(
                        rl("molecular_transformer"),
                        EmiStack.of(SolarKineticModule.MOLECULAR_TRANSFORMER.get()));
        EmiRecipeCategory waterGasCategory =
                new EmiRecipeCategory(
                        rl("water_gas"), EmiStack.of(GiantGasModule.WATER_GAS_MACHINE.get()));
        EmiRecipeCategory gasFurnaceCategory =
                new EmiRecipeCategory(
                        rl("gas_furnace"), EmiStack.of(GiantGasModule.GAS_FURNACE.get()));
        EmiRecipeCategory gasGenCategory =
                new EmiRecipeCategory(
                        rl("gas_generator"), EmiStack.of(GiantGasModule.GAS_GENERATOR.get()));
        EmiRecipeCategory cultureCategory =
                new EmiRecipeCategory(
                        rl("culture_incubator"),
                        EmiStack.of(GiantGasModule.CULTURE_INCUBATOR.get()));
        EmiRecipeCategory fermenterCategory =
                new EmiRecipeCategory(
                        rl("organic_fermenter"),
                        EmiStack.of(GiantGasModule.ORGANIC_FERMENTER.get()));
        EmiRecipeCategory ic2RecipesCategory =
                new EmiRecipeCategory(
                        rl("ic2_recipes"),
                        EmiStack.of(GiantGasModule.METHANE_ICE_CLUSTER_ITEM.get())) {
                    @Override
                    public Component getName() {
                        return Component.translatableWithFallback(
                                "emi.category.ecc2.ic2_recipes", "IC2 Canning & ASP Processing");
                    }
                };
        EmiRecipeCategory giantMachineCategory =
                new EmiRecipeCategory(
                        rl("giant_machine"),
                        EmiStack.of(GiantGasModule.GIANT_MACERATOR_UNIT.get()));

        // Categories & Workstations
        registry.addCategory(mtCategory);
        registry.addWorkstation(
                mtCategory, EmiStack.of(SolarKineticModule.MOLECULAR_TRANSFORMER.get()));

        registry.addCategory(waterGasCategory);
        registry.addWorkstation(
                waterGasCategory, EmiStack.of(GiantGasModule.WATER_GAS_MACHINE.get()));

        registry.addCategory(gasFurnaceCategory);
        registry.addWorkstation(
                gasFurnaceCategory, EmiStack.of(GiantGasModule.GAS_FURNACE.get()));
        registry.addWorkstation(
                VanillaEmiRecipeCategories.SMELTING, EmiStack.of(GiantGasModule.GAS_FURNACE.get()));

        registry.addCategory(gasGenCategory);
        registry.addWorkstation(gasGenCategory, EmiStack.of(GiantGasModule.GAS_GENERATOR.get()));

        registry.addCategory(cultureCategory);
        registry.addWorkstation(
                cultureCategory, EmiStack.of(GiantGasModule.CULTURE_INCUBATOR.get()));

        registry.addCategory(fermenterCategory);
        registry.addWorkstation(
                fermenterCategory, EmiStack.of(GiantGasModule.ORGANIC_FERMENTER.get()));

        registry.addCategory(ic2RecipesCategory);
        for (ItemStack catalyst : CompatRecipes.ic2BridgeCatalysts()) {
            registry.addWorkstation(ic2RecipesCategory, EmiStack.of(catalyst));
        }

        registry.addCategory(giantMachineCategory);
        for (ItemStack giant : CompatRecipes.giantMachineCatalysts()) {
            registry.addWorkstation(giantMachineCategory, EmiStack.of(giant));
        }

        // 1. Molecular Transformer recipes (queried directly from EMI's RecipeManager)
        var mtRecipeType = SolarKineticModule.MOLECULAR_TRANSFORMER_RECIPE_TYPE.get();
        List<CompatRecipes.MtEntry> mtEntries = new ArrayList<>();
        for (RecipeHolder<MolecularTransformerRecipe> holder :
                registry.getRecipeManager().getAllRecipesFor(mtRecipeType)) {
            MolecularTransformerRecipe recipe = holder.value();
            if (!recipe.isAvailable() || recipe.energy() <= 0L) {
                continue;
            }
            List<ItemStack> inputs = recipe.resolveInputExamples();
            ItemStack output = recipe.resolveOutput();
            if (!inputs.isEmpty() && !output.isEmpty()) {
                mtEntries.add(
                        new CompatRecipes.MtEntry(
                                holder.id().toString(), inputs, output, recipe.energy()));
            }
        }
        if (mtEntries.isEmpty() && Minecraft.getInstance().level != null) {
            mtEntries = CompatRecipes.molecularTransformerRecipes(Minecraft.getInstance().level);
        }
        for (CompatRecipes.MtEntry entry : mtEntries) {
            registry.addRecipe(new MtEmiRecipe(mtCategory, entry));
        }

        // 2. Water Gas Synthesis
        for (CompatRecipes.FluidProcessEntry entry : CompatRecipes.waterGasRecipes()) {
            registry.addRecipe(new FluidProcessEmiRecipe(waterGasCategory, entry));
        }

        // 3. Gas Furnace Fuels
        for (CompatRecipes.GasFurnaceFuelEntry entry : CompatRecipes.gasFurnaceFuelRecipes()) {
            registry.addRecipe(new GasFurnaceFuelEmiRecipe(gasFurnaceCategory, entry));
        }

        // 4. Gas Generator Fuels
        for (CompatRecipes.GasGenFuelEntry entry : CompatRecipes.gasGeneratorFuelRecipes()) {
            registry.addRecipe(new GasGenFuelEmiRecipe(gasGenCategory, entry));
        }

        // 5. Culture Incubator
        for (CompatRecipes.FluidProcessEntry entry : CompatRecipes.cultureIncubatorRecipes()) {
            registry.addRecipe(new FluidProcessEmiRecipe(cultureCategory, entry));
        }

        // 6. Organic Fermenter
        for (CompatRecipes.FermenterEntry entry : CompatRecipes.organicFermenterRecipes()) {
            registry.addRecipe(new FermenterEmiRecipe(fermenterCategory, entry));
        }

        // 7. Methane Ice Canning & ASP IC2 Bridge Recipes
        for (CompatRecipes.Ic2BridgeEntry entry : CompatRecipes.ic2BridgeRecipes()) {
            registry.addRecipe(new Ic2BridgeEmiRecipe(ic2RecipesCategory, entry));
        }

        // 8. Giant Machines (3x3x3)
        for (CompatRecipes.GiantMachineEntry entry : CompatRecipes.giantMachineEntries()) {
            registry.addRecipe(new GiantMachineEmiRecipe(giantMachineCategory, entry));
        }

        // 9. Informative EmiInfoRecipe pages
        for (CompatRecipes.InfoPage page : CompatRecipes.infoPages()) {
            List<EmiIngredient> stacks =
                    page.items().stream().map(s -> (EmiIngredient) EmiStack.of(s)).toList();
            if (!stacks.isEmpty()) {
                registry.addRecipe(new EmiInfoRecipe(stacks, page.lines(), syntheticId(page.id())));
            }
        }
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(EcoCraft2.MOD_ID, path);
    }

    private static ResourceLocation syntheticId(String raw) {
        String clean = raw.replace(':', '_').replaceAll("[^a-z0-9/._-]", "_");
        return rl("/" + clean);
    }

    private static EmiIngredient itemChoices(List<ItemStack> stacks) {
        if (stacks.size() == 1) {
            return EmiStack.of(stacks.get(0));
        }
        return EmiIngredient.of(stacks.stream().map(EmiStack::of).toList());
    }

    // ---- 1. Molecular Transformer ----

    private static final class MtEmiRecipe extends BasicEmiRecipe {
        private final CompatRecipes.MtEntry entry;

        MtEmiRecipe(EmiRecipeCategory category, CompatRecipes.MtEntry entry) {
            super(category, syntheticId("mt_" + entry.id()), 168, 66);
            this.entry = entry;
            this.inputs.add(itemChoices(entry.inputs()));
            this.outputs.add(EmiStack.of(entry.output()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(inputs.get(0), 10, 4);
            widgets.addTexture(TEX_MT, 14, 25, 10, 15, 224, 18, 10, 15, 256, 256);
            widgets.addAnimatedTexture(
                    TEX_MT, 14, 25, 10, 15, 224, 0, 10, 15, 256, 256, 3000, false, false, false);
            widgets.addSlot(outputs.get(0), 10, 44).recipeContext(this);

            widgets.addText(
                    Component.translatable(
                            "gui.ecc2.mt.energy", Ecc2DataSync.formatInt(entry.energyEu())),
                    36,
                    14,
                    0xFF283240,
                    false);
            widgets.addText(
                    Component.translatable(
                            "jei.ecc2.energy_cost", Ecc2DataSync.formatCompact(entry.energyEu())),
                    36,
                    29,
                    0xFF1A5A6E,
                    false);
            widgets.addText(
                    Component.literal("Tier 14 · Unlimited EU/t"), 36, 44, 0xFF556677, false);
        }
    }

    // ---- 2 & 5. Water Gas & Culture Incubator ----

    private static final class FluidProcessEmiRecipe extends BasicEmiRecipe {
        private final CompatRecipes.FluidProcessEntry entry;

        FluidProcessEmiRecipe(EmiRecipeCategory category, CompatRecipes.FluidProcessEntry entry) {
            super(category, syntheticId(entry.id()), 172, 54);
            this.entry = entry;
            this.inputs.add(EmiStack.of(entry.inputFluid(), entry.inputFluidMb()));
            this.inputs.add(itemChoices(entry.inputItems()));
            this.outputs.add(EmiStack.of(entry.outputFluid(), entry.outputFluidMb()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addTank(inputs.get(0), 8, 6, 18, 18, Math.max(100, entry.inputFluidMb()));
            widgets.addSlot(inputs.get(1), 32, 6);
            widgets.addFillingArrow(56, 6, entry.ticks() * 50);
            widgets.addTank(outputs.get(0), 88, 6, 18, 18, Math.max(100, entry.outputFluidMb()))
                    .recipeContext(this);

            widgets.addText(
                    Component.literal(
                            Ecc2DataSync.formatInt(entry.euCost()) + " EU / " + entry.ticks() + "t"),
                    112,
                    11,
                    0xFF283240,
                    false);
            widgets.addText(entry.note(), 8, 32, 0xFF445566, false);
            widgets.addText(
                    Component.literal(
                            Ecc2DataSync.formatInt(entry.inputFluidMb())
                                    + " mB → "
                                    + Ecc2DataSync.formatInt(entry.outputFluidMb())
                                    + " mB"),
                    8,
                    43,
                    0xFF1A5A6E,
                    false);
        }
    }

    // ---- 3. Gas Furnace Fuels ----

    private static final class GasFurnaceFuelEmiRecipe extends BasicEmiRecipe {
        private final CompatRecipes.GasFurnaceFuelEntry entry;

        GasFurnaceFuelEmiRecipe(
                EmiRecipeCategory category, CompatRecipes.GasFurnaceFuelEntry entry) {
            super(category, syntheticId(entry.id()), 172, 42);
            this.entry = entry;
            this.inputs.add(EmiStack.of(entry.fluid(), entry.mbPerOp()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addTank(inputs.get(0), 8, 12, 18, 18, entry.mbPerOp());
            widgets.addTexture(EmiTexture.FULL_FLAME, 32, 14);
            widgets.addText(
                    Component.translatable(CompatTelemetry.fuelLangKey(entry.spec())),
                    52,
                    9,
                    0xFF1E3A52,
                    false);
            widgets.addText(
                    Component.literal(
                            entry.mbPerOp() + " mB / smelt (" + entry.baseTicks() + "t · 0 EU)"),
                    52,
                    22,
                    0xFF445566,
                    false);
        }
    }

    // ---- 4. Gas Generator Fuels ----

    private static final class GasGenFuelEmiRecipe extends BasicEmiRecipe {
        private final CompatRecipes.GasGenFuelEntry entry;

        GasGenFuelEmiRecipe(EmiRecipeCategory category, CompatRecipes.GasGenFuelEntry entry) {
            super(category, syntheticId(entry.id()), 176, 42);
            this.entry = entry;
            this.inputs.add(EmiStack.of(entry.fluid(), entry.amountMb()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addTank(inputs.get(0), 8, 12, 18, 18, entry.amountMb());
            widgets.addTexture(EmiTexture.FULL_FLAME, 32, 14);
            widgets.addText(
                    Component.translatable(CompatTelemetry.fuelLangKey(entry.spec())),
                    52,
                    9,
                    0xFF1E3A52,
                    false);
            widgets.addText(
                    Component.translatable(
                            "jei.ecc2.fuel_output",
                            Ecc2DataSync.formatInt(entry.euPerBucket()),
                            Ecc2DataSync.formatInt(entry.euPerTick())),
                    52,
                    22,
                    0xFF2C4A2C,
                    false);
        }
    }

    // ---- 6. Organic Fermenter ----

    private static final class FermenterEmiRecipe extends BasicEmiRecipe {
        private final CompatRecipes.FermenterEntry entry;

        FermenterEmiRecipe(EmiRecipeCategory category, CompatRecipes.FermenterEntry entry) {
            super(category, syntheticId(entry.id()), 172, 52);
            this.entry = entry;
            this.inputs.add(EmiStack.of(entry.cultureFluid(), entry.cultureMb()));
            this.inputs.add(EmiStack.of(entry.food()));
            this.outputs.add(EmiStack.of(entry.biogasFluid(), entry.biogasMb()));
            if (!entry.containerRemainder().isEmpty()) {
                this.outputs.add(EmiStack.of(entry.containerRemainder()));
            }
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addTank(inputs.get(0), 8, 6, 18, 18, Math.max(100, entry.cultureMb()));
            widgets.addSlot(inputs.get(1), 32, 6);
            widgets.addFillingArrow(56, 6, entry.ticks() * 50);
            widgets.addTank(outputs.get(0), 88, 6, 18, 18, Math.max(100, entry.biogasMb()))
                    .recipeContext(this);
            if (outputs.size() > 1) {
                widgets.addSlot(outputs.get(1), 112, 6).recipeContext(this);
            }
            widgets.addText(
                    Component.literal(
                            "H = "
                                    + entry.nutrition()
                                    + " → "
                                    + Ecc2DataSync.formatInt(entry.biogasMb())
                                    + " mB Biogas (20×H)"),
                    8,
                    30,
                    0xFF1A5A6E,
                    false);
            widgets.addText(
                    Component.literal(
                            entry.cultureMb()
                                    + " mB Culture · "
                                    + entry.euCost()
                                    + " EU / "
                                    + entry.ticks()
                                    + "t"),
                    8,
                    41,
                    0xFF445566,
                    false);
        }
    }

    // ---- 7. Methane Ice Canning & ASP IC2 Recipes ----

    private static final class Ic2BridgeEmiRecipe extends BasicEmiRecipe {
        private final CompatRecipes.Ic2BridgeEntry entry;

        Ic2BridgeEmiRecipe(EmiRecipeCategory category, CompatRecipes.Ic2BridgeEntry entry) {
            super(category, syntheticId(entry.id()), 172, 48);
            this.entry = entry;
            for (ItemStack in : entry.inputs()) {
                this.inputs.add(EmiStack.of(in));
            }
            this.outputs.add(EmiStack.of(entry.output()));
            if (!entry.catalystMachines().isEmpty()) {
                this.catalysts.add(itemChoices(entry.catalystMachines()));
            }
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addText(entry.machineLabel(), 6, 5, 0xFF283240, false);
            if (!inputs.isEmpty()) {
                widgets.addSlot(inputs.get(0), 8, 22);
            }
            if (inputs.size() > 1) {
                widgets.addSlot(inputs.get(1), 30, 22);
            }
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 56, 23);
            widgets.addSlot(outputs.get(0), 88, 22).recipeContext(this);
            if (!catalysts.isEmpty()) {
                widgets.addSlot(catalysts.get(0), 144, 22).catalyst(true);
            }
        }
    }

    // ---- 8. Giant Machines (3x3x3) ----

    private static final class GiantMachineEmiRecipe extends BasicEmiRecipe {
        private final CompatRecipes.GiantMachineEntry entry;

        GiantMachineEmiRecipe(EmiRecipeCategory category, CompatRecipes.GiantMachineEntry entry) {
            super(category, syntheticId(entry.id()), 184, 56);
            this.entry = entry;
            this.inputs.add(EmiStack.of(entry.unitCubeStack()));
            this.catalysts.add(EmiStack.of(entry.sourceMachineIcon()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(inputs.get(0), 8, 6);
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 32, 7);
            widgets.addSlot(catalysts.get(0), 62, 6).catalyst(true);
            widgets.addText(
                    Component.translatable(entry.type().containerTitleKey()),
                    86,
                    11,
                    0xFF1E3A52,
                    false);
            widgets.addText(CompatRecipes.giantSpecLine(entry), 8, 30, 0xFF2C4A2C, false);
            widgets.addText(CompatRecipes.giantPowerLine(entry), 8, 42, 0xFF445566, false);
        }
    }
}
