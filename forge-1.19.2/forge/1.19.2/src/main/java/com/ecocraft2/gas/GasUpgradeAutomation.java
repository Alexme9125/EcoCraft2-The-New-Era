package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.bridge.Ecc2EnergyHelper;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.content.ProcessingUpgrades;
import com.industrialage.ia2.world.MachineAutomation;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Shared IC2NE item/fluid upgrade automation helper for Forge 1.19.2 single-block Gas & Biogas
 * machines, honoring {@link MachineAccess}, {@link MachineAutomation#faces}, and {@link
 * ProcessingUpgrades#transferLimit}.
 */
public final class GasUpgradeAutomation {

    private static final int FLUID_TRANSFER_PER_OP_MB = 1_000;

    private GasUpgradeAutomation() {}

    /**
     * Evaluates whether a machine with {@code upgrades} is allowed to run under current redstone
     * signal. Without a redstone inverter upgrade, always returns {@code true}; with one, requires
     * a redstone signal.
     */
    public static boolean isRunAllowed(
            ServerLevel level, BlockPos pos, List<ItemStack> upgrades) {
        return !Ecc2EnergyHelper.hasRedstoneInverter(upgrades) || level.hasNeighborSignal(pos);
    }

    /** Executes IC2NE item ejector/puller upgrades (standard & advanced) on a single-block machine. */
    public static boolean executeItemUpgrades(
            ServerLevel level,
            BlockEntity be,
            ItemStackHandler inventory,
            List<ItemStack> upgrades,
            int[] inputSlots,
            int[] outputSlots,
            Predicate<ItemStack> inputFilter) {
        if (upgrades.isEmpty()) {
            return false;
        }
        boolean changed = false;
        BlockPos pos = be.getBlockPos();
        for (ItemStack upgrade : upgrades) {
            if (upgrade.isEmpty()) {
                continue;
            }
            String id = Ecc2BridgePlatform.itemId(upgrade);
            boolean isEject =
                    "mhera:ejector_upgrade".equals(id) || "mhera:advanced_ejector_upgrade".equals(id);
            boolean isPull =
                    "mhera:pulling_upgrade".equals(id) || "mhera:advanced_pulling_upgrade".equals(id);
            if (!isEject && !isPull) {
                continue;
            }
            int limit = Math.max(1, ProcessingUpgrades.transferLimit(upgrade.getCount()));
            List<Direction> faces = MachineAutomation.faces(upgrade);
            for (Direction face : faces) {
                if (!MachineAccess.allowsUpgrade(be, face, isPull, false)) {
                    continue;
                }
                BlockPos neighborPos = pos.relative(face);
                if (!level.hasChunkAt(neighborPos)) {
                    continue;
                }
                BlockEntity neighbor = level.getBlockEntity(neighborPos);
                if (neighbor == null) {
                    continue;
                }
                var capOpt =
                        neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, face.getOpposite())
                                .resolve();
                if (capOpt.isEmpty()) {
                    continue;
                }
                IItemHandler remote = capOpt.get();
                if (isEject && ejectItems(inventory, outputSlots, remote, limit)) {
                    changed = true;
                    break;
                }
                if (isPull && pullItems(inventory, inputSlots, remote, limit, inputFilter)) {
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    /** Executes IC2NE fluid ejector/puller upgrades on a single-block machine. */
    public static boolean executeFluidUpgrades(
            ServerLevel level,
            BlockEntity be,
            List<ItemStack> upgrades,
            @Nullable Predicate<FluidStack> pullFilter,
            @Nullable IntSupplier pullRoomSupplier,
            @Nullable java.util.function.Consumer<FluidStack> onPulled,
            @Nullable Supplier<FluidStack> ejectSourceSupplier,
            @Nullable java.util.function.IntConsumer onEjectedMb) {
        if (upgrades.isEmpty()) {
            return false;
        }
        boolean changed = false;
        BlockPos pos = be.getBlockPos();
        for (ItemStack upgrade : upgrades) {
            if (upgrade.isEmpty()) {
                continue;
            }
            String id = Ecc2BridgePlatform.itemId(upgrade);
            boolean isFluidEject = "mhera:fluid_ejector_upgrade".equals(id);
            boolean isFluidPull = "mhera:fluid_pulling_upgrade".equals(id);
            if (!isFluidEject && !isFluidPull) {
                continue;
            }
            int budget = Math.max(FLUID_TRANSFER_PER_OP_MB, upgrade.getCount() * FLUID_TRANSFER_PER_OP_MB);
            List<Direction> faces = MachineAutomation.faces(upgrade);
            for (Direction face : faces) {
                if (!MachineAccess.allowsUpgrade(be, face, isFluidPull, true)) {
                    continue;
                }
                BlockPos neighborPos = pos.relative(face);
                if (!level.hasChunkAt(neighborPos)) {
                    continue;
                }
                BlockEntity neighbor = level.getBlockEntity(neighborPos);
                if (neighbor == null) {
                    continue;
                }
                var capOpt =
                        neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, face.getOpposite())
                                .resolve();
                if (capOpt.isEmpty()) {
                    continue;
                }
                IFluidHandler remote = capOpt.get();
                if (isFluidEject && ejectSourceSupplier != null && onEjectedMb != null) {
                    FluidStack avail = ejectSourceSupplier.get();
                    if (!avail.isEmpty()) {
                        FluidStack offer = avail.copy();
                        offer.setAmount(Math.min(offer.getAmount(), budget));
                        int filled = remote.fill(offer, IFluidHandler.FluidAction.EXECUTE);
                        if (filled > 0) {
                            onEjectedMb.accept(filled);
                            changed = true;
                            break;
                        }
                    }
                }
                if (isFluidPull
                        && pullFilter != null
                        && pullRoomSupplier != null
                        && onPulled != null) {
                    int room = Math.min(budget, pullRoomSupplier.getAsInt());
                    if (room > 0) {
                        FluidStack sim = remote.drain(room, IFluidHandler.FluidAction.SIMULATE);
                        if (!sim.isEmpty() && pullFilter.test(sim)) {
                            FluidStack drained =
                                    remote.drain(sim, IFluidHandler.FluidAction.EXECUTE);
                            if (!drained.isEmpty()) {
                                onPulled.accept(drained);
                                changed = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    /** Ejects up to {@code maxTransfer} items from {@code outputSlots} into {@code target}. */
    public static boolean ejectItems(
            ItemStackHandler inventory, int[] outputSlots, IItemHandler target, int maxTransfer) {
        for (int slot : outputSlots) {
            ItemStack cur = inventory.getStackInSlot(slot);
            if (cur.isEmpty()) {
                continue;
            }
            int moveCount = Math.min(cur.getCount(), maxTransfer);
            ItemStack offer = Ecc2BridgePlatform.copyWithCount(cur, moveCount);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, offer, false);
            int moved = moveCount - remainder.getCount();
            if (moved > 0) {
                inventory.extractItem(slot, moved, false);
                return true;
            }
        }
        return false;
    }

    /**
     * Pulls up to {@code maxTransfer} valid items from {@code source} into {@code inputSlots},
     * prioritizing partially filled slots (smallest count first) and falling back to empty slots so
     * partial lanes never starve empty lanes.
     */
    public static boolean pullItems(
            ItemStackHandler inventory,
            int[] inputSlots,
            IItemHandler source,
            int maxTransfer,
            Predicate<ItemStack> filter) {
        if (inputSlots.length == 0 || maxTransfer <= 0) {
            return false;
        }
        for (int s = 0; s < source.getSlots(); s++) {
            ItemStack probe = source.extractItem(s, maxTransfer, true);
            if (probe.isEmpty() || !filter.test(probe)) {
                continue;
            }
            // Collect partially filled compatible slots sorted ascending by count, then empty slots
            int bestSlot = -1;
            int bestCount = Integer.MAX_VALUE;
            int firstEmpty = -1;
            for (int slot : inputSlots) {
                ItemStack existing = inventory.getStackInSlot(slot);
                if (existing.isEmpty()) {
                    if (firstEmpty < 0) {
                        firstEmpty = slot;
                    }
                } else if (ItemStack.isSameItemSameTags(existing, probe)
                        && existing.getCount() < existing.getMaxStackSize()
                        && existing.getCount() < bestCount) {
                    ItemStack simRem = inventory.insertItem(slot, probe, true);
                    if (simRem.getCount() < probe.getCount()) {
                        bestSlot = slot;
                        bestCount = existing.getCount();
                    }
                }
            }
            int targetSlot = bestSlot >= 0 ? bestSlot : firstEmpty;
            if (targetSlot < 0) {
                continue;
            }
            ItemStack simRem = inventory.insertItem(targetSlot, probe, true);
            int accepted = probe.getCount() - simRem.getCount();
            if (accepted > 0) {
                ItemStack pulled = source.extractItem(s, accepted, false);
                if (!pulled.isEmpty()) {
                    inventory.insertItem(targetSlot, pulled, false);
                    return true;
                }
            }
        }
        return false;
    }
}
