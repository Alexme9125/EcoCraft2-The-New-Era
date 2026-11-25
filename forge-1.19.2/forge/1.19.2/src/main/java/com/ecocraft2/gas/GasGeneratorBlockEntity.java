package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasGeneratorEngine;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.access.MachinePortCache;
import com.industrialage.ia2.energy.graph.EnergyGraph;
import com.industrialage.ia2.power.EuBuffer;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 * Gas Generator BlockEntity: burns Water Gas (32 EU/t), Natural Gas (64 EU/t), or Biogas (32 EU/t)
 * from a 16,000 mB tank into a 16,000 EU MV Tier 2 buffer with 1 charging slot.
 */
public final class GasGeneratorBlockEntity extends BlockEntity
        implements GasMachineBlock.TickableMachine,
                WorldPowerPort,
                EuBuffer,
                MachineAccessTarget,
                MenuProvider {

    public static final int SLOT_GAS_IN = 0;
    public static final int SLOT_GAS_OUT = 1;
    public static final int SLOT_CHARGE = 2;
    public static final int TOTAL_SLOTS = 3;

    private final GasGeneratorEngine engine = new GasGeneratorEngine();
    private final MachinePortCache portCache = new MachinePortCache(this);
    private long lastTick = Long.MIN_VALUE;

    private final ItemStackHandler inventory =
            new ItemStackHandler(TOTAL_SLOTS) {
                @Override
                public int getSlotLimit(int slot) {
                    return slot == SLOT_CHARGE ? 1 : super.getSlotLimit(slot);
                }

                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return switch (slot) {
                        case SLOT_GAS_IN ->
                                FluidUtil.getFluidHandler(Ecc2BridgePlatform.copyWithCount(stack, 1))
                                        .isPresent();
                        case SLOT_CHARGE ->
                                Ecc2EnergyHelper.acceptsCharge(
                                        stack, GasGeneratorEngine.OUTPUT_TIER, false);
                        default -> false;
                    };
                }

                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
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
                    if (slot != SLOT_GAS_IN && slot != SLOT_CHARGE) {
                        return stack;
                    }
                    return inventory.insertItem(slot, stack, simulate);
                }

                @Override
                public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                    if (slot != SLOT_GAS_OUT) {
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

    private final IFluidHandler gasFluidHandler =
            new IFluidHandler() {
                @Override
                public int getTanks() {
                    return 1;
                }

                @Override
                public @NotNull FluidStack getFluidInTank(int tank) {
                    if (engine.currentFuel() == null || engine.gasMb() <= 0) {
                        return FluidStack.EMPTY;
                    }
                    return new FluidStack(
                            GasContainerHelper.fluidForFuel(engine.currentFuel()), engine.gasMb());
                }

                @Override
                public int getTankCapacity(int tank) {
                    return GasGeneratorEngine.GAS_TANK_CAPACITY_MB;
                }

                @Override
                public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                    return GasContainerHelper.fuelOf(stack).isPresent();
                }

                @Override
                public int fill(FluidStack resource, FluidAction action) {
                    var fuel = GasContainerHelper.fuelOf(resource);
                    if (fuel.isEmpty()) {
                        return 0;
                    }
                    int accepted = engine.fillGas(fuel.get(), resource.getAmount(), action.simulate());
                    if (accepted > 0 && action.execute()) {
                        setChanged();
                    }
                    return accepted;
                }

                @Override
                public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                    if (engine.currentFuel() == null
                            || GasContainerHelper.fuelOf(resource)
                                    .map(f -> f != engine.currentFuel())
                                    .orElse(true)) {
                        return FluidStack.EMPTY;
                    }
                    return drain(resource.getAmount(), action);
                }

                @Override
                public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                    GasFuelSpec fuel = engine.currentFuel();
                    if (fuel == null) {
                        return FluidStack.EMPTY;
                    }
                    int drained = engine.drainGas(maxDrain, action.simulate());
                    if (drained > 0 && action.execute()) {
                        setChanged();
                    }
                    return drained > 0
                            ? new FluidStack(GasContainerHelper.fluidForFuel(fuel), drained)
                            : FluidStack.EMPTY;
                }
            };

    private final ContainerData syncData =
            new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case 0 -> (int) Math.floor(engine.storedEu());
                        case 1 -> engine.gasMb();
                        case 2 -> engine.currentFuel() == null ? -1 : engine.currentFuel().ordinal();
                        case 3 -> (int) Math.round(engine.lastTickGeneratedEu());
                        case 4 -> engine.isActive() ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 5;
                }
            };

    public GasGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(GasRegistry.GAS_GENERATOR_BE.get(), pos, state);
    }

    public GasGeneratorEngine engine() {
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

        if (GasContainerHelper.drainItemIntoTank(
                inventory,
                SLOT_GAS_IN,
                SLOT_GAS_OUT,
                GasGeneratorEngine.GAS_TANK_CAPACITY_MB - engine.gasMb(),
                fs ->
                        GasContainerHelper.fuelOf(fs)
                                .map(f -> engine.fillGas(f, fs.getAmount(), true) > 0)
                                .orElse(false),
                fs -> GasContainerHelper.fuelOf(fs)
                        .ifPresent(f -> engine.fillGas(f, fs.getAmount(), false)))) {
            setChanged();
        }

        double before = engine.storedEu();
        double generated =
                engine.tick(
                        (offer, tier) ->
                                Ecc2EnergyHelper.chargeStack(
                                        inventory.getStackInSlot(SLOT_CHARGE), offer, tier, false));
        if (generated > 0.0 || engine.storedEu() != before) {
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

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
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
        return FluidUtil.interactWithFluidHandler(player, hand, gasFluidHandler);
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
            return portCache.fluid(side, () -> gasFluidHandler).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public boolean ranThisTick(ServerLevel level) {
        return lastTick == level.getGameTime();
    }

    @Override
    public EnergyGraph.Ports energyPorts() {
        return Ecc2EnergyHelper.ALL_OUTPUT_PORTS;
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
    public int packetsPerTick() {
        return 1;
    }

    @Override
    public double draw(double amount) {
        double drawn = engine.drawEnergy(amount);
        if (drawn > 0.0) {
            setChanged();
        }
        return drawn;
    }

    @Override
    public double packetLimit() {
        return engine.packetSize();
    }

    @Override
    public EuBuffer.Reading bufferedEu() {
        return new EuBuffer.Reading(engine.storedEu(), GasGeneratorEngine.EU_CAPACITY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("GasMb", engine.gasMb());
        tag.putInt("FuelOrd", engine.currentFuel() == null ? -1 : engine.currentFuel().ordinal());
        tag.putDouble("PendingEu", engine.pendingEu());
        tag.putDouble("StoredEu", engine.storedEu());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
        }
        int ord = tag.contains("FuelOrd") ? tag.getInt("FuelOrd") : -1;
        GasFuelSpec[] vals = GasFuelSpec.values();
        GasFuelSpec fuel = ord >= 0 && ord < vals.length ? vals[ord] : null;
        engine.restoreState(
                fuel, tag.getInt("GasMb"), tag.getDouble("PendingEu"), tag.getDouble("StoredEu"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.gas_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new GasGeneratorMenu(containerId, playerInv, this, syncData);
    }
}
