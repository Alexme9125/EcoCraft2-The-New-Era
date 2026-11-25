package com.ecocraft2.common.gas;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.giant.GiantUpgradeProfile;
import com.ecocraft2.common.solar.SolarTier;

import java.util.Objects;

/**
 * Pure-Java state machine for the Culture Incubator ({@code ecc2:culture_incubator}).
 *
 * <p>Process: {@code 1 bonemeal + 100 mB water + 200 EU / 100 ticks -> 100 mB ecc2:culture_medium}
 * (LV Tier 1 input, 8,000 mB water & culture medium tanks, standard IC2 upgrades).
 */
public final class CultureIncubatorEngine {

    public static final int BASE_EU_PER_OP = Ecc2ConfigDefaults.CULTURE_INCUBATOR_EU_PER_OP;
    public static final int BASE_TICKS_PER_OP = Ecc2ConfigDefaults.CULTURE_INCUBATOR_TICKS_PER_OP;
    public static final int BASE_EU_PER_TICK = BASE_EU_PER_OP / BASE_TICKS_PER_OP; // 2 EU/t
    public static final int WATER_PER_OP_MB = Ecc2ConfigDefaults.CULTURE_INCUBATOR_WATER_PER_OP_MB;
    public static final int CULTURE_OUTPUT_PER_OP_MB = Ecc2ConfigDefaults.CULTURE_INCUBATOR_OUTPUT_PER_OP_MB;
    public static final int WATER_TANK_CAPACITY_MB = Ecc2ConfigDefaults.CULTURE_INCUBATOR_TANK_CAPACITY_MB;
    public static final int CULTURE_TANK_CAPACITY_MB = Ecc2ConfigDefaults.CULTURE_INCUBATOR_TANK_CAPACITY_MB;
    public static final int BASE_INPUT_TIER = Ecc2ConfigDefaults.CULTURE_INCUBATOR_INPUT_TIER;
    public static final int BASE_EU_CAPACITY = Ecc2ConfigDefaults.CULTURE_INCUBATOR_BASE_EU_CAPACITY;

    private GiantUpgradeProfile upgrades = GiantUpgradeProfile.NONE;
    private double storedEu;
    private int waterMb;
    private int cultureMb;
    private int progress;
    private boolean active;

    /**
     * Advances the Culture Incubator by one tick.
     *
     * @param hasBonemeal whether at least 1 bonemeal is present in the input slot
     * @return {@link GasMachineOutcome}; on {@link GasMachineOutcome#COMPLETED}, caller must consume 1 bonemeal
     */
    public GasMachineOutcome tick(boolean hasBonemeal) {
        if (!hasBonemeal || waterMb < WATER_PER_OP_MB) {
            progress = 0;
            active = false;
            return GasMachineOutcome.IDLE;
        }

        if (cultureMb + CULTURE_OUTPUT_PER_OP_MB > CULTURE_TANK_CAPACITY_MB) {
            active = false;
            return GasMachineOutcome.OUTPUT_FULL;
        }

        double cost = euPerTick();
        if (storedEu + 1e-12 < cost) {
            active = false;
            return GasMachineOutcome.NO_POWER;
        }

        storedEu = Math.max(0.0, storedEu - cost);
        progress++;
        active = true;

        if (progress >= operationTicks()) {
            progress = 0;
            waterMb -= WATER_PER_OP_MB;
            cultureMb += CULTURE_OUTPUT_PER_OP_MB;
            return GasMachineOutcome.COMPLETED;
        }
        return GasMachineOutcome.WORKING;
    }

    // --- Energy API ---

    public int inputTier() {
        return upgrades.effectiveTier(BASE_INPUT_TIER);
    }

    public double maxInputPacket() {
        return SolarTier.packetSize(inputTier());
    }

    public double energyCapacity() {
        return upgrades.energyCapacity(BASE_EU_CAPACITY);
    }

    public double storedEu() {
        return storedEu;
    }

    public void setStoredEu(double eu) {
        if (!Double.isFinite(eu) || eu <= 0.0) {
            this.storedEu = 0.0;
        } else {
            this.storedEu = Math.min(energyCapacity(), eu);
        }
    }

    public double demandedEu() {
        return Math.max(0.0, energyCapacity() - storedEu);
    }

    /** Injects EU and returns exact unaccepted remainder. */
    public double injectEu(double amount) {
        if (amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        double accepted = Math.min(amount, demandedEu());
        storedEu = Math.min(energyCapacity(), storedEu + accepted);
        return amount - accepted;
    }

    // --- Tank API ---

    public int waterMb() {
        return waterMb;
    }

    public void setWaterMb(int mb) {
        this.waterMb = Math.max(0, Math.min(WATER_TANK_CAPACITY_MB, mb));
    }

    public int fillWater(int amountMb, boolean simulate) {
        if (amountMb <= 0) {
            return 0;
        }
        int accepted = Math.min(WATER_TANK_CAPACITY_MB - waterMb, amountMb);
        if (!simulate) {
            waterMb += accepted;
        }
        return accepted;
    }

    public int drainWater(int maxDrainMb, boolean simulate) {
        if (maxDrainMb <= 0 || waterMb <= 0) {
            return 0;
        }
        int drained = Math.min(waterMb, maxDrainMb);
        if (!simulate) {
            waterMb -= drained;
        }
        return drained;
    }

    public int cultureMb() {
        return cultureMb;
    }

    public void setCultureMb(int mb) {
        this.cultureMb = Math.max(0, Math.min(CULTURE_TANK_CAPACITY_MB, mb));
    }

    public int drainCulture(int maxDrainMb, boolean simulate) {
        if (maxDrainMb <= 0 || cultureMb <= 0) {
            return 0;
        }
        int drained = Math.min(cultureMb, maxDrainMb);
        if (!simulate) {
            cultureMb -= drained;
        }
        return drained;
    }

    // --- Upgrades & Status ---

    public GiantUpgradeProfile upgrades() {
        return upgrades;
    }

    public void setUpgrades(GiantUpgradeProfile upgrades) {
        this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
        this.storedEu = Math.min(this.storedEu, energyCapacity());
    }

    public int operationTicks() {
        return upgrades.operationTicks(BASE_TICKS_PER_OP);
    }

    public double euPerTick() {
        return upgrades.euPerTickPerOp(BASE_EU_PER_TICK);
    }

    public int progress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(operationTicks(), progress));
    }

    public boolean isActive() {
        return active;
    }

    public int scaledProgress(int barLength) {
        if (barLength <= 0 || progress <= 0) {
            return 0;
        }
        return Math.min(barLength, (progress * barLength) / operationTicks());
    }
}
