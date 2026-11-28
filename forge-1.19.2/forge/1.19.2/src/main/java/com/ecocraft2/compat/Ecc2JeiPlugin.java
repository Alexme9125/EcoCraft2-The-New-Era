package com.ecocraft2.compat;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.gas.GasRegistry;
import com.ecocraft2.solar.MolecularTransformerRecipe;
import com.ecocraft2.solar.SolarKineticRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Optional JEI plugin for EcoCraft 2 (Molecular Transformer + Water Gas / Gas Generator / Biogas). */
@JeiPlugin
public final class Ecc2JeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = Ecc2BridgePlatform.rl("jei_plugin");

    public static final RecipeType<MolecularTransformerRecipe> MT_TYPE =
            RecipeType.create("ecera", "molecular_transformer", MolecularTransformerRecipe.class);

    public static final RecipeType<GasFuelEntry> GAS_GEN_TYPE =
            RecipeType.create("ecera", "gas_generator", GasFuelEntry.class);

    public static final RecipeType<SimpleProcessEntry> WATER_GAS_TYPE =
            RecipeType.create("ecera", "water_gas", SimpleProcessEntry.class);

    public static final RecipeType<SimpleProcessEntry> CULTURE_TYPE =
            RecipeType.create("ecera", "culture_incubator", SimpleProcessEntry.class);

    public static final RecipeType<SimpleProcessEntry> FERMENTER_TYPE =
            RecipeType.create("ecera", "organic_fermenter", SimpleProcessEntry.class);

    public record GasFuelEntry(FluidStack fluid, int euPerBucket, int euPerTick) {}

    public record SimpleProcessEntry(
            List<ItemStack> inputItems, FluidStack inputFluid, FluidStack outputFluid, int euCost) {}

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MtCategory(gui),
                new GasGenCategory(gui),
                new SimpleProcessCategory(
                        gui,
                        WATER_GAS_TYPE,
                        "jei.ecera.category.water_gas",
                        new ItemStack(GasRegistry.WATER_GAS_MACHINE.get())),
                new SimpleProcessCategory(
                        gui,
                        CULTURE_TYPE,
                        "jei.ecera.category.culture_incubator",
                        new ItemStack(GasRegistry.CULTURE_INCUBATOR.get())),
                new SimpleProcessCategory(
                        gui,
                        FERMENTER_TYPE,
                        "jei.ecera.category.organic_fermenter",
                        new ItemStack(GasRegistry.ORGANIC_FERMENTER.get())));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            List<MolecularTransformerRecipe> mtRecipes =
                    level.getRecipeManager()
                            .getAllRecipesFor(SolarKineticRegistry.MT_RECIPE_TYPE.get())
                            .stream()
                            .filter(MolecularTransformerRecipe::isAvailable)
                            .toList();
            registration.addRecipes(MT_TYPE, mtRecipes);
        }

        List<GasFuelEntry> fuels = new ArrayList<>();
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
                fuels.add(new GasFuelEntry(new FluidStack(fluid, 1000), euPerBucket, euPerTick));
            }
        }
        registration.addRecipes(GAS_GEN_TYPE, fuels);

        registration.addRecipes(
                WATER_GAS_TYPE,
                List.of(
                        new SimpleProcessEntry(
                                collectCoalDustInputs(),
                                new FluidStack(Fluids.WATER, 100),
                                new FluidStack(GasRegistry.WATER_GAS.get(), 1000),
                                1000)));

        registration.addRecipes(
                CULTURE_TYPE,
                List.of(
                        new SimpleProcessEntry(
                                collectCultureNutrientInputs(),
                                new FluidStack(Fluids.WATER, 100),
                                new FluidStack(GasRegistry.CULTURE_MEDIUM.get(), 100),
                                200)));

        Fluid biogas = Ecc2BridgePlatform.findFluid(Ecc2Ids.IC2_BIOGAS_ID);
        if (biogas != Fluids.EMPTY) {
            int cultureCost = Ecc2Config.organicFermenterCulturePerOp();
            int perHunger = Ecc2Config.organicFermenterBiogasPerHunger();
            registration.addRecipes(
                    FERMENTER_TYPE,
                    List.of(
                            new SimpleProcessEntry(
                                    List.of(new ItemStack(Items.APPLE)),
                                    new FluidStack(GasRegistry.CULTURE_MEDIUM.get(), cultureCost),
                                    new FluidStack(biogas, 4 * perHunger),
                                    200),
                            new SimpleProcessEntry(
                                    List.of(new ItemStack(Items.BREAD)),
                                    new FluidStack(GasRegistry.CULTURE_MEDIUM.get(), cultureCost),
                                    new FluidStack(biogas, 5 * perHunger),
                                    200),
                            new SimpleProcessEntry(
                                    List.of(new ItemStack(Items.COOKED_BEEF)),
                                    new FluidStack(GasRegistry.CULTURE_MEDIUM.get(), cultureCost),
                                    new FluidStack(biogas, 8 * perHunger),
                                    200)));
        }
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

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                new ItemStack(SolarKineticRegistry.MOLECULAR_TRANSFORMER.get()), MT_TYPE);
        registration.addRecipeCatalyst(
                new ItemStack(GasRegistry.WATER_GAS_MACHINE.get()), WATER_GAS_TYPE);
        registration.addRecipeCatalyst(
                new ItemStack(GasRegistry.GAS_FURNACE.get()), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(
                new ItemStack(GasRegistry.GAS_GENERATOR.get()), GAS_GEN_TYPE);
        registration.addRecipeCatalyst(
                new ItemStack(GasRegistry.CULTURE_INCUBATOR.get()), CULTURE_TYPE);
        registration.addRecipeCatalyst(
                new ItemStack(GasRegistry.ORGANIC_FERMENTER.get()), FERMENTER_TYPE);
    }

    private static final class MtCategory implements IRecipeCategory<MolecularTransformerRecipe> {
        private final IDrawable bg;
        private final IDrawable icon;

        MtCategory(IGuiHelper gui) {
            this.bg = gui.createBlankDrawable(150, 46);
            this.icon =
                    gui.createDrawableItemStack(
                            new ItemStack(SolarKineticRegistry.MOLECULAR_TRANSFORMER.get()));
        }

        @Override
        public RecipeType<MolecularTransformerRecipe> getRecipeType() {
            return MT_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.ecera.category.molecular_transformer");
        }

        @Override
        public IDrawable getBackground() {
            return bg;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder,
                MolecularTransformerRecipe recipe,
                IFocusGroup focuses) {
            ItemStack[] matches = recipe.input().getItems();
            List<ItemStack> inputs = new ArrayList<>();
            for (ItemStack m : matches) {
                inputs.add(Ecc2BridgePlatform.copyWithCount(m, recipe.inputCount()));
            }
            builder.addSlot(RecipeIngredientRole.INPUT, 12, 14).addItemStacks(inputs);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 120, 14)
                    .addItemStack(recipe.resolveOutput());
        }

        @Override
        public void draw(
                MolecularTransformerRecipe recipe,
                IRecipeSlotsView slotsView,
                PoseStack poseStack,
                double mouseX,
                double mouseY) {
            var font = Minecraft.getInstance().font;
            Component text = Component.translatable("jei.ecera.energy_cost", recipe.energy());
            font.draw(poseStack, text, 38, 18, 0xFF334455);
        }
    }

    private static final class GasGenCategory implements IRecipeCategory<GasFuelEntry> {
        private final IDrawable bg;
        private final IDrawable icon;

        GasGenCategory(IGuiHelper gui) {
            this.bg = gui.createBlankDrawable(160, 40);
            this.icon = gui.createDrawableItemStack(new ItemStack(GasRegistry.GAS_GENERATOR.get()));
        }

        @Override
        public RecipeType<GasFuelEntry> getRecipeType() {
            return GAS_GEN_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.ecera.category.gas_generator");
        }

        @Override
        public IDrawable getBackground() {
            return bg;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder, GasFuelEntry recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 12)
                    .addIngredient(ForgeTypes.FLUID_STACK, recipe.fluid())
                    .setFluidRenderer(1000, false, 16, 16);
        }

        @Override
        public void draw(
                GasFuelEntry recipe,
                IRecipeSlotsView slotsView,
                PoseStack poseStack,
                double mouseX,
                double mouseY) {
            var font = Minecraft.getInstance().font;
            Component text =
                    Component.translatable(
                            "jei.ecera.fuel_output", recipe.euPerBucket(), recipe.euPerTick());
            font.draw(poseStack, text, 34, 16, 0xFF334455);
        }
    }

    private static final class SimpleProcessCategory implements IRecipeCategory<SimpleProcessEntry> {
        private final RecipeType<SimpleProcessEntry> type;
        private final String titleKey;
        private final IDrawable bg;
        private final IDrawable icon;

        SimpleProcessCategory(
                IGuiHelper gui,
                RecipeType<SimpleProcessEntry> type,
                String titleKey,
                ItemStack iconStack) {
            this.type = type;
            this.titleKey = titleKey;
            this.bg = gui.createBlankDrawable(150, 44);
            this.icon = gui.createDrawableItemStack(iconStack);
        }

        @Override
        public RecipeType<SimpleProcessEntry> getRecipeType() {
            return type;
        }

        @Override
        public Component getTitle() {
            return Component.translatable(titleKey);
        }

        @Override
        public IDrawable getBackground() {
            return bg;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder, SimpleProcessEntry recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 14).addItemStacks(recipe.inputItems());
            builder.addSlot(RecipeIngredientRole.INPUT, 34, 14)
                    .addIngredient(ForgeTypes.FLUID_STACK, recipe.inputFluid())
                    .setFluidRenderer(Math.max(100, recipe.inputFluid().getAmount()), false, 16, 16);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 120, 14)
                    .addIngredient(ForgeTypes.FLUID_STACK, recipe.outputFluid())
                    .setFluidRenderer(
                            Math.max(100, recipe.outputFluid().getAmount()), false, 16, 16);
        }

        @Override
        public void draw(
                SimpleProcessEntry recipe,
                IRecipeSlotsView slotsView,
                PoseStack poseStack,
                double mouseX,
                double mouseY) {
            var font = Minecraft.getInstance().font;
            Component text = Component.translatable("jei.ecera.energy_cost", recipe.euCost());
            font.draw(poseStack, text, 58, 18, 0xFF334455);
        }
    }
}
