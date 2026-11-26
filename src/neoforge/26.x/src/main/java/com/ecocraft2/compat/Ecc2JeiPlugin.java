package com.ecocraft2.compat;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.gas.CultureIncubatorScreen;
import com.ecocraft2.gas.GasFurnaceScreen;
import com.ecocraft2.gas.GasGeneratorScreen;
import com.ecocraft2.gas.OrganicFermenterScreen;
import com.ecocraft2.gas.WaterGasMachineScreen;
import com.ecocraft2.giant.GiantGasModule;
import com.ecocraft2.giant.GiantMachineScreen;
import com.ecocraft2.solar.Ecc2DataSync;
import com.ecocraft2.solar.MolecularTransformerScreen;
import com.ecocraft2.solar.SolarKineticModule;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * NeoForge 26.x (26.1.2 &amp; 26.2) JEI plugin for EcoCraft 2.
 *
 * <p>Loaded exclusively by JEI via {@code @JeiPlugin} when JEI is installed.
 */
@JeiPlugin
public final class Ecc2JeiPlugin implements IModPlugin {

    private static final Identifier PLUGIN_UID = id("jei_plugin");

    private static final Identifier TEX_MT = id("textures/gui/molecular_transformer.png");
    private static final Identifier TEX_WATER_GAS = id("textures/gui/water_gas_machine.png");
    private static final Identifier TEX_GAS_FURNACE = id("textures/gui/gas_furnace.png");
    private static final Identifier TEX_GAS_GEN = id("textures/gui/gas_generator.png");
    private static final Identifier TEX_CULTURE = id("textures/gui/culture_incubator.png");
    private static final Identifier TEX_FERMENTER = id("textures/gui/organic_fermenter.png");
    private static final Identifier TEX_GIANT = id("textures/gui/giant_machine.png");

    public static final IRecipeType<CompatRecipes.MtEntry> MT_TYPE =
            IRecipeType.create(
                    EcoCraft2.MOD_ID, "molecular_transformer", CompatRecipes.MtEntry.class);

    public static final IRecipeType<CompatRecipes.FluidProcessEntry> WATER_GAS_TYPE =
            IRecipeType.create(
                    EcoCraft2.MOD_ID, "water_gas", CompatRecipes.FluidProcessEntry.class);

    public static final IRecipeType<CompatRecipes.GasFurnaceFuelEntry> GAS_FURNACE_TYPE =
            IRecipeType.create(
                    EcoCraft2.MOD_ID, "gas_furnace", CompatRecipes.GasFurnaceFuelEntry.class);

    public static final IRecipeType<CompatRecipes.GasGenFuelEntry> GAS_GEN_TYPE =
            IRecipeType.create(
                    EcoCraft2.MOD_ID, "gas_generator", CompatRecipes.GasGenFuelEntry.class);

    public static final IRecipeType<CompatRecipes.FluidProcessEntry> CULTURE_TYPE =
            IRecipeType.create(
                    EcoCraft2.MOD_ID, "culture_incubator", CompatRecipes.FluidProcessEntry.class);

    public static final IRecipeType<CompatRecipes.FermenterEntry> FERMENTER_TYPE =
            IRecipeType.create(
                    EcoCraft2.MOD_ID, "organic_fermenter", CompatRecipes.FermenterEntry.class);

    public static final IRecipeType<CompatRecipes.Ic2BridgeEntry> IC2_RECIPES_TYPE =
            IRecipeType.create(
                    EcoCraft2.MOD_ID, "ic2_recipes", CompatRecipes.Ic2BridgeEntry.class);

