package com.ecocraft2.gas;

import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.common.gas.GasMachineOutcome;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.giant.Ecc2DataReader;
import com.ecocraft2.giant.Ecc2DataWriter;
import com.ecocraft2.giant.Ecc2FluidTankView;
import com.ecocraft2.giant.Ecc2UpgradeHelper;
import com.ecocraft2.giant.GiantGasModule;
import com.ecocraft2.giant.GiantGasPlatform;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.logistics.FluidContents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gas Furnace ({@code ecc2:gas_furnace}): pure gas-driven smelting (no EU required) consuming
 * strictly 50 mB of {@code water_gas}, {@code natural_gas}, or {@code ic2:biogas} per smelted item.
 */
public final class GasFurnaceBlockEntity extends GasMachineBlockEntityBase {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_GAS_CELL_IN = 2;
    public static final int SLOT_GAS_CELL_OUT = 3;
    public static final int UPGRADE_START = 4;
    public static final int UPGRADE_COUNT = 4;
    public static final int INVENTORY_SIZE = 8;

    private static final int[] ALL_AUTOMATED_SLOTS = {0, 1, 2, 3};
    private static final int[] EMPTY_SLOTS = new int[0];
    private static final int[] INPUT_SLOTS = {SLOT_INPUT};
    private static final int[] OUTPUT_SLOTS = {SLOT_OUTPUT, SLOT_GAS_CELL_OUT};

    private final GasFurnaceEngine engine = new GasFurnaceEngine();

