package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.gas.GasFuelSpec;
import com.ecocraft2.common.gas.GasFurnaceEngine;
import com.ecocraft2.common.gas.GasMachineOutcome;
import com.ecocraft2.config.Ecc2Config;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.access.MachinePortCache;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
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
 * Gas Furnace BlockEntity: smelts any vanilla smelting recipe using 50 mB Water Gas / Natural Gas
 * / Biogas per item (100 ticks base; overclocker accelerates while preserving exact 50 mB/item).
 */
public final class GasFurnaceBlockEntity extends BlockEntity
        implements GasMachineBlock.TickableMachine, MachineAccessTarget, MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_GAS_IN = 2;
    public static final int SLOT_GAS_OUT = 3;
    public static final int SLOT_UPGRADE_START = 4;
    public static final int TOTAL_SLOTS = 8;

    private final GasFurnaceEngine engine = new GasFurnaceEngine();
    private final MachinePortCache portCache = new MachinePortCache(this);

    private final ItemStackHandler inventory =
            new ItemStackHandler(TOTAL_SLOTS) {
                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return switch (slot) {
                        case SLOT_INPUT -> true;
                        case SLOT_GAS_IN ->
                                FluidUtil.getFluidHandler(Ecc2BridgePlatform.copyWithCount(stack, 1))
                                        .isPresent();
                        case 4, 5, 6, 7 -> Ecc2EnergyHelper.isUpgradeItem(stack);
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
                    if (slot != SLOT_INPUT && slot != SLOT_GAS_IN) {
                        return stack;
                    }
                    return inventory.insertItem(slot, stack, simulate);
                }

                @Override
                public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                    if (slot != SLOT_OUTPUT && slot != SLOT_GAS_OUT) {
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
                    return GasFurnaceEngine.GAS_TANK_CAPACITY_MB;
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
                        case 0 -> engine.gasMb();
                        case 1 -> engine.currentFuel() == null ? -1 : engine.currentFuel().ordinal();
                        case 2 -> engine.scaledProgress(24);
                        case 3 -> engine.isActive() ? 1 : 0;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 4;
                }
            };

    private static final int[] UPGRADE_IN_SLOTS = {SLOT_INPUT};
    private static final int[] UPGRADE_OUT_SLOTS = {SLOT_OUTPUT, SLOT_GAS_OUT};

    public GasFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(GasRegistry.GAS_FURNACE_BE.get(), pos, state);
        engine.configure(Ecc2Config.gasFurnaceGasPerItem(), Ecc2Config.gasFurnaceBaseTicks());
    }

    public GasFurnaceEngine engine() {
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
        engine.configure(Ecc2Config.gasFurnaceGasPerItem(), Ecc2Config.gasFurnaceBaseTicks());
        List<ItemStack> upgrades = collectUpgrades();
        engine.setOverclockers(Ecc2EnergyHelper.readUpgradeProfile(upgrades).overclockers());

        if (GasContainerHelper.drainItemIntoTank(
                inventory,
                SLOT_GAS_IN,
                SLOT_GAS_OUT,
                GasFurnaceEngine.GAS_TANK_CAPACITY_MB - engine.gasMb(),
                fs ->
                        GasContainerHelper.fuelOf(fs)
                                .map(f -> engine.fillGas(f, fs.getAmount(), true) > 0)
                                .orElse(false),
                fs -> GasContainerHelper.fuelOf(fs)
                        .ifPresent(f -> engine.fillGas(f, fs.getAmount(), false)))) {
            setChanged();
        }

        boolean runAllowed = GasUpgradeAutomation.isRunAllowed(serverLevel, worldPosition, upgrades);
        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        ItemStack result = ItemStack.EMPTY;
        if (runAllowed && !input.isEmpty()) {
            SimpleContainer probe = new SimpleContainer(1);
            probe.setItem(0, input);
            result =
                    serverLevel
                            .getRecipeManager()
                            .getRecipeFor(RecipeType.SMELTING, probe, serverLevel)
                            .map(r -> r.assemble(probe))
                            .orElse(ItemStack.EMPTY);
        }

        boolean hasRecipe = !result.isEmpty();
        boolean canAccept = hasRecipe && GasContainerHelper.canFitSlot(inventory, SLOT_OUTPUT, result);
        var outcome = engine.tick(hasRecipe, canAccept);
        if (outcome == GasMachineOutcome.COMPLETED && !result.isEmpty()) {
            inventory.extractItem(SLOT_INPUT, 1, false);
            GasContainerHelper.insertIntoSlot(inventory, SLOT_OUTPUT, result);
            setChanged();
        } else if (outcome == GasMachineOutcome.WORKING) {
            setChanged();
        }

        if ((serverLevel.getGameTime() & 3L) == 0L && !upgrades.isEmpty()) {
            boolean moved =
                    GasUpgradeAutomation.executeItemUpgrades(
                            serverLevel,
                            this,
                            inventory,
                            upgrades,
                            UPGRADE_IN_SLOTS,
                            UPGRADE_OUT_SLOTS,
                            stack -> {
                                SimpleContainer probe = new SimpleContainer(1);
                                probe.setItem(0, stack);
                                return serverLevel
                                        .getRecipeManager()
                                        .getRecipeFor(RecipeType.SMELTING, probe, serverLevel)
                                        .isPresent();
                            });
            moved |=
                    GasUpgradeAutomation.executeFluidUpgrades(
                            serverLevel,
                            this,
                            upgrades,
                            fs ->
                                    GasContainerHelper.fuelOf(fs)
                                            .map(f -> engine.fillGas(f, fs.getAmount(), true) > 0)
                                            .orElse(false),
                            () -> GasFurnaceEngine.GAS_TANK_CAPACITY_MB - engine.gasMb(),
                            fs ->
                                    GasContainerHelper.fuelOf(fs)
                                            .ifPresent(f -> engine.fillGas(f, fs.getAmount(), false)),
                            null,
                            null);
            if (moved) {
                setChanged();
            }
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

    private List<ItemStack> collectUpgrades() {
        List<ItemStack> upgrades = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            ItemStack u = inventory.getStackInSlot(SLOT_UPGRADE_START + i);
            if (!u.isEmpty()) {
                upgrades.add(u);
            }
        }
        return upgrades;
    }

    private void refreshUpgrades() {
        engine.setOverclockers(Ecc2EnergyHelper.readUpgradeProfile(collectUpgrades()).overclockers());
    }

    @Override
    public void setRemoved() {
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("GasMb", engine.gasMb());
        tag.putInt("FuelOrd", engine.currentFuel() == null ? -1 : engine.currentFuel().ordinal());
        tag.putInt("Progress", engine.progress());
        tag.putInt("GasSpent", engine.gasSpentOnCurrentItem());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
        }
        refreshUpgrades();
        int ord = tag.contains("FuelOrd") ? tag.getInt("FuelOrd") : -1;
        GasFuelSpec[] vals = GasFuelSpec.values();
        GasFuelSpec fuel = ord >= 0 && ord < vals.length ? vals[ord] : null;
        engine.restoreState(fuel, tag.getInt("GasMb"), tag.getInt("Progress"), tag.getInt("GasSpent"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecera.gas_furnace");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new GasFurnaceMenu(containerId, playerInv, this, syncData);
    }
}
