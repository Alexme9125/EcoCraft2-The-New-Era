package com.ecocraft2.common.giant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure-Java deterministic planner that evenly distributes items across a Giant Machine's
 * 9 input slots ({@code 0..8}).
 *
 * <p>Behavioral guarantees:
 * <ul>
 *     <li><strong>Strict item conservation</strong>: for every distinct item group, total count after
 *         planning equals total count before planning; any invariant violation aborts cleanly with
 *         {@link PlanResult#changed()} {@code == false}.</li>
 *     <li><strong>Mixed-item fairness without eviction</strong>: each item type keeps its own
 *         occupied slots and receives a proportional (D'Hondt) share of empty slots, never stealing
 *         slots occupied by other item types.</li>
 *     <li><strong>Multi-input recipe alignment</strong>: honors {@code minUnit} (recipe input count
 *         per operation, clamped to slot capacity) so multi-item recipes (e.g., 4-item or 9-item
 *         Compressor recipes) never deadlock as uncraftable 1-item splits, and scraps below
 *         {@code minUnit} consolidate onto a single slot.</li>
 *     <li><strong>Cap &amp; abnormal stack safety</strong>: respects {@code maxStackSize} (including
 *         unstackable {@code 1} and {@code 16}-stack items) and freezes any abnormally oversized
 *         stack ({@code count > maxStackSize}) untouched rather than truncating it.</li>
 *     <li><strong>Minimal churn &amp; single-pass idempotency</strong>: preserves existing slot
 *         indices whenever possible so active lanes keep their progress, and re-running on an
 *         already-planned grid returns {@code changed == false}.</li>
 * </ul>
 */
public final class GiantInputDistributor {

    /** Sentinel group key representing an empty slot. */
    public static final int EMPTY_GROUP = -1;

    private GiantInputDistributor() {}

    /**
     * Immutable input description of one inventory slot before redistribution.
     *
     * @param groupKey     non-negative ID identifying exact item+components/NBT equivalence, or
     *                     {@link #EMPTY_GROUP} ({@code -1}) when empty
     * @param count        current item count ({@code 0} when empty)
     * @param maxStackSize effective per-slot stack cap {@code min(containerLimit, itemMaxStackSize)}
     * @param minUnit      recipe input count per operation ({@code >= 1})
     */
    public record SlotInput(int groupKey, int count, int maxStackSize, int minUnit) {
        /** Convenience factory for an empty slot. */
        public static SlotInput empty() {
            return new SlotInput(EMPTY_GROUP, 0, 64, 1);
        }

        /** Returns {@code true} if this slot contains no items. */
        public boolean isEmpty() {
            return groupKey < 0 || count <= 0;
        }
    }

    /**
     * Planned target state of one inventory slot after redistribution.
     *
     * @param groupKey target group ID, or {@link #EMPTY_GROUP} ({@code -1}) when empty
     * @param count    target item count ({@code 0} when empty)
     */
    public record SlotOutput(int groupKey, int count) {
        /** Empty slot output singleton. */
        public static final SlotOutput EMPTY = new SlotOutput(EMPTY_GROUP, 0);

        /** Returns {@code true} if this planned slot is empty. */
        public boolean isEmpty() {
            return groupKey < 0 || count <= 0;
        }
    }

    /**
     * Result of planning an input redistribution across all slots.
     *
     * @param outputs planned slot outputs matching the input array length
     * @param changed {@code true} iff at least one slot's group or count changed
     */
    public record PlanResult(SlotOutput[] outputs, boolean changed) {
        /** Creates an unchanged plan mirroring {@code inputs}. */
        public static PlanResult unchanged(SlotInput[] inputs) {
            SlotOutput[] copy = new SlotOutput[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                SlotInput in = inputs[i];
                copy[i] =
                        (in == null || in.isEmpty())
                                ? SlotOutput.EMPTY
                                : new SlotOutput(in.groupKey(), in.count());
            }
            return new PlanResult(copy, false);
        }
    }

    private static final class GroupState {
        final int groupKey;
        final List<Integer> ownedSlots = new ArrayList<>();
        final List<Integer> assignedSlots = new ArrayList<>();
        long total;
        int cap = Integer.MAX_VALUE;
        int minUnit = 1;
        int effectiveUnit = 1;
        int desiredSlots = 1;

        GroupState(int groupKey) {
            this.groupKey = groupKey;
        }
    }

    /**
     * Computes a deterministic, strictly conservative redistribution plan for {@code inputs}.
     *
     * @param inputs current slot states (typically length 9)
     * @return redistribution plan; {@link PlanResult#changed()} is {@code false} if no move is
     *     needed or if any safety check rejects mutation
     */
    public static PlanResult plan(SlotInput[] inputs) {
        Objects.requireNonNull(inputs, "inputs");
        int n = inputs.length;
        if (n == 0) {
            return new PlanResult(new SlotOutput[0], false);
        }

        SlotOutput[] planned = new SlotOutput[n];
        boolean[] frozen = new boolean[n];
        int[] initialCount = new int[n];
        int[] initialGroup = new int[n];

        Map<Integer, GroupState> groups = new LinkedHashMap<>();
        List<Integer> emptySlots = new ArrayList<>();

        // Phase 0: classify empty, frozen (abnormally oversized), and participating slots.
        for (int i = 0; i < n; i++) {
            SlotInput in = inputs[i];
            if (in == null || in.isEmpty()) {
                initialGroup[i] = EMPTY_GROUP;
                initialCount[i] = 0;
                planned[i] = SlotOutput.EMPTY;
                emptySlots.add(i);
                continue;
            }
            int key = in.groupKey();
            int count = in.count();
            int cap = Math.max(1, in.maxStackSize());
            initialGroup[i] = key;
            initialCount[i] = count;

            if (count > cap) {
                // Abnormally oversized modded stack: freeze untouched, never truncate or merge.
                frozen[i] = true;
                planned[i] = new SlotOutput(key, count);
                continue;
            }

            GroupState gs = groups.computeIfAbsent(key, GroupState::new);
            gs.ownedSlots.add(i);
            gs.total += count;
            gs.cap = Math.min(gs.cap, cap);
            gs.minUnit = Math.max(gs.minUnit, Math.max(1, in.minUnit()));
        }

        if (groups.isEmpty()) {
            return PlanResult.unchanged(inputs);
        }

        // Phase 1a: compute each group's desired slot count and release voluntarily consolidated
        // slots (only possible when minUnit > 1 consolidates uncraftable scraps, guaranteeing
        // single-pass idempotency without ever stealing another group's active slot).
        List<GroupState> groupList = new ArrayList<>(groups.values());
        for (GroupState gs : groupList) {
            gs.effectiveUnit = Math.min(gs.minUnit, gs.cap);
            long minReq = ceilDiv(gs.total, gs.cap);
            long fullUnits = gs.total / gs.effectiveUnit;
            long desired = Math.max(minReq, Math.max(1L, fullUnits));
            gs.desiredSlots = (int) Math.min(n, desired);

            // Sort owned slots by descending existing count, then ascending slot index so
            // highest-count / running lanes are preserved first.
            gs.ownedSlots.sort(
                    Comparator.<Integer>comparingInt(idx -> initialCount[idx])
                            .reversed()
                            .thenComparingInt(Integer::intValue));

            int keepCount = Math.min(gs.ownedSlots.size(), gs.desiredSlots);
            for (int i = 0; i < keepCount; i++) {
                gs.assignedSlots.add(gs.ownedSlots.get(i));
            }
            for (int i = keepCount; i < gs.ownedSlots.size(); i++) {
                emptySlots.add(gs.ownedSlots.get(i));
            }
        }
        emptySlots.sort(Integer::compareTo);

        // Phase 1b: proportionally allocate available empty slots using exact-integer D'Hondt
        // weighted by total item count (tie-breaking by fewer assigned slots, then group order).
        int emptyIdx = 0;
        while (emptyIdx < emptySlots.size()) {
            GroupState best = null;
            for (GroupState gs : groupList) {
                if (gs.assignedSlots.size() >= gs.desiredSlots) {
                    continue;
                }
                if (best == null) {
                    best = gs;
                    continue;
                }
                long lhs = gs.total * (best.assignedSlots.size() + 1L);
                long rhs = best.total * (gs.assignedSlots.size() + 1L);
                if (lhs > rhs) {
                    best = gs;
                } else if (lhs == rhs && gs.assignedSlots.size() < best.assignedSlots.size()) {
                    best = gs;
                }
            }
            if (best == null) {
                break;
            }
            best.assignedSlots.add(emptySlots.get(emptyIdx++));
        }

        // Reset all non-frozen slots to EMPTY before populating planned group targets.
        for (int i = 0; i < n; i++) {
            if (!frozen[i]) {
                planned[i] = SlotOutput.EMPTY;
            }
        }

        // Phase 2 & 3: compute target counts per group with recipe-unit alignment and minimal churn.
        for (GroupState gs : groupList) {
            int k = gs.assignedSlots.size();
            if (k <= 0) {
                return PlanResult.unchanged(inputs);
            }
            int[] targetCounts = computeGroupCounts(gs.total, k, gs.cap, gs.effectiveUnit);

            // Order assigned slots by existing count of THIS group descending, then slot index ascending.
            // Owned slots (count > 0) naturally precede newly allocated empty slots (count == 0).
            gs.assignedSlots.sort(
                    Comparator.<Integer>comparingInt(
                                    idx -> initialGroup[idx] == gs.groupKey ? initialCount[idx] : 0)
                            .reversed()
                            .thenComparingInt(Integer::intValue));

            for (int i = 0; i < k; i++) {
                int slotIdx = gs.assignedSlots.get(i);
                int count = targetCounts[i];
                planned[slotIdx] =
                        count > 0 ? new SlotOutput(gs.groupKey, count) : SlotOutput.EMPTY;
            }
        }

        // Phase 4: strict pre-commit conservation and bounds assertion.
        if (!verifyInvariants(inputs, planned, frozen, groups)) {
            return PlanResult.unchanged(inputs);
        }

        boolean changed = false;
        for (int i = 0; i < n; i++) {
            SlotOutput out = planned[i];
            if (out.groupKey() != initialGroup[i] || out.count() != initialCount[i]) {
                changed = true;
                break;
            }
        }
        return new PlanResult(planned, changed);
    }

    /**
     * Computes non-increasing target counts of length {@code k} summing to {@code total},
     * with each entry in {@code [0, cap]} and recipe units of size {@code unit} preserved first.
     */
    private static int[] computeGroupCounts(long total, int k, int cap, int unit) {
        int[] counts = new int[k];
        if (total <= 0 || k <= 0) {
            return counts;
        }
        int minReq = (int) Math.min(k, ceilDiv(total, cap));
        long totalUnits = total / unit;
        int activeCount = Math.min(k, Math.max(minReq, (int) Math.min(k, Math.max(1L, totalUnits))));

        if (unit <= 1) {
            long base = total / activeCount;
            int rem = (int) (total % activeCount);
            for (int i = 0; i < activeCount; i++) {
                counts[i] = (int) (base + (i < rem ? 1 : 0));
            }
            return counts;
        }

        long remaining = total;
        if (totalUnits == 0) {
            // Consolidate uncraftable scraps (< unit) into the fewest slots possible.
            for (int i = 0; i < k && remaining > 0; i++) {
                int add = (int) Math.min(remaining, cap);
                counts[i] = add;
                remaining -= add;
            }
            return counts;
        }

        // Step 1: allocate full recipe units evenly across up to activeCount slots.
        int maxUnitsPerSlot = Math.max(1, cap / unit);
        int unitSlots = (int) Math.min(activeCount, totalUnits);
        long allocatableUnits = Math.min(totalUnits, (long) unitSlots * maxUnitsPerSlot);
        long baseUnits = allocatableUnits / unitSlots;
        int extraUnits = (int) (allocatableUnits % unitSlots);
        for (int i = 0; i < unitSlots; i++) {
            long unitsHere = baseUnits + (i < extraUnits ? 1 : 0);
            int itemsHere = (int) (unitsHere * unit);
            counts[i] = itemsHere;
            remaining -= itemsHere;
        }

        // Step 2: distribute remaining items evenly across activeCount slots (water-filling up to cap),
        // falling back to all k slots only if activeCount slots are completely full.
        if (remaining > 0) {
            remaining = waterFill(counts, activeCount, cap, remaining);
        }
        if (remaining > 0) {
            waterFill(counts, k, cap, remaining);
        }

        // Ensure non-increasing order so highest counts align with highest existing slots.
        sortDescending(counts);
        return counts;
    }

    /**
     * O(limit^2) exact water-fill raising the smallest entries in {@code counts[0..limit-1]}
     * toward {@code cap} until {@code remaining} reaches 0 or all entries hit {@code cap}.
     */
    private static long waterFill(int[] counts, int limit, int cap, long remaining) {
        while (remaining > 0) {
            int minVal = Integer.MAX_VALUE;
            int nextVal = cap;
            int minCount = 0;
            for (int i = 0; i < limit; i++) {
                int v = counts[i];
                if (v >= cap) {
                    continue;
                }
                if (v < minVal) {
                    minVal = v;
                    minCount = 1;
                } else if (v == minVal) {
                    minCount++;
                }
            }
            if (minCount == 0 || minVal >= cap) {
                break;
            }
            for (int i = 0; i < limit; i++) {
                int v = counts[i];
                if (v > minVal && v < nextVal) {
                    nextVal = v;
                }
            }
            long delta = nextVal - (long) minVal;
            long fullStepCost = delta * minCount;
            if (remaining >= fullStepCost) {
                for (int i = 0; i < limit; i++) {
                    if (counts[i] == minVal) {
                        counts[i] = nextVal;
                    }
                }
                remaining -= fullStepCost;
            } else {
                long addEach = remaining / minCount;
                long extra = remaining % minCount;
                for (int i = 0; i < limit; i++) {
                    if (counts[i] == minVal) {
                        long add = addEach + (extra > 0 ? 1 : 0);
                        if (extra > 0) {
                            extra--;
                        }
                        counts[i] += (int) add;
                    }
                }
                remaining = 0;
            }
        }
        return remaining;
    }

    private static void sortDescending(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            int key = arr[i];
            int j = i - 1;
            while (j >= 0 && arr[j] < key) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    private static boolean verifyInvariants(
            SlotInput[] inputs,
            SlotOutput[] planned,
            boolean[] frozen,
            Map<Integer, GroupState> groups) {
        Map<Integer, Long> afterTotals = new LinkedHashMap<>();
        for (int i = 0; i < inputs.length; i++) {
            SlotOutput out = planned[i];
            if (out == null) {
                return false;
            }
            if (frozen[i]) {
                SlotInput in = inputs[i];
                if (in == null || out.groupKey() != in.groupKey() || out.count() != in.count()) {
                    return false;
                }
                continue;
            }
            if (out.isEmpty()) {
                if (out.groupKey() != EMPTY_GROUP || out.count() != 0) {
                    return false;
                }
                continue;
            }
            GroupState gs = groups.get(out.groupKey());
            if (gs == null || out.count() <= 0 || out.count() > gs.cap) {
                return false;
            }
            afterTotals.merge(out.groupKey(), (long) out.count(), Long::sum);
        }
        if (afterTotals.size() != groups.size()) {
            return false;
        }
        for (GroupState gs : groups.values()) {
            Long after = afterTotals.get(gs.groupKey);
            if (after == null || after != gs.total) {
                return false;
            }
        }
        return true;
    }

    private static long ceilDiv(long numerator, long denominator) {
        return (numerator + denominator - 1L) / denominator;
    }
}
