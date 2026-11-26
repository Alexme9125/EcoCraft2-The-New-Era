package com.ecocraft2.common.gas;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.giant.GiantUpgradeProfile;

/**
 * Pure-Java state machine for the Gas Furnace ({@code ecc2:gas_furnace}).
 *
 * <p>Smelts any furnace recipe without EU, consuming strictly {@value #GAS_PER_ITEM_MB} mB of any
 * {@link GasFuelSpec} per smelted item. Overclockers shorten smelting duration using IC2's
 * {@code 0.7^n} scaling while preserving exact {@value #GAS_PER_ITEM_MB} mB total fuel consumption
 * per item via integer cumulative metering.
 */
public final class GasFurnaceEngine {

    /** Exact gas consumed per smelted item in mB. */
    public static final int GAS_PER_ITEM_MB = Ecc2ConfigDefaults.GAS_FURNACE_MB_PER_ITEM;

    /** Base smelting time in ticks per item (before overclockers). */
    public static final int BASE_SMELT_TICKS = Ecc2ConfigDefaults.GAS_FURNACE_BASE_TICKS;

    /** Gas fuel tank capacity in mB. */
    public static final int GAS_TANK_CAPACITY_MB = Ecc2ConfigDefaults.GAS_FURNACE_TANK_CAPACITY_MB;

    private GasFuelSpec currentFuel;
    private int gasPerItemMb = GAS_PER_ITEM_MB;
    private int baseSmeltTicks = BASE_SMELT_TICKS;
    private int gasMb;
    private int overclockers;
    private int progress;
    private int gasSpentOnCurrentItem;
    private boolean active;

    /** Configures runtime gas cost per smelted item and base smelting duration. */
    public void configure(int gasPerItemMb, int baseSmeltTicks) {
        this.gasPerItemMb = Math.max(1, gasPerItemMb);
        this.baseSmeltTicks = Math.max(1, baseSmeltTicks);
        this.gasSpentOnCurrentItem = Math.min(this.gasSpentOnCurrentItem, this.gasPerItemMb);
        this.progress = Math.min(this.progress, operationTicks());
    }

    /** Returns configured gas consumed per smelted item in mB. */
    public int gasPerItemMb() {
        return gasPerItemMb;
    }

    /** Returns configured base smelting time in ticks before overclockers. */
    public int baseSmeltTicks() {
        return baseSmeltTicks;
    }

    /**
     * Advances the Gas Furnace by one tick.
     *
     * @param hasSmeltableInput whether a valid smeltable input is present
     * @param canAcceptOutput   whether the output slot can accept the smelted result
     * @return {@link GasMachineOutcome} for this tick; on {@link GasMachineOutcome#COMPLETED},
     *         the caller must consume 1 input item and insert 1 smelted output
     */
    public GasMachineOutcome tick(boolean hasSmeltableInput, boolean canAcceptOutput) {
        if (!hasSmeltableInput) {
            progress = 0;
            gasSpentOnCurrentItem = 0;
            active = false;
            return GasMachineOutcome.IDLE;
        }

        if (!canAcceptOutput) {
            active = false;
            return GasMachineOutcome.OUTPUT_FULL;
        }

        int opTicks = operationTicks();
        int nextProgress = progress + 1;
        int targetSpent = nextProgress >= opTicks
                ? gasPerItemMb
                : (int) (((long) nextProgress * gasPerItemMb + opTicks - 1L) / opTicks);
        int neededThisTick = Math.max(0, targetSpent - gasSpentOnCurrentItem);

        if (neededThisTick > gasMb || (progress == 0 && gasMb <= 0 && gasSpentOnCurrentItem < gasPerItemMb)) {
            active = false;
            return GasMachineOutcome.NO_POWER;
        }

        if (neededThisTick > 0) {
            gasMb -= neededThisTick;
            gasSpentOnCurrentItem += neededThisTick;
            if (gasMb == 0) {
                currentFuel = null;
            }
        }

        progress = nextProgress;
        active = true;

        if (progress >= opTicks) {
            progress = 0;
            gasSpentOnCurrentItem = 0;
            return GasMachineOutcome.COMPLETED;
        }
        return GasMachineOutcome.WORKING;
    }

