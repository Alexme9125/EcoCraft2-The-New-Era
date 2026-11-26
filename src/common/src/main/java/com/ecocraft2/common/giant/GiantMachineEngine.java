package com.ecocraft2.common.giant;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.solar.SolarTier;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/**
 * Pure-Java 9-lane x 3-operation parallel execution engine for 3x3x3 Giant Machines.
 *
 * <p>Core invariants:
 * <ul>
 *     <li>Maintains 9 independent lanes ({@code 0..8}), each tracking its own {@code progress} and locked {@code batchSize} ({@code 0..3}).</li>
 *     <li>Partial batches of 1 or 2 operations run normally whenever fewer than 3 inputs or output/water slots are available.</li>
 *     <li>Energy consumed per tick equals {@code sum(activeLaneBatch) * upgradedEuPerTick}.</li>
 *     <li>Base voltage is Tier 3 (HV, 512 EU/t), scaled by IC2 Transformer, Overclocker, and Energy Storage upgrades.</li>
 *     <li>Water-consuming machines (Ore Washer) reserve uncommitted water on lane start and deduct exact
 *         {@code batchSize * waterPerOperationMb} on completion with zero water loss on cancellation.</li>
 * </ul>
 */
public final class GiantMachineEngine {

    /** Number of independent parallel input lanes (3x3 grid). */
    public static final int LANE_COUNT = Ecc2ConfigDefaults.GIANT_LANE_COUNT;

    /** Maximum concurrent operations per lane batch. */
    public static final int MAX_BATCH_PER_LANE = Ecc2ConfigDefaults.GIANT_MAX_BATCH_PER_LANE;

    /**
     * Platform-neutral callback bridging the 9 input/output slot lanes to the engine.
     */
    public interface LaneHandler {

        /**
         * Returns how many operations in {@code [0, maxBatch]} lane {@code lane} can currently run,
         * bounded by input item count / recipe requirement and output slot space.
         *
         * @param lane     lane index ({@code 0..8})
         * @param maxBatch upper bound ({@code 1..maxBatchPerLane})
         * @return runnable operations count ({@code 0..maxBatch})
         */
        int availableBatch(int lane, int maxBatch);

        /**
         * Returns how many operations in {@code [0, maxBatch]} lane {@code lane} still have valid
         * inputs for, ignoring transient output slot fullness so an in-progress lane pauses instead
         * of resetting progress and wasting EU when the output grid fills up.
         *
         * @param lane     lane index ({@code 0..8})
         * @param maxBatch upper bound ({@code 1..maxBatchPerLane})
         * @return input-supported operations count ({@code 0..maxBatch})
         */
        default int availableInputBatch(int lane, int maxBatch) {
            return availableBatch(lane, maxBatch);
        }

        /**
         * Executes completion for {@code completedOps} operations on {@code lane},
         * consuming inputs and inserting outputs.
         *
         * @param lane         lane index ({@code 0..8})
         * @param completedOps number of completed operations ({@code 1..maxBatchPerLane})
         */
        void completeOperations(int lane, int completedOps);
    }

    /**
     * Summary of a single Giant Machine tick.
     *
     * @param activeLanes      number of lanes that advanced this tick ({@code 0..9})
     * @param activeTotalBatch sum of batch sizes across advancing lanes ({@code 0..27})
     * @param completedOps     total operations completed across all lanes this tick
     * @param euConsumed       exact EU deducted this tick
     * @param waterConsumedMb  exact water in mB deducted this tick
     */
    public record TickSummary(
            int activeLanes,
            int activeTotalBatch,
            int completedOps,
            double euConsumed,
            int waterConsumedMb
    ) {
        /** Returns {@code true} if at least one lane advanced or completed on this tick. */
        public boolean isActive() {
            return activeLanes > 0 || completedOps > 0;
        }
    }

    private final GiantMachineSpec spec;
    private final int[] progress = new int[LANE_COUNT];
    private final int[] batchSize = new int[LANE_COUNT];
    private final boolean[] laneActive = new boolean[LANE_COUNT];
    private final GiantPortPermissions portPermissions = new GiantPortPermissions();

    private GiantUpgradeProfile upgrades = GiantUpgradeProfile.NONE;
    private int baseTier;
    private int maxBatchPerLane = MAX_BATCH_PER_LANE;
    private int waterTankCapacityMb;
    private double storedEu;
    private int waterMb;
    private double lastTickEuConsumed;
    private int lastActiveTotalBatch;
    private boolean active;

