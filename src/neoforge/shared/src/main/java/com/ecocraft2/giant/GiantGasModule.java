package com.ecocraft2.giant;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.gas.CultureIncubatorBlockEntity;
import com.ecocraft2.gas.CultureIncubatorMenu;
import com.ecocraft2.gas.Ecc2Fluids;
import com.ecocraft2.gas.GasFurnaceBlockEntity;
import com.ecocraft2.gas.GasFurnaceMenu;
import com.ecocraft2.gas.GasGeneratorBlockEntity;
import com.ecocraft2.gas.GasGeneratorMenu;
import com.ecocraft2.gas.GasMachineBlock;
import com.ecocraft2.gas.MethaneIceClusterBlock;
import com.ecocraft2.gas.OrganicFermenterBlockEntity;
import com.ecocraft2.gas.OrganicFermenterMenu;
import com.ecocraft2.gas.WaterGasMachineBlockEntity;
import com.ecocraft2.gas.WaterGasMachineMenu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Module installer for EcoCraft 2's 3x3x3 Giant Machines ({@code com.ecocraft2.giant}) and Water
 * Gas / Natural Gas / Biogas Industry ({@code com.ecocraft2.gas}).
 *
 * <p>Automatically discovered and invoked by {@link EcoCraft2}. Exposes all public registry holders
 * for downstream compat modules (JEI, EMI, Jade, TOP).
 */
public final class GiantGasModule {

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(EcoCraft2.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(EcoCraft2.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EcoCraft2.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, EcoCraft2.MOD_ID);

    // ---- Giant Machine Component & 5 Extensible Unit Blocks ----

    public static final DeferredHolder<Item, Item> MEGASTRUCTURE_COMPONENT =
            GiantGasPlatform.registerSimpleItem(ITEMS, Ecc2Ids.MEGASTRUCTURE_COMPONENT);

    private static final Map<GiantMachineType, DeferredBlock<GiantUnitBlock>> GIANT_BLOCKS_MAP =
            new LinkedHashMap<>();
    private static final Map<GiantMachineType, DeferredItem<BlockItem>> GIANT_ITEMS_MAP =
            new LinkedHashMap<>();

    public static final DeferredBlock<GiantUnitBlock> GIANT_MACERATOR_UNIT =
            registerGiantUnit(GiantMachineType.MACERATOR);
    public static final DeferredBlock<GiantUnitBlock> GIANT_COMPRESSOR_UNIT =
            registerGiantUnit(GiantMachineType.COMPRESSOR);
    public static final DeferredBlock<GiantUnitBlock> GIANT_RECYCLER_UNIT =
            registerGiantUnit(GiantMachineType.RECYCLER);
    public static final DeferredBlock<GiantUnitBlock> GIANT_EXTRACTOR_UNIT =
            registerGiantUnit(GiantMachineType.EXTRACTOR);
    public static final DeferredBlock<GiantUnitBlock> GIANT_ORE_WASHER_UNIT =
            registerGiantUnit(GiantMachineType.ORE_WASHER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GiantUnitBlockEntity>>
            GIANT_UNIT_BE =
                    BLOCK_ENTITIES.register(
                            "giant_unit",
                            () ->
                                    GiantGasPlatform.blockEntityType(
                                            GiantUnitBlockEntity::new,
                                            GIANT_BLOCKS_MAP.values().stream()
                                                    .map(DeferredBlock::get)
                                                    .toArray(Block[]::new)));

    // ---- Gas & Biogas Blocks & BlockEntities ----

    public static final DeferredBlock<MethaneIceClusterBlock> METHANE_ICE_CLUSTER =
            GiantGasPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.METHANE_ICE_CLUSTER,
                    MethaneIceClusterBlock::new,
                    MethaneIceClusterBlock::defaultProperties);
    public static final DeferredItem<BlockItem> METHANE_ICE_CLUSTER_ITEM =
            ITEMS.registerSimpleBlockItem(METHANE_ICE_CLUSTER);

    public static final DeferredBlock<GasMachineBlock.Electric> WATER_GAS_MACHINE =
            GiantGasPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.WATER_GAS_MACHINE,
                    props ->
                            new GasMachineBlock.Electric(
                                    props,
                                    WaterGasMachineBlockEntity::new,
                                    () -> GiantGasModule.WATER_GAS_MACHINE_BE.get()),
                    GasMachineBlock::defaultProperties);
    public static final DeferredItem<BlockItem> WATER_GAS_MACHINE_ITEM =
            ITEMS.registerSimpleBlockItem(WATER_GAS_MACHINE);

