package com.ecocraft2.gas;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.GasMachineOutcome;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.ecocraft2.giant.Ecc2DataReader;
import com.ecocraft2.giant.Ecc2DataWriter;
import com.ecocraft2.giant.Ecc2FluidTankView;
import com.ecocraft2.giant.Ecc2UpgradeHelper;
import com.ecocraft2.giant.GiantGasModule;
import com.ecocraft2.giant.GiantGasPlatform;
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
 * Organic Fermenter ({@code ecc2:organic_fermenter}): LV Tier 1; ferments {@code 50 mB
 * ecc2:culture_medium + any edible item (nutrition H > 0) + 200 EU / 100t -> 20 * H mB ic2:biogas},
 * automatically returning food containers (bowls, glass bottles, etc.) to the return slot.
 */
public final class OrganicFermenterBlockEntity extends GasMachineBlockEntityBase
        implements WorldPowerPort, EuBuffer {

    public static final int SLOT_FOOD_INPUT = 0;
    public static final int SLOT_CONTAINER_RETURN = 1;
    public static final int SLOT_CULTURE_CELL_IN = 2;
    public static final int SLOT_CULTURE_CELL_OUT = 3;
    public static final int SLOT_BIOGAS_CELL_IN = 4;
    public static final int SLOT_BIOGAS_CELL_OUT = 5;
    public static final int SLOT_BATTERY = 6;
    public static final int UPGRADE_START = 7;
    public static final int UPGRADE_COUNT = 4;
    public static final int INVENTORY_SIZE = 11;

    private static final int[] ALL_AUTOMATED_SLOTS = {0, 1, 2, 3, 4, 5, 6};
    private static final int[] INPUT_SLOTS = {SLOT_FOOD_INPUT};
    private static final int[] OUTPUT_SLOTS = {
        SLOT_CONTAINER_RETURN, SLOT_CULTURE_CELL_OUT, SLOT_BIOGAS_CELL_OUT
    };
    private static final EnergyGraph.Ports SINK_PORTS =
            new EnergyGraph.Ports(Set.of(CapabilityFacade.Face.values()), Set.of(), false);

    private final OrganicFermenterEngine engine = new OrganicFermenterEngine();

    public OrganicFermenterBlockEntity(BlockPos pos, BlockState state) {
        super(GiantGasModule.ORGANIC_FERMENTER_BE.get(), pos, state, INVENTORY_SIZE);
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

        // Drain culture medium containers 2 -> 3
        changed |=
                drainContainerSlot(
                        SLOT_CULTURE_CELL_IN,
                        SLOT_CULTURE_CELL_OUT,
                        Ecc2Ids.CULTURE_MEDIUM_ID::equals,
                        OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB - engine.cultureMb(),
                        (fid, amt) -> engine.fillCulture(amt, false));

        // Fill biogas containers 4 -> 5 (ic2:fluid_cell -> ecc2:biogas_cell, bucket -> ic2:biogas_bucket)
        changed |=
                fillContainerSlot(
                        SLOT_BIOGAS_CELL_IN,
                        SLOT_BIOGAS_CELL_OUT,
                        Ecc2Ids.IC2_BIOGAS_ID,
                        engine.biogasMb(),
                        amt -> engine.drainBiogas(amt, false));

        // Ferment food + culture_medium -> ic2:biogas + container remainder
        boolean redstoneBlocked =
                Ecc2UpgradeHelper.hasRedstoneInverter(upgrades)
                        && !level.hasNeighborSignal(worldPosition);
        ItemStack food = items.get(SLOT_FOOD_INPUT);
        int nutrition = redstoneBlocked ? 0 : GiantGasPlatform.foodNutrition(food);
        ItemStack remainder =
                nutrition > 0 ? GiantGasPlatform.foodContainerRemainder(food) : ItemStack.EMPTY;
        boolean canAcceptRemainder =
                remainder.isEmpty() || canMergeIntoSlot(SLOT_CONTAINER_RETURN, remainder);

        GasMachineOutcome outcome = engine.tick(nutrition, canAcceptRemainder);
        if (outcome == GasMachineOutcome.COMPLETED) {
            food.shrink(1);
            if (!remainder.isEmpty()) {
                mergeIntoSlot(SLOT_CONTAINER_RETURN, remainder);
            }
            changed = true;
        } else if (outcome == GasMachineOutcome.WORKING) {
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
                        stack -> GiantGasPlatform.foodNutrition(stack) > 0);
        changed |=
                Ecc2UpgradeHelper.executeFluidUpgrades(
                        level,
                        upgrades,
                        endpoints,
                        Ecc2Ids.CULTURE_MEDIUM_ID::equals,
                        (fid, amt) -> engine.fillCulture(amt, false),
                        () -> OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB - engine.cultureMb(),
                        () -> Ecc2Ids.IC2_BIOGAS_ID,
                        engine::biogasMb,
                        amt -> engine.drainBiogas(amt, false));

        syncActiveState(level, state, engine.isActive());
        if (changed) {
            setChanged();
        }
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

    // ---- Fluid & Item Capabilities ----

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
                    return engine.cultureMb() > 0 ? Ecc2Ids.CULTURE_MEDIUM_ID : "";
                }
                if (tank == 1) {
                    return engine.biogasMb() > 0 ? Ecc2Ids.IC2_BIOGAS_ID : "";
                }
                return "";
            }

            @Override
            public int fluidAmount(int tank) {
                return tank == 0 ? engine.cultureMb() : tank == 1 ? engine.biogasMb() : 0;
            }

            @Override
            public int tankCapacity(int tank) {
                return tank == 0
                        ? OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB
                        : tank == 1 ? OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB : 0;
            }

            @Override
            public boolean acceptsFluid(int tank, String fluidId) {
                return tank == 0 && Ecc2Ids.CULTURE_MEDIUM_ID.equals(fluidId);
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
                if ((tank != -1 && tank != 0) || !Ecc2Ids.CULTURE_MEDIUM_ID.equals(fluidId)) {
                    return 0;
                }
                return engine.fillCulture(amount, simulate);
            }

            @Override
            public int drainFluid(int tank, String fluidId, int maxDrain, boolean simulate) {
                if (tank != -1 && tank != 1) {
                    return 0;
                }
                if (!fluidId.isEmpty() && !Ecc2Ids.IC2_BIOGAS_ID.equals(fluidId)) {
                    return 0;
                }
                return engine.drainBiogas(maxDrain, simulate);
            }

            @Override
            public String defaultDrainFluidId() {
                return engine.biogasMb() > 0 ? Ecc2Ids.IC2_BIOGAS_ID : "";
            }

            @Override
            public void restoreSnapshot(Snapshot snapshot) {
                if (snapshot.amounts().length >= 2) {
                    engine.setCultureMb(snapshot.amounts()[0]);
                    engine.setBiogasMb(snapshot.amounts()[1]);
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
        return ALL_AUTOMATED_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_FOOD_INPUT -> GiantGasPlatform.foodNutrition(stack) > 0;
            case SLOT_CULTURE_CELL_IN ->
                    GiantGasPlatform.isDrainableContainer(stack, Ecc2Ids.CULTURE_MEDIUM_ID::equals);
            case SLOT_BIOGAS_CELL_IN ->
                    GiantGasPlatform.isFillableContainer(stack, Ecc2Ids.IC2_BIOGAS_ID);
            case SLOT_BATTERY ->
                    PortableGear.canDischarge(stack, engine.inputTier())
                            || stack.is(Items.REDSTONE);
            case 7, 8, 9, 10 -> Ecc2UpgradeHelper.isAcceptedUpgrade(stack, true, true);
            default -> false;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        if (slot == SLOT_BATTERY) {
            return PortableGear.canSupplyCharge(stack, engine.inputTier())
                    || stack.is(Items.REDSTONE);
        }
        return (slot == SLOT_FOOD_INPUT
                        || slot == SLOT_CULTURE_CELL_IN
                        || slot == SLOT_BIOGAS_CELL_IN)
                && canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_CONTAINER_RETURN
                || slot == SLOT_CULTURE_CELL_OUT
                || slot == SLOT_BIOGAS_CELL_OUT;
    }

    @Override
    public int automationLimit(int slot, ItemStack stack) {
        return slot == SLOT_BATTERY ? PortableGear.powerSlotLimit(stack) : stack.getMaxStackSize();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.organic_fermenter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new OrganicFermenterMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveState(Ecc2DataWriter out) {
        out.putItems("ecc2_items", items);
        out.putDouble("ecc2_eu", engine.storedEu());
        out.putInt("ecc2_culture", engine.cultureMb());
        out.putInt("ecc2_biogas", engine.biogasMb());
        out.putInt("ecc2_progress", engine.progress());
        out.putInt("ecc2_nutrition", engine.activeNutrition());
    }

    @Override
    protected void loadState(Ecc2DataReader in) {
        in.loadItems("ecc2_items", items);
        engine.setUpgrades(
                Ecc2UpgradeHelper.readProfile(
                        items.subList(UPGRADE_START, UPGRADE_START + UPGRADE_COUNT)));
        engine.setStoredEu(in.getDouble("ecc2_eu", 0.0));
        engine.setCultureMb(in.getInt("ecc2_culture", 0));
        engine.setBiogasMb(in.getInt("ecc2_biogas", 0));
        engine.setProgress(in.getInt("ecc2_progress", 0));
        engine.setActiveNutrition(in.getInt("ecc2_nutrition", 0));
    }

    // ---- Public Getters ----

    public OrganicFermenterEngine engine() {
        return engine;
    }

    public int cultureMb() {
        return engine.cultureMb();
    }

    public int biogasMb() {
        return engine.biogasMb();
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

    @Override
    public List<FluidContents> fluidContents() {
        return List.of(
                engine.cultureMb() > 0
                        ? new FluidContents(
                                GiantGasPlatform.fluidDisplayName(Ecc2Ids.CULTURE_MEDIUM_ID),
                                engine.cultureMb(),
                                OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB)
                        : FluidContents.empty(OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB),
                engine.biogasMb() > 0
                        ? new FluidContents(
                                GiantGasPlatform.fluidDisplayName(Ecc2Ids.IC2_BIOGAS_ID),
                                engine.biogasMb(),
                                OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB)
                        : FluidContents.empty(OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB));
    }
}
