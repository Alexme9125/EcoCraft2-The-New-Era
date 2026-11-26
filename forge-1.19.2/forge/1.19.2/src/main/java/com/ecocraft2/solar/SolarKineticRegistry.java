package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.kinetic.KineticMachineTier;
import com.ecocraft2.common.solar.SolarHelmetSpec;
import com.ecocraft2.common.solar.SolarTier;
import com.ecocraft2.kinetic.AdvancedKineticBlock;
import com.ecocraft2.kinetic.AdvancedKineticBlockEntity;
import com.ecocraft2.kinetic.AdvancedKineticMenu;
import com.ecocraft2.registry.Ecc2Registries;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;

/** Forge 1.19.2 registrations for ASP 2 (panels, helmets, MT, items) and 6 Kinetic Generators. */
public final class SolarKineticRegistry {

    // ---- 4 Solar Panel Blocks ----
    public static final RegistryObject<Block> ADVANCED_SOLAR_PANEL =
            registerBlock(Ecc2Ids.ADVANCED_SOLAR_PANEL, () -> new SolarPanelBlock(SolarTier.ADVANCED));
    public static final RegistryObject<Block> HYBRID_SOLAR_PANEL =
            registerBlock(Ecc2Ids.HYBRID_SOLAR_PANEL, () -> new SolarPanelBlock(SolarTier.HYBRID));
    public static final RegistryObject<Block> ULTIMATE_HYBRID_SOLAR_PANEL =
            registerBlock(
                    Ecc2Ids.ULTIMATE_HYBRID_SOLAR_PANEL,
                    () -> new SolarPanelBlock(SolarTier.ULTIMATE_HYBRID));
    public static final RegistryObject<Block> QUANTUM_SOLAR_PANEL =
            registerBlock(Ecc2Ids.QUANTUM_SOLAR_PANEL, () -> new SolarPanelBlock(SolarTier.QUANTUM));

    // ---- Creative Quantum Generator & Molecular Transformer ----
    public static final RegistryObject<Block> QUANTUM_GENERATOR =
            registerBlock(Ecc2Ids.QUANTUM_GENERATOR, QuantumGeneratorBlock::new);
    public static final RegistryObject<Block> MOLECULAR_TRANSFORMER =
            registerBlock(Ecc2Ids.MOLECULAR_TRANSFORMER, MolecularTransformerBlock::new);

    // ---- 6 Advanced Wind & Water Kinetic Generators ----
    public static final RegistryObject<Block> DURABLE_WIND_KINETIC =
            registerBlock(
                    Ecc2Ids.DURABLE_WIND_KINETIC_GENERATOR,
                    () -> AdvancedKineticBlock.wind(KineticMachineTier.DURABLE));
    public static final RegistryObject<Block> OVERCLOCKED_WIND_KINETIC =
            registerBlock(
                    Ecc2Ids.OVERCLOCKED_WIND_KINETIC_GENERATOR,
                    () -> AdvancedKineticBlock.wind(KineticMachineTier.OVERCLOCKED));
    public static final RegistryObject<Block> PRECISION_WIND_KINETIC =
            registerBlock(
                    Ecc2Ids.PRECISION_WIND_KINETIC_GENERATOR,
                    () -> AdvancedKineticBlock.wind(KineticMachineTier.PRECISION));
    public static final RegistryObject<Block> DURABLE_WATER_KINETIC =
            registerBlock(
                    Ecc2Ids.DURABLE_WATER_KINETIC_GENERATOR,
                    () -> AdvancedKineticBlock.water(KineticMachineTier.DURABLE));
    public static final RegistryObject<Block> OVERCLOCKED_WATER_KINETIC =
            registerBlock(
                    Ecc2Ids.OVERCLOCKED_WATER_KINETIC_GENERATOR,
                    () -> AdvancedKineticBlock.water(KineticMachineTier.OVERCLOCKED));
    public static final RegistryObject<Block> PRECISION_WATER_KINETIC =
            registerBlock(
                    Ecc2Ids.PRECISION_WATER_KINETIC_GENERATOR,
                    () -> AdvancedKineticBlock.water(KineticMachineTier.PRECISION));