    public static final DeferredBlock<GasMachineBlock> GAS_FURNACE =
            GiantGasPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.GAS_FURNACE,
                    props ->
                            new GasMachineBlock(
                                    props,
                                    GasFurnaceBlockEntity::new,
                                    () -> GiantGasModule.GAS_FURNACE_BE.get()),
                    GasMachineBlock::defaultProperties);
    public static final DeferredItem<BlockItem> GAS_FURNACE_ITEM =
            ITEMS.registerSimpleBlockItem(GAS_FURNACE);

    public static final DeferredBlock<GasMachineBlock.Electric> GAS_GENERATOR =
            GiantGasPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.GAS_GENERATOR,
                    props ->
                            new GasMachineBlock.Electric(
                                    props,
                                    GasGeneratorBlockEntity::new,
                                    () -> GiantGasModule.GAS_GENERATOR_BE.get()),
                    GasMachineBlock::defaultProperties);
    public static final DeferredItem<BlockItem> GAS_GENERATOR_ITEM =
            ITEMS.registerSimpleBlockItem(GAS_GENERATOR);

    public static final DeferredBlock<GasMachineBlock.Electric> CULTURE_INCUBATOR =
            GiantGasPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.CULTURE_INCUBATOR,
                    props ->
                            new GasMachineBlock.Electric(
                                    props,
                                    CultureIncubatorBlockEntity::new,
                                    () -> GiantGasModule.CULTURE_INCUBATOR_BE.get()),
                    GasMachineBlock::defaultProperties);
    public static final DeferredItem<BlockItem> CULTURE_INCUBATOR_ITEM =
            ITEMS.registerSimpleBlockItem(CULTURE_INCUBATOR);

    public static final DeferredBlock<GasMachineBlock.Electric> ORGANIC_FERMENTER =
            GiantGasPlatform.registerBlock(
                    BLOCKS,
                    Ecc2Ids.ORGANIC_FERMENTER,
                    props ->
                            new GasMachineBlock.Electric(
                                    props,
                                    OrganicFermenterBlockEntity::new,
                                    () -> GiantGasModule.ORGANIC_FERMENTER_BE.get()),
                    GasMachineBlock::defaultProperties);
    public static final DeferredItem<BlockItem> ORGANIC_FERMENTER_ITEM =
            ITEMS.registerSimpleBlockItem(ORGANIC_FERMENTER);

    public static final DeferredHolder<
                    BlockEntityType<?>, BlockEntityType<WaterGasMachineBlockEntity>>
            WATER_GAS_MACHINE_BE =
                    BLOCK_ENTITIES.register(
                            Ecc2Ids.WATER_GAS_MACHINE,
                            () ->
                                    GiantGasPlatform.blockEntityType(
                                            WaterGasMachineBlockEntity::new,
                                            WATER_GAS_MACHINE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasFurnaceBlockEntity>>
            GAS_FURNACE_BE =
                    BLOCK_ENTITIES.register(
                            Ecc2Ids.GAS_FURNACE,
                            () ->
                                    GiantGasPlatform.blockEntityType(
                                            GasFurnaceBlockEntity::new, GAS_FURNACE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasGeneratorBlockEntity>>
            GAS_GENERATOR_BE =
                    BLOCK_ENTITIES.register(
                            Ecc2Ids.GAS_GENERATOR,
                            () ->
                                    GiantGasPlatform.blockEntityType(
                                            GasGeneratorBlockEntity::new, GAS_GENERATOR.get()));

    public static final DeferredHolder<
                    BlockEntityType<?>, BlockEntityType<CultureIncubatorBlockEntity>>
            CULTURE_INCUBATOR_BE =
                    BLOCK_ENTITIES.register(
                            Ecc2Ids.CULTURE_INCUBATOR,
                            () ->
                                    GiantGasPlatform.blockEntityType(
                                            CultureIncubatorBlockEntity::new,
                                            CULTURE_INCUBATOR.get()));

    public static final DeferredHolder<
                    BlockEntityType<?>, BlockEntityType<OrganicFermenterBlockEntity>>
            ORGANIC_FERMENTER_BE =
                    BLOCK_ENTITIES.register(
                            Ecc2Ids.ORGANIC_FERMENTER,
                            () ->
                                    GiantGasPlatform.blockEntityType(
                                            OrganicFermenterBlockEntity::new,
                                            ORGANIC_FERMENTER.get()));

    // ---- Menus ----

    public static final DeferredHolder<MenuType<?>, MenuType<GiantMachineMenu>> GIANT_MACHINE_MENU =
            MENUS.register(
                    "giant_machine",
                    () ->
                            new MenuType<>(
                                    (IContainerFactory<GiantMachineMenu>)
                                            GiantMachineMenu::fromNetwork,
                                    FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<WaterGasMachineMenu>>
            WATER_GAS_MACHINE_MENU =
                    MENUS.register(
                            Ecc2Ids.WATER_GAS_MACHINE,
                            () ->
                                    new MenuType<>(
                                            (IContainerFactory<WaterGasMachineMenu>)
                                                    WaterGasMachineMenu::fromNetwork,
                                            FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<GasFurnaceMenu>> GAS_FURNACE_MENU =
            MENUS.register(
                    Ecc2Ids.GAS_FURNACE,
                    () ->
                            new MenuType<>(
                                    (IContainerFactory<GasFurnaceMenu>) GasFurnaceMenu::fromNetwork,
                                    FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<GasGeneratorMenu>> GAS_GENERATOR_MENU =
            MENUS.register(
                    Ecc2Ids.GAS_GENERATOR,
                    () ->
                            new MenuType<>(
                                    (IContainerFactory<GasGeneratorMenu>)
                                            GasGeneratorMenu::fromNetwork,
                                    FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<CultureIncubatorMenu>>
            CULTURE_INCUBATOR_MENU =
                    MENUS.register(
                            Ecc2Ids.CULTURE_INCUBATOR,
                            () ->
                                    new MenuType<>(
                                            (IContainerFactory<CultureIncubatorMenu>)
                                                    CultureIncubatorMenu::fromNetwork,
                                            FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<OrganicFermenterMenu>>
            ORGANIC_FERMENTER_MENU =
                    MENUS.register(
                            Ecc2Ids.ORGANIC_FERMENTER,
                            () ->
                                    new MenuType<>(
                                            (IContainerFactory<OrganicFermenterMenu>)
                                                    OrganicFermenterMenu::fromNetwork,
                                            FeatureFlags.DEFAULT_FLAGS));

    private static volatile boolean installed;

    private GiantGasModule() {}

    /** Entry point invoked automatically by {@link EcoCraft2}. */
    public static synchronized void install(
            IEventBus modBus, IEventBus gameBus, ModContainer container, Dist dist) {
        if (installed) {
            return;
        }
        installed = true;

        Ecc2Fluids.register(modBus, dist);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);

        GiantGasPlatform.registerCapabilities(
                modBus,
                List.of(
                        GIANT_UNIT_BE,
                        WATER_GAS_MACHINE_BE,
                        GAS_FURNACE_BE,
                        GAS_GENERATOR_BE,
                        CULTURE_INCUBATOR_BE,
                        ORGANIC_FERMENTER_BE));

        GiantGasPlatform.registerServerboundPayload(
                modBus,
                GiantPortTogglePayload.TYPE,
                GiantPortTogglePayload.STREAM_CODEC,
                GiantPortTogglePayload::handleServer);

        EcoCraft2.addCreativeTabItems(
                output -> {
                    output.accept(MEGASTRUCTURE_COMPONENT.get());
                    for (var item : GIANT_ITEMS_MAP.values()) {
                        output.accept(item.get());
                    }
                    output.accept(WATER_GAS_MACHINE_ITEM.get());
                    output.accept(GAS_FURNACE_ITEM.get());
                    output.accept(GAS_GENERATOR_ITEM.get());
                    output.accept(METHANE_ICE_CLUSTER_ITEM.get());
                    output.accept(CULTURE_INCUBATOR_ITEM.get());
                    output.accept(ORGANIC_FERMENTER_ITEM.get());
                    output.accept(Ecc2Fluids.WATER_GAS_CELL.get());
                    output.accept(Ecc2Fluids.NATURAL_GAS_CELL.get());
                    output.accept(Ecc2Fluids.CULTURE_MEDIUM_CELL.get());
                    output.accept(Ecc2Fluids.BASIC_CULTURE_MEDIUM_CELL.get());
                    output.accept(Ecc2Fluids.WATER_GAS_BUCKET.get());
                    output.accept(Ecc2Fluids.NATURAL_GAS_BUCKET.get());
                    output.accept(Ecc2Fluids.CULTURE_MEDIUM_BUCKET.get());
                });

        gameBus.addListener(ItemTooltipEvent.class, GiantGasModule::appendTooltips);

        if (dist == Dist.CLIENT) {
            GiantGasClientScreens.install(modBus);
        }
    }

    /** Public helper exposing the registered unit block for any {@link GiantMachineType}. */
    public static DeferredBlock<GiantUnitBlock> giantUnitBlock(GiantMachineType type) {
        return GIANT_BLOCKS_MAP.get(type);
    }

    private static DeferredBlock<GiantUnitBlock> registerGiantUnit(GiantMachineType type) {
        DeferredBlock<GiantUnitBlock> block =
                GiantGasPlatform.registerBlock(
                        BLOCKS,
                        type.unitBlockPath(),
                        props -> new GiantUnitBlock(props, type),
                        GiantGasModule::giantUnitProperties);
        DeferredItem<BlockItem> item = ITEMS.registerSimpleBlockItem(block);
        GIANT_BLOCKS_MAP.put(type, block);
        GIANT_ITEMS_MAP.put(type, item);
        return block;
    }

    private static BlockBehaviour.Properties giantUnitProperties() {
        return BlockBehaviour.Properties.of()
                .strength(3.5F, 12.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    private static void appendTooltips(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item == MEGASTRUCTURE_COMPONENT.get()) {
            event.getToolTip()
                    .add(
                            Component.translatable("tooltip.ecc2.megastructure_component")
                                    .withStyle(ChatFormatting.GRAY));
        } else if (item instanceof BlockItem bi && bi.getBlock() instanceof GiantUnitBlock) {
            event.getToolTip()
                    .add(
                            Component.translatable("tooltip.ecc2.giant_unit")
                                    .withStyle(ChatFormatting.GRAY));
            event.getToolTip()
                    .add(
                            Component.translatable("tooltip.ecc2.giant_unit.ports")
                                    .withStyle(ChatFormatting.AQUA));
        } else if (item == WATER_GAS_MACHINE_ITEM.get()) {
            event.getToolTip()
                    .add(
                            Component.translatable("tooltip.ecc2.water_gas_machine")
                                    .withStyle(ChatFormatting.GRAY));
        } else if (item == GAS_FURNACE_ITEM.get()) {
            event.getToolTip()
                    .add(
                            Component.translatable("tooltip.ecc2.gas_furnace")
                                    .withStyle(ChatFormatting.GRAY));
        } else if (item == GAS_GENERATOR_ITEM.get()) {
            event.getToolTip()
                    .add(
                            Component.translatable("tooltip.ecc2.gas_generator")
                                    .withStyle(ChatFormatting.GRAY));
        } else if (item == METHANE_ICE_CLUSTER_ITEM.get()) {
            event.getToolTip()
                    .add(
                            Component.translatable("tooltip.ecc2.methane_ice_cluster")
                                    .withStyle(ChatFormatting.AQUA));
        } else if (item == CULTURE_INCUBATOR_ITEM.get()) {
            event.getToolTip()
                    .add(
                            Component.translatable("tooltip.ecc2.culture_incubator")
                                    .withStyle(ChatFormatting.GRAY));
        } else if (item == ORGANIC_FERMENTER_ITEM.get()) {
            event.getToolTip()
                    .add(
                            Component.translatable("tooltip.ecc2.organic_fermenter")
                                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
