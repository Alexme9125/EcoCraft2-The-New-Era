package com.ecocraft2.common.gas;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.giant.GiantUpgradeProfile;
import com.ecocraft2.common.solar.SkyCondition;
import com.ecocraft2.common.solar.SolarTier;

import java.util.Objects;

/**
 * Pure-Java state machine for the Water Gas Machine ({@code ecc2:water_gas_machine}).
 *
 * <p>Process: {@code 100 mB water + 1 coal_dust + 1,000 EU / 200 ticks -> 1,000 mB ecc2:water_gas}.
 * Powered by a built-in top solar panel generating {@value #SOLAR_EU_PER_TICK} EU/t during clear daytime
 * and/or an LV (Tier 1) grid connection, with 8,000 mB water and water gas tanks and standard IC2 upgrades.
 */
public final class WaterGasEngine {

    public static final int SOLAR_EU_PER_TICK = Ecc2ConfigDefaults.WATER_GAS_SOLAR_EU_PER_TICK;
    public static final int BASE_EU_PER_OP = Ecc2ConfigDefaults.WATER_GAS_EU_PER_OP;
    public static final int BASE_TICKS_PER_OP = Ecc2ConfigDefaults.WATER_GAS_TICKS_PER_OP;
    public static final int BASE_EU_PER_TICK = BASE_EU_PER_OP / BASE_TICKS_PER_OP; // 5 EU/t
    public static final int WATER_PER_OP_MB = Ecc2ConfigDefaults.WATER_GAS_WATER_PER_OP_MB;
    public static final int GAS_OUTPUT_PER_OP_MB = Ecc2ConfigDefaults.WATER_GAS_OUTPUT_PER_OP_MB;
    public static final int WATER_TANK_CAPACITY_MB = Ecc2ConfigDefaults.WATER_GAS_TANK_CAPACITY_MB;
    public static final int GAS_TANK_CAPACITY_MB = Ecc2ConfigDefaults.WATER_GAS_TANK_CAPACITY_MB;
    public static final int BASE_INPUT_TIER = Ecc2ConfigDefaults.WATER_GAS_INPUT_TIER;
    public static final int BASE_EU_CAPACITY = Ecc2ConfigDefaults.WATER_GAS_BASE_EU_CAPACITY;

    private GiantUpgradeProfile upgrades = GiantUpgradeProfile.NONE;
    private double storedEu;
    private int waterMb;
    private int waterGasMb;
    private int progress;
    private SkyCondition skyCondition = SkyCondition.NONE;
    private boolean solarActive;
    private boolean active;

