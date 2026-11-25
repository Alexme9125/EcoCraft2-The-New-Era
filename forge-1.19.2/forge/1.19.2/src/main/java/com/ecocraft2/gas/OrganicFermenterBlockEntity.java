package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.GasMachineOutcome;
import com.ecocraft2.common.gas.OrganicFermenterEngine;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.access.MachinePortCache;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Organic Fermenter BlockEntity: {@code 50 mB ecc2:culture_medium + any FoodProperties item (H > 0)
 * + 200 EU / 100t -> 20 * H mB ic2:biogas}, returning food container remainders (bowls, bottles)
 * into the dedicated container return slot.
 */
public final class OrganicFermenterBlockEntity extends BlockEntity
        implements GasMachineBlock.TickableMachine,
                WorldPowerPort,
                EuBuffer,
                MachineAccessTarget,
                MenuProvider {

    public static final int SLOT_FOOD = 0;
    public static final int SLOT_CONTAINER_RETURN = 1;
    public static final int SLOT_CULTURE_IN = 2;
    public static final int SLOT_CULTURE_OUT = 3;
    public static final int SLOT_BIOGAS_IN = 4;
    public static final int SLOT_BIOGAS_OUT = 5;
    public static final int SLOT_BATTERY = 6;
    public static final int SLOT_UPGRADE_START = 7;
    public static final int TOTAL_SLOTS = 11;

    private final OrganicFermenterEngine engine = new OrganicFermenterEngine();
    private final MachinePortCache portCache = new MachinePortCache(this);
    private long lastTick = Long.MIN_VALUE;

    private final ItemStackHandler inventory =
            new ItemStackHandler(TOTAL_SLOTS) {
                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return switch (slot) {
                        case SLOT_FOOD -> foodNutrition(stack) > 0;
                        case SLOT_CULTURE_IN, SLOT_BIOGAS_IN ->
                                FluidUtil.getFluidHandler(Ecc2BridgePlatform.copyWithCount(stack, 1))
                                        .isPresent();
                        case SLOT_BATTERY -> Ecc2EnergyHelper.canDischarge(stack, engine.inputTier());
                        case 7, 8, 9, 10 -> Ecc2EnergyHelper.isUpgradeItem(stack);
                        default -> false;
                    };
                }

                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                    if (slot >= SLOT_UPGRADE_START) {
                        refreshUpgrades();
                    }
                }
            };

    private final IItemHandler sidedItemHandler =
            new IItemHandler() {
                @Override
                public int getSlots() {
                    return TOTAL_SLOTS;
                }

                @Override
                public @NotNull ItemStack getStackInSlot(int slot) {
                    return inventory.getStackInSlot(slot);
                }

                @Override
                public @NotNull ItemStack insertItem(
                        int slot, @NotNull ItemStack stack, boolean simulate) {
                    if (slot != SLOT_FOOD
                            && slot != SLOT_CULTURE_IN
                            && slot != SLOT_BIOGAS_IN
                            && slot != SLOT_BATTERY) {
                        return stack;
                    }
                    return inventory.insertItem(slot, stack, simulate);
                }

                @Override
                public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                    if (slot != SLOT_CONTAINER_RETURN
                            && slot != SLOT_CULTURE_OUT
                            && slot != SLOT_BIOGAS_OUT) {
                        return ItemStack.EMPTY;
                    }
                    return inventory.extractItem(slot, amount, simulate);
                }

                @Override
                public int getSlotLimit(int slot) {
                    return inventory.getSlotLimit(slot);
                }

                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return inventory.isItemValid(slot, stack);
                }
            };

    private final IFluidHandler dualFluidHandler =
            new IFluidHandler() {
                @Override
                public int getTanks() {
                    return 2;
                }

                @Override
                public @NotNull FluidStack getFluidInTank(int tank) {
                    if (tank == 0) {
                        return engine.cultureMb() > 0
                                ? new FluidStack(GasRegistry.CULTURE_MEDIUM.get(), engine.cultureMb())
                                : FluidStack.EMPTY;
                    }
                    Fluid biogas = biogasFluid();
                    return engine.biogasMb() > 0 && biogas != Fluids.EMPTY
                            ? new FluidStack(biogas, engine.biogasMb())
                            : FluidStack.EMPTY;
                }

                @Override
                public int getTankCapacity(int tank) {
                    return tank == 0
                            ? OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB
                            : OrganicFermenterEngine.BIOGAS_TANK_CAPACITY_MB;
                }

                @Override
                public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                    return tank == 0
                            ? stack.getFluid() == GasRegistry.CULTURE_MEDIUM.get()
                            : Ecc2Ids.IC2_BIOGAS_ID.equals(Ecc2BridgePlatform.fluidId(stack));
                }

                @Override
                public int fill(FluidStack resource, FluidAction action) {
                    if (resource.isEmpty()
                            || resource.getFluid() != GasRegistry.CULTURE_MEDIUM.get()) {
                        return 0;
                    }
                    int accepted = engine.fillCulture(resource.getAmount(), action.simulate());
                    if (accepted > 0 && action.execute()) {
                        setChanged();
                    }
                    return accepted;
                }

                @Override
                public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                    if (resource.isEmpty()
                            || !Ecc2Ids.IC2_BIOGAS_ID.equals(Ecc2BridgePlatform.fluidId(resource))) {
                        return FluidStack.EMPTY;
                    }
                    return drain(resource.getAmount(), action);
                }

                @Override
                public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                    Fluid biogas = biogasFluid();
                    if (biogas == Fluids.EMPTY) {
                        return FluidStack.EMPTY;
                    }
                    int drained = engine.drainBiogas(maxDrain, action.simulate());
                    if (drained > 0 && action.execute()) {
                        setChanged();
                    }
                    return drained > 0 ? new FluidStack(biogas, drained) : FluidStack.EMPTY;
                }
            };

    private final ContainerData syncData =
            new ContainerData() {
                @Override
                public int get(int index) {
                    int eu = (int) Math.floor(engine.storedEu());
                    int cap = (int) Math.floor(engine.energyCapacity());
                    return switch (index) {
                        case 0 -> eu & 0xFFFF;
                        case 1 -> (eu >>> 16) & 0xFFFF;
                        case 2 -> cap & 0xFFFF;
                        case 3 -> (cap >>> 16) & 0xFFFF;
                        case 4 -> engine.cultureMb();
                        case 5 -> engine.biogasMb();
                        case 6 -> engine.scaledProgress(24);
                        case 7 -> engine.isActive() ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 8;
                }
            };

    public OrganicFermenterBlockEntity(BlockPos pos, BlockState state) {
        super(GasRegistry.ORGANIC_FERMENTER_BE.get(), pos, state);
    }

    public static int foodNutrition(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        FoodProperties props = stack.getFoodProperties(null);
        return props == null ? 0 : Math.max(0, props.getNutrition());
    }

    private static Fluid biogasFluid() {
        return Ecc2BridgePlatform.findFluid(Ecc2Ids.IC2_BIOGAS_ID);
    }

    public OrganicFermenterEngine engine() {
        return engine;
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    @Override
    public Container dropInventory() {
        SimpleContainer drops = new SimpleContainer(TOTAL_SLOTS);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            drops.setItem(i, inventory.getStackInSlot(i));
        }
        return drops;
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        lastTick = serverLevel.getGameTime();
        refreshUpgrades();

        ItemStack battery = inventory.getStackInSlot(SLOT_BATTERY);
        if (!battery.isEmpty() && engine.demandedEu() > 0.0) {
            double drawn =
                    Ecc2EnergyHelper.dischargeStack(
                            battery,
                            Math.min(engine.maxInputPacket(), engine.demandedEu()),
                            engine.energyCapacity(),
                            engine.inputTier());
            if (drawn > 0.0) {
                engine.injectEu(drawn);
                setChanged();
            }
        }

        if (GasContainerHelper.drainItemIntoTank(
                inventory,
                SLOT_CULTURE_IN,
                SLOT_CULTURE_OUT,
                OrganicFermenterEngine.CULTURE_TANK_CAPACITY_MB - engine.cultureMb(),
                fs -> fs.getFluid() == GasRegistry.CULTURE_MEDIUM.get(),
                fs -> engine.fillCulture(fs.getAmount(), false))) {
            setChanged();
        }
        Fluid biogas = biogasFluid();
        if (engine.biogasMb() > 0
                && biogas != Fluids.EMPTY
                && GasContainerHelper.fillItemFromTank(
                        inventory,
                        SLOT_BIOGAS_IN,
                        SLOT_BIOGAS_OUT,
                        new FluidStack(biogas, engine.biogasMb()),
                        filled -> engine.drainBiogas(filled, false))) {
            setChanged();
        }

        ItemStack foodStack = inventory.getStackInSlot(SLOT_FOOD);
        int nutrition = foodNutrition(foodStack);
        ItemStack containerRemainder =
                foodStack.isEmpty() ? ItemStack.EMPTY : foodStack.getCraftingRemainingItem();
        boolean canAcceptReturn =
                containerRemainder.isEmpty()
                        || GasContainerHelper.canFitSlot(
                                inventory, SLOT_CONTAINER_RETURN, containerRemainder);

        var outcome = engine.tick(nutrition, canAcceptReturn);
        if (outcome == GasMachineOutcome.COMPLETED) {
            inventory.extractItem(SLOT_FOOD, 1, false);
            if (!containerRemainder.isEmpty()) {
                GasContainerHelper.insertIntoSlot(
                        inventory, SLOT_CONTAINER_RETURN, containerRemainder);
            }
            setChanged();
        } else if (outcome == GasMachineOutcome.WORKING) {
            setChanged();
        }

        BlockState state = getBlockState();
        if (state.hasProperty(GasMachineBlock.ACTIVE)
                && state.getValue(GasMachineBlock.ACTIVE) != engine.isActive()) {
            serverLevel.setBlock(
                    worldPosition,
                    state.setValue(GasMachineBlock.ACTIVE, engine.isActive()),
                    Block.UPDATE_CLIENTS);
        }
    }

    private void refreshUpgrades() {
        List<ItemStack> upgrades = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            ItemStack u = inventory.getStackInSlot(SLOT_UPGRADE_START + i);
            if (!u.isEmpty()) {
                upgrades.add(u);
            }
        }
        engine.setUpgrades(Ecc2EnergyHelper.readUpgradeProfile(upgrades));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            refreshUpgrades();
            WorldEnergy.connect(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        WorldEnergy.disconnect(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        WorldEnergy.disconnect(this);
        portCache.invalidate();
        super.setRemoved();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        portCache.invalidate();
    }

    @Override
    public void invalidateMachinePorts() {
        portCache.invalidate();
    }

    @Override
    public boolean exchangeHeldFluid(Player player, InteractionHand hand, Direction side) {
        return FluidUtil.interactWithFluidHandler(player, hand, dualFluidHandler);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(
            @NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            if (side != null && !MachineAccess.connects(this, side)) {
                return LazyOptional.empty();
            }
            return portCache.item(side, () -> sidedItemHandler).cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            if (side != null && !MachineAccess.connects(this, side)) {
                return LazyOptional.empty();
            }
            return portCache.fluid(side, () -> dualFluidHandler).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public boolean ranThisTick(ServerLevel level) {
        return lastTick == level.getGameTime();
    }

    @Override
    public EnergyGraph.Ports energyPorts() {
        return Ecc2EnergyHelper.ALL_INPUT_PORTS;
    }

    @Override
    public double receive(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return 0.0;
        }
        double rem = engine.injectEu(amount);
        double accepted = Math.max(0.0, amount - rem);
        if (accepted > 0.0) {
            setChanged();
        }
        return accepted;
    }

    @Override
    public double packetLimit() {
        return engine.maxInputPacket();
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        return new EuBuffer.Reading(engine.storedEu(), engine.energyCapacity());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putDouble("StoredEu", engine.storedEu());
        tag.putInt("CultureMb", engine.cultureMb());
        tag.putInt("BiogasMb", engine.biogasMb());
        tag.putInt("Progress", engine.progress());
        tag.putInt("ActiveNutrition", engine.activeNutrition());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
        }
        refreshUpgrades();
        engine.setStoredEu(tag.getDouble("StoredEu"));
        engine.setCultureMb(tag.getInt("CultureMb"));
        engine.setBiogasMb(tag.getInt("BiogasMb"));
        engine.setProgress(tag.getInt("Progress"));
        engine.setActiveNutrition(tag.getInt("ActiveNutrition"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.organic_fermenter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new OrganicFermenterMenu(containerId, playerInv, this, syncData);
    }
}
