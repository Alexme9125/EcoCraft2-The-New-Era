package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.registry.Ecc2Registries;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/** Forge 1.19.2 registrations for Water Gas, Natural Gas, Biogas fluids, cells, ore, and 5 machines. */
public final class GasRegistry {

    // ---- 3 FluidTypes ----
    public static final RegistryObject<FluidType> WATER_GAS_TYPE =
            Ecc2Registries.FLUID_TYPES.register(
                    Ecc2Ids.WATER_GAS,
                    () ->
                            new Ecc2FluidType(
                                    FluidType.Properties.create()
                                            .density(Ecc2Ids.WATER_GAS_DENSITY)
                                            .viscosity(Ecc2Ids.WATER_GAS_VISCOSITY)
                                            .temperature(Ecc2Ids.WATER_GAS_TEMPERATURE)
                                            .lightLevel(Ecc2Ids.WATER_GAS_LIGHT),
                                    Ecc2BridgePlatform.rl("block/fluid/water_gas_still"),
                                    Ecc2BridgePlatform.rl("block/fluid/water_gas_flow"),
                                    Ecc2Ids.WATER_GAS_TINT_ARGB));

    public static final RegistryObject<FluidType> NATURAL_GAS_TYPE =
            Ecc2Registries.FLUID_TYPES.register(
                    Ecc2Ids.NATURAL_GAS,
                    () ->
                            new Ecc2FluidType(
                                    FluidType.Properties.create()
                                            .density(Ecc2Ids.NATURAL_GAS_DENSITY)
                                            .viscosity(Ecc2Ids.NATURAL_GAS_VISCOSITY)
                                            .temperature(Ecc2Ids.NATURAL_GAS_TEMPERATURE)
                                            .lightLevel(Ecc2Ids.NATURAL_GAS_LIGHT),
                                    Ecc2BridgePlatform.rl("block/fluid/natural_gas_still"),
                                    Ecc2BridgePlatform.rl("block/fluid/natural_gas_flow"),
                                    Ecc2Ids.NATURAL_GAS_TINT_ARGB));

    public static final RegistryObject<FluidType> CULTURE_MEDIUM_TYPE =
            Ecc2Registries.FLUID_TYPES.register(
                    Ecc2Ids.CULTURE_MEDIUM,
                    () ->
                            new Ecc2FluidType(
                                    FluidType.Properties.create()
                                            .density(Ecc2Ids.CULTURE_MEDIUM_DENSITY)
                                            .viscosity(Ecc2Ids.CULTURE_MEDIUM_VISCOSITY)
                                            .temperature(Ecc2Ids.CULTURE_MEDIUM_TEMPERATURE)
                                            .lightLevel(Ecc2Ids.CULTURE_MEDIUM_LIGHT),
                                    Ecc2BridgePlatform.rl("block/fluid/culture_medium_still"),
                                    Ecc2BridgePlatform.rl("block/fluid/culture_medium_flow"),
                                    Ecc2Ids.CULTURE_MEDIUM_TINT_ARGB));

    // ---- 3 Still + 3 Flowing Fluids ----
    public static final RegistryObject<FlowingFluid> WATER_GAS =
            Ecc2Registries.FLUIDS.register(
                    Ecc2Ids.WATER_GAS,
                    () -> new ForgeFlowingFluid.Source(waterGasProps()));
    public static final RegistryObject<FlowingFluid> FLOWING_WATER_GAS =
            Ecc2Registries.FLUIDS.register(
                    Ecc2Ids.FLOWING_WATER_GAS,
                    () -> new ForgeFlowingFluid.Flowing(waterGasProps()));

    public static final RegistryObject<FlowingFluid> NATURAL_GAS =
            Ecc2Registries.FLUIDS.register(
                    Ecc2Ids.NATURAL_GAS,
                    () -> new ForgeFlowingFluid.Source(naturalGasProps()));
    public static final RegistryObject<FlowingFluid> FLOWING_NATURAL_GAS =
            Ecc2Registries.FLUIDS.register(
                    Ecc2Ids.FLOWING_NATURAL_GAS,
                    () -> new ForgeFlowingFluid.Flowing(naturalGasProps()));

