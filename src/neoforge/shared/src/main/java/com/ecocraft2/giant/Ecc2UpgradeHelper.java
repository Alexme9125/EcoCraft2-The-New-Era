package com.ecocraft2.giant;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.giant.GiantUpgradeProfile;
import com.industrialage.ia2.access.MachineAccess;
import com.industrialage.ia2.content.ProcessingUpgrades;
import com.industrialage.ia2.world.MachineAutomation;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared IC2NE upgrade parsing and routed item/fluid automation executor for EcoCraft 2's Giant
 * Machines and Gas/Biogas Machines.
 */
public final class Ecc2UpgradeHelper {

    public static final String OVERCLOCKER = "ic2:overclocker_upgrade";
    public static final String TRANSFORMER = "ic2:transformer_upgrade";
    public static final String ENERGY_STORAGE = "ic2:energy_storage_upgrade";
    public static final String EJECTOR = "ic2:ejector_upgrade";
    public static final String ADVANCED_EJECTOR = "ic2:advanced_ejector_upgrade";
    public static final String PULLING = "ic2:pulling_upgrade";
    public static final String ADVANCED_PULLING = "ic2:advanced_pulling_upgrade";
    public static final String FLUID_EJECTOR = "ic2:fluid_ejector_upgrade";
    public static final String FLUID_PULLING = "ic2:fluid_pulling_upgrade";
    public static final String REDSTONE_INVERTER = "ic2:redstone_inverter_upgrade";
    public static final String PV_POWER = Ecc2Ids.PV_POWER_UPGRADE_ID;

    /** Represents one physical world port face through which upgrades may push/pull. */
    public record PortEndpoint(
            BlockEntity hostEntity,
            BlockPos pos,
            Direction outwardFace,
            boolean allowsInput,
            boolean allowsOutput) {}

    private Ecc2UpgradeHelper() {}

