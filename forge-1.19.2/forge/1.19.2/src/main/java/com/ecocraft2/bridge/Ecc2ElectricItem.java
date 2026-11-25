package com.ecocraft2.bridge;

import net.minecraft.world.item.ItemStack;

/**
 * Implemented by EcoCraft 2 rechargeable electric items (such as the three Solar Helmets) so
 * IC2NE's energy storage charging slots, charge pads, battery packs, and armor absorption interact
 * with them natively.
 *
 * <p>Hooked into {@code com.industrialage.ia2.forge.equipment.ForgeGear} via Mixin.
 */
public interface Ecc2ElectricItem {

    /** Returns the maximum EU storage capacity of this item stack. */
    double ecc2Capacity(ItemStack stack);

    /** Returns the per-tick EU transfer limit of this item stack. */
    double ecc2TransferLimit(ItemStack stack);

    /** Returns the IC2 voltage tier (1=LV, 2=MV, 3=HV, 4=EV, 5=IV) of this item stack. */
    int ecc2Tier(ItemStack stack);

    /** Returns {@code true} if this item can discharge into machines or other items. */
    boolean ecc2CanProvide(ItemStack stack);

    /** Returns the currently stored EU of this item stack. */
    double ecc2Stored(ItemStack stack);

    /** Sets the stored EU of this item stack, clamped to {@code [0, ecc2Capacity(stack)]}. */
    void ecc2SetStored(ItemStack stack, double eu);

    /** Charges this item stack by up to {@code offer} EU and returns accepted EU. */
    double ecc2Charge(
            ItemStack stack,
            double offer,
            int chargerTier,
            boolean ignoreLimit,
            boolean simulate);

    /** Discharges up to {@code requested} EU from this item stack and returns extracted EU. */
    double ecc2Discharge(
            ItemStack stack,
            double requested,
            int chargerTier,
            boolean ignoreLimit,
            boolean simulate);
}