    public GasFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(GiantGasModule.GAS_FURNACE_BE.get(), pos, state, INVENTORY_SIZE);
    }

    @Override
    protected void serverTick(ServerLevel level, BlockState state) {
        lastTick = level.getServer().getTickCount();
        boolean changed = false;

        engine.configure(Ecc2Config.gasFurnaceGasPerItem(), Ecc2Config.gasFurnaceBaseTicks());

        var upgrades = items.subList(UPGRADE_START, UPGRADE_START + UPGRADE_COUNT);
        int oc = Ecc2UpgradeHelper.readProfile(upgrades).overclockers();
        if (oc != engine.overclockers()) {
            engine.setOverclockers(oc);
            changed = true;
        }

        // Drain gas cell / bucket from slot 2 -> 3
        changed |=
                drainContainerSlot(
                        SLOT_GAS_CELL_IN,
                        SLOT_GAS_CELL_OUT,
                        this::acceptsGasFluid,
                        GasFurnaceEngine.GAS_TANK_CAPACITY_MB - engine.gasMb(),
                        (fid, amt) ->
                                GasFuelSpec.byFluidId(fid)
                                        .ifPresent(spec -> engine.fillGas(spec, amt, false)));

        // Resolve vanilla smelting recipe
        ItemStack input = items.get(SLOT_INPUT);
        boolean redstoneBlocked =
                Ecc2UpgradeHelper.hasRedstoneInverter(upgrades)
                        && !level.hasNeighborSignal(worldPosition);
        ItemStack result =
                redstoneBlocked || input.isEmpty()
                        ? ItemStack.EMPTY
                        : GiantGasPlatform.smeltingResult(level, input);
        boolean hasRecipe = !result.isEmpty();
        boolean canAcceptOut = hasRecipe && canMergeIntoSlot(SLOT_OUTPUT, result);

        GasMachineOutcome outcome = engine.tick(hasRecipe, canAcceptOut);
        if (outcome == GasMachineOutcome.COMPLETED) {
            input.shrink(1);
            mergeIntoSlot(SLOT_OUTPUT, result);
            changed = true;
        } else if (outcome == GasMachineOutcome.WORKING) {
            changed = true;
        }

        // Item & fluid upgrades
        var endpoints = sixSideEndpoints();
        changed |=
                Ecc2UpgradeHelper.executeItemUpgrades(
                        level,
                        upgrades,
                        endpoints,
                        this,
                        INPUT_SLOTS,
                        OUTPUT_SLOTS,
                        stack -> !GiantGasPlatform.smeltingResult(level, stack).isEmpty());
        changed |=
                Ecc2UpgradeHelper.executeFluidUpgrades(
                        level,
                        upgrades,
                        endpoints,
                        this::acceptsGasFluid,
                        (fid, amt) ->
                                GasFuelSpec.byFluidId(fid)
                                        .map(spec -> engine.fillGas(spec, amt, false))
                                        .orElse(0),
                        () -> GasFurnaceEngine.GAS_TANK_CAPACITY_MB - engine.gasMb(),
                        null,
                        null,
                        null);

        syncActiveState(level, state, engine.isActive());
        if (changed) {
            setChanged();
        }
    }

    private boolean acceptsGasFluid(String fluidId) {
        var specOpt = GasFuelSpec.byFluidId(fluidId);
        if (specOpt.isEmpty()) {
            return false;
        }
        GasFuelSpec current = engine.currentFuel();
        return engine.gasMb() <= 0 || current == null || current == specOpt.get();
    }

    @Override
    public Ecc2FluidTankView fluidCapabilityTank(Direction side) {
        return new Ecc2FluidTankView() {
            @Override
            public int tankCount() {
                return 1;
            }

            @Override
            public String fluidId(int tank) {
                return tank == 0 && engine.gasMb() > 0 && engine.currentFuel() != null
                        ? engine.currentFuel().fluidId()
                        : "";
            }

            @Override
            public int fluidAmount(int tank) {
                return tank == 0 ? engine.gasMb() : 0;
            }

            @Override
            public int tankCapacity(int tank) {
                return tank == 0 ? GasFurnaceEngine.GAS_TANK_CAPACITY_MB : 0;
            }

            @Override
            public boolean acceptsFluid(int tank, String fluidId) {
                return tank == 0 && acceptsGasFluid(fluidId);
            }

            @Override
            public boolean allowsInput() {
                return true;
            }

            @Override
            public boolean allowsOutput() {
                return false;
            }

            @Override
            public int fillFluid(int tank, String fluidId, int amount, boolean simulate) {
                if (tank != -1 && tank != 0) {
                    return 0;
                }
                return GasFuelSpec.byFluidId(fluidId)
                        .map(spec -> engine.fillGas(spec, amount, simulate))
                        .orElse(0);
            }

            @Override
            public int drainFluid(int tank, String fluidId, int maxDrain, boolean simulate) {
                return 0;
            }

            @Override
            public void restoreSnapshot(Snapshot snapshot) {
                if (snapshot.amounts().length > 0 && snapshot.fluidIds().length > 0) {
                    GasFuelSpec spec = GasFuelSpec.byFluidId(snapshot.fluidIds()[0]).orElse(null);
                    engine.restoreState(
                            spec,
                            snapshot.amounts()[0],
                            engine.progress(),
                            engine.gasSpentOnCurrentItem());
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
            case SLOT_INPUT ->
                    !(getLevel() instanceof ServerLevel server)
                            || !GiantGasPlatform.smeltingResult(server, stack).isEmpty();
            case SLOT_GAS_CELL_IN ->
                    GiantGasPlatform.isDrainableContainer(stack, this::acceptsGasFluid);
            case 4, 5, 6, 7 -> Ecc2UpgradeHelper.isAcceptedUpgrade(stack, false, true);
            default -> false;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return MachineAccess.allowsInput(this, side)
                && (slot == SLOT_INPUT || slot == SLOT_GAS_CELL_IN)
                && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return MachineAccess.allowsOutput(this, side)
                && (slot == SLOT_OUTPUT || slot == SLOT_GAS_CELL_OUT);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.gas_furnace");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GasFurnaceMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveState(Ecc2DataWriter out) {
        out.putItems("ecc2_items", items);
        out.putString(
                "ecc2_fuel", engine.currentFuel() == null ? "" : engine.currentFuel().fluidId());
        out.putInt("ecc2_gas", engine.gasMb());
        out.putInt("ecc2_progress", engine.progress());
        out.putInt("ecc2_spent", engine.gasSpentOnCurrentItem());
    }

    @Override
    protected void loadState(Ecc2DataReader in) {
        in.loadItems("ecc2_items", items);
        engine.configure(Ecc2Config.gasFurnaceGasPerItem(), Ecc2Config.gasFurnaceBaseTicks());
        engine.setOverclockers(
                Ecc2UpgradeHelper.readProfile(
                                items.subList(UPGRADE_START, UPGRADE_START + UPGRADE_COUNT))
                        .overclockers());
        GasFuelSpec fuel = GasFuelSpec.byFluidId(in.getString("ecc2_fuel", "")).orElse(null);
        engine.restoreState(
                fuel,
                in.getInt("ecc2_gas", 0),
                in.getInt("ecc2_progress", 0),
                in.getInt("ecc2_spent", 0));
    }

    // ---- Public Getters ----

    public GasFurnaceEngine engine() {
        return engine;
    }

    public GasFuelSpec currentFuel() {
        return engine.currentFuel();
    }

    public int gasMb() {
        return engine.gasMb();
    }

    public int progress() {
        return engine.progress();
    }

    public int operationTicks() {
        return engine.operationTicks();
    }

    public boolean isMachineActive() {
        return engine.isActive();
    }

    @Override
    public List<FluidContents> fluidContents() {
        GasFuelSpec fuel = engine.currentFuel();
        return List.of(
                engine.gasMb() > 0 && fuel != null
                        ? new FluidContents(
                                GiantGasPlatform.fluidDisplayName(fuel.fluidId()),
                                engine.gasMb(),
                                GasFurnaceEngine.GAS_TANK_CAPACITY_MB)
                        : FluidContents.empty(GasFurnaceEngine.GAS_TANK_CAPACITY_MB));
    }
}
