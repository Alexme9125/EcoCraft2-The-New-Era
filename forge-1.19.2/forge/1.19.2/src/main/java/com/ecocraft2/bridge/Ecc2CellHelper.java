package com.ecocraft2.bridge;

import com.industrialage.ia2.logistics.CellFamily;
import com.industrialage.ia2.logistics.CellItem;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge helper giving EcoCraft 2 named fluid cells and Mixins clean access to IC2NE's
 * package-private {@link CellItem} and {@link CellFamily} helpers.
 */
public final class Ecc2CellHelper {

    private static final Logger LOG = LoggerFactory.getLogger(Ecc2CellHelper.class);

    private static volatile MethodHandle contentsHandle;
    private static volatile MethodHandle withContentsHandle;
    private static volatile MethodHandle familyOfHandle;
    private static volatile MethodHandle familyAdmitsHandle;
    private static volatile boolean bound;
    private static volatile boolean failed;

    private Ecc2CellHelper() {}

    /** Reads the {@link FluidStack} contained in any IC2NE or EcoCraft 2 fluid cell. */
    public static FluidStack readContents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        if (ensureBound()) {
            try {
                return (FluidStack) contentsHandle.invoke(stack);
            } catch (Throwable failure) {
                LOG.error("Failed to invoke IC2NE CellItem.contents", failure);
            }
        }
        String fluidId = Ecc2ExternalRecipes.fluidForNamedCell(Ecc2BridgePlatform.itemId(stack));
        return fluidId == null
                ? FluidStack.EMPTY
                : Ecc2BridgePlatform.fluidStack(fluidId, CellFamily.CAPACITY);
    }

    /** Updates fluid contents on any IC2NE or EcoCraft 2 cell, retyping to/from named cells. */
    public static ItemStack withContents(ItemStack stack, FluidStack fluid) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (ensureBound()) {
            try {
                return (ItemStack) withContentsHandle.invoke(stack, fluid);
            } catch (Throwable failure) {
                LOG.error("Failed to invoke IC2NE CellItem.withContents", failure);
            }
        }
        return stack.copy();
    }

    /** Returns whether the cell stack's {@link CellFamily} admits {@code fluid}. */
    public static boolean cellAdmits(ItemStack cellStack, FluidStack fluid) {
        if (cellStack == null || cellStack.isEmpty() || fluid == null || fluid.isEmpty()) {
            return false;
        }
        String itemId = Ecc2BridgePlatform.itemId(cellStack);
        String fluidId = Ecc2BridgePlatform.fluidId(fluid);
        if (ensureBound()) {
            try {
                CellFamily family = (CellFamily) familyOfHandle.invoke(itemId);
                return (boolean) familyAdmitsHandle.invoke(family, fluidId);
            } catch (Throwable failure) {
                LOG.error("Failed to invoke IC2NE CellFamily admission check", failure);
            }
        }
        Boolean basic = Ecc2ExternalRecipes.isBasicNamedCell(itemId);
        if (Boolean.TRUE.equals(basic)) {
            return Ecc2ExternalRecipes.basicCellAdmits(fluidId);
        }
        return true;
    }

    private static boolean ensureBound() {
        if (bound) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            var base = MethodHandles.lookup();
            var cellLookup = MethodHandles.privateLookupIn(CellItem.class, base);
            contentsHandle =
                    cellLookup.findStatic(
                            CellItem.class,
                            "contents",
                            MethodType.methodType(FluidStack.class, ItemStack.class));
            withContentsHandle =
                    cellLookup.findStatic(
                            CellItem.class,
                            "withContents",
                            MethodType.methodType(
                                    ItemStack.class, ItemStack.class, FluidStack.class));
            var familyLookup = MethodHandles.privateLookupIn(CellFamily.class, base);
            familyOfHandle =
                    familyLookup.findStatic(
                            CellFamily.class,
                            "of",
                            MethodType.methodType(CellFamily.class, String.class));
            familyAdmitsHandle =
                    familyLookup.findVirtual(
                            CellFamily.class,
                            "admits",
                            MethodType.methodType(boolean.class, String.class));
            bound = true;
            return true;
        } catch (Throwable failure) {
            failed = true;
            LOG.error("Failed to bind IC2NE CellItem/CellFamily handles", failure);
            return false;
        }
    }
}
