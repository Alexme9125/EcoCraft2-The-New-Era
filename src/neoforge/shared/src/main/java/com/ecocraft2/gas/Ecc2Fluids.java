package com.ecocraft2.gas;

import com.ecocraft2.EcoCraft2;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.giant.GiantGasClientPlatform;
import com.ecocraft2.giant.GiantGasPlatform;
import com.industrialage.ia2.logistics.CellItem;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers EcoCraft 2's 3 new fluids ({@code water_gas}, {@code natural_gas}, {@code
 * culture_medium}), their flowing states, liquid blocks, buckets, client fluid appearance, and all
 * 5 named IC2NE-compatible fluid cells ({@link CellItem}).
 */
public final class Ecc2Fluids {

    private static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, EcoCraft2.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, EcoCraft2.MOD_ID);
    private static final DeferredRegister.Blocks FLUID_BLOCKS =
            DeferredRegister.createBlocks(EcoCraft2.MOD_ID);
    private static final DeferredRegister.Items FLUID_ITEMS =
            DeferredRegister.createItems(EcoCraft2.MOD_ID);

    // ---- Water Gas ----
    public static final DeferredHolder<FluidType, FluidType> WATER_GAS_TYPE =
            registerFluidType(
                    Ecc2Ids.WATER_GAS,
                    Ecc2Ids.WATER_GAS_DENSITY,
                    Ecc2Ids.WATER_GAS_VISCOSITY,
                    Ecc2Ids.WATER_GAS_TEMPERATURE,
                    Ecc2Ids.WATER_GAS_LIGHT);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> WATER_GAS_SOURCE =
            FLUIDS.register(
                    Ecc2Ids.WATER_GAS,
                    () -> new BaseFlowingFluid.Source(waterGasProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> WATER_GAS_FLOWING =
            FLUIDS.register(
                    Ecc2Ids.FLOWING_WATER_GAS,
                    () -> new BaseFlowingFluid.Flowing(waterGasProperties()));
    public static final DeferredBlock<LiquidBlock> WATER_GAS_BLOCK =
            registerLiquidBlock(Ecc2Ids.WATER_GAS, WATER_GAS_SOURCE, Ecc2Ids.WATER_GAS_LIGHT);
    public static final DeferredHolder<Item, BucketItem> WATER_GAS_BUCKET =
            registerBucket(Ecc2Ids.WATER_GAS_BUCKET, WATER_GAS_SOURCE);

    // ---- Natural Gas ----
    public static final DeferredHolder<FluidType, FluidType> NATURAL_GAS_TYPE =
            registerFluidType(
                    Ecc2Ids.NATURAL_GAS,
                    Ecc2Ids.NATURAL_GAS_DENSITY,
                    Ecc2Ids.NATURAL_GAS_VISCOSITY,
                    Ecc2Ids.NATURAL_GAS_TEMPERATURE,
                    Ecc2Ids.NATURAL_GAS_LIGHT);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NATURAL_GAS_SOURCE =
            FLUIDS.register(
                    Ecc2Ids.NATURAL_GAS,
                    () -> new BaseFlowingFluid.Source(naturalGasProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> NATURAL_GAS_FLOWING =
            FLUIDS.register(
                    Ecc2Ids.FLOWING_NATURAL_GAS,
                    () -> new BaseFlowingFluid.Flowing(naturalGasProperties()));
    public static final DeferredBlock<LiquidBlock> NATURAL_GAS_BLOCK =
            registerLiquidBlock(Ecc2Ids.NATURAL_GAS, NATURAL_GAS_SOURCE, Ecc2Ids.NATURAL_GAS_LIGHT);
    public static final DeferredHolder<Item, BucketItem> NATURAL_GAS_BUCKET =
            registerBucket(Ecc2Ids.NATURAL_GAS_BUCKET, NATURAL_GAS_SOURCE);

    // ---- Culture Medium ----
    public static final DeferredHolder<FluidType, FluidType> CULTURE_MEDIUM_TYPE =
            registerFluidType(
                    Ecc2Ids.CULTURE_MEDIUM,
                    Ecc2Ids.CULTURE_MEDIUM_DENSITY,
                    Ecc2Ids.CULTURE_MEDIUM_VISCOSITY,
                    Ecc2Ids.CULTURE_MEDIUM_TEMPERATURE,
                    Ecc2Ids.CULTURE_MEDIUM_LIGHT);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CULTURE_MEDIUM_SOURCE =
            FLUIDS.register(
                    Ecc2Ids.CULTURE_MEDIUM,
                    () -> new BaseFlowingFluid.Source(cultureMediumProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> CULTURE_MEDIUM_FLOWING =
            FLUIDS.register(
                    Ecc2Ids.FLOWING_CULTURE_MEDIUM,
                    () -> new BaseFlowingFluid.Flowing(cultureMediumProperties()));
    public static final DeferredBlock<LiquidBlock> CULTURE_MEDIUM_BLOCK =
            registerLiquidBlock(
                    Ecc2Ids.CULTURE_MEDIUM, CULTURE_MEDIUM_SOURCE, Ecc2Ids.CULTURE_MEDIUM_LIGHT);
    public static final DeferredHolder<Item, BucketItem> CULTURE_MEDIUM_BUCKET =
            registerBucket(Ecc2Ids.CULTURE_MEDIUM_BUCKET, CULTURE_MEDIUM_SOURCE);

    // ---- 5 Named Fluid Cells (IC2NE CellItem::new for full CellFamily / TransportPlatform parity) ----
    public static final DeferredHolder<Item, CellItem> WATER_GAS_CELL =
            FLUID_ITEMS.registerItem(Ecc2Ids.WATER_GAS_CELL, CellItem::new);
    public static final DeferredHolder<Item, CellItem> NATURAL_GAS_CELL =
            FLUID_ITEMS.registerItem(Ecc2Ids.NATURAL_GAS_CELL, CellItem::new);
    public static final DeferredHolder<Item, CellItem> CULTURE_MEDIUM_CELL =
            FLUID_ITEMS.registerItem(Ecc2Ids.CULTURE_MEDIUM_CELL, CellItem::new);
    public static final DeferredHolder<Item, CellItem> BASIC_CULTURE_MEDIUM_CELL =
            FLUID_ITEMS.registerItem(Ecc2Ids.BASIC_CULTURE_MEDIUM_CELL, CellItem::new);
    public static final DeferredHolder<Item, CellItem> BIOGAS_CELL =
            FLUID_ITEMS.registerItem(Ecc2Ids.BIOGAS_CELL, CellItem::new);

    public static final List<Ecc2FluidSpec> ALL_SPECS =
            List.of(
                    new Ecc2FluidSpec(
                            Ecc2Ids.WATER_GAS,
                            "block/fluid/water_gas_still",
                            "block/fluid/water_gas_flow",
                            Ecc2Ids.WATER_GAS_DENSITY,
                            Ecc2Ids.WATER_GAS_VISCOSITY,
                            Ecc2Ids.WATER_GAS_TEMPERATURE,
                            Ecc2Ids.WATER_GAS_LIGHT,
                            Ecc2Ids.WATER_GAS_TINT_ARGB,
                            WATER_GAS_TYPE,
                            WATER_GAS_SOURCE,
                            WATER_GAS_FLOWING),
                    new Ecc2FluidSpec(
                            Ecc2Ids.NATURAL_GAS,
                            "block/fluid/natural_gas_still",
                            "block/fluid/natural_gas_flow",
                            Ecc2Ids.NATURAL_GAS_DENSITY,
                            Ecc2Ids.NATURAL_GAS_VISCOSITY,
                            Ecc2Ids.NATURAL_GAS_TEMPERATURE,
                            Ecc2Ids.NATURAL_GAS_LIGHT,
                            Ecc2Ids.NATURAL_GAS_TINT_ARGB,
                            NATURAL_GAS_TYPE,
                            NATURAL_GAS_SOURCE,
                            NATURAL_GAS_FLOWING),
                    new Ecc2FluidSpec(
                            Ecc2Ids.CULTURE_MEDIUM,
                            "block/fluid/culture_medium_still",
                            "block/fluid/culture_medium_flow",
                            Ecc2Ids.CULTURE_MEDIUM_DENSITY,
                            Ecc2Ids.CULTURE_MEDIUM_VISCOSITY,
                            Ecc2Ids.CULTURE_MEDIUM_TEMPERATURE,
                            Ecc2Ids.CULTURE_MEDIUM_LIGHT,
                            Ecc2Ids.CULTURE_MEDIUM_TINT_ARGB,
                            CULTURE_MEDIUM_TYPE,
                            CULTURE_MEDIUM_SOURCE,
                            CULTURE_MEDIUM_FLOWING));

    private Ecc2Fluids() {}

    /** Registers all fluid types, fluids, liquid blocks, buckets, named cells, and client fluid visuals. */
    public static void register(IEventBus modBus, Dist dist) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        FLUID_BLOCKS.register(modBus);
        FLUID_ITEMS.register(modBus);
        if (dist == Dist.CLIENT) {
            GiantGasClientPlatform.registerFluidClientExtensions(modBus, ALL_SPECS);
        }
    }

    private static DeferredHolder<FluidType, FluidType> registerFluidType(
            String path, int density, int viscosity, int temp, int light) {
        return FLUID_TYPES.register(
                path,
                () ->
                        new FluidType(
                                FluidType.Properties.create()
                                        .descriptionId("fluid_type.ecc2." + path)
                                        .density(density)
                                        .viscosity(viscosity)
                                        .temperature(temp)
                                        .lightLevel(light)));
    }

    private static DeferredBlock<LiquidBlock> registerLiquidBlock(
            String path,
            DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
            int lightLevel) {
        return FLUID_BLOCKS.registerBlock(
                path,
                props ->
                        new LiquidBlock(
                                source.get(),
                                GiantGasPlatform.fluidNonSolid(props)
                                        .noLootTable()
                                        .liquid()
                                        .replaceable()
                                        .strength(100.0F)
                                        .lightLevel(s -> lightLevel)));
    }

    private static DeferredHolder<Item, BucketItem> registerBucket(
            String path, DeferredHolder<Fluid, BaseFlowingFluid.Source> source) {
        return FLUID_ITEMS.registerItem(
                path,
                props ->
                        new BucketItem(
                                source.get(), props.stacksTo(1).craftRemainder(Items.BUCKET)));
    }

    private static BaseFlowingFluid.Properties waterGasProperties() {
        return new BaseFlowingFluid.Properties(WATER_GAS_TYPE, WATER_GAS_SOURCE, WATER_GAS_FLOWING)
                .block(WATER_GAS_BLOCK)
                .bucket(WATER_GAS_BUCKET)
                .tickRate(4);
    }

    private static BaseFlowingFluid.Properties naturalGasProperties() {
        return new BaseFlowingFluid.Properties(
                        NATURAL_GAS_TYPE, NATURAL_GAS_SOURCE, NATURAL_GAS_FLOWING)
                .block(NATURAL_GAS_BLOCK)
                .bucket(NATURAL_GAS_BUCKET)
                .tickRate(3);
    }

    private static BaseFlowingFluid.Properties cultureMediumProperties() {
        return new BaseFlowingFluid.Properties(
                        CULTURE_MEDIUM_TYPE, CULTURE_MEDIUM_SOURCE, CULTURE_MEDIUM_FLOWING)
                .block(CULTURE_MEDIUM_BLOCK)
                .bucket(CULTURE_MEDIUM_BUCKET)
                .tickRate(8);
    }
}