    // ---- 14 ASP Crafting Items ----
    public static final RegistryObject<Item> SUNNARIUM = registerSimpleItem(Ecc2Ids.SUNNARIUM);
    public static final RegistryObject<Item> SUNNARIUM_PART =
            registerSimpleItem(Ecc2Ids.SUNNARIUM_PART);
    public static final RegistryObject<Item> SUNNARIUM_ALLOY =
            registerSimpleItem(Ecc2Ids.SUNNARIUM_ALLOY);
    public static final RegistryObject<Item> IRRADIANT_URANIUM =
            registerSimpleItem(Ecc2Ids.IRRADIANT_URANIUM);
    public static final RegistryObject<Item> ENRICHED_SUNNARIUM =
            registerSimpleItem(Ecc2Ids.ENRICHED_SUNNARIUM);
    public static final RegistryObject<Item> ENRICHED_SUNNARIUM_ALLOY =
            registerSimpleItem(Ecc2Ids.ENRICHED_SUNNARIUM_ALLOY);
    public static final RegistryObject<Item> IRRADIANT_GLASS_PANE =
            registerSimpleItem(Ecc2Ids.IRRADIANT_GLASS_PANE);
    public static final RegistryObject<Item> IRIDIUM_IRON_PLATE =
            registerSimpleItem(Ecc2Ids.IRIDIUM_IRON_PLATE);
    public static final RegistryObject<Item> REINFORCED_IRIDIUM_IRON_PLATE =
            registerSimpleItem(Ecc2Ids.REINFORCED_IRIDIUM_IRON_PLATE);
    public static final RegistryObject<Item> IRRADIANT_REINFORCED_PLATE =
            registerSimpleItem(Ecc2Ids.IRRADIANT_REINFORCED_PLATE);
    public static final RegistryObject<Item> IRIDIUM_INGOT =
            registerSimpleItem(Ecc2Ids.IRIDIUM_INGOT);
    public static final RegistryObject<Item> URANIUM_INGOT =
            registerSimpleItem(Ecc2Ids.URANIUM_INGOT);
    public static final RegistryObject<Item> MT_CORE = registerSimpleItem(Ecc2Ids.MT_CORE);
    public static final RegistryObject<Item> QUANTUM_CORE =
            registerSimpleItem(Ecc2Ids.QUANTUM_CORE);

