package com.ecocraft2.gas;

import com.ecocraft2.giant.Ecc2BlockEntityBase;
import com.ecocraft2.giant.Ecc2CapabilityProvider;
import com.ecocraft2.giant.Ecc2FluidTankView;
import com.ecocraft2.giant.Ecc2UpgradeHelper;
import com.ecocraft2.giant.GiantGasPlatform;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.access.MachineAccessTarget;
import com.industrialage.ia2.access.ProcessingSlots;
import com.industrialage.ia2.logistics.FluidContents;
import com.industrialage.ia2.power.WorldEnergy;
import com.industrialage.ia2.power.WorldPowerPort;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared BlockEntity base for EcoCraft 2's 5 gas and biogas machines: handles inventory storage,
 * clean drop on break, IC2NE {@link WorldEnergy} attach/detach, {@link MachineAccessTarget}, fluid
 * container drain/fill slots, and 6-side automation endpoints.
 */
public abstract class GasMachineBlockEntityBase extends Ecc2BlockEntityBase
        implements MachineAccessTarget, Ecc2CapabilityProvider, ProcessingSlots, MenuProvider {

    protected final NonNullList<ItemStack> items;
    protected int lastTick = Integer.MIN_VALUE;

    protected GasMachineBlockEntityBase(
            BlockEntityType<?> type, BlockPos pos, BlockState state, int inventorySize) {
        super(type, pos, state);
        this.items = NonNullList.withSize(inventorySize, ItemStack.EMPTY);
    }

    /** Executes one server tick for this machine. */
    protected abstract void serverTick(ServerLevel level, BlockState state);

    /** Returns fluid tank summaries for Jade / TOP / IC2 inspection. */
    public abstract List<FluidContents> fluidContents();

    @Override
    public void onLoad() {
        super.onLoad();
        if (this instanceof WorldPowerPort && getLevel() instanceof ServerLevel) {
            WorldEnergy.connect(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this instanceof WorldPowerPort) {
            WorldEnergy.disconnect(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this instanceof WorldPowerPort) {
            WorldEnergy.disconnect(this);
        }
    }

    @Override
    protected void onRemovedFromWorld(ServerLevel level, BlockPos pos, BlockState oldState) {
        Containers.dropContents(level, pos, this);
        clearContent();
    }

    @Override
    public boolean exposesMachineAccess() {
        return true;
    }

    @Override
    public boolean exchangeHeldFluid(Player player, InteractionHand hand, Direction side) {
        var view = fluidCapabilityTank(side);
        if (view == null || !MachineAccess.connects(this, side)) {
            return false;
        }
        return GiantGasPlatform.interactPlayerFluid(this, player, hand, side, view);
    }

    @Override
    public ProcessingSlots itemCapabilitySlots(Direction side) {
        return this;
    }

    protected void syncActiveState(ServerLevel level, BlockState state, boolean active) {
        if (!isRemoved()
                && state.hasProperty(GasMachineBlock.ACTIVE)
                && state.getValue(GasMachineBlock.ACTIVE) != active) {
            level.setBlock(
                    worldPosition,
                    state.setValue(GasMachineBlock.ACTIVE, active),
                    Block.UPDATE_CLIENTS);
        }
    }

    protected List<Ecc2UpgradeHelper.PortEndpoint> sixSideEndpoints() {
        var list = new ArrayList<Ecc2UpgradeHelper.PortEndpoint>(6);
        for (Direction dir : Direction.values()) {
            list.add(
                    new Ecc2UpgradeHelper.PortEndpoint(
                            this,
                            worldPosition,
                            dir,
                            MachineAccess.allowsInput(this, dir),
                            MachineAccess.allowsOutput(this, dir)));
        }
        return list;
    }

    protected boolean canMergeIntoSlot(int slot, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return true;
        }
        ItemStack existing = items.get(slot);
        if (existing.isEmpty()) {
            return incoming.getCount() <= incoming.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(existing, incoming)
                && existing.getCount() + incoming.getCount() <= existing.getMaxStackSize();
    }

    protected void mergeIntoSlot(int slot, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return;
        }
        ItemStack existing = items.get(slot);
        if (existing.isEmpty()) {
            items.set(slot, incoming.copy());
        } else {
            existing.grow(incoming.getCount());
        }
    }

    /** Drains a fluid container from {@code inSlot} into an input tank, returning the empty container to {@code outSlot}. */
    protected boolean drainContainerSlot(
            int inSlot,
            int outSlot,
            Predicate<String> fluidFilter,
            int maxSpaceMb,
            BiConsumer<String, Integer> tankFill) {
        ItemStack in = items.get(inSlot);
        if (in.isEmpty() || maxSpaceMb <= 0) {
            return false;
        }
        var drained = GiantGasPlatform.drainFluidContainer(in, fluidFilter, maxSpaceMb);
        if (drained == null || drained.amountMb() <= 0) {
            return false;
        }
        ItemStack remainder = drained.resultingItem();
        if (!remainder.isEmpty() && !canMergeIntoSlot(outSlot, remainder)) {
            return false;
        }
        tankFill.accept(drained.fluidId(), drained.amountMb());
        in.shrink(1);
        if (!remainder.isEmpty()) {
            mergeIntoSlot(outSlot, remainder);
        }
        return true;
    }

    /** Fills an empty fluid container from {@code inSlot} using an output tank, moving the filled item to {@code outSlot}. */
    protected boolean fillContainerSlot(
            int inSlot,
            int outSlot,
            String fluidId,
            int availableMb,
            IntConsumer tankDrain) {
        ItemStack in = items.get(inSlot);
        if (in.isEmpty() || fluidId == null || fluidId.isEmpty() || availableMb <= 0) {
            return false;
        }
        var filled = GiantGasPlatform.fillFluidContainer(in, fluidId, availableMb);
        if (filled == null || filled.amountMb() <= 0) {
            return false;
        }
        ItemStack result = filled.resultingItem();
        if (result.isEmpty() || !canMergeIntoSlot(outSlot, result)) {
            return false;
        }
        tankDrain.accept(filled.amountMb());
        in.shrink(1);
        mergeIntoSlot(outSlot, result);
        return true;
    }

    // ---- Container ----

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override
    public int automationLimit(int slot, ItemStack stack) {
        return stack.getMaxStackSize();
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }
}
