package com.ecocraft2.common.gas;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.giant.GiantUpgradeProfile;
import com.ecocraft2.common.solar.SolarTier;

import java.util.Objects;

/**
 * Pure-Java state machine for the Organic Fermenter ({@code ecc2:organic_fermenter}).
 *
 * <p>Process: {@code 50 mB ecc2:culture_medium + food (nutrition H > 0) + 200 EU / 100 ticks -> 20 * H mB ic2:biogas}
 * (LV Tier 1 input, 8,000 mB culture medium tank, 16,000 mB biogas tank, standard IC2 upgrades,
 * container-return check for stews/bottles).
 */
public final class OrganicFermenterEngine {

    public static final int BASE_EU_PER_OP = Ecc2ConfigDefaults.ORGANIC_FERMENTER_EU_PER_OP;
    public static final int BASE_TICKS_PER_OP = Ecc2ConfigDefaults.ORGANIC_FERMENTER_TICKS_PER_OP;
    public static final int BASE_EU_PER_TICK = BASE_EU_PER_OP / BASE_TICKS_PER_OP; // 2 EU/t
    public static final int CULTURE_PER_OP_MB = Ecc2ConfigDefaults.ORGANIC_FERMENTER_CULTURE_PER_OP_MB;
    public static final int BIOGAS_PER_NUTRITION_MB = Ecc2ConfigDefaults.ORGANIC_FERMENTER_BIOGAS_PER_NUTRITION_MB;
    public static final int CULTURE_TANK_CAPACITY_MB = Ecc2ConfigDefaults.ORGANIC_FERMENTER_CULTURE_TANK_MB;
    public static final int BIOGAS_TANK_CAPACITY_MB = Ecc2ConfigDefaults.ORGANIC_FERMENTER_BIOGAS_TANK_MB;
    public static final int BASE_INPUT_TIER = Ecc2ConfigDefaults.ORGANIC_FERMENTER_INPUT_TIER;
    public static final int BASE_EU_CAPACITY = Ecc2ConfigDefaults.ORGANIC_FERMENTER_BASE_EU_CAPACITY;

    private GiantUpgradeProfile upgrades = GiantUpgradeProfile.NONE;
    private double storedEu;
    private int cultureMb;
    private int biogasMb;
    private int progress;
    private int activeNutrition;
    private int lastCompletedBiogasMb;
    private boolean active;

    /**
     * Computes biogas yield in mB for a food item with hunger nutrition value {@code H}.
     *
     * @param foodNutrition hunger points restored by the food ({@code <= 0} yields {@code 0})
     * @return biogas yield {@code 20 * H} mB (capped at tank capacity to prevent overflow)
     */
    public static int biogasYieldForNutrition(int foodNutrition) {
        if (foodNutrition <= 0) {
            return 0;
        }
        long yield = (long) foodNutrition * BIOGAS_PER_NUTRITION_MB;
        return (int) Math.min(BIOGAS_TANK_CAPACITY_MB, yield);
    }

    /**
     * Advances the Organic Fermenter by one tick assuming no container return obstruction.
     *
     * @param foodNutrition nutrition value {@code H} of the current food item ({@code <= 0} if empty/inedible)
     * @return {@link GasMachineOutcome} for this tick
     */
    public GasMachineOutcome tick(int foodNutrition) {
        return tick(foodNutrition, true);
    }

    /**
     * Advances the Organic Fermenter by one tick.
     *
     * @param foodNutrition           nutrition value {@code H} of the current food item ({@code <= 0} if empty/inedible)
     * @param canAcceptContainerReturn {@code true} if the food has no container item or the container return slot can fit it
     * @return {@link GasMachineOutcome}; on {@link GasMachineOutcome#COMPLETED}, caller must consume 1 food item
     *         and place any container remainder (e.g. bowl/glass bottle) into the return slot
     */
    public GasMachineOutcome tick(int foodNutrition, boolean canAcceptContainerReturn) {
        this.lastCompletedBiogasMb = 0;

        if (foodNutrition <= 0 || cultureMb < CULTURE_PER_OP_MB) {
            progress = 0;
            activeNutrition = 0;
            active = false;
            return GasMachineOutcome.IDLE;
        }

        // Prevent swapping low-nutrition food for high-nutrition food mid-cycle.
        if (activeNutrition != 0 && activeNutrition != foodNutrition) {
            progress = 0;
        }
        activeNutrition = foodNutrition;

        int yieldMb = biogasYieldForNutrition(foodNutrition);
        if (!canAcceptContainerReturn || biogasMb + yieldMb > BIOGAS_TANK_CAPACITY_MB) {
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
            activeNutrition = 0;
            cultureMb -= CULTURE_PER_OP_MB;
            biogasMb += yieldMb;
            lastCompletedBiogasMb = yieldMb;
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

    public int cultureMb() {
        return cultureMb;
    }

    public void setCultureMb(int mb) {
        this.cultureMb = Math.max(0, Math.min(CULTURE_TANK_CAPACITY_MB, mb));
    }

    public int fillCulture(int amountMb, boolean simulate) {
        if (amountMb <= 0) {
            return 0;
        }
        int accepted = Math.min(CULTURE_TANK_CAPACITY_MB - cultureMb, amountMb);
        if (!simulate) {
            cultureMb += accepted;
        }
        return accepted;
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

    public int biogasMb() {
        return biogasMb;
    }

    public void setBiogasMb(int mb) {
        this.biogasMb = Math.max(0, Math.min(BIOGAS_TANK_CAPACITY_MB, mb));
    }

    public int drainBiogas(int maxDrainMb, boolean simulate) {
        if (maxDrainMb <= 0 || biogasMb <= 0) {
            return 0;
        }
        int drained = Math.min(biogasMb, maxDrainMb);
        if (!simulate) {
            biogasMb -= drained;
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

    public int activeNutrition() {
        return activeNutrition;
    }

    public void setActiveNutrition(int nutrition) {
        this.activeNutrition = Math.max(0, nutrition);
    }

    /** Returns biogas produced on the most recent tick if {@link GasMachineOutcome#COMPLETED}, else {@code 0}. */
    public int lastCompletedBiogasMb() {
        return lastCompletedBiogasMb;
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
