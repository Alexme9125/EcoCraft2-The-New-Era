package com.ecocraft2.gas;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.GasMachineOutcome;
import com.ecocraft2.common.gas.WaterGasEngine;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.giant.Ecc2DataReader;
import com.ecocraft2.giant.Ecc2DataWriter;
import com.ecocraft2.giant.Ecc2FluidTankView;
import com.ecocraft2.giant.Ecc2UpgradeHelper;
import com.ecocraft2.giant.GiantGasModule;
import com.ecocraft2.giant.GiantGasPlatform;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.gameplay.PortableGear;
import com.industrialage.ia2.logistics.FluidContents;
import com.industrialage.ia2.platform.CapabilityFacade;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldPowerPort;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Water Gas Machine ({@code ecc2:water_gas_machine}): built-in 2 EU/t daytime solar array + LV
 * Tier 1 grid input; synthesizes {@code 100 mB water + 1 ic2:coal_dust + 1000 EU / 200t -> 1000 mB
 * ecc2:water_gas}.
 */
public final class WaterGasMachineBlockEntity extends GasMachineBlockEntityBase
        implements WorldPowerPort, EuBuffer {

    public static final int SLOT_COAL_DUST = 0;
    public static final int SLOT_WATER_CELL_IN = 1;
    public static final int SLOT_WATER_CELL_OUT = 2;
    public static final int SLOT_GAS_CELL_IN = 3;
    public static final int SLOT_GAS_CELL_OUT = 4;
    public static final int SLOT_BATTERY = 5;
    public static final int UPGRADE_START = 6;
    public static final int UPGRADE_COUNT = 4;
    public static final int INVENTORY_SIZE = 10;

    private static final int[] ALL_AUTOMATED_SLOTS = {0, 1, 2, 3, 4, 5};
    private static final int[] EMPTY_SLOTS = new int[0];
    private static final int[] INPUT_SLOTS = {SLOT_COAL_DUST};
    private static final int[] OUTPUT_SLOTS = {SLOT_WATER_CELL_OUT, SLOT_GAS_CELL_OUT};
    private static final EnergyGraph.Ports SINK_PORTS =
            new EnergyGraph.Ports(Set.of(CapabilityFacade.Face.values()), Set.of(), false);

    private final WaterGasEngine engine = new WaterGasEngine();

    public WaterGasMachineBlockEntity(BlockPos pos, BlockState state) {
        super(GiantGasModule.WATER_GAS_MACHINE_BE.get(), pos, state, INVENTORY_SIZE);
    }

    @Override
    protected void serverTick(ServerLevel level, BlockState state) {
        lastTick = level.getServer().getTickCount();
        boolean changed = false;

        var upgrades = items.subList(UPGRADE_START, UPGRADE_START + UPGRADE_COUNT);
        var profile = Ecc2UpgradeHelper.readProfile(upgrades);
        if (!profile.equals(engine.upgrades())) {
            engine.setUpgrades(profile);
            changed = true;
        }

        // Battery discharge slot
        ItemStack battery = items.get(SLOT_BATTERY);
        if (!battery.isEmpty() && engine.demandedEu() > 0.0) {
            double drawn =
                    PortableGear.discharge(
                            battery, engine.demandedEu(), engine.energyCapacity(), engine.inputTier());
            if (drawn > 0.0) {
                engine.injectEu(drawn);
                changed = true;
            } else if (battery.is(Items.REDSTONE) && engine.demandedEu() >= 800.0) {
                engine.injectEu(800.0);
                battery.shrink(1);
                changed = true;
            }
        }

        // Drain water container slot 1 -> 2
        changed |=
                drainContainerSlot(
                        SLOT_WATER_CELL_IN,
                        SLOT_WATER_CELL_OUT,
                        Ecc2Ids.MINECRAFT_WATER_ID::equals,
                        WaterGasEngine.WATER_TANK_CAPACITY_MB - engine.waterMb(),
                        (fid, amt) -> engine.fillWater(amt, false));

        // Fill water gas container slot 3 -> 4 (fills ic2:fluid_cell -> ecc2:water_gas_cell!)
        changed |=
                fillContainerSlot(
                        SLOT_GAS_CELL_IN,
                        SLOT_GAS_CELL_OUT,
                        Ecc2Ids.WATER_GAS_ID,
                        engine.waterGasMb(),
                        amt -> engine.drainWaterGas(amt, false));

        // Evaluate top solar conditions + run WaterGasEngine
        engine.setSolarEuPerTick(Ecc2Config.waterGasSolarEuPerTick());
        boolean hasCoal = isCoalDust(items.get(SLOT_COAL_DUST));
        boolean redstoneBlocked =
                Ecc2UpgradeHelper.hasRedstoneInverter(upgrades)
                        && !level.hasNeighborSignal(worldPosition);
        BlockPos above = worldPosition.above();
        boolean hasSky = level.dimensionType().hasSkyLight();
        boolean seesSky = hasSky && level.canSeeSky(above);
        boolean isDay = GiantGasPlatform.isDaytime(level);
        boolean isRaining = level.isRaining() || level.isThundering();
        boolean canRain = level.getBiome(above).value().hasPrecipitation();

        GasMachineOutcome outcome =
                engine.tick(!redstoneBlocked && hasCoal, hasSky, seesSky, isDay, isRaining, canRain);
        if (outcome == GasMachineOutcome.COMPLETED) {
            items.get(SLOT_COAL_DUST).shrink(1);
            changed = true;
        } else if (outcome == GasMachineOutcome.WORKING || engine.isSolarActive()) {
            changed = true;
        }

        // Execute IC2 item & fluid upgrades
        var endpoints = sixSideEndpoints();
        changed |=
                Ecc2UpgradeHelper.executeItemUpgrades(
                        level,
                        upgrades,
                        endpoints,
                        this,
                        INPUT_SLOTS,
                        OUTPUT_SLOTS,
                        WaterGasMachineBlockEntity::isCoalDust);
        changed |=
                Ecc2UpgradeHelper.executeFluidUpgrades(
                        level,
                        upgrades,
                        endpoints,
                        Ecc2Ids.MINECRAFT_WATER_ID::equals,
                        (fid, amt) -> engine.fillWater(amt, false),
                        () -> WaterGasEngine.WATER_TANK_CAPACITY_MB - engine.waterMb(),
                        () -> Ecc2Ids.WATER_GAS_ID,
                        engine::waterGasMb,
                        amt -> engine.drainWaterGas(amt, false));

        syncActiveState(level, state, engine.isActive());
        if (changed) {
            setChanged();
        }
    }

    public static boolean isCoalDust(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String id = GiantGasPlatform.itemId(stack);
        return "ic2:coal_dust".equals(id) || GiantGasPlatform.hasItemTag(stack, "c:dusts/coal");
    }

    // ---- WorldPowerPort & EuBuffer ----

    @Override
    public EnergyGraph.Ports energyPorts() {
        return SINK_PORTS;
    }

    @Override
    public boolean ranThisTick(ServerLevel level) {
        return !isRemoved() && lastTick == level.getServer().getTickCount();
    }

    @Override
    public boolean source() {
        return false;
    }

    @Override
    public double packetLimit() {
        return engine.maxInputPacket();
    }

    @Override
    public double receive(double amount) {
        if (amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        double remainder = engine.injectEu(amount);
        double accepted = Math.max(0.0, amount - remainder);
        if (accepted > 0.0) {
            setChanged();
        }
        return accepted;
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        return new EuBuffer.Reading(engine.storedEu(), engine.energyCapacity());
    }

    // ---- Capabilities & Slots ----

    @Override
    public Ecc2FluidTankView fluidCapabilityTank(Direction side) {
        return new Ecc2FluidTankView() {
            @Override
            public int tankCount() {
                return 2;
            }

            @Override
            public String fluidId(int tank) {
                if (tank == 0) {
                    return engine.waterMb() > 0 ? Ecc2Ids.MINECRAFT_WATER_ID : "";
                }
                if (tank == 1) {
                    return engine.waterGasMb() > 0 ? Ecc2Ids.WATER_GAS_ID : "";
                }
                return "";
            }

            @Override
            public int fluidAmount(int tank) {
                return tank == 0 ? engine.waterMb() : tank == 1 ? engine.waterGasMb() : 0;
            }

            @Override
            public int tankCapacity(int tank) {
                return tank == 0
                        ? WaterGasEngine.WATER_TANK_CAPACITY_MB
                        : tank == 1 ? WaterGasEngine.GAS_TANK_CAPACITY_MB : 0;
            }

            @Override
            public boolean acceptsFluid(int tank, String fluidId) {
                return tank == 0 && Ecc2Ids.MINECRAFT_WATER_ID.equals(fluidId);
            }

            @Override
            public boolean allowsInput() {
                return true;
            }

            @Override
            public boolean allowsOutput() {
                return true;
            }

            @Override
            public int fillFluid(int tank, String fluidId, int amount, boolean simulate) {
                if ((tank != -1 && tank != 0) || !Ecc2Ids.MINECRAFT_WATER_ID.equals(fluidId)) {
                    return 0;
                }
                return engine.fillWater(amount, simulate);
            }

            @Override
            public int drainFluid(int tank, String fluidId, int maxDrain, boolean simulate) {
                if (tank != -1 && tank != 1) {
                    return 0;
                }
                if (!fluidId.isEmpty() && !Ecc2Ids.WATER_GAS_ID.equals(fluidId)) {
                    return 0;
                }
                return engine.drainWaterGas(maxDrain, simulate);
            }

            @Override
            public String defaultDrainFluidId() {
                return engine.waterGasMb() > 0 ? Ecc2Ids.WATER_GAS_ID : "";
            }

            @Override
            public void restoreSnapshot(Snapshot snapshot) {
                if (snapshot.amounts().length >= 2) {
                    engine.setWaterMb(snapshot.amounts()[0]);
                    engine.setWaterGasMb(snapshot.amounts()[1]);
                }
            }

            @Override
            public void markChanged() {
                setChanged();
            }
        };
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return MachineAccess.connects(this, side) ? ALL_AUTOMATED_SLOTS : EMPTY_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_COAL_DUST -> isCoalDust(stack);
            case SLOT_WATER_CELL_IN ->
                    GiantGasPlatform.isDrainableContainer(stack, Ecc2Ids.MINECRAFT_WATER_ID::equals);
            case SLOT_GAS_CELL_IN ->
                    GiantGasPlatform.isFillableContainer(stack, Ecc2Ids.WATER_GAS_ID);
            case SLOT_BATTERY ->
                    PortableGear.canDischarge(stack, engine.inputTier())
                            || stack.is(Items.REDSTONE);
            case 6, 7, 8, 9 -> Ecc2UpgradeHelper.isAcceptedUpgrade(stack, true, true);
            default -> false;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (!MachineAccess.allowsInput(this, side)) {
            return false;
        }
        if (slot == SLOT_BATTERY) {
            return PortableGear.canSupplyCharge(stack, engine.inputTier())
                    || stack.is(Items.REDSTONE);
        }
        return (slot == SLOT_COAL_DUST || slot == SLOT_WATER_CELL_IN || slot == SLOT_GAS_CELL_IN)
                && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return MachineAccess.allowsOutput(this, side)
                && (slot == SLOT_WATER_CELL_OUT || slot == SLOT_GAS_CELL_OUT);
    }

    @Override
    public int automationLimit(int slot, ItemStack stack) {
        return slot == SLOT_BATTERY ? PortableGear.powerSlotLimit(stack) : stack.getMaxStackSize();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.water_gas_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new WaterGasMachineMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveState(Ecc2DataWriter out) {
        out.putItems("ecc2_items", items);
        out.putDouble("ecc2_eu", engine.storedEu());
        out.putInt("ecc2_water", engine.waterMb());
        out.putInt("ecc2_gas", engine.waterGasMb());
        out.putInt("ecc2_progress", engine.progress());
    }

    @Override
    protected void loadState(Ecc2DataReader in) {
        in.loadItems("ecc2_items", items);
        engine.setUpgrades(
                Ecc2UpgradeHelper.readProfile(
                        items.subList(UPGRADE_START, UPGRADE_START + UPGRADE_COUNT)));
        engine.setStoredEu(in.getDouble("ecc2_eu", 0.0));
        engine.setWaterMb(in.getInt("ecc2_water", 0));
        engine.setWaterGasMb(in.getInt("ecc2_gas", 0));
        engine.setProgress(in.getInt("ecc2_progress", 0));
    }

    // ---- Public Getters for Menus & Compat (JEI/EMI/Jade/TOP) ----

    public WaterGasEngine engine() {
        return engine;
    }

    public int waterMb() {
        return engine.waterMb();
    }

    public int waterGasMb() {
        return engine.waterGasMb();
    }

    public double storedEu() {
        return engine.storedEu();
    }

    public double energyCapacity() {
        return engine.energyCapacity();
    }

    public int progress() {
        return engine.progress();
    }

    public int operationTicks() {
        return engine.operationTicks();
    }

    public boolean isSolarActive() {
        return engine.isSolarActive();
    }

    @Override
    public List<FluidContents> fluidContents() {
        return List.of(
                engine.waterMb() > 0
                        ? new FluidContents(
                                GiantGasPlatform.fluidDisplayName(Ecc2Ids.MINECRAFT_WATER_ID),
                                engine.waterMb(),
                                WaterGasEngine.WATER_TANK_CAPACITY_MB)
                        : FluidContents.empty(WaterGasEngine.WATER_TANK_CAPACITY_MB),
                engine.waterGasMb() > 0
                        ? new FluidContents(
                                GiantGasPlatform.fluidDisplayName(Ecc2Ids.WATER_GAS_ID),
                                engine.waterGasMb(),
                                WaterGasEngine.GAS_TANK_CAPACITY_MB)
                        : FluidContents.empty(WaterGasEngine.GAS_TANK_CAPACITY_MB));
    }
}