    public static final RegistryObject<FlowingFluid> CULTURE_MEDIUM =
            Ecc2Registries.FLUIDS.register(
                    Ecc2Ids.CULTURE_MEDIUM,
                    () -> new ForgeFlowingFluid.Source(cultureMediumProps()));
    public static final RegistryObject<FlowingFluid> FLOWING_CULTURE_MEDIUM =
            Ecc2Registries.FLUIDS.register(
                    Ecc2Ids.FLOWING_CULTURE_MEDIUM,
                    () -> new ForgeFlowingFluid.Flowing(cultureMediumProps()));

    // ---- 3 Fluid Blocks (no BlockItems) ----
    public static final RegistryObject<LiquidBlock> WATER_GAS_BLOCK =
            Ecc2Registries.BLOCKS.register(
                    Ecc2Ids.WATER_GAS,
                    () ->
                            new LiquidBlock(
                                    WATER_GAS,
                                    BlockBehaviour.Properties.of(Material.WATER)
                                            .noCollission()
                                            .strength(100.0f)
                                            .noLootTable()
                                            .lightLevel(s -> Ecc2Ids.WATER_GAS_LIGHT)));

    public static final RegistryObject<LiquidBlock> NATURAL_GAS_BLOCK =
            Ecc2Registries.BLOCKS.register(
                    Ecc2Ids.NATURAL_GAS,
                    () ->
                            new LiquidBlock(
                                    NATURAL_GAS,
                                    BlockBehaviour.Properties.of(Material.WATER)
                                            .noCollission()
                                            .strength(100.0f)
                                            .noLootTable()));

    public static final RegistryObject<LiquidBlock> CULTURE_MEDIUM_BLOCK =
            Ecc2Registries.BLOCKS.register(
                    Ecc2Ids.CULTURE_MEDIUM,
                    () ->
                            new LiquidBlock(
                                    CULTURE_MEDIUM,
                                    BlockBehaviour.Properties.of(Material.WATER)
                                            .noCollission()
                                            .strength(100.0f)
                                            .noLootTable()
                                            .lightLevel(s -> Ecc2Ids.CULTURE_MEDIUM_LIGHT)));