    /**
     * Creates a Giant Machine engine for a built-in {@link GiantMachineKind}.
     *
     * @param kind built-in giant machine kind
     */
    public GiantMachineEngine(GiantMachineKind kind) {
        this(Objects.requireNonNull(kind, "kind").spec());
    }

    /**
     * Creates a Giant Machine engine for any {@link GiantMachineSpec} (built-in or addon).
     *
     * @param spec machine specification
     */
    public GiantMachineEngine(GiantMachineSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.baseTier = spec.baseTier();
        this.waterTankCapacityMb = spec.waterTankCapacityMb();
    }

    /** Returns the configured maximum batch size per lane ({@code 1..64}). */
    public int maxBatchPerLane() {
        return maxBatchPerLane;
    }

    /** Sets the configured maximum batch size per lane (clamped to {@code [1, 64]}). */
    public void setMaxBatchPerLane(int maxBatchPerLane) {
        this.maxBatchPerLane = Math.max(1, Math.min(64, maxBatchPerLane));
    }

    /** Configures the base IC2 voltage tier (clamped to {@code [1, 14]}). */
    public void setBaseTier(int baseTier) {
        this.baseTier = Math.max(1, Math.min(14, baseTier));
    }

    /** Configures the water tank capacity in mB (effective only for water-using specs). */
    public void setWaterTankCapacityMb(int capacityMb) {
        if (!spec.usesWater()) {
            this.waterTankCapacityMb = 0;
            this.waterMb = 0;
            return;
        }
        this.waterTankCapacityMb = Math.max(spec.waterPerOperationMb(), capacityMb);
        this.waterMb = Math.min(this.waterMb, this.waterTankCapacityMb);
    }

    /**
     * Advances all 9 lanes by one tick.
     *
     * @param handler callback providing per-lane batch availability and completion handling
     * @return {@link TickSummary} of work performed this tick
     */
    public TickSummary tick(LaneHandler handler) {
        Objects.requireNonNull(handler, "handler");

        int opTicks = operationTicks();
        double euPerOp = upgradedEuPerTick();
        int waterPerOp = spec.waterPerOperationMb();
        boolean usesWater = spec.usesWater();

        // Pass 1: validate already-running lanes against input availability and compute already-committed water.
        // Transient output fullness pauses the lane instead of resetting progress and wasting EU.
        int committedWater = 0;
        for (int i = 0; i < LANE_COUNT; i++) {
            laneActive[i] = false;
            if (batchSize[i] > 0) {
                int inputAvail = clampBatch(handler.availableInputBatch(i, batchSize[i]), batchSize[i]);
                if (inputAvail == 0) {
                    batchSize[i] = 0;
                    progress[i] = 0;
                } else {
                    if (inputAvail < batchSize[i]) {
                        batchSize[i] = inputAvail;
                    }
                    if (usesWater) {
                        committedWater += batchSize[i] * waterPerOp;
                    }
                }
            }
        }

        // Pass 2: start idle lanes that have inputs, output space, and uncommitted water.
        for (int i = 0; i < LANE_COUNT; i++) {
            if (batchSize[i] == 0) {
                int avail = clampBatch(handler.availableBatch(i, maxBatchPerLane), maxBatchPerLane);
                if (avail <= 0) {
                    continue;
                }
                int startBatch = avail;
                if (usesWater) {
                    int freeWater = Math.max(0, waterMb - committedWater);
                    int waterCap = freeWater / waterPerOp;
                    startBatch = Math.min(startBatch, waterCap);
                }
                if (startBatch > 0) {
                    batchSize[i] = startBatch;
                    progress[i] = 0;
                    if (usesWater) {
                        committedWater += startBatch * waterPerOp;
                    }
                }
            }
        }

        // Pass 3: advance lanes that have output space and can afford their EU cost
        // (or finalize already-finished lanes waiting on output space or water).
        int activeLanesCount = 0;
        int activeBatchSum = 0;
        int completedOpsTotal = 0;
        double euSpent = 0.0;
        int waterSpent = 0;

        for (int i = 0; i < LANE_COUNT; i++) {
            int batch = batchSize[i];
            if (batch <= 0) {
                continue;
            }

            if (progress[i] < opTicks) {
                if (clampBatch(handler.availableBatch(i, batch), batch) <= 0) {
                    // Output full while inputs still exist: pause and preserve progress/EU.
                    continue;
                }
                double laneCost = batch * euPerOp;
                if (storedEu + 1e-12 < laneCost) {
                    continue;
                }
                storedEu = Math.max(0.0, storedEu - laneCost);
                euSpent += laneCost;
                progress[i]++;
                laneActive[i] = true;
                activeLanesCount++;
                activeBatchSum += batch;
            }

            if (progress[i] >= opTicks) {
                int waterNeeded = usesWater ? batch * waterPerOp : 0;
                if (!usesWater || waterMb >= waterNeeded) {
                    // Re-verify output/input availability right before completion to guarantee zero loss.
                    int finalOps = clampBatch(handler.availableBatch(i, batch), batch);
                    if (finalOps > 0) {
                        int actualWater = usesWater ? finalOps * waterPerOp : 0;
                        if (!usesWater || waterMb >= actualWater) {
                            waterMb -= actualWater;
                            waterSpent += actualWater;
                            handler.completeOperations(i, finalOps);
                            completedOpsTotal += finalOps;
                            progress[i] = 0;
                            batchSize[i] = 0;
                        }
                    } else if (clampBatch(handler.availableInputBatch(i, batch), batch) <= 0) {
                        progress[i] = 0;
                        batchSize[i] = 0;
                    }
                    // Otherwise inputs remain intact and only output space is full: hold at progress == opTicks.
                }
            }
        }

        this.lastTickEuConsumed = euSpent;
        this.lastActiveTotalBatch = activeBatchSum;
        this.active = activeLanesCount > 0 || completedOpsTotal > 0;
        return new TickSummary(activeLanesCount, activeBatchSum, completedOpsTotal, euSpent, waterSpent);
    }