    /**
     * Fills gas fuel into the furnace tank. Refuses mixing different gas types while non-empty.
     *
     * @param fuel     gas fuel type
     * @param amountMb volume to insert in mB
     * @param simulate if {@code true}, returns accepted mB without mutating state
     * @return accepted mB
     */
    public int fillGas(GasFuelSpec fuel, int amountMb, boolean simulate) {
        if (fuel == null || amountMb <= 0) {
            return 0;
        }
        if (gasMb > 0 && currentFuel != null && currentFuel != fuel) {
            return 0;
        }
        int accepted = Math.min(GAS_TANK_CAPACITY_MB - gasMb, amountMb);
        if (!simulate && accepted > 0) {
            currentFuel = fuel;
            gasMb += accepted;
        }
        return accepted;
    }

    /**
     * Drains gas fuel from the furnace tank.
     *
     * @param maxDrainMb maximum mB to drain
     * @param simulate   if {@code true}, returns drained mB without mutating state
     * @return drained mB
     */
    public int drainGas(int maxDrainMb, boolean simulate) {
        if (maxDrainMb <= 0 || gasMb <= 0) {
            return 0;
        }
        int drained = Math.min(gasMb, maxDrainMb);
        if (!simulate) {
            gasMb -= drained;
            if (gasMb == 0) {
                currentFuel = null;
            }
        }
        return drained;
    }

    /** Returns the currently stored {@link GasFuelSpec}, or {@code null} when tank is empty. */
    public GasFuelSpec currentFuel() {
        return currentFuel;
    }

    /** Returns stored gas volume in mB ({@code 0..8,000}). */
    public int gasMb() {
        return gasMb;
    }

    /** Returns the installed Overclocker Upgrade count ({@code >= 0}). */
    public int overclockers() {
        return overclockers;
    }

    /**
     * Sets the installed Overclocker Upgrade count ({@code >= 0}).
     *
     * @param overclockers count of overclockers
     */
    public void setOverclockers(int overclockers) {
        if (overclockers < 0) {
            throw new IllegalArgumentException("overclockers must be >= 0");
        }
        this.overclockers = overclockers;
    }

    /** Returns upgraded smelting duration in ticks: {@code max(1, round(baseSmeltTicks * 0.7^overclockers))}. */
    public int operationTicks() {
        if (overclockers == 0) {
            return baseSmeltTicks;
        }
        double scaled = baseSmeltTicks * Math.pow(GiantUpgradeProfile.OVERCLOCKER_TIME_FACTOR, overclockers);
        return Math.max(1, (int) Math.round(scaled));
    }

    /** Returns current tick progress on the active item. */
    public int progress() {
        return progress;
    }

    /** Returns cumulative gas (in mB, {@code 0..gasPerItemMb}) already spent on the current in-progress item. */
    public int gasSpentOnCurrentItem() {
        return gasSpentOnCurrentItem;
    }

    /** Returns {@code true} if the furnace actively smelted on the most recent tick. */
    public boolean isActive() {
        return active;
    }

    /** Scales smelting progress to GUI pixel gauge length in {@code [0, barLength]}. */
    public int scaledProgress(int barLength) {
        if (barLength <= 0 || progress <= 0) {
            return 0;
        }
        return Math.min(barLength, (progress * barLength) / operationTicks());
    }

    /**
     * Restores persisted furnace state from NBT/serialization.
     *
     * @param fuel                  saved fuel type (ignored if {@code gasMb <= 0})
     * @param gasMb                 saved gas mB
     * @param progress              saved tick progress
     * @param gasSpentOnCurrentItem saved mB already spent on current item
     */
    public void restoreState(GasFuelSpec fuel, int gasMb, int progress, int gasSpentOnCurrentItem) {
        int clampedGas = Math.max(0, Math.min(GAS_TANK_CAPACITY_MB, gasMb));
        this.gasMb = (fuel != null && clampedGas > 0) ? clampedGas : 0;
        this.currentFuel = this.gasMb > 0 ? fuel : null;
        this.progress = Math.max(0, Math.min(operationTicks(), progress));
        this.gasSpentOnCurrentItem = Math.max(0, Math.min(gasPerItemMb, gasSpentOnCurrentItem));
        this.active = false;
    }
}
