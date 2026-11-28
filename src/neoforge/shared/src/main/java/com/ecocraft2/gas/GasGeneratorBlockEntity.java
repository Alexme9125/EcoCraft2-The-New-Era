package com.ecocraft2.gas;

import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.ecocraft2.config.Ecc2Config;
import com.ecocraft2.giant.Ecc2DataReader;
import com.ecocraft2.giant.Ecc2DataWriter;
import com.ecocraft2.giant.Ecc2FluidTankView;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gas Generator ({@code ecc2:gas_generator}): MV Tier 2 (128 EU/t) 6-side EU generator with 16,000
 * EU buffer, 16,000 mB gas tank, internal charging slot, and lossless combustion chamber
 * accounting.
 */
public final class GasGeneratorBlockEntity extends GasMachineBlockEntityBase
        implements WorldPowerPort, EuBuffer {

    public static final int SLOT_GAS_CELL_IN = 0;
    public static final int SLOT_GAS_CELL_OUT = 1;
    public static final int SLOT_CHARGE = 2;
    public static final int INVENTORY_SIZE = 3;

    private static final int[] ALL_AUTOMATED_SLOTS = {0, 1, 2};
    private static final int[] EMPTY_SLOTS = new int[0];
    private static final EnergyGraph.Ports SOURCE_PORTS =
            new EnergyGraph.Ports(Set.of(), Set.of(CapabilityFacade.Face.values()), false);

    private final GasGeneratorEngine engine = new GasGeneratorEngine();

    public GasGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(GiantGasModule.GAS_GENERATOR_BE.get(), pos, state, INVENTORY_SIZE);
    }

    @Override
    protected void serverTick(ServerLevel level, BlockState state) {
        lastTick = level.getServer().getTickCount();
        boolean changed = false;

        applyFuelConfig();

        // Drain gas cell/bucket into 16,000 mB tank
        changed |=
                drainContainerSlot(
                        SLOT_GAS_CELL_IN,
                        SLOT_GAS_CELL_OUT,
                        this::acceptsGasFluid,
                        GasGeneratorEngine.GAS_TANK_CAPACITY_MB - engine.gasMb(),
                        (fid, amt) ->
                                GiantGasPlatform.resolveGasFuel(fid)
                                        .ifPresent(spec -> engine.fillGas(spec, amt, false)));

        // Burn gas & charge internal electric item slot prior to grid draw
        ItemStack chargeItem = items.get(SLOT_CHARGE);
        double generated =
                engine.tick(
                        chargeItem.isEmpty()
                                ? null
                                : (offer, tier) -> PortableGear.charge(chargeItem, offer, tier));
        if (generated > 0.0) {
            changed = true;
        }

        syncActiveState(level, state, engine.isActive());
        if (changed) {
            setChanged();
        }
    }

    private boolean acceptsGasFluid(String fluidId) {
        var specOpt = GiantGasPlatform.resolveGasFuel(fluidId);
        if (specOpt.isEmpty()) {
            return false;
        }
        GasFuelSpec current = engine.currentFuel();
        boolean hasFuel = engine.gasMb() > 0 || engine.pendingEu() > 1e-12;
        return !hasFuel || current == null || current == specOpt.get();
    }

    // ---- WorldPowerPort & EuBuffer (MV Tier 2 6-side EU SOURCE) ----

    @Override
    public EnergyGraph.Ports energyPorts() {
        return SOURCE_PORTS;
    }

    @Override
    public boolean ranThisTick(ServerLevel level) {
        return !isRemoved() && lastTick == level.getServer().getTickCount();
    }

    @Override
    public boolean source() {
        return true;
    }

    @Override
    public double offer() {
        return engine.offeredEnergy();
    }

    @Override
    public double packetLimit() {
        return engine.packetSize();
    }

    @Override
    public double draw(double requested) {
        double drawn = engine.drawEnergy(requested);
        if (drawn > 0.0) {
            setChanged();
        }
        return drawn;
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        return new EuBuffer.Reading(engine.storedEu(), GasGeneratorEngine.EU_CAPACITY);
    }

    // ---- Fluid & Item Capabilities ----

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
                return tank == 0 ? GasGeneratorEngine.GAS_TANK_CAPACITY_MB : 0;
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
                return GiantGasPlatform.resolveGasFuel(fluidId)
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
                    engine.restoreTankState(spec, snapshot.amounts()[0]);
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
            case SLOT_GAS_CELL_IN ->
                    GiantGasPlatform.isDrainableContainer(stack, this::acceptsGasFluid);
            case SLOT_CHARGE -> PortableGear.acceptsCharge(stack, GasGeneratorEngine.OUTPUT_TIER);
            default -> false;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (!MachineAccess.allowsInput(this, side)) {
            return false;
        }
        if (slot == SLOT_CHARGE) {
            return canPlaceItem(slot, stack)
                    && !PortableGear.chargeComplete(stack, GasGeneratorEngine.OUTPUT_TIER);
        }
        return slot == SLOT_GAS_CELL_IN && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (!MachineAccess.allowsOutput(this, side)) {
            return false;
        }
        if (slot == SLOT_CHARGE) {
            return PortableGear.chargeComplete(stack, GasGeneratorEngine.OUTPUT_TIER);
        }
        return slot == SLOT_GAS_CELL_OUT;
    }

    @Override
    public int automationLimit(int slot, ItemStack stack) {
        return slot == SLOT_CHARGE ? PortableGear.powerSlotLimit(stack) : stack.getMaxStackSize();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecera.gas_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new GasGeneratorMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveState(Ecc2DataWriter out) {
        out.putItems("ecera_items", items);
        out.putString(
                "ecera_fuel", engine.currentFuel() == null ? "" : engine.currentFuel().fluidId());
        out.putInt("ecera_gas", engine.gasMb());
        out.putDouble("ecera_pending_eu", engine.pendingEu());
        out.putDouble("ecera_eu", engine.storedEu());
    }

    @Override
    protected void loadState(Ecc2DataReader in) {
        in.loadItems("ecera_items", items);
        applyFuelConfig();
        GasFuelSpec fuel = GasFuelSpec.byFluidId(in.getString("ecera_fuel", "")).orElse(null);
        engine.restoreState(
                fuel,
                in.getInt("ecera_gas", 0),
                in.getDouble("ecera_pending_eu", 0.0),
                in.getDouble("ecera_eu", 0.0));
    }

    private void applyFuelConfig() {
        engine.configureFuel(
                GasFuelSpec.WATER_GAS,
                Ecc2Config.waterGasEnergyPerBucket(),
                Ecc2Config.waterGasOutputEuPerTick());
        engine.configureFuel(
                GasFuelSpec.NATURAL_GAS,
                Ecc2Config.naturalGasEnergyPerBucket(),
                Ecc2Config.naturalGasOutputEuPerTick());
        engine.configureFuel(
                GasFuelSpec.BIOGAS,
                Ecc2Config.biogasEnergyPerBucket(),
                Ecc2Config.biogasOutputEuPerTick());
    }

    // ---- Public Getters ----

    public GasGeneratorEngine engine() {
        return engine;
    }

    public GasFuelSpec currentFuel() {
        return engine.currentFuel();
    }

    public int gasMb() {
        return engine.gasMb();
    }

    public double storedEu() {
        return engine.storedEu();
    }

    public double generatingEuPerTick() {
        return engine.lastTickGeneratedEu();
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
                                GasGeneratorEngine.GAS_TANK_CAPACITY_MB)
                        : FluidContents.empty(GasGeneratorEngine.GAS_TANK_CAPACITY_MB));
    }
}