    /**
     * Advances the Water Gas Machine by one tick.
     *
     * @param hasCoalDust whether at least 1 coal dust is available in the input slot
     * @param hasSky      whether the dimension has sky light
     * @param seesSky     whether the top solar array sees unobstructed sky
     * @param isDay       whether the world is currently daytime
     * @param isRaining   whether global precipitation is active
     * @param canRain     whether the local biome can rain
     * @return {@link GasMachineOutcome} for this tick; on {@link GasMachineOutcome#COMPLETED},
     *         the caller must consume 1 coal dust
     */
    public GasMachineOutcome tick(
            boolean hasCoalDust,
            boolean hasSky,
            boolean seesSky,
            boolean isDay,
            boolean isRaining,
            boolean canRain
    ) {
        this.skyCondition = SkyCondition.evaluate(hasSky, seesSky, isDay, isRaining, canRain);
        this.solarActive = this.skyCondition.isFullSun();
        if (this.solarActive) {
            this.storedEu = Math.min(energyCapacity(), this.storedEu + SOLAR_EU_PER_TICK);
        }

        if (!hasCoalDust || waterMb < WATER_PER_OP_MB) {
            progress = 0;
            active = false;
            return GasMachineOutcome.IDLE;
        }

        if (waterGasMb + GAS_OUTPUT_PER_OP_MB > GAS_TANK_CAPACITY_MB) {
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

        int opTicks = operationTicks();
        if (progress >= opTicks) {
            progress = 0;
            waterMb -= WATER_PER_OP_MB;
            waterGasMb += GAS_OUTPUT_PER_OP_MB;
            return GasMachineOutcome.COMPLETED;
        }
        return GasMachineOutcome.WORKING;
    }

    // --- Energy API ---

    /** Returns effective IC2 input tier (Tier 1 LV + transformer upgrades). */
    public int inputTier() {
        return upgrades.effectiveTier(BASE_INPUT_TIER);
    }

    /** Returns maximum single-packet EU input voltage. */
    public double maxInputPacket() {
        return SolarTier.packetSize(inputTier());
    }

    /** Returns total internal EU buffer capacity. */
    public double energyCapacity() {
        return upgrades.energyCapacity(BASE_EU_CAPACITY);
    }

    /** Returns current stored EU. */
    public double storedEu() {
        return storedEu;
    }

    /** Sets stored EU clamped to {@code [0, energyCapacity()]}. */
    public void setStoredEu(double eu) {
        if (!Double.isFinite(eu) || eu <= 0.0) {
            this.storedEu = 0.0;
        } else {
            this.storedEu = Math.min(energyCapacity(), eu);
        }
    }

    /** Returns remaining EU buffer space. */
    public double demandedEu() {
        return Math.max(0.0, energyCapacity() - storedEu);
    }

    /**
     * Injects EU from the power grid and returns the exact unaccepted remainder.
     *
     * @param amount offered EU
     * @return unaccepted remainder EU
     */
    public double injectEu(double amount) {
        if (amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        double accepted = Math.min(amount, demandedEu());
        storedEu = Math.min(energyCapacity(), storedEu + accepted);
        return amount - accepted;
    }

    // --- Tank API ---

    /** Returns stored water in mB ({@code 0..8,000}). */
    public int waterMb() {
        return waterMb;
    }

    /** Sets stored water in mB clamped to {@code [0, 8,000]}. */
    public void setWaterMb(int mb) {
        this.waterMb = Math.max(0, Math.min(WATER_TANK_CAPACITY_MB, mb));
    }

    /** Inserts water into the input tank and returns accepted mB. */
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

    /** Drains water from the input tank and returns drained mB. */
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

    /** Returns stored water gas in mB ({@code 0..8,000}). */
    public int waterGasMb() {
        return waterGasMb;
    }

    /** Sets stored water gas in mB clamped to {@code [0, 8,000]}. */
    public void setWaterGasMb(int mb) {
        this.waterGasMb = Math.max(0, Math.min(GAS_TANK_CAPACITY_MB, mb));
    }

    /** Drains produced water gas from the output tank and returns drained mB. */
    public int drainWaterGas(int maxDrainMb, boolean simulate) {
        if (maxDrainMb <= 0 || waterGasMb <= 0) {
            return 0;
        }
        int drained = Math.min(waterGasMb, maxDrainMb);
        if (!simulate) {
            waterGasMb -= drained;
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

    /** Returns upgraded operation duration in ticks. */
    public int operationTicks() {
        return upgrades.operationTicks(BASE_TICKS_PER_OP);
    }

    /** Returns upgraded EU/t cost while processing. */
    public double euPerTick() {
        return upgrades.euPerTickPerOp(BASE_EU_PER_TICK);
    }

    /** Returns current tick progress ({@code 0..operationTicks()}). */
    public int progress() {
        return progress;
    }

    /** Sets current tick progress clamped to {@code [0, operationTicks()]}. */
    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(operationTicks(), progress));
    }

    /** Returns the last evaluated sky condition of the top solar panel. */
    public SkyCondition skyCondition() {
        return skyCondition;
    }

    /** Returns {@code true} if the integrated top solar panel generated 2 EU/t on the last tick. */
    public boolean isSolarActive() {
        return solarActive;
    }

    /** Returns {@code true} if the machine actively processed on the last tick. */
    public boolean isActive() {
        return active;
    }

    /** Scales progress to GUI pixels in {@code [0, barLength]}. */
    public int scaledProgress(int barLength) {
        if (barLength <= 0 || progress <= 0) {
            return 0;
        }
        return Math.min(barLength, (progress * barLength) / operationTicks());
    }
}