    /**
     * Rolls an independent recycler scrap chance ({@code 1 / scrapChanceDenominator}) for one operation.
     *
     * @param randomNextIntBound function equivalent to {@code Random#nextInt(int bound)}
     * @return {@code true} if this operation produces a Scrap item
     */
    public boolean rollRecyclerScrap(IntUnaryOperator randomNextIntBound) {
        Objects.requireNonNull(randomNextIntBound, "randomNextIntBound");
        if (!spec.isRecycler()) {
            return false;
        }
        return randomNextIntBound.applyAsInt(spec.scrapChanceDenominator()) == 0;
    }

    // --- Energy API ---

    /** Returns the effective IC2 input tier ({@code baseTier + transformerUpgrades}, capped at 14). */
    public int inputTier() {
        return upgrades.effectiveTier(baseTier);
    }

    /** Returns the maximum single-packet EU input voltage for the current tier. */
    public double maxInputPacket() {
        return SolarTier.packetSize(inputTier());
    }

    /** Returns total EU storage capacity including Energy Storage Upgrades. */
    public double energyCapacity() {
        return upgrades.energyCapacity(spec.baseEnergyCapacity());
    }

    /** Returns current stored EU in {@code [0, energyCapacity()]}. */
    public double storedEu() {
        return storedEu;
    }

    /** Sets stored EU (clamped to {@code [0, energyCapacity()]}). */
    public void setStoredEu(double eu) {
        if (!Double.isFinite(eu) || eu <= 0.0) {
            this.storedEu = 0.0;
        } else {
            this.storedEu = Math.min(energyCapacity(), eu);
        }
    }

    /** Returns remaining EU buffer space ({@code max(0, energyCapacity() - storedEu)}). */
    public double demandedEu() {
        return Math.max(0.0, energyCapacity() - storedEu);
    }

    /**
     * Injects EU into the internal buffer and returns the exact unaccepted remainder.
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

    // --- Water Tank API (Ore Washer & water-using addons) ---

    /** Returns the water tank capacity in mB ({@code 0} if machine does not use water). */
    public int waterTankCapacityMb() {
        return waterTankCapacityMb;
    }

    /** Returns the current stored water in mB. */
    public int waterMb() {
        return waterMb;
    }

    /** Sets stored water in mB, clamped to {@code [0, waterTankCapacityMb()]}. */
    public void setWaterMb(int mb) {
        this.waterMb = Math.max(0, Math.min(waterTankCapacityMb, mb));
    }

    /**
     * Fills water into the internal tank.
     *
     * @param amountMb amount of water to insert in mB
     * @param simulate if {@code true}, calculates accepted amount without mutating state
     * @return accepted mB ({@code 0} for non-water machines)
     */
    public int fillWater(int amountMb, boolean simulate) {
        if (!spec.usesWater() || amountMb <= 0) {
            return 0;
        }
        int room = Math.max(0, waterTankCapacityMb - waterMb);
        int accepted = Math.min(room, amountMb);
        if (!simulate) {
            waterMb += accepted;
        }
        return accepted;
    }

    /**
     * Drains water from the internal tank.
     *
     * @param maxDrainMb maximum mB to drain
     * @param simulate   if {@code true}, calculates drained amount without mutating state
     * @return drained mB
     */
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