    public static final IRecipeType<CompatRecipes.GiantMachineEntry> GIANT_MACHINE_TYPE =
            IRecipeType.create(
                    EcoCraft2.MOD_ID, "giant_machine", CompatRecipes.GiantMachineEntry.class);

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new MtCategory(gui),
                new FluidProcessCategory(
                        gui,
                        WATER_GAS_TYPE,
                        Component.translatable("jei.ecc2.category.water_gas"),
                        new ItemStack(GiantGasModule.WATER_GAS_MACHINE.get()),
                        TEX_WATER_GAS),
                new GasFurnaceCategory(gui),
                new GasGenCategory(gui),
                new FluidProcessCategory(
                        gui,
                        CULTURE_TYPE,
                        Component.translatable("jei.ecc2.category.culture_incubator"),
                        new ItemStack(GiantGasModule.CULTURE_INCUBATOR.get()),
                        TEX_CULTURE),
                new FermenterCategory(gui),
                new Ic2BridgeCategory(gui),
                new GiantMachineCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        registration.addRecipes(MT_TYPE, CompatRecipes.molecularTransformerRecipes(level));
        registration.addRecipes(WATER_GAS_TYPE, CompatRecipes.waterGasRecipes());
        registration.addRecipes(GAS_FURNACE_TYPE, CompatRecipes.gasFurnaceFuelRecipes());
        registration.addRecipes(GAS_GEN_TYPE, CompatRecipes.gasGeneratorFuelRecipes());
        registration.addRecipes(CULTURE_TYPE, CompatRecipes.cultureIncubatorRecipes());
        registration.addRecipes(FERMENTER_TYPE, CompatRecipes.organicFermenterRecipes());
        registration.addRecipes(IC2_RECIPES_TYPE, CompatRecipes.ic2BridgeRecipes());
        registration.addRecipes(GIANT_MACHINE_TYPE, CompatRecipes.giantMachineEntries());

        for (CompatRecipes.InfoPage page : CompatRecipes.infoPages()) {
            if (!page.items().isEmpty()) {
                registration.addItemStackInfo(
                        page.items(), page.lines().toArray(Component[]::new));
            }
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(
                MT_TYPE, new ItemStack(SolarKineticModule.MOLECULAR_TRANSFORMER.get()));
        registration.addCraftingStation(
                WATER_GAS_TYPE, new ItemStack(GiantGasModule.WATER_GAS_MACHINE.get()));
        registration.addCraftingStation(
                RecipeTypes.SMELTING, new ItemStack(GiantGasModule.GAS_FURNACE.get()));
        registration.addCraftingStation(
                GAS_FURNACE_TYPE, new ItemStack(GiantGasModule.GAS_FURNACE.get()));
        registration.addCraftingStation(
                GAS_GEN_TYPE, new ItemStack(GiantGasModule.GAS_GENERATOR.get()));
        registration.addCraftingStation(
                CULTURE_TYPE, new ItemStack(GiantGasModule.CULTURE_INCUBATOR.get()));
        registration.addCraftingStation(
                FERMENTER_TYPE, new ItemStack(GiantGasModule.ORGANIC_FERMENTER.get()));
        for (ItemStack catalyst : CompatRecipes.ic2BridgeCatalysts()) {
            registration.addCraftingStation(IC2_RECIPES_TYPE, catalyst);
        }
        for (ItemStack giant : CompatRecipes.giantMachineCatalysts()) {
            registration.addCraftingStation(GIANT_MACHINE_TYPE, giant);
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Exact coordinates from assets-gen/gui_coordinates.json
        registration.addRecipeClickArea(MolecularTransformerScreen.class, 23, 47, 10, 15, MT_TYPE);
        registration.addRecipeClickArea(
                WaterGasMachineScreen.class, 60, 46, 24, 15, WATER_GAS_TYPE);
        registration.addRecipeClickArea(
                GasFurnaceScreen.class, 90, 36, 24, 15, RecipeTypes.SMELTING, GAS_FURNACE_TYPE);
        registration.addRecipeClickArea(GasGeneratorScreen.class, 76, 36, 14, 14, GAS_GEN_TYPE);
        registration.addRecipeClickArea(
                CultureIncubatorScreen.class, 64, 46, 24, 15, CULTURE_TYPE);
        registration.addRecipeClickArea(
                OrganicFermenterScreen.class, 64, 44, 24, 15, FERMENTER_TYPE);
        registration.addRecipeClickArea(
                GiantMachineScreen.class, 100, 44, 24, 15, GIANT_MACHINE_TYPE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(EcoCraft2.MOD_ID, path);
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth - 6)) + "…";
    }

    // ---- Category 1: Molecular Transformer ----

    private static final class MtCategory implements IRecipeCategory<CompatRecipes.MtEntry> {
        private static final int WIDTH = 172;
        private static final int HEIGHT = 68;

        private final IDrawable icon;
        private final IDrawableStatic progressIdle;
        private final IDrawableAnimated progressFill;

        MtCategory(IGuiHelper gui) {
            this.icon =
                    gui.createDrawableItemStack(
                            new ItemStack(SolarKineticModule.MOLECULAR_TRANSFORMER.get()));
            this.progressIdle =
                    gui.drawableBuilder(TEX_MT, 224, 18, 10, 15).setTextureSize(256, 256).build();
            this.progressFill =
                    gui.drawableBuilder(TEX_MT, 224, 0, 10, 15)
                            .setTextureSize(256, 256)
                            .buildAnimated(60, IDrawableAnimated.StartDirection.TOP, false);
        }

