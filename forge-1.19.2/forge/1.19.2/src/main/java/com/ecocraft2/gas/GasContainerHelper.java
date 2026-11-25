package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.ecocraft2.common.gas.GasFuelSpec;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Shared fluid-container filling/draining and gas-fuel resolution helpers for all 5 Gas & Biogas
 * machines.
 */
public final class GasContainerHelper {

    private GasContainerHelper() {}

    /** Resolves a Forge {@link FluidStack} to a {@link GasFuelSpec}, if combustible. */
    public static Optional<GasFuelSpec> fuelOf(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return GasFuelSpec.byFluidId(Ecc2BridgePlatform.fluidId(stack));
    }

    /** Returns the Forge {@link Fluid} registered for a {@link GasFuelSpec}. */
    public static Fluid fluidForFuel(GasFuelSpec spec) {
        return spec == null ? net.minecraft.world.level.material.Fluids.EMPTY : Ecc2BridgePlatform.findFluid(spec.fluidId());
    }

    /**
     * Drains up to {@code maxRoomMb} of an accepted fluid from a container in {@code inSlot},
     * placing the empty remainder container into {@code outSlot}.
     *
     * @return {@code true} if fluid was transferred
     */
    public static boolean drainItemIntoTank(
            ItemStackHandler inv,
            int inSlot,
            int outSlot,
            int maxRoomMb,
            Predicate<FluidStack> fluidFilter,
            java.util.function.Consumer<FluidStack> onDrained) {
        ItemStack inStack = inv.getStackInSlot(inSlot);
        if (inStack.isEmpty() || maxRoomMb <= 0) {
            return false;
        }
        ItemStack single = Ecc2BridgePlatform.copyWithCount(inStack, 1);
        var opt = FluidUtil.getFluidHandler(single).resolve();
        if (opt.isEmpty()) {
            return false;
        }
        var handler = opt.get();
        FluidStack sim = handler.drain(maxRoomMb, IFluidHandler.FluidAction.SIMULATE);
        if (sim.isEmpty() || !fluidFilter.test(sim)) {
            return false;
        }
        FluidStack drained = handler.drain(sim, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return false;
        }
        ItemStack remainder = handler.getContainer();
        if (!canFitSlot(inv, outSlot, remainder)) {
            return false;
        }
        inv.extractItem(inSlot, 1, false);
        insertIntoSlot(inv, outSlot, remainder);
        onDrained.accept(drained);
        return true;
    }

    /**
     * Fills a fluid container/cell in {@code inSlot} with up to {@code available.getAmount()} mB,
     * placing the filled container/named cell into {@code outSlot}.
     *
     * @return {@code true} if fluid was transferred
     */
    public static boolean fillItemFromTank(
            ItemStackHandler inv,
            int inSlot,
            int outSlot,
            FluidStack available,
            IntConsumer onFilledMb) {
        ItemStack inStack = inv.getStackInSlot(inSlot);
        if (inStack.isEmpty() || available.isEmpty()) {
            return false;
        }
        ItemStack single = Ecc2BridgePlatform.copyWithCount(inStack, 1);
        var opt = FluidUtil.getFluidHandler(single).resolve();
        if (opt.isEmpty()) {
            return false;
        }
        var handler = opt.get();
        int sim = handler.fill(available.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (sim <= 0) {
            return false;
        }
        int filled = handler.fill(available.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) {
            return false;
        }
        ItemStack filledContainer = handler.getContainer();
        if (!canFitSlot(inv, outSlot, filledContainer)) {
            return false;
        }
        inv.extractItem(inSlot, 1, false);
        insertIntoSlot(inv, outSlot, filledContainer);
        onFilledMb.accept(filled);
        return true;
    }

    public static boolean canFitSlot(ItemStackHandler inv, int slot, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return true;
        }
        ItemStack cur = inv.getStackInSlot(slot);
        if (cur.isEmpty()) {
            return item.getCount() <= item.getMaxStackSize();
        }
        return ItemStack.isSameItemSameTags(cur, item)
                && cur.getCount() + item.getCount() <= cur.getMaxStackSize();
    }

    public static void insertIntoSlot(ItemStackHandler inv, int slot, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        ItemStack cur = inv.getStackInSlot(slot);
        if (cur.isEmpty()) {
            inv.setStackInSlot(slot, item.copy());
        } else {
            cur.grow(item.getCount());
            inv.setStackInSlot(slot, cur);
        }
    }
}
