package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.gas.CultureIncubatorEngine;
import com.ecocraft2.common.gas.GasMachineOutcome;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 * Culture Incubator BlockEntity: {@code 1 bone meal or rotten flesh + 100 mB water + 200 EU / 100t
 * -> 100 mB ecc2:culture_medium} (LV Tier 1, 8,000 mB water & culture tanks, fills both universal
 * and basic IC2 cells into {@code culture_medium_cell} / {@code basic_culture_medium_cell}).
 */
public final class CultureIncubatorBlockEntity extends BlockEntity
        implements GasMachineBlock.TickableMachine,
                WorldPowerPort,
                EuBuffer,
                MachineAccessTarget,
                MenuProvider {

    public static final int SLOT_BONEMEAL = 0;
    public static final int SLOT_WATER_IN = 1;
    public static final int SLOT_WATER_OUT = 2;
    public static final int SLOT_CULTURE_IN = 3;
    public static final int SLOT_CULTURE_OUT = 4;
    public static final int SLOT_BATTERY = 5;
    public static final int SLOT_UPGRADE_START = 6;
    public static final int TOTAL_SLOTS = 10;

    private final CultureIncubatorEngine engine = new CultureIncubatorEngine();
    private final MachinePortCache portCache = new MachinePortCache(this);
    private long lastTick = Long.MIN_VALUE;

    private final ItemStackHandler inventory =
            new ItemStackHandler(TOTAL_SLOTS) {
                @Override
                public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                    return switch (slot) {
                        case SLOT_BONEMEAL -> isNutrientInput(stack);
                        case SLOT_WATER_IN, SLOT_CULTURE_IN ->
                                FluidUtil.getFluidHandler(Ecc2BridgePlatform.copyWithCount(stack, 1))
                                        .isPresent();
                        case SLOT_BATTERY -> Ecc2EnergyHelper.canDischarge(stack, engine.inputTier());
                        case 6, 7, 8, 9 -> Ecc2EnergyHelper.isUpgradeItem(stack);
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
                    if (slot != SLOT_BONEMEAL
                            && slot != SLOT_WATER_IN
                            && slot != SLOT_CULTURE_IN
                            && slot != SLOT_BATTERY) {
                        return stack;
                    }
                    return inventory.insertItem(slot, stack, simulate);
                }

                @Override
                public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                    if (slot != SLOT_WATER_OUT && slot != SLOT_CULTURE_OUT) {
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
                        return engine.waterMb() > 0
                                ? new FluidStack(Fluids.WATER, engine.waterMb())
                                : FluidStack.EMPTY;
                    }
                    return engine.cultureMb() > 0
                            ? new FluidStack(GasRegistry.CULTURE_MEDIUM.get(), engine.cultureMb())
                            : FluidStack.EMPTY;
                }

                @Override
                public int getTankCapacity(int tank) {
                    return tank == 0
                            ? CultureIncubatorEngine.WATER_TANK_CAPACITY_MB
                            : CultureIncubatorEngine.CULTURE_TANK_CAPACITY_MB;
                }

                @Override
                public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
                    return tank == 0
                            ? Ecc2BridgePlatform.isWaterFluid(stack)
                            : stack.getFluid() == GasRegistry.CULTURE_MEDIUM.get();
                }

                @Override
                public int fill(FluidStack resource, FluidAction action) {
                    if (resource.isEmpty() || !Ecc2BridgePlatform.isWaterFluid(resource)) {
                        return 0;
                    }
                    int accepted = engine.fillWater(resource.getAmount(), action.simulate());
                    if (accepted > 0 && action.execute()) {
                        setChanged();
                    }
                    return accepted;
                }

                @Override
                public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                    if (resource.isEmpty()
                            || resource.getFluid() != GasRegistry.CULTURE_MEDIUM.get()) {
                        return FluidStack.EMPTY;
                    }
                    return drain(resource.getAmount(), action);
                }

                @Override
                public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
                    int drained = engine.drainCulture(maxDrain, action.simulate());
                    if (drained > 0 && action.execute()) {
                        setChanged();
                    }
                    return drained > 0
                            ? new FluidStack(GasRegistry.CULTURE_MEDIUM.get(), drained)
                            : FluidStack.EMPTY;
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
                        case 4 -> engine.waterMb();
                        case 5 -> engine.cultureMb();
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

    private static final int[] UPGRADE_IN_SLOTS = {SLOT_BONEMEAL};
    private static final int[] UPGRADE_OUT_SLOTS = {SLOT_WATER_OUT, SLOT_CULTURE_OUT};

    public CultureIncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(GasRegistry.CULTURE_INCUBATOR_BE.get(), pos, state);
    }

    /**
     * Returns {@code true} if {@code stack} is an accepted nutrient input ({@code #ecc2:culture_nutrients},
     * Bone Meal, or Rotten Flesh).
     */
    public static boolean isNutrientInput(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (Ecc2BridgePlatform.hasItemTag(stack, Ecc2Ids.TAG_CULTURE_NUTRIENTS)
                        || stack.is(Items.BONE_MEAL)
                        || stack.is(Items.ROTTEN_FLESH));
    }

    public CultureIncubatorEngine engine() {
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
        List<ItemStack> upgrades = collectUpgrades();
        engine.setUpgrades(Ecc2EnergyHelper.readUpgradeProfile(upgrades));

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
                SLOT_WATER_IN,
                SLOT_WATER_OUT,
                CultureIncubatorEngine.WATER_TANK_CAPACITY_MB - engine.waterMb(),
                Ecc2BridgePlatform::isWaterFluid,
                fs -> engine.fillWater(fs.getAmount(), false))) {
            setChanged();
        }
        if (engine.cultureMb() > 0
                && GasContainerHelper.fillItemFromTank(
                        inventory,
                        SLOT_CULTURE_IN,
                        SLOT_CULTURE_OUT,
                        new FluidStack(GasRegistry.CULTURE_MEDIUM.get(), engine.cultureMb()),
                        filled -> engine.drainCulture(filled, false))) {
            setChanged();
        }

        boolean hasNutrient =
                GasUpgradeAutomation.isRunAllowed(serverLevel, worldPosition, upgrades)
                        && isNutrientInput(inventory.getStackInSlot(SLOT_BONEMEAL));
        var outcome = engine.tick(hasNutrient);
        if (outcome == GasMachineOutcome.COMPLETED) {
            inventory.extractItem(SLOT_BONEMEAL, 1, false);
            setChanged();
        } else if (outcome == GasMachineOutcome.WORKING) {
            setChanged();
        }

        if ((lastTick & 3L) == 0L && !upgrades.isEmpty()) {
            boolean moved =
                    GasUpgradeAutomation.executeItemUpgrades(
                            serverLevel,
                            this,
                            inventory,
                            upgrades,
                            UPGRADE_IN_SLOTS,
                            UPGRADE_OUT_SLOTS,
                            CultureIncubatorBlockEntity::isNutrientInput);
            moved |=
                    GasUpgradeAutomation.executeFluidUpgrades(
                            serverLevel,
                            this,
                            upgrades,
                            Ecc2BridgePlatform::isWaterFluid,
                            () -> CultureIncubatorEngine.WATER_TANK_CAPACITY_MB - engine.waterMb(),
                            fs -> engine.fillWater(fs.getAmount(), false),
                            () ->
                                    engine.cultureMb() > 0
                                            ? new FluidStack(
                                                    GasRegistry.CULTURE_MEDIUM.get(),
                                                    engine.cultureMb())
                                            : FluidStack.EMPTY,
                            drained -> engine.drainCulture(drained, false));
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
        engine.setUpgrades(Ecc2EnergyHelper.readUpgradeProfile(collectUpgrades()));
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
        tag.putInt("WaterMb", engine.waterMb());
        tag.putInt("CultureMb", engine.cultureMb());
        tag.putInt("Progress", engine.progress());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
        }
        refreshUpgrades();
        engine.setStoredEu(tag.getDouble("StoredEu"));
        engine.setWaterMb(tag.getInt("WaterMb"));
        engine.setCultureMb(tag.getInt("CultureMb"));
        engine.setProgress(tag.getInt("Progress"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.ecc2.culture_incubator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new CultureIncubatorMenu(containerId, playerInv, this, syncData);
    }
}