    /** Returns {@code true} if {@code stack} is an accepted IC2 machine upgrade. */
    public static boolean isAcceptedUpgrade(
            ItemStack stack, boolean allowElectrical, boolean allowFluid) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return switch (GiantGasPlatform.itemId(stack)) {
            case OVERCLOCKER,
                    EJECTOR,
                    ADVANCED_EJECTOR,
                    PULLING,
                    ADVANCED_PULLING,
                    REDSTONE_INVERTER ->
                    true;
            case TRANSFORMER, ENERGY_STORAGE -> allowElectrical;
            case FLUID_EJECTOR, FLUID_PULLING -> allowFluid;
            default -> false;
        };
    }

    /** Counts Overclocker, Transformer, and Energy Storage upgrades across {@code upgradeSlots}. */
    public static GiantUpgradeProfile readProfile(List<ItemStack> upgradeSlots) {
        int oc = 0;
        int tf = 0;
        int es = 0;
        for (ItemStack stack : upgradeSlots) {
            if (stack.isEmpty()) {
                continue;
            }
            switch (GiantGasPlatform.itemId(stack)) {
                case OVERCLOCKER -> oc += stack.getCount();
                case TRANSFORMER -> tf += stack.getCount();
                case ENERGY_STORAGE -> es += stack.getCount();
                default -> {}
            }
        }
        return new GiantUpgradeProfile(oc, tf, es);
    }

    /** Returns {@code true} if {@code stack} is a Water Gas Machine PV Power Upgrade. */
    public static boolean isPvPowerUpgrade(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && PV_POWER.equals(GiantGasPlatform.itemId(stack));
    }

    /** Counts installed PV Power Upgrades across {@code upgradeSlots}. */
    public static int countPvPowerUpgrades(List<ItemStack> upgradeSlots) {
        int total = 0;
        for (ItemStack stack : upgradeSlots) {
            if (isPvPowerUpgrade(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Returns {@code true} if at least one Redstone Inverter Upgrade is installed. */
    public static boolean hasRedstoneInverter(List<ItemStack> upgradeSlots) {
        for (ItemStack stack : upgradeSlots) {
            if (!stack.isEmpty() && REDSTONE_INVERTER.equals(GiantGasPlatform.itemId(stack))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs item ejector and pulling upgrades across the specified endpoints (respecting port
     * permissions and IC2NE opposing pipe nozzle backoff via {@link MachineAccess#allowsUpgrade}).
     */
    public static boolean executeItemUpgrades(
            ServerLevel level,
            List<ItemStack> upgradeSlots,
            List<PortEndpoint> endpoints,
            Container inventory,
            int[] inputSlots,
            int[] outputSlots,
            Predicate<ItemStack> validInput) {
        boolean changed = false;
        for (ItemStack upgrade : upgradeSlots) {
            if (upgrade.isEmpty()) {
                continue;
            }
            String id = GiantGasPlatform.itemId(upgrade);
            boolean eject = EJECTOR.equals(id) || ADVANCED_EJECTOR.equals(id);
            boolean pull = PULLING.equals(id) || ADVANCED_PULLING.equals(id);
            if (!eject && !pull) {
                continue;
            }
            List<Direction> allowedFaces = MachineAutomation.faces(upgrade);
            int limit = ProcessingUpgrades.transferLimit(upgrade.getCount());
            for (PortEndpoint ep : endpoints) {
                if (!allowedFaces.contains(ep.outwardFace())) {
                    continue;
                }
                if (eject && !ep.allowsOutput()) {
                    continue;
                }
                if (pull && !ep.allowsInput()) {
                    continue;
                }
                if (!MachineAccess.allowsUpgrade(ep.hostEntity(), ep.outwardFace(), eject, false)) {
                    continue;
                }
                BlockPos neighbor = ep.pos().relative(ep.outwardFace());
                Direction targetFace = ep.outwardFace().getOpposite();
                if (eject) {
                    changed |= ejectItems(level, neighbor, targetFace, inventory, outputSlots, limit);
                } else {
                    changed |=
                            pullItems(
                                    level,
                                    neighbor,
                                    targetFace,
                                    inventory,
                                    inputSlots,
                                    limit,
                                    validInput);
                }
            }
        }
        return changed;
    }

    /**
     * Runs fluid ejector and pulling upgrades across the specified endpoints, honoring port
     * permissions and opposing fluid pipe nozzle backoff.
     */
    public static boolean executeFluidUpgrades(
            ServerLevel level,
            List<ItemStack> upgradeSlots,
            List<PortEndpoint> endpoints,
            Predicate<String> pullFluidFilter,
            BiFunction<String, Integer, Integer> insertPulledFluid,
            IntSupplier pullRoomSupplier,
            Supplier<String> ejectFluidIdSupplier,
            IntSupplier ejectAvailableSupplier,
            IntConsumer consumeEjectedFluid) {
        boolean changed = false;
        for (ItemStack upgrade : upgradeSlots) {
            if (upgrade.isEmpty()) {
                continue;
            }
            String id = GiantGasPlatform.itemId(upgrade);
            boolean eject = FLUID_EJECTOR.equals(id);
            boolean pull = FLUID_PULLING.equals(id);
            if (!eject && !pull) {
                continue;
            }
            List<Direction> allowedFaces = MachineAutomation.faces(upgrade);
            int rateMb = Math.min(8_000, 1_000 * Math.max(1, upgrade.getCount()));
            for (PortEndpoint ep : endpoints) {
                if (!allowedFaces.contains(ep.outwardFace())) {
                    continue;
                }
                if (eject && !ep.allowsOutput()) {
                    continue;
                }
                if (pull && !ep.allowsInput()) {
                    continue;
                }
                if (!MachineAccess.allowsUpgrade(ep.hostEntity(), ep.outwardFace(), eject, true)) {
                    continue;
                }
                BlockPos neighbor = ep.pos().relative(ep.outwardFace());
                Direction targetFace = ep.outwardFace().getOpposite();
                if (eject && ejectFluidIdSupplier != null && ejectAvailableSupplier != null) {
                    String fid = ejectFluidIdSupplier.get();
                    int avail = ejectAvailableSupplier.getAsInt();
                    if (fid != null && !fid.isEmpty() && avail > 0) {
                        int pushed =
                                GiantGasPlatform.pushFluidToNeighbor(
                                        level, neighbor, targetFace, fid, Math.min(avail, rateMb));
                        if (pushed > 0) {
                            consumeEjectedFluid.accept(pushed);
                            changed = true;
                        }
                    }
                } else if (pull && pullFluidFilter != null && pullRoomSupplier != null) {
                    int room = pullRoomSupplier.getAsInt();
                    if (room > 0) {
                        var pulled =
                                GiantGasPlatform.pullFluidFromNeighbor(
                                        level,
                                        neighbor,
                                        targetFace,
                                        pullFluidFilter,
                                        Math.min(room, rateMb));
                        if (pulled != null && pulled.amountMb() > 0) {
                            int accepted =
                                    insertPulledFluid.apply(pulled.fluidId(), pulled.amountMb());
                            if (accepted > 0) {
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean ejectItems(
            ServerLevel level,
            BlockPos neighbor,
            Direction targetFace,
            Container inventory,
            int[] outputSlots,
            int limit) {
        int remaining = limit;
        boolean movedAny = false;
        for (int slot : outputSlots) {
            if (remaining <= 0) {
                break;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack offered = stack.copyWithCount(Math.min(stack.getCount(), remaining));
            int moved = GiantGasPlatform.pushItemToNeighbor(level, neighbor, targetFace, offered);
            if (moved > 0) {
                inventory.removeItem(slot, moved);
                remaining -= moved;
                movedAny = true;
            }
        }
        return movedAny;
    }

    private static boolean pullItems(
            ServerLevel level,
            BlockPos neighbor,
            Direction targetFace,
            Container inventory,
            int[] inputSlots,
            int limit,
            Predicate<ItemStack> validInput) {
        if (inputSlots.length == 0 || limit <= 0) {
            return false;
        }
        // Collect partially filled slots sorted by ascending stack size so multi-lane machines
        // top up existing matching lanes evenly, while falling back to any empty lane so a single
        // partial lane never starves other empty lanes when different valid inputs exist.
        int[] partialSlots = new int[inputSlots.length];
        int partialCount = 0;
        int firstEmptySlot = -1;
        for (int slot : inputSlots) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                if (firstEmptySlot < 0) {
                    firstEmptySlot = slot;
                }
            } else {
                int cap = Math.min(inventory.getMaxStackSize(), existing.getMaxStackSize());
                if (existing.getCount() < cap) {
                    partialSlots[partialCount++] = slot;
                }
            }
        }
        // Insertion sort partial slots by ascending count.
        for (int i = 1; i < partialCount; i++) {
            int keySlot = partialSlots[i];
            int keyCount = inventory.getItem(keySlot).getCount();
            int j = i - 1;
            while (j >= 0 && inventory.getItem(partialSlots[j]).getCount() > keyCount) {
                partialSlots[j + 1] = partialSlots[j];
                j--;
            }
            partialSlots[j + 1] = keySlot;
        }

        for (int i = 0; i < partialCount; i++) {
            int slot = partialSlots[i];
            ItemStack existing = inventory.getItem(slot);
            int space =
                    Math.min(inventory.getMaxStackSize(), existing.getMaxStackSize())
                            - existing.getCount();
            if (space <= 0) {
                continue;
            }
            Predicate<ItemStack> matchExisting =
                    candidate ->
                            validInput.test(candidate)
                                    && ItemStack.isSameItemSameComponents(candidate, existing);
            ItemStack pulled =
                    GiantGasPlatform.pullItemFromNeighbor(
                            level, neighbor, targetFace, Math.min(space, limit), matchExisting);
            if (!pulled.isEmpty()) {
                existing.grow(pulled.getCount());
                inventory.setChanged();
                return true;
            }
        }

        if (firstEmptySlot < 0) {
            return false;
        }
        int space = inventory.getMaxStackSize();
        if (space <= 0) {
            return false;
        }
        ItemStack pulled =
                GiantGasPlatform.pullItemFromNeighbor(
                        level, neighbor, targetFace, Math.min(space, limit), validInput);
        if (pulled.isEmpty()) {
            return false;
        }
        inventory.setItem(firstEmptySlot, pulled);
        return true;
    }

    /** Convenience helper checking a custom predicate on (slot, stack) for container insertion. */
    public static boolean canInsertIntoSlot(
            Container inv, int slot, ItemStack toInsert, BiPredicate<Integer, ItemStack> matcher) {
        if (toInsert.isEmpty() || !matcher.test(slot, toInsert)) {
            return false;
        }
        ItemStack current = inv.getItem(slot);
        if (current.isEmpty()) {
            return toInsert.getCount() <= toInsert.getMaxStackSize();
        }
        return ItemStack.isSameItemSameComponents(current, toInsert)
                && current.getCount() + toInsert.getCount() <= current.getMaxStackSize();
    }
}
