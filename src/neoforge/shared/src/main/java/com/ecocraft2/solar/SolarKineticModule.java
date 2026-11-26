package com.ecocraft2.solar;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.kinetic.KineticMachineTier;
import com.ecocraft2.common.solar.SolarHelmetSpec;
import com.ecocraft2.common.solar.SolarTier;
import com.ecocraft2.kinetic.AdvancedKineticBlockEntity;
import com.ecocraft2.kinetic.AdvancedKineticMenu;
import com.ecocraft2.kinetic.AdvancedWaterKineticBlock;
import com.ecocraft2.kinetic.AdvancedWindKineticBlock;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Central NeoForge module registrar for EcoCraft 2's Advanced Solar Panels 2 (ASP 2) and Advanced
 * Wind/Water Kinetic Generators.
 *
 * <p>Automatically discovered and invoked by {@link EcoCraft2} via
 * {@link #install(IEventBus, IEventBus, ModContainer, Dist)}. Exposes all public registry holders
 * and recipe query helpers for optional compat modules (JEI, EMI, Jade, TOP).
 */
public final class SolarKineticModule {

    /** Matched Molecular Transformer recipe entry (recipe ID + live recipe instance). */
    public record MatchedMtRecipe(String id, MolecularTransformerRecipe recipe) {}

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(EcoCraft2.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(EcoCraft2.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EcoCraft2.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, EcoCraft2.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, EcoCraft2.MOD_ID);
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, EcoCraft2.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, EcoCraft2.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, EcoCraft2.MOD_ID);

    // ---- Data Components ----

    /** Stored EU data component on EcoCraft 2 electric items such as Solar Helmets. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> HELMET_EU =
            DATA_COMPONENTS.register(
                    "eu",
                    () ->
                            DataComponentType.<Double>builder()
                                    .persistent(Codec.DOUBLE)
                                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                                    .build());

    // ---- Blocks: 4 Solar Panels, Quantum Generator, Molecular Transformer ----

    public static final DeferredBlock<SolarPanelBlock> ADVANCED_SOLAR_PANEL =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.ADVANCED_SOLAR_PANEL,
                    props -> new SolarPanelBlock(props, SolarTier.ADVANCED),
                    SolarKineticModule::machineProperties);

    public static final DeferredBlock<SolarPanelBlock> HYBRID_SOLAR_PANEL =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.HYBRID_SOLAR_PANEL,
                    props -> new SolarPanelBlock(props, SolarTier.HYBRID),
                    SolarKineticModule::machineProperties);

    public static final DeferredBlock<SolarPanelBlock> ULTIMATE_HYBRID_SOLAR_PANEL =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.ULTIMATE_HYBRID_SOLAR_PANEL,
                    props -> new SolarPanelBlock(props, SolarTier.ULTIMATE_HYBRID),
                    SolarKineticModule::machineProperties);

    public static final DeferredBlock<SolarPanelBlock> QUANTUM_SOLAR_PANEL =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.QUANTUM_SOLAR_PANEL,
                    props -> new SolarPanelBlock(props, SolarTier.QUANTUM),
                    SolarKineticModule::machineProperties);

    public static final DeferredBlock<QuantumGeneratorBlock> QUANTUM_GENERATOR =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.QUANTUM_GENERATOR,
                    QuantumGeneratorBlock::new,
                    () ->
                            machineProperties()
                                    .lightLevel(
                                            state ->
                                                    state.getValue(QuantumGeneratorBlock.ACTIVE)
                                                            ? 6
                                                            : 0));

    public static final DeferredBlock<MolecularTransformerBlock> MOLECULAR_TRANSFORMER =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.MOLECULAR_TRANSFORMER,
                    MolecularTransformerBlock::new,
                    () ->
                            machineProperties()
                                    .noOcclusion()
                                    .lightLevel(
                                            state ->
                                                    state.getValue(
                                                                    MolecularTransformerBlock
                                                                            .ACTIVE)
                                                            ? 12
                                                            : 0));

    // ---- Blocks: 6 Advanced Wind & Water Kinetic Generators ----

    public static final DeferredBlock<AdvancedWindKineticBlock> DURABLE_WIND_KINETIC_GENERATOR =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.DURABLE_WIND_KINETIC_GENERATOR,
                    props -> new AdvancedWindKineticBlock(props, KineticMachineTier.DURABLE),
                    SolarKineticModule::machineProperties);

    public static final DeferredBlock<AdvancedWindKineticBlock> OVERCLOCKED_WIND_KINETIC_GENERATOR =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.OVERCLOCKED_WIND_KINETIC_GENERATOR,
                    props -> new AdvancedWindKineticBlock(props, KineticMachineTier.OVERCLOCKED),
                    SolarKineticModule::machineProperties);

    public static final DeferredBlock<AdvancedWindKineticBlock> PRECISION_WIND_KINETIC_GENERATOR =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.PRECISION_WIND_KINETIC_GENERATOR,
                    props -> new AdvancedWindKineticBlock(props, KineticMachineTier.PRECISION),
                    SolarKineticModule::machineProperties);

    public static final DeferredBlock<AdvancedWaterKineticBlock> DURABLE_WATER_KINETIC_GENERATOR =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.DURABLE_WATER_KINETIC_GENERATOR,
                    props -> new AdvancedWaterKineticBlock(props, KineticMachineTier.DURABLE),
                    SolarKineticModule::machineProperties);

    public static final DeferredBlock<AdvancedWaterKineticBlock>
            OVERCLOCKED_WATER_KINETIC_GENERATOR =
                    SolarPlatform.registerBlock(
                            BLOCKS,
                            Ecc2Ids.OVERCLOCKED_WATER_KINETIC_GENERATOR,
                            props ->
                                    new AdvancedWaterKineticBlock(
                                            props, KineticMachineTier.OVERCLOCKED),
                            SolarKineticModule::machineProperties);

    public static final DeferredBlock<AdvancedWaterKineticBlock> PRECISION_WATER_KINETIC_GENERATOR =
            SolarPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.PRECISION_WATER_KINETIC_GENERATOR,
                    props -> new AdvancedWaterKineticBlock(props, KineticMachineTier.PRECISION),
                    SolarKineticModule::machineProperties);

    // ---- BlockItems (12) ----

    public static final DeferredItem<BlockItem> ADVANCED_SOLAR_PANEL_ITEM =
            ITEMS.registerSimpleBlockItem(ADVANCED_SOLAR_PANEL);
    public static final DeferredItem<BlockItem> HYBRID_SOLAR_PANEL_ITEM =
            ITEMS.registerSimpleBlockItem(HYBRID_SOLAR_PANEL);
    public static final DeferredItem<BlockItem> ULTIMATE_HYBRID_SOLAR_PANEL_ITEM =
            ITEMS.registerSimpleBlockItem(ULTIMATE_HYBRID_SOLAR_PANEL);
    public static final DeferredItem<BlockItem> QUANTUM_SOLAR_PANEL_ITEM =
            ITEMS.registerSimpleBlockItem(QUANTUM_SOLAR_PANEL);
    public static final DeferredItem<BlockItem> QUANTUM_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(QUANTUM_GENERATOR);
    public static final DeferredItem<BlockItem> MOLECULAR_TRANSFORMER_ITEM =
            ITEMS.registerSimpleBlockItem(MOLECULAR_TRANSFORMER);
    public static final DeferredItem<BlockItem> DURABLE_WIND_KINETIC_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(DURABLE_WIND_KINETIC_GENERATOR);
    public static final DeferredItem<BlockItem> OVERCLOCKED_WIND_KINETIC_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(OVERCLOCKED_WIND_KINETIC_GENERATOR);
    public static final DeferredItem<BlockItem> PRECISION_WIND_KINETIC_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(PRECISION_WIND_KINETIC_GENERATOR);
    public static final DeferredItem<BlockItem> DURABLE_WATER_KINETIC_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(DURABLE_WATER_KINETIC_GENERATOR);
    public static final DeferredItem<BlockItem> OVERCLOCKED_WATER_KINETIC_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(OVERCLOCKED_WATER_KINETIC_GENERATOR);
    public static final DeferredItem<BlockItem> PRECISION_WATER_KINETIC_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(PRECISION_WATER_KINETIC_GENERATOR);

    // ---- Standalone ASP 2 Crafting Materials (14 exact IDs from spec/CONTRACTS.md) ----

    public static final DeferredItem<Item> SUNNARIUM =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.SUNNARIUM);
    public static final DeferredItem<Item> SUNNARIUM_PART =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.SUNNARIUM_PART);
    public static final DeferredItem<Item> SUNNARIUM_ALLOY =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.SUNNARIUM_ALLOY);
    public static final DeferredItem<Item> IRRADIANT_URANIUM =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.IRRADIANT_URANIUM);
    public static final DeferredItem<Item> ENRICHED_SUNNARIUM =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.ENRICHED_SUNNARIUM);
    public static final DeferredItem<Item> ENRICHED_SUNNARIUM_ALLOY =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.ENRICHED_SUNNARIUM_ALLOY);
    public static final DeferredItem<Item> IRRADIANT_GLASS_PANE =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.IRRADIANT_GLASS_PANE);
    public static final DeferredItem<Item> IRIDIUM_IRON_PLATE =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.IRIDIUM_IRON_PLATE);
    public static final DeferredItem<Item> REINFORCED_IRIDIUM_IRON_PLATE =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.REINFORCED_IRIDIUM_IRON_PLATE);
    public static final DeferredItem<Item> IRRADIANT_REINFORCED_PLATE =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.IRRADIANT_REINFORCED_PLATE);
    public static final DeferredItem<Item> IRIDIUM_INGOT =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.IRIDIUM_INGOT);
    public static final DeferredItem<Item> URANIUM_INGOT =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.URANIUM_INGOT);
    public static final DeferredItem<Item> MT_CORE =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.MT_CORE);
    public static final DeferredItem<Item> QUANTUM_CORE =
            SolarPlatform.registerSimpleItem(ITEMS, Ecc2Ids.QUANTUM_CORE);

    // ---- 3 Solar Helmets ----

    public static final DeferredItem<SolarHelmetItem> ADVANCED_SOLAR_HELMET =
            SolarPlatform.registerHelmet(
                    ITEMS, Ecc2Ids.ADVANCED_SOLAR_HELMET, SolarHelmetSpec.ADVANCED);
    public static final DeferredItem<SolarHelmetItem> HYBRID_SOLAR_HELMET =
            SolarPlatform.registerHelmet(
                    ITEMS, Ecc2Ids.HYBRID_SOLAR_HELMET, SolarHelmetSpec.HYBRID);
    public static final DeferredItem<SolarHelmetItem> ULTIMATE_HYBRID_SOLAR_HELMET =
            SolarPlatform.registerHelmet(
                    ITEMS, Ecc2Ids.ULTIMATE_HYBRID_SOLAR_HELMET, SolarHelmetSpec.ULTIMATE_HYBRID);

    // ---- Block Entities (4) ----

    public static final DeferredHolder<
                    BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>>
            SOLAR_PANEL_BE =
                    BLOCK_ENTITIES.register(
                            "solar_panel",
                            () ->
                                    SolarPlatform.createBlockEntityType(
                                            SolarPanelBlockEntity::new,
                                            ADVANCED_SOLAR_PANEL.get(),
                                            HYBRID_SOLAR_PANEL.get(),
                                            ULTIMATE_HYBRID_SOLAR_PANEL.get(),
                                            QUANTUM_SOLAR_PANEL.get()));

    public static final DeferredHolder<
                    BlockEntityType<?>, BlockEntityType<QuantumGeneratorBlockEntity>>
            QUANTUM_GENERATOR_BE =
                    BLOCK_ENTITIES.register(
                            "quantum_generator",
                            () ->
                                    SolarPlatform.createBlockEntityType(
                                            QuantumGeneratorBlockEntity::new,
                                            QUANTUM_GENERATOR.get()));

    public static final DeferredHolder<
                    BlockEntityType<?>, BlockEntityType<MolecularTransformerBlockEntity>>
            MOLECULAR_TRANSFORMER_BE =
                    BLOCK_ENTITIES.register(
                            "molecular_transformer",
                            () ->
                                    SolarPlatform.createBlockEntityType(
                                            MolecularTransformerBlockEntity::new,
                                            MOLECULAR_TRANSFORMER.get()));

    public static final DeferredHolder<
                    BlockEntityType<?>, BlockEntityType<AdvancedKineticBlockEntity>>
            KINETIC_GENERATOR_BE =
                    BLOCK_ENTITIES.register(
                            "advanced_kinetic_generator",
                            () ->
                                    SolarPlatform.createBlockEntityType(
                                            AdvancedKineticBlockEntity::new,
                                            DURABLE_WIND_KINETIC_GENERATOR.get(),
                                            OVERCLOCKED_WIND_KINETIC_GENERATOR.get(),
                                            PRECISION_WIND_KINETIC_GENERATOR.get(),
                                            DURABLE_WATER_KINETIC_GENERATOR.get(),
                                            OVERCLOCKED_WATER_KINETIC_GENERATOR.get(),
                                            PRECISION_WATER_KINETIC_GENERATOR.get()));

    // ---- Menus (4) ----

    public static final DeferredHolder<MenuType<?>, MenuType<SolarPanelMenu>> SOLAR_PANEL_MENU =
            MENUS.register("solar_panel", () -> IMenuTypeExtension.create(SolarPanelMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<QuantumGeneratorMenu>>
            QUANTUM_GENERATOR_MENU =
                    MENUS.register(
                            "quantum_generator",
                            () -> IMenuTypeExtension.create(QuantumGeneratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MolecularTransformerMenu>>
            MOLECULAR_TRANSFORMER_MENU =
                    MENUS.register(
                            "molecular_transformer",
                            () -> IMenuTypeExtension.create(MolecularTransformerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AdvancedKineticMenu>>
            KINETIC_GENERATOR_MENU =
                    MENUS.register(
                            "advanced_kinetic_generator",
                            () -> IMenuTypeExtension.create(AdvancedKineticMenu::new));

    // ---- Recipes & Conditions ----

    public static final DeferredHolder<RecipeType<?>, RecipeType<MolecularTransformerRecipe>>
            MOLECULAR_TRANSFORMER_RECIPE_TYPE =
                    RECIPE_TYPES.register(
                            Ecc2Ids.RECIPE_MOLECULAR_TRANSFORMER,
                            () ->
                                    SolarPlatform.createRecipeType(
                                            Ecc2Ids.RECIPE_MOLECULAR_TRANSFORMER));

    public static final DeferredHolder<
                    RecipeSerializer<?>, RecipeSerializer<MolecularTransformerRecipe>>
            MOLECULAR_TRANSFORMER_SERIALIZER =
                    RECIPE_SERIALIZERS.register(
                            Ecc2Ids.RECIPE_MOLECULAR_TRANSFORMER,
                            SolarPlatform::createMolecularTransformerSerializer);

    public static final DeferredHolder<
                    RecipeSerializer<?>, RecipeSerializer<SolarHelmetUpgradeRecipe>>
            SOLAR_HELMET_UPGRADE_SERIALIZER =
                    RECIPE_SERIALIZERS.register(
                            "solar_helmet_upgrade",
                            SolarPlatform::createSolarHelmetUpgradeSerializer);

    public static final DeferredHolder<
                    MapCodec<? extends ICondition>, MapCodec<SolarConfigRecipeCondition>>
            CONFIG_RECIPE_CONDITION =
                    CONDITIONS.register("config_flag", () -> SolarConfigRecipeCondition.CODEC);

    private SolarKineticModule() {}

    /** Entry point invoked automatically by {@link EcoCraft2}. */
    public static void install(
            IEventBus modBus, IEventBus gameBus, ModContainer container, Dist dist) {
        SolarPlatform.registerArmorMaterials(modBus);
        DATA_COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        CONDITIONS.register(modBus);

        SolarPlatform.registerPayloads(modBus);
        SolarPlatform.registerCapabilities(modBus);
        SolarPlatform.registerRecipeSyncAndFilter(gameBus);
        SolarHelmetHandler.install(gameBus);

        if (dist == Dist.CLIENT) {
            SolarClientPlatform.registerClient(modBus, gameBus);
        }

        EcoCraft2.addCreativeTabItems(
                output -> {
                    // 4 Solar Panels + Quantum Generator + Molecular Transformer
                    output.accept(ADVANCED_SOLAR_PANEL_ITEM.get());
                    output.accept(HYBRID_SOLAR_PANEL_ITEM.get());
                    output.accept(ULTIMATE_HYBRID_SOLAR_PANEL_ITEM.get());
                    output.accept(QUANTUM_SOLAR_PANEL_ITEM.get());
                    output.accept(QUANTUM_GENERATOR_ITEM.get());
                    output.accept(MOLECULAR_TRANSFORMER_ITEM.get());

                    // 6 Advanced Wind & Water Kinetic Generators
                    output.accept(DURABLE_WIND_KINETIC_GENERATOR_ITEM.get());
                    output.accept(OVERCLOCKED_WIND_KINETIC_GENERATOR_ITEM.get());
                    output.accept(PRECISION_WIND_KINETIC_GENERATOR_ITEM.get());
                    output.accept(DURABLE_WATER_KINETIC_GENERATOR_ITEM.get());
                    output.accept(OVERCLOCKED_WATER_KINETIC_GENERATOR_ITEM.get());
                    output.accept(PRECISION_WATER_KINETIC_GENERATOR_ITEM.get());

                    // 3 Solar Helmets (both empty and fully-charged variants)
                    output.accept(ADVANCED_SOLAR_HELMET.get());
                    output.accept(SolarHelmetLogic.createChargedStack(ADVANCED_SOLAR_HELMET.get()));
                    output.accept(HYBRID_SOLAR_HELMET.get());
                    output.accept(SolarHelmetLogic.createChargedStack(HYBRID_SOLAR_HELMET.get()));
                    output.accept(ULTIMATE_HYBRID_SOLAR_HELMET.get());
                    output.accept(
                            SolarHelmetLogic.createChargedStack(
                                    ULTIMATE_HYBRID_SOLAR_HELMET.get()));

                    // 14 ASP 2 Crafting Materials
                    output.accept(SUNNARIUM_PART.get());
                    output.accept(SUNNARIUM.get());
                    output.accept(SUNNARIUM_ALLOY.get());
                    output.accept(IRRADIANT_URANIUM.get());
                    output.accept(ENRICHED_SUNNARIUM.get());
                    output.accept(ENRICHED_SUNNARIUM_ALLOY.get());
                    output.accept(IRRADIANT_GLASS_PANE.get());
                    output.accept(IRIDIUM_IRON_PLATE.get());
                    output.accept(REINFORCED_IRIDIUM_IRON_PLATE.get());
                    output.accept(IRRADIANT_REINFORCED_PLATE.get());
                    output.accept(IRIDIUM_INGOT.get());
                    output.accept(URANIUM_INGOT.get());
                    output.accept(MT_CORE.get());
                    output.accept(QUANTUM_CORE.get());
                });
    }

    /**
     * Returns all currently available (runtime-resolvable) Molecular Transformer recipes for JEI,
     * EMI, and machine lookup.
     */
    public static List<MatchedMtRecipe> allMolecularTransformerRecipes(Level level) {
        return SolarPlatform.allMolecularTransformerRecipes(level);
    }

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }
}