    // ---- 3 Solar Helmets ----
    public static final RegistryObject<Item> ADVANCED_SOLAR_HELMET =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.ADVANCED_SOLAR_HELMET,
                    () ->
                            new SolarHelmetItem(
                                    SolarArmorMaterials.ADVANCED_SOLAR,
                                    SolarHelmetSpec.ADVANCED,
                                    "advanced_solar"));
    public static final RegistryObject<Item> HYBRID_SOLAR_HELMET =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.HYBRID_SOLAR_HELMET,
                    () ->
                            new DyeableSolarHelmetItem(
                                    SolarArmorMaterials.HYBRID_SOLAR,
                                    SolarHelmetSpec.HYBRID,
                                    "hybrid_solar"));
    public static final RegistryObject<Item> ULTIMATE_HYBRID_SOLAR_HELMET =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.ULTIMATE_HYBRID_SOLAR_HELMET,
                    () ->
                            new DyeableSolarHelmetItem(
                                    SolarArmorMaterials.ULTIMATE_HYBRID_SOLAR,
                                    SolarHelmetSpec.ULTIMATE_HYBRID,
                                    "ultimate_hybrid_solar"));

    // ---- Block Entities ----
    public static final RegistryObject<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL_BE =
            Ecc2Registries.BLOCK_ENTITIES.register(
                    "solar_panel",
                    () ->
                            BlockEntityType.Builder.of(
                                            SolarPanelBlockEntity::new,
                                            ADVANCED_SOLAR_PANEL.get(),
                                            HYBRID_SOLAR_PANEL.get(),
                                            ULTIMATE_HYBRID_SOLAR_PANEL.get(),
                                            QUANTUM_SOLAR_PANEL.get())
                                    .build(null));

    public static final RegistryObject<BlockEntityType<QuantumGeneratorBlockEntity>>
            QUANTUM_GENERATOR_BE =
                    Ecc2Registries.BLOCK_ENTITIES.register(
                            Ecc2Ids.QUANTUM_GENERATOR,
                            () ->
                                    BlockEntityType.Builder.of(
                                                    QuantumGeneratorBlockEntity::new,
                                                    QUANTUM_GENERATOR.get())
                                            .build(null));

    public static final RegistryObject<BlockEntityType<MolecularTransformerBlockEntity>>
            MOLECULAR_TRANSFORMER_BE =
                    Ecc2Registries.BLOCK_ENTITIES.register(
                            Ecc2Ids.MOLECULAR_TRANSFORMER,
                            () ->
                                    BlockEntityType.Builder.of(
                                                    MolecularTransformerBlockEntity::new,
                                                    MOLECULAR_TRANSFORMER.get())
                                            .build(null));

    public static final RegistryObject<BlockEntityType<AdvancedKineticBlockEntity>>
            ADVANCED_KINETIC_BE =
                    Ecc2Registries.BLOCK_ENTITIES.register(
                            "advanced_kinetic_generator",
                            () ->
                                    BlockEntityType.Builder.of(
                                                    AdvancedKineticBlockEntity::new,
                                                    DURABLE_WIND_KINETIC.get(),
                                                    OVERCLOCKED_WIND_KINETIC.get(),
                                                    PRECISION_WIND_KINETIC.get(),
                                                    DURABLE_WATER_KINETIC.get(),
                                                    OVERCLOCKED_WATER_KINETIC.get(),
                                                    PRECISION_WATER_KINETIC.get())
                                            .build(null));

    // ---- Menus ----
    public static final RegistryObject<MenuType<SolarPanelMenu>> SOLAR_PANEL_MENU =
            Ecc2Registries.MENUS.register(
                    "solar_panel", () -> IForgeMenuType.create(SolarPanelMenu::new));
    public static final RegistryObject<MenuType<QuantumGeneratorMenu>> QUANTUM_GENERATOR_MENU =
            Ecc2Registries.MENUS.register(
                    Ecc2Ids.QUANTUM_GENERATOR,
                    () -> IForgeMenuType.create(QuantumGeneratorMenu::new));
    public static final RegistryObject<MenuType<MolecularTransformerMenu>>
            MOLECULAR_TRANSFORMER_MENU =
                    Ecc2Registries.MENUS.register(
                            Ecc2Ids.MOLECULAR_TRANSFORMER,
                            () -> IForgeMenuType.create(MolecularTransformerMenu::new));
    public static final RegistryObject<MenuType<AdvancedKineticMenu>> ADVANCED_KINETIC_MENU =
            Ecc2Registries.MENUS.register(
                    "advanced_kinetic_generator",
                    () -> IForgeMenuType.create(AdvancedKineticMenu::new));

    // ---- Molecular Transformer Recipe Type & Serializer ----
    public static final RegistryObject<RecipeType<MolecularTransformerRecipe>> MT_RECIPE_TYPE =
            Ecc2Registries.RECIPE_TYPES.register(
                    Ecc2Ids.RECIPE_MOLECULAR_TRANSFORMER,
                    () -> RecipeType.simple(Ecc2BridgePlatform.rl(Ecc2Ids.RECIPE_MOLECULAR_TRANSFORMER)));

    public static final RegistryObject<RecipeSerializer<MolecularTransformerRecipe>>
            MT_RECIPE_SERIALIZER =
                    Ecc2Registries.RECIPE_SERIALIZERS.register(
                            Ecc2Ids.RECIPE_MOLECULAR_TRANSFORMER,
                            MolecularTransformerRecipe.Serializer::new);

    public static final RegistryObject<RecipeSerializer<SolarHelmetUpgradeRecipe>>
            SOLAR_HELMET_UPGRADE_SERIALIZER =
                    Ecc2Registries.RECIPE_SERIALIZERS.register(
                            "solar_helmet_upgrade",
                            SolarHelmetUpgradeRecipe.Serializer::new);

    private SolarKineticRegistry() {}

    /** Ensures class initialization so all registry entries are queued before bus registration. */
    public static void bootstrap() {}

    private static RegistryObject<Block> registerBlock(String name, Supplier<Block> factory) {
        RegistryObject<Block> block = Ecc2Registries.BLOCKS.register(name, factory);
        Ecc2Registries.ITEMS.register(
                name, () -> new BlockItem(block.get(), Ecc2Registries.tabItemProperties()));
        return block;
    }

    private static RegistryObject<Item> registerSimpleItem(String name) {
        return Ecc2Registries.ITEMS.register(name, () -> new Item(Ecc2Registries.tabItemProperties()));
    }
}