    // ---- 3 Buckets ----
    public static final RegistryObject<Item> WATER_GAS_BUCKET =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.WATER_GAS_BUCKET,
                    () ->
                            new BucketItem(
                                    WATER_GAS,
                                    Ecc2Registries.tabItemProperties()
                                            .craftRemainder(Items.BUCKET)
                                            .stacksTo(1)));
    public static final RegistryObject<Item> NATURAL_GAS_BUCKET =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.NATURAL_GAS_BUCKET,
                    () ->
                            new BucketItem(
                                    NATURAL_GAS,
                                    Ecc2Registries.tabItemProperties()
                                            .craftRemainder(Items.BUCKET)
                                            .stacksTo(1)));
    public static final RegistryObject<Item> CULTURE_MEDIUM_BUCKET =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.CULTURE_MEDIUM_BUCKET,
                    () ->
                            new BucketItem(
                                    CULTURE_MEDIUM,
                                    Ecc2Registries.tabItemProperties()
                                            .craftRemainder(Items.BUCKET)
                                            .stacksTo(1)));

    // ---- 4 Named Fluid Cells (Biogas reuses ic2:biogas_cell) ----
    public static final RegistryObject<Item> WATER_GAS_CELL =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.WATER_GAS_CELL, () -> new Ecc2NamedCellItem("gui.ecc2.gas.water_gas"));
    public static final RegistryObject<Item> NATURAL_GAS_CELL =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.NATURAL_GAS_CELL,
                    () -> new Ecc2NamedCellItem("gui.ecc2.gas.natural_gas"));
    public static final RegistryObject<Item> CULTURE_MEDIUM_CELL =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.CULTURE_MEDIUM_CELL,
                    () -> new Ecc2NamedCellItem("gui.ecc2.gas.culture_medium"));
    public static final RegistryObject<Item> BASIC_CULTURE_MEDIUM_CELL =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.BASIC_CULTURE_MEDIUM_CELL,
                    () -> new Ecc2NamedCellItem("gui.ecc2.gas.culture_medium"));

    // ---- Seafloor Methane Ice Cluster ----
    public static final RegistryObject<Block> METHANE_ICE_CLUSTER =
            registerBlock(Ecc2Ids.METHANE_ICE_CLUSTER, MethaneIceClusterBlock::new);

    // ---- 5 Gas & Biogas Machine Blocks ----
    public static final RegistryObject<Block> WATER_GAS_MACHINE =
            registerBlock(
                    Ecc2Ids.WATER_GAS_MACHINE,
                    () ->
                            GasMachineBlock.powered(
                                    WaterGasMachineBlockEntity::new,
                                    GasRegistry.WATER_GAS_MACHINE_BE,
                                    "tooltip.ecc2.water_gas_machine"));

    public static final RegistryObject<Item> PV_POWER_UPGRADE =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.PV_POWER_UPGRADE_PATH,
                    () ->
                            new Item(
                                    Ecc2Registries.tabItemProperties()
                                            .stacksTo(
                                                    Ecc2ConfigDefaults
                                                            .WATER_GAS_SOLAR_UPGRADE_MAX_EFFECTIVE)) {
                                @Override
                                public void appendHoverText(
                                        ItemStack stack,
                                        @Nullable Level level,
                                        List<Component> tooltip,
                                        TooltipFlag flag) {
                                    tooltip.add(
                                            Component.translatable("tooltip.ecc2.pv_power_upgrade")
                                                    .withStyle(ChatFormatting.GRAY));
                                }
                            });

    public static final RegistryObject<Block> GAS_FURNACE =
            registerBlock(
                    Ecc2Ids.GAS_FURNACE,
                    () ->
                            new GasMachineBlock<>(
                                    GasFurnaceBlockEntity::new,
                                    GasRegistry.GAS_FURNACE_BE,
                                    "tooltip.ecc2.gas_furnace"));

    public static final RegistryObject<Block> GAS_GENERATOR =
            registerBlock(
                    Ecc2Ids.GAS_GENERATOR,
                    () ->
                            GasMachineBlock.powered(
                                    GasGeneratorBlockEntity::new,
                                    GasRegistry.GAS_GENERATOR_BE,
                                    "tooltip.ecc2.gas_generator"));

    public static final RegistryObject<Block> CULTURE_INCUBATOR =
            registerBlock(
                    Ecc2Ids.CULTURE_INCUBATOR,
                    () ->
                            GasMachineBlock.powered(
                                    CultureIncubatorBlockEntity::new,
                                    GasRegistry.CULTURE_INCUBATOR_BE,
                                    "tooltip.ecc2.culture_incubator"));

    public static final RegistryObject<Block> ORGANIC_FERMENTER =
            registerBlock(
                    Ecc2Ids.ORGANIC_FERMENTER,
                    () ->
                            GasMachineBlock.powered(
                                    OrganicFermenterBlockEntity::new,
                                    GasRegistry.ORGANIC_FERMENTER_BE,
                                    "tooltip.ecc2.organic_fermenter"));

    // ---- 5 Block Entities ----
    public static final RegistryObject<BlockEntityType<WaterGasMachineBlockEntity>>
            WATER_GAS_MACHINE_BE =
                    Ecc2Registries.BLOCK_ENTITIES.register(
                            Ecc2Ids.WATER_GAS_MACHINE,
                            () ->
                                    BlockEntityType.Builder.of(
                                                    WaterGasMachineBlockEntity::new,
                                                    WATER_GAS_MACHINE.get())
                                            .build(null));

    public static final RegistryObject<BlockEntityType<GasFurnaceBlockEntity>> GAS_FURNACE_BE =
            Ecc2Registries.BLOCK_ENTITIES.register(
                    Ecc2Ids.GAS_FURNACE,
                    () ->
                            BlockEntityType.Builder.of(
                                            GasFurnaceBlockEntity::new, GAS_FURNACE.get())
                                    .build(null));

    public static final RegistryObject<BlockEntityType<GasGeneratorBlockEntity>> GAS_GENERATOR_BE =
            Ecc2Registries.BLOCK_ENTITIES.register(
                    Ecc2Ids.GAS_GENERATOR,
                    () ->
                            BlockEntityType.Builder.of(
                                            GasGeneratorBlockEntity::new, GAS_GENERATOR.get())
                                    .build(null));

    public static final RegistryObject<BlockEntityType<CultureIncubatorBlockEntity>>
            CULTURE_INCUBATOR_BE =
                    Ecc2Registries.BLOCK_ENTITIES.register(
                            Ecc2Ids.CULTURE_INCUBATOR,
                            () ->
                                    BlockEntityType.Builder.of(
                                                    CultureIncubatorBlockEntity::new,
                                                    CULTURE_INCUBATOR.get())
                                            .build(null));

    public static final RegistryObject<BlockEntityType<OrganicFermenterBlockEntity>>
            ORGANIC_FERMENTER_BE =
                    Ecc2Registries.BLOCK_ENTITIES.register(
                            Ecc2Ids.ORGANIC_FERMENTER,
                            () ->
                                    BlockEntityType.Builder.of(
                                                    OrganicFermenterBlockEntity::new,
                                                    ORGANIC_FERMENTER.get())
                                            .build(null));

    // ---- 5 Menus ----
    public static final RegistryObject<MenuType<WaterGasMachineMenu>> WATER_GAS_MACHINE_MENU =
            Ecc2Registries.MENUS.register(
                    Ecc2Ids.WATER_GAS_MACHINE,
                    () -> IForgeMenuType.create(WaterGasMachineMenu::new));
    public static final RegistryObject<MenuType<GasFurnaceMenu>> GAS_FURNACE_MENU =
            Ecc2Registries.MENUS.register(
                    Ecc2Ids.GAS_FURNACE, () -> IForgeMenuType.create(GasFurnaceMenu::new));
    public static final RegistryObject<MenuType<GasGeneratorMenu>> GAS_GENERATOR_MENU =
            Ecc2Registries.MENUS.register(
                    Ecc2Ids.GAS_GENERATOR, () -> IForgeMenuType.create(GasGeneratorMenu::new));
    public static final RegistryObject<MenuType<CultureIncubatorMenu>> CULTURE_INCUBATOR_MENU =
            Ecc2Registries.MENUS.register(
                    Ecc2Ids.CULTURE_INCUBATOR,
                    () -> IForgeMenuType.create(CultureIncubatorMenu::new));
    public static final RegistryObject<MenuType<OrganicFermenterMenu>> ORGANIC_FERMENTER_MENU =
            Ecc2Registries.MENUS.register(
                    Ecc2Ids.ORGANIC_FERMENTER,
                    () -> IForgeMenuType.create(OrganicFermenterMenu::new));

    private GasRegistry() {}

    public static void bootstrap() {}

    private static ForgeFlowingFluid.Properties waterGasProps() {
        return new ForgeFlowingFluid.Properties(WATER_GAS_TYPE, WATER_GAS, FLOWING_WATER_GAS)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .block(WATER_GAS_BLOCK)
                .bucket(WATER_GAS_BUCKET);
    }

    private static ForgeFlowingFluid.Properties naturalGasProps() {
        return new ForgeFlowingFluid.Properties(NATURAL_GAS_TYPE, NATURAL_GAS, FLOWING_NATURAL_GAS)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .block(NATURAL_GAS_BLOCK)
                .bucket(NATURAL_GAS_BUCKET);
    }

    private static ForgeFlowingFluid.Properties cultureMediumProps() {
        return new ForgeFlowingFluid.Properties(
                        CULTURE_MEDIUM_TYPE, CULTURE_MEDIUM, FLOWING_CULTURE_MEDIUM)
                .slopeFindDistance(4)
                .levelDecreasePerBlock(1)
                .block(CULTURE_MEDIUM_BLOCK)
                .bucket(CULTURE_MEDIUM_BUCKET);
    }

    private static RegistryObject<Block> registerBlock(String name, Supplier<Block> factory) {
        RegistryObject<Block> block = Ecc2Registries.BLOCKS.register(name, factory);
        Ecc2Registries.ITEMS.register(
                name, () -> new BlockItem(block.get(), Ecc2Registries.tabItemProperties()));
        return block;
    }
}