        @Override
        public IRecipeType<CompatRecipes.MtEntry> getRecipeType() {
            return MT_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.ecc2.category.molecular_transformer");
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder, CompatRecipes.MtEntry recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 12, 5)
                    .setStandardSlotBackground()
                    .addItemStacks(recipe.inputs());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 12, 47)
                    .setStandardSlotBackground()
                    .add(recipe.output());
        }

        @Override
        public void draw(
                CompatRecipes.MtEntry recipe,
                IRecipeSlotsView slotsView,
                GuiGraphicsExtractor graphics,
                double mouseX,
                double mouseY) {
            progressIdle.draw(graphics, 15, 26);
            progressFill.draw(graphics, 15, 26);

            Font font = Minecraft.getInstance().font;
            int maxW = WIDTH - 42;
            String inName =
                    recipe.inputs().isEmpty()
                            ? "-"
                            : recipe.inputs().get(0).getHoverName().getString();
            String outName = recipe.output().getHoverName().getString();
            if (recipe.output().getCount() > 1) {
                outName = recipe.output().getCount() + "× " + outName;
            }
            String euLine =
                    Component.translatable(
                                    "jei.ecc2.energy_cost",
                                    Ecc2DataSync.formatInt(recipe.energyEu()))
                            .getString();

            graphics.text(
                    font,
                    trim(
                            font,
                            Component.translatable("gui.ecc2.mt.input", inName).getString(),
                            maxW),
                    38,
                    7,
                    0xFF283240,
                    false);
            graphics.text(
                    font,
                    trim(
                            font,
                            Component.translatable("gui.ecc2.mt.output", outName).getString(),
                            maxW),
                    38,
                    20,
                    0xFF1A5A6E,
                    false);
            graphics.text(
                    font,
                    trim(
                            font,
                            Component.translatable(
                                            "gui.ecc2.mt.energy",
                                            Ecc2DataSync.formatInt(recipe.energyEu()))
                                    .getString(),
                            maxW),
                    38,
                    35,
                    0xFF2C4A2C,
                    false);
            graphics.text(
                    font,
                    trim(
                            font,
                            Component.translatable("jei.ecc2.mt.tier_unlimited", euLine)
                                    .getString(),
                            maxW),
                    38,
                    49,
                    0xFF556677,
                    false);
        }
    }

    // ---- Categories 2 & 5: Water Gas Machine & Culture Incubator ----

    private static final class FluidProcessCategory
            implements IRecipeCategory<CompatRecipes.FluidProcessEntry> {
        private static final int WIDTH = 176;
        private static final int HEIGHT = 56;

        private final IRecipeType<CompatRecipes.FluidProcessEntry> type;
        private final Component title;
        private final IDrawable icon;
        private final IDrawableStatic arrowEmpty;
        private final IDrawableAnimated arrowFill;

        FluidProcessCategory(
                IGuiHelper gui,
                IRecipeType<CompatRecipes.FluidProcessEntry> type,
                Component title,
                ItemStack iconStack,
                Identifier tex) {
            this.type = type;
            this.title = title;
            this.icon = gui.createDrawableItemStack(iconStack);
            this.arrowEmpty = gui.getRecipeArrow();
            this.arrowFill =
                    gui.drawableBuilder(tex, 180, 0, 24, 15)
                            .setTextureSize(256, 256)
                            .buildAnimated(80, IDrawableAnimated.StartDirection.LEFT, false);
        }

        @Override
        public IRecipeType<CompatRecipes.FluidProcessEntry> getRecipeType() {
            return type;
        }

        @Override
        public Component getTitle() {
            return title;
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder,
                CompatRecipes.FluidProcessEntry recipe,
                IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 8)
                    .setStandardSlotBackground()
                    .add(
                            NeoForgeTypes.FLUID_STACK,
                            new FluidStack(recipe.inputFluid(), recipe.inputFluidMb()))
                    .setFluidRenderer(Math.max(100, recipe.inputFluidMb()), false, 16, 16);
            builder.addSlot(RecipeIngredientRole.INPUT, 34, 8)
                    .setStandardSlotBackground()
                    .addItemStacks(recipe.inputItems());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 96, 8)
                    .setStandardSlotBackground()
                    .add(
                            NeoForgeTypes.FLUID_STACK,
                            new FluidStack(recipe.outputFluid(), recipe.outputFluidMb()))
                    .setFluidRenderer(Math.max(100, recipe.outputFluidMb()), false, 16, 16);
        }

        @Override
        public void draw(
                CompatRecipes.FluidProcessEntry recipe,
                IRecipeSlotsView slotsView,
                GuiGraphicsExtractor graphics,
                double mouseX,
                double mouseY) {
            arrowEmpty.draw(graphics, 60, 9);
            arrowFill.draw(graphics, 60, 9);

            Font font = Minecraft.getInstance().font;
            String euTicks =
                    Component.translatable(
                                            "jei.ecc2.energy_cost",
                                            Ecc2DataSync.formatInt(recipe.euCost()))
                                    .getString()
                            + " / "
                            + recipe.ticks()
                            + "t";
            graphics.text(font, euTicks, 120, 12, 0xFF283240, false);
            graphics.text(
                    font,
                    trim(font, recipe.note().getString(), WIDTH - 16),
                    8,
                    34,
                    0xFF445566,
                    false);
            String flow =
                    Ecc2DataSync.formatInt(recipe.inputFluidMb())
                            + " mB → "
                            + Ecc2DataSync.formatInt(recipe.outputFluidMb())
                            + " mB";
            graphics.text(font, flow, 8, 45, 0xFF1A5A6E, false);
        }
    }

    // ---- Category 3: Gas Furnace Fuels ----

    private static final class GasFurnaceCategory
            implements IRecipeCategory<CompatRecipes.GasFurnaceFuelEntry> {
        private static final int WIDTH = 176;
        private static final int HEIGHT = 44;

        private final IDrawable icon;
        private final IDrawableAnimated flame;

        GasFurnaceCategory(IGuiHelper gui) {
            this.icon =
                    gui.createDrawableItemStack(new ItemStack(GiantGasModule.GAS_FURNACE.get()));
            this.flame =
                    gui.drawableBuilder(TEX_GAS_FURNACE, 180, 0, 14, 14)
                            .setTextureSize(256, 256)
                            .buildAnimated(60, IDrawableAnimated.StartDirection.TOP, true);
        }

        @Override
        public IRecipeType<CompatRecipes.GasFurnaceFuelEntry> getRecipeType() {
            return GAS_FURNACE_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.ecc2.category.gas_furnace");
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder,
                CompatRecipes.GasFurnaceFuelEntry recipe,
                IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 14)
                    .setStandardSlotBackground()
                    .add(
                            NeoForgeTypes.FLUID_STACK,
                            new FluidStack(recipe.fluid(), recipe.mbPerOp()))
                    .setFluidRenderer(recipe.mbPerOp(), false, 16, 16);
        }

        @Override
        public void draw(
                CompatRecipes.GasFurnaceFuelEntry recipe,
                IRecipeSlotsView slotsView,
                GuiGraphicsExtractor graphics,
                double mouseX,
                double mouseY) {
            flame.draw(graphics, 34, 15);
            Font font = Minecraft.getInstance().font;
            Component fuelName = Component.translatable(CompatTelemetry.fuelLangKey(recipe.spec()));
            graphics.text(font, fuelName, 54, 10, 0xFF1E3A52, false);
            graphics.text(
                    font,
                    trim(
                            font,
                            Component.translatable(
                                            "jei.ecc2.gas_furnace.rate",
                                            recipe.mbPerOp(),
                                            recipe.baseTicks())
                                    .getString(),
                            WIDTH - 58),
                    54,
                    24,
                    0xFF445566,
                    false);
        }
    }

    // ---- Category 4: Gas Generator Fuels ----

    private static final class GasGenCategory
            implements IRecipeCategory<CompatRecipes.GasGenFuelEntry> {
        private static final int WIDTH = 180;
        private static final int HEIGHT = 44;

        private final IDrawable icon;
        private final IDrawableAnimated flame;

        GasGenCategory(IGuiHelper gui) {
            this.icon =
                    gui.createDrawableItemStack(new ItemStack(GiantGasModule.GAS_GENERATOR.get()));
            this.flame =
                    gui.drawableBuilder(TEX_GAS_GEN, 180, 0, 14, 14)
                            .setTextureSize(256, 256)
                            .buildAnimated(60, IDrawableAnimated.StartDirection.TOP, true);
        }

        @Override
        public IRecipeType<CompatRecipes.GasGenFuelEntry> getRecipeType() {
            return GAS_GEN_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.ecc2.category.gas_generator");
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder,
                CompatRecipes.GasGenFuelEntry recipe,
                IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 14)
                    .setStandardSlotBackground()
                    .add(
                            NeoForgeTypes.FLUID_STACK,
                            new FluidStack(recipe.fluid(), recipe.amountMb()))
                    .setFluidRenderer(recipe.amountMb(), false, 16, 16);
        }

        @Override
        public void draw(
                CompatRecipes.GasGenFuelEntry recipe,
                IRecipeSlotsView slotsView,
                GuiGraphicsExtractor graphics,
                double mouseX,
                double mouseY) {
            flame.draw(graphics, 34, 15);
            Font font = Minecraft.getInstance().font;
            Component fuelName = Component.translatable(CompatTelemetry.fuelLangKey(recipe.spec()));
            Component stats =
                    Component.translatable(
                            "jei.ecc2.fuel_output",
                            Ecc2DataSync.formatInt(recipe.euPerBucket()),
                            Ecc2DataSync.formatInt(recipe.euPerTick()));
            graphics.text(font, fuelName, 54, 10, 0xFF1E3A52, false);
            graphics.text(
                    font, trim(font, stats.getString(), WIDTH - 58), 54, 24, 0xFF2C4A2C, false);
        }
    }

    // ---- Category 6: Organic Fermenter ----

    private static final class FermenterCategory
            implements IRecipeCategory<CompatRecipes.FermenterEntry> {
        private static final int WIDTH = 176;
        private static final int HEIGHT = 54;

        private final IDrawable icon;
        private final IDrawableStatic arrowEmpty;
        private final IDrawableAnimated arrowFill;

        FermenterCategory(IGuiHelper gui) {
            this.icon =
                    gui.createDrawableItemStack(
                            new ItemStack(GiantGasModule.ORGANIC_FERMENTER.get()));
            this.arrowEmpty = gui.getRecipeArrow();
            this.arrowFill =
                    gui.drawableBuilder(TEX_FERMENTER, 180, 0, 24, 15)
                            .setTextureSize(256, 256)
                            .buildAnimated(80, IDrawableAnimated.StartDirection.LEFT, false);
        }

        @Override
        public IRecipeType<CompatRecipes.FermenterEntry> getRecipeType() {
            return FERMENTER_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.ecc2.category.organic_fermenter");
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder,
                CompatRecipes.FermenterEntry recipe,
                IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 10, 8)
                    .setStandardSlotBackground()
                    .add(
                            NeoForgeTypes.FLUID_STACK,
                            new FluidStack(recipe.cultureFluid(), recipe.cultureMb()))
                    .setFluidRenderer(Math.max(100, recipe.cultureMb()), false, 16, 16);
            builder.addSlot(RecipeIngredientRole.INPUT, 34, 8)
                    .setStandardSlotBackground()
                    .add(recipe.food());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 96, 8)
                    .setStandardSlotBackground()
                    .add(
                            NeoForgeTypes.FLUID_STACK,
                            new FluidStack(recipe.biogasFluid(), recipe.biogasMb()))
                    .setFluidRenderer(Math.max(100, recipe.biogasMb()), false, 16, 16);
            if (!recipe.containerRemainder().isEmpty()) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 120, 8)
                        .setStandardSlotBackground()
                        .add(recipe.containerRemainder());
            }
        }

        @Override
        public void draw(
                CompatRecipes.FermenterEntry recipe,
                IRecipeSlotsView slotsView,
                GuiGraphicsExtractor graphics,
                double mouseX,
                double mouseY) {
            arrowEmpty.draw(graphics, 60, 9);
            arrowFill.draw(graphics, 60, 9);
            Font font = Minecraft.getInstance().font;
            String formula =
                    Component.translatable(
                                    "jei.ecc2.fermenter.yield",
                                    recipe.nutrition(),
                                    Ecc2DataSync.formatInt(recipe.biogasMb()))
                            .getString();
            String cost =
                    Component.translatable(
                                    "jei.ecc2.fermenter.cost",
                                    recipe.cultureMb(),
                                    recipe.euCost(),
                                    recipe.ticks())
                            .getString();
            graphics.text(font, trim(font, formula, WIDTH - 16), 8, 31, 0xFF1A5A6E, false);
            graphics.text(font, trim(font, cost, WIDTH - 16), 8, 43, 0xFF445566, false);
        }
    }

    // ---- Category 7: Methane Ice Canning & ASP IC2 Bridge Recipes ----

    private static final class Ic2BridgeCategory
            implements IRecipeCategory<CompatRecipes.Ic2BridgeEntry> {
        private static final int WIDTH = 176;
        private static final int HEIGHT = 50;

        private final IDrawable icon;
        private final IDrawableStatic arrow;

        Ic2BridgeCategory(IGuiHelper gui) {
            this.icon =
                    gui.createDrawableItemStack(
                            new ItemStack(GiantGasModule.METHANE_ICE_CLUSTER_ITEM.get()));
            this.arrow = gui.getRecipeArrow();
        }

        @Override
        public IRecipeType<CompatRecipes.Ic2BridgeEntry> getRecipeType() {
            return IC2_RECIPES_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatableWithFallback(
                    "jei.ecc2.category.ic2_recipes", "IC2 Canning & ASP Processing");
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder,
                CompatRecipes.Ic2BridgeEntry recipe,
                IFocusGroup focuses) {
            if (!recipe.inputs().isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, 10, 22)
                        .setStandardSlotBackground()
                        .add(recipe.inputs().get(0));
            }
            if (recipe.inputs().size() > 1) {
                builder.addSlot(RecipeIngredientRole.INPUT, 32, 22)
                        .setStandardSlotBackground()
                        .add(recipe.inputs().get(1));
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, 94, 22)
                    .setStandardSlotBackground()
                    .add(recipe.output());
            if (!recipe.catalystMachines().isEmpty()) {
                builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 146, 22)
                        .setStandardSlotBackground()
                        .addItemStacks(recipe.catalystMachines());
            }
        }

        @Override
        public void draw(
                CompatRecipes.Ic2BridgeEntry recipe,
                IRecipeSlotsView slotsView,
                GuiGraphicsExtractor graphics,
                double mouseX,
                double mouseY) {
            arrow.draw(graphics, 58, 23);
            Font font = Minecraft.getInstance().font;
            graphics.text(
                    font,
                    trim(font, recipe.machineLabel().getString(), WIDTH - 12),
                    6,
                    6,
                    0xFF283240,
                    false);
        }
    }

    // ---- Category 8: Giant Machines (3x3x3 Multiblocks) ----

    private static final class GiantMachineCategory
            implements IRecipeCategory<CompatRecipes.GiantMachineEntry> {
        private static final int WIDTH = 188;
        private static final int HEIGHT = 58;

        private final IDrawable icon;
        private final IDrawableAnimated flowArrow;

        GiantMachineCategory(IGuiHelper gui) {
            this.icon =
                    gui.createDrawableItemStack(
                            new ItemStack(GiantGasModule.GIANT_MACERATOR_UNIT.get()));
            this.flowArrow =
                    gui.drawableBuilder(TEX_GIANT, 24, 238, 24, 15)
                            .setTextureSize(256, 256)
                            .buildAnimated(60, IDrawableAnimated.StartDirection.LEFT, false);
        }

        @Override
        public IRecipeType<CompatRecipes.GiantMachineEntry> getRecipeType() {
            return GIANT_MACHINE_TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("jei.ecc2.category.giant_machine");
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return HEIGHT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(
                IRecipeLayoutBuilder builder,
                CompatRecipes.GiantMachineEntry recipe,
                IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 8, 8)
                    .setStandardSlotBackground()
                    .add(recipe.unitCubeStack());
            builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 64, 8)
                    .setStandardSlotBackground()
                    .add(recipe.sourceMachineIcon());
        }

        @Override
        public void draw(
                CompatRecipes.GiantMachineEntry recipe,
                IRecipeSlotsView slotsView,
                GuiGraphicsExtractor graphics,
                double mouseX,
                double mouseY) {
            flowArrow.draw(graphics, 32, 9);
            Font font = Minecraft.getInstance().font;
            Component title = Component.translatable(recipe.type().containerTitleKey());
            graphics.text(
                    font, trim(font, title.getString(), WIDTH - 92), 88, 12, 0xFF1E3A52, false);
            graphics.text(
                    font,
                    trim(font, CompatRecipes.giantSpecLine(recipe).getString(), WIDTH - 14),
                    8,
                    32,
                    0xFF2C4A2C,
                    false);
            graphics.text(
                    font,
                    trim(font, CompatRecipes.giantPowerLine(recipe).getString(), WIDTH - 14),
                    8,
                    44,
                    0xFF445566,
                    false);
        }
    }
}
