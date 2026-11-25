package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2ArmorSuitBridge;
import com.ecocraft2.bridge.Ecc2ElectricItem;
import com.ecocraft2.common.solar.SolarHelmetSpec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * NeoForge 26.x component-equippable Solar Helmet item (Advanced, Hybrid, or Ultimate Hybrid)
 * implementing both {@link Ecc2ElectricItem} and {@link Ecc2ArmorSuitBridge}.
 */
public final class SolarHelmetItem extends Item
        implements Ecc2ElectricItem, Ecc2ArmorSuitBridge {

    private final SolarHelmetSpec spec;

    public SolarHelmetItem(SolarHelmetSpec spec, Properties properties) {
        super(properties);
        this.spec = spec;
    }

    public SolarHelmetSpec spec() {
        return spec;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return SolarHelmetLogic.barWidth(stack, spec);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return SolarHelmetLogic.BAR_COLOR;
    }

    // ---- Ecc2ElectricItem ----

    @Override
    public double ecc2Capacity(ItemStack stack) {
        return spec.capacity();
    }

    @Override
    public double ecc2TransferLimit(ItemStack stack) {
        return spec.transferLimit();
    }

    @Override
    public int ecc2Tier(ItemStack stack) {
        return spec.tier();
    }

    @Override
    public boolean ecc2CanProvide(ItemStack stack) {
        return false;
    }

    @Override
    public double ecc2Stored(ItemStack stack) {
        return SolarHelmetLogic.stored(stack, spec);
    }

    @Override
    public void ecc2SetStored(ItemStack stack, double eu) {
        SolarHelmetLogic.setStored(stack, spec, eu);
    }

    @Override
    public double ecc2Charge(
            ItemStack stack,
            double offer,
            int chargerTier,
            boolean ignoreLimit,
            boolean simulate) {
        return SolarHelmetLogic.charge(stack, spec, offer, chargerTier, ignoreLimit, simulate);
    }

    @Override
    public double ecc2Discharge(
            ItemStack stack,
            double requested,
            int chargerTier,
            boolean ignoreLimit,
            boolean simulate) {
        return 0.0;
    }

    // ---- Ecc2ArmorSuitBridge ----

    @Override
    public SuitClass ecc2SuitClass(ItemStack stack) {
        return SolarHelmetLogic.suitClass(spec);
    }
}