    // --- Upgrades & Derived Parameters ---

    /** Returns the active {@link GiantUpgradeProfile}. */
    public GiantUpgradeProfile upgrades() {
        return upgrades;
    }

    /**
     * Updates the installed upgrade profile and clamps stored EU to the new capacity.
     *
     * @param upgrades new upgrade profile
     */
    public void setUpgrades(GiantUpgradeProfile upgrades) {
        this.upgrades = Objects.requireNonNull(upgrades, "upgrades");
        this.storedEu = Math.min(this.storedEu, energyCapacity());
    }

    /** Returns upgraded operation duration in ticks per lane cycle. */
    public int operationTicks() {
        return upgrades.operationTicks(spec.baseOperationTicks());
    }

    /** Returns upgraded EU/t per single active operation. */
    public double upgradedEuPerTick() {
        return upgrades.euPerTickPerOp(spec.baseEuPerTick());
    }

    // --- Lane & Telemetry Accessors ---

    /** Returns the machine specification. */
    public GiantMachineSpec spec() {
        return spec;
    }

    /** Returns the mutable 8-corner {@link GiantPortPermissions} table. */
    public GiantPortPermissions portPermissions() {
        return portPermissions;
    }

    /** Returns current tick progress of {@code lane} ({@code 0..operationTicks()}). */
    public int laneProgress(int lane) {
        checkLaneIndex(lane);
        return progress[lane];
    }

    /** Returns current locked batch size of {@code lane} ({@code 0..3}). */
    public int laneBatchSize(int lane) {
        checkLaneIndex(lane);
        return batchSize[lane];
    }

    /** Returns {@code true} if {@code lane} actively advanced during the last tick. */
    public boolean isLaneActive(int lane) {
        checkLaneIndex(lane);
        return laneActive[lane];
    }

    /**
     * Scales a lane's progress to an integer GUI micro-progress bar length.
     *
     * @param lane      lane index ({@code 0..8})
     * @param barLength pixel length
     * @return scaled pixel count in {@code [0, barLength]}
     */
    public int scaledLaneProgress(int lane, int barLength) {
        checkLaneIndex(lane);
        if (barLength <= 0 || batchSize[lane] <= 0) {
            return 0;
        }
        int opTicks = operationTicks();
        return Math.min(barLength, (progress[lane] * barLength) / opTicks);
    }

    /** Returns total EU consumed across all advancing lanes during the last tick. */
    public double lastTickEuConsumed() {
        return lastTickEuConsumed;
    }

    /** Returns sum of batch sizes across all lanes that advanced during the last tick ({@code 0..27}). */
    public int lastActiveTotalBatch() {
        return lastActiveTotalBatch;
    }

    /** Returns {@code true} if any lane was active on the most recent tick. */
    public boolean isActive() {
        return active;
    }

    /**
     * Restores persisted lane progress and batch arrays from NBT/serialization.
     *
     * @param savedProgress  array of length 9
     * @param savedBatchSize array of length 9
     */
    public void restoreLanes(int[] savedProgress, int[] savedBatchSize) {
        Objects.requireNonNull(savedProgress, "savedProgress");
        Objects.requireNonNull(savedBatchSize, "savedBatchSize");
        if (savedProgress.length != LANE_COUNT || savedBatchSize.length != LANE_COUNT) {
            throw new IllegalArgumentException("Lane arrays must have length " + LANE_COUNT);
        }
        int opTicks = operationTicks();
        for (int i = 0; i < LANE_COUNT; i++) {
            int b = Math.max(0, Math.min(maxBatchPerLane, savedBatchSize[i]));
            batchSize[i] = b;
            progress[i] = b > 0 ? Math.max(0, Math.min(opTicks, savedProgress[i])) : 0;
            laneActive[i] = false;
        }
    }

    /** Returns defensive copies of all 9 lane progress values. */
    public int[] copyLaneProgress() {
        return Arrays.copyOf(progress, LANE_COUNT);
    }

    /** Returns defensive copies of all 9 lane batch sizes. */
    public int[] copyLaneBatchSizes() {
        return Arrays.copyOf(batchSize, LANE_COUNT);
    }

    private static int clampBatch(int value, int upperBound) {
        return Math.max(0, Math.min(upperBound, value));
    }

    private static void checkLaneIndex(int lane) {
        if (lane < 0 || lane >= LANE_COUNT) {
            throw new IndexOutOfBoundsException("lane must be in 0..8, got " + lane);
        }
    }
}
