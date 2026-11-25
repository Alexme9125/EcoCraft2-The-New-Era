package com.ecocraft2.giant;

/**
 * Platform-neutral multi-tank view exposed by EcoCraft 2 giant and gas machines, bridged cleanly to
 * NeoForge 1.21.1 {@code IFluidHandler} and NeoForge 26.x transactional {@code ResourceHandler}.
 */
public interface Ecc2FluidTankView {

    int tankCount();

    /** Full namespaced fluid ID (e.g. {@code "minecraft:water"}), or {@code ""} when empty. */
    String fluidId(int tank);

    int fluidAmount(int tank);

    int tankCapacity(int tank);

    boolean acceptsFluid(int tank, String fluidId);

    boolean allowsInput();

    boolean allowsOutput();

    /**
     * Inserts fluid into any matching input tank (or {@code tank >= 0} if specified).
     *
     * @param tank target tank index, or {@code -1} to match any eligible input tank
     * @param fluidId full namespaced fluid ID
     * @param amount mB to insert
     * @param simulate when {@code true}, performs no mutation
     * @return accepted mB
     */
    int fillFluid(int tank, String fluidId, int amount, boolean simulate);

    /**
     * Drains fluid from an eligible output tank (or {@code tank >= 0} if specified).
     *
     * @param tank target tank index, or {@code -1} to match any eligible output tank
     * @param fluidId required fluid ID, or {@code ""} to drain whatever fluid is present
     * @param maxDrain maximum mB to drain
     * @param simulate when {@code true}, performs no mutation
     * @return drained mB
     */
    int drainFluid(int tank, String fluidId, int maxDrain, boolean simulate);

    /** Fluid ID that would be drained by {@code drainFluid(-1, "", ...)} or {@code ""}. */
    default String defaultDrainFluidId() {
        for (int i = 0; i < tankCount(); i++) {
            if (fluidAmount(i) > 0 && !fluidId(i).isEmpty()) {
                return fluidId(i);
            }
        }
        return "";
    }

    /** Captures a rollback snapshot for NeoForge 26.x transactional transfers. */
    default Snapshot captureSnapshot() {
        int n = tankCount();
        String[] ids = new String[n];
        int[] amounts = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = fluidId(i);
            amounts[i] = fluidAmount(i);
        }
        return new Snapshot(ids, amounts);
    }

    /** Restores a previously captured snapshot on transaction rollback. */
    void restoreSnapshot(Snapshot snapshot);

    /** Notifies the backing BlockEntity that its tank state changed. */
    void markChanged();

    record Snapshot(String[] fluidIds, int[] amounts) {}
}
