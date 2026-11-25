package com.ecocraft2.solar;

import com.ecocraft2.bridge.Ecc2ArmorSuitBridge;
import com.ecocraft2.common.solar.SolarHelmetSpec;
import com.ecocraft2.config.Ecc2Config;
import net.minecraft.world.item.ItemStack;

/**
 * Shared pure logic for all three EcoCraft 2 Solar Helmets across NeoForge 1.21.1 and 26.x.
 */
public final class SolarHelmetLogic {

    /** Cyan EU charge bar color matching IC2NE powered gear. */
    public static final int BAR_COLOR = 0x58C7D5;

    private SolarHelmetLogic() {}

    public static double stored(ItemStack stack, SolarHelmetSpec spec) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }
        double raw = stack.getOrDefault(SolarKineticModule.HELMET_EU.get(), 0.0);
        if (!Double.isFinite(raw) || raw <= 0.0) {
            return 0.0;
        }
        return Math.min(spec.capacity(), raw);
    }

    public static void setStored(ItemStack stack, SolarHelmetSpec spec, double eu) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        double clamped =
                Double.isFinite(eu) ? Math.max(0.0, Math.min(spec.capacity(), eu)) : 0.0;
        if (clamped <= 0.0) {
            stack.remove(SolarKineticModule.HELMET_EU.get());
        } else {
            stack.set(SolarKineticModule.HELMET_EU.get(), clamped);
        }
    }

    public static double charge(
            ItemStack stack,
            SolarHelmetSpec spec,
            double offer,
            int chargerTier,
            boolean ignoreLimit,
            boolean simulate) {
        if (stack == null
                || stack.isEmpty()
                || stack.getCount() != 1
                || !Double.isFinite(offer)
                || offer <= 0.0
                || chargerTier < spec.tier()) {
            return 0.0;
        }
        double prior = stored(stack, spec);
        double room = Math.max(0.0, spec.capacity() - prior);
        if (room <= 0.0) {
            return 0.0;
        }
        double limit = ignoreLimit ? offer : Math.min(offer, spec.transferLimit());
        double accepted = Math.min(room, limit);
        if (!simulate && accepted > 0.0) {
            setStored(stack, spec, prior + accepted);
        }
        return accepted;
    }

    public static int barWidth(ItemStack stack, SolarHelmetSpec spec) {
        double cap = spec.capacity();
        if (cap <= 0.0) {
            return 0;
        }
        return (int) Math.round(13.0 * stored(stack, spec) / cap);
    }

    public static Ecc2ArmorSuitBridge.SuitClass suitClass(SolarHelmetSpec spec) {
        return spec == SolarHelmetSpec.ADVANCED
                ? Ecc2ArmorSuitBridge.SuitClass.NANO
                : Ecc2ArmorSuitBridge.SuitClass.QUANTUM;
    }

    public static int configuredDayPower(SolarHelmetSpec spec) {
        return switch (spec) {
            case ADVANCED -> Ecc2Config.advancedHelmetGenDay();
            case HYBRID -> Ecc2Config.hybridHelmetGenDay();
            case ULTIMATE_HYBRID -> Ecc2Config.ultimateHelmetGenDay();
        };
    }

    public static int configuredNightPower(SolarHelmetSpec spec) {
        return switch (spec) {
            case ADVANCED -> Ecc2Config.advancedHelmetGenNight();
            case HYBRID -> Ecc2Config.hybridHelmetGenNight();
            case ULTIMATE_HYBRID -> Ecc2Config.ultimateHelmetGenNight();
        };
    }

    /** Creates a fully charged variant of the given helmet item for the Creative Mode Tab. */
    public static ItemStack createChargedStack(SolarHelmetItem item) {
        ItemStack full = new ItemStack(item);
        item.ecc2SetStored(full, item.spec().capacity());
        return full;
    }
}
