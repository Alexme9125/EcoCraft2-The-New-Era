package com.ecocraft2.common.solar;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;

import java.util.Objects;

/**
 * Pure-Java state machine for the Molecular Transformer.
 *
 * <p>Fixes all upstream ASP Molecular Transformer defects:
 * <ul>
 *     <li>{@link #demanded()} returns exact {@code max(0, totalEu - energyUsed - energyIn)}.</li>
 *     <li>{@link #inject(double)} accepts {@code min(amount, demanded())} and returns the exact unaccepted remainder.</li>
 *     <li>Clears {@code energyIn} on recipe completion so surplus energy never bleeds into the next craft.</li>
 *     <li>Pauses completion when output cannot fit so zero input or output items are ever lost.</li>
 *     <li>Uses an {@code int idleTicks} counter capped at {@value #MAX_IDLE_TICKS} (zero signed-byte overflow flicker).</li>
 *     <li>Rejects non-positive or non-finite {@code totalEu <= 0}.</li>
 * </ul>
 */
public final class MolecularTransformerEngine {

    /** Unlimited input tier (Tier 14, never explodes from over-voltage). */
    public static final int INPUT_TIER = Ecc2ConfigDefaults.MOLECULAR_TRANSFORMER_INPUT_TIER;

    /** Maximum idle grace ticks before visual active state turns off. */
    public static final int MAX_IDLE_TICKS = Ecc2ConfigDefaults.MOLECULAR_TRANSFORMER_MAX_IDLE_TICKS;

    private static final double COMPLETION_EPSILON = 1e-9;

    /** Result of a single Molecular Transformer tick. */
    public enum TickResult {
        /** No valid recipe is present and no work was interrupted. */
        IDLE,
        /** An in-progress craft was aborted because the input or recipe disappeared. */
        CANCELLED,
        /** Actively accumulating EU toward the current recipe. */
        WORKING,
        /** Required EU reached, but paused because the output slot cannot accept the result yet. */
        OUTPUT_BLOCKED,
        /** Recipe finished cleanly; caller should consume input and insert output. */
        COMPLETED
    }

    private boolean working;
    private String activeRecipeKey;
    private double totalEu;
    private double energyUsed;
    private double energyIn;
    private double lastTickPower;
    private int idleTicks = MAX_IDLE_TICKS;
    private boolean outputBlocked;

    /**
     * Returns the exact remaining EU demanded for the active craft:
     * {@code max(0, totalEu - energyUsed - energyIn)}.
     *
     * @return demanded EU ({@code >= 0})
     */
    public double demanded() {
        if (!working || totalEu <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, totalEu - energyUsed - energyIn);
    }

    /**
     * Injects EU into the transformer, accepting only {@code min(amount, demanded())}
     * and returning the exact unaccepted remainder.
     *
     * @param amount offered EU from the power grid
     * @return unaccepted remainder EU (equals {@code 0.0} if {@code amount <= 0} or non-finite;
     *         equals {@code amount - accepted} for positive finite amounts)
     */
    public double inject(double amount) {
        if (amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        double wanted = demanded();
        double accepted = Math.min(amount, wanted);
        energyIn += accepted;
        return amount - accepted;
    }

    /**
     * Explicitly begins a craft for a recipe requiring {@code recipeTotalEu} EU.
     *
     * @param recipeKey     optional recipe identifier (may be {@code null})
     * @param recipeTotalEu required EU (must be strictly positive and finite)
     * @throws IllegalArgumentException if {@code recipeTotalEu <= 0} or non-finite
     */
    public void beginRecipe(String recipeKey, double recipeTotalEu) {
        if (recipeTotalEu <= 0.0 || !Double.isFinite(recipeTotalEu)) {
            throw new IllegalArgumentException("recipeTotalEu must be positive and finite: " + recipeTotalEu);
        }
        this.working = true;
        this.activeRecipeKey = recipeKey;
        this.totalEu = recipeTotalEu;
        this.energyUsed = 0.0;
        this.energyIn = 0.0;
        this.outputBlocked = false;
    }

    /**
     * Advances the Molecular Transformer by one tick without a recipe key.
     *
     * @param hasValidRecipe  whether a matching recipe exists for the input slot
     * @param recipeTotalEu   EU required by the recipe (values {@code <= 0} are rejected)
     * @param canAcceptOutput whether the output slot can currently receive the recipe output
     * @return {@link TickResult} describing what happened on this tick
     */
    public TickResult tick(boolean hasValidRecipe, double recipeTotalEu, boolean canAcceptOutput) {
        return tick(hasValidRecipe ? "" : null, recipeTotalEu, canAcceptOutput);
    }

    /**
     * Advances the Molecular Transformer by one tick.
     *
     * @param recipeKey       non-null recipe key if a valid recipe matches the input slot, or {@code null} if none
     * @param recipeTotalEu   EU required by the recipe (non-positive or non-finite values are rejected)
     * @param canAcceptOutput whether the output slot can currently receive the recipe output
     * @return {@link TickResult} describing what happened on this tick
     */
    public TickResult tick(String recipeKey, double recipeTotalEu, boolean canAcceptOutput) {
        boolean validRecipe = recipeKey != null && recipeTotalEu > 0.0 && Double.isFinite(recipeTotalEu);
        if (!validRecipe) {
            boolean wasWorking = working;
            resetWork();
            lastTickPower = 0.0;
            incrementIdleTicks();
            return wasWorking ? TickResult.CANCELLED : TickResult.IDLE;
        }

        // If recipe changed mid-craft (different key or different EU requirement), restart cleanly.
        if (working && (!Objects.equals(activeRecipeKey, recipeKey) || Double.compare(totalEu, recipeTotalEu) != 0)) {
            resetWork();
        }

        if (!working) {
            if (!canAcceptOutput) {
                lastTickPower = 0.0;
                outputBlocked = true;
                incrementIdleTicks();
                return TickResult.OUTPUT_BLOCKED;
            }
            beginRecipe(recipeKey, recipeTotalEu);
        }

        double consumed = energyIn;
        lastTickPower = consumed;
        energyIn = 0.0;

        if (consumed > 0.0) {
            energyUsed = Math.min(totalEu, energyUsed + consumed);
            idleTicks = 0;
        } else {
            incrementIdleTicks();
        }

        if (energyUsed >= totalEu - COMPLETION_EPSILON) {
            if (!canAcceptOutput) {
                energyUsed = totalEu;
                energyIn = 0.0;
                outputBlocked = true;
                return TickResult.OUTPUT_BLOCKED;
            }
            // Complete cleanly: clear energyIn and reset state for next craft.
            resetWork();
            idleTicks = 0;
            return TickResult.COMPLETED;
        }

        outputBlocked = false;
        return TickResult.WORKING;
    }

    /** Returns {@code true} if a craft is currently active (including paused on full output). */
    public boolean isWorking() {
        return working;
    }

    /** Returns {@code true} if completion or start is currently paused because output cannot fit. */
    public boolean isOutputBlocked() {
        return outputBlocked;
    }

    /**
     * Returns {@code true} if the transformer should render its active plasma core and emit light.
     * Guaranteed never to wrap or flicker after prolonged idle time.
     */
    public boolean isActive() {
        return idleTicks < MAX_IDLE_TICKS;
    }

    /** Returns the recipe key of the active craft, or {@code null} when idle. */
    public String activeRecipeKey() {
        return activeRecipeKey;
    }

    /** Returns the required EU of the active craft, or {@code 0.0} when idle. */
    public double totalEu() {
        return totalEu;
    }

    /** Returns cumulative EU consumed toward the active craft. */
    public double energyUsed() {
        return energyUsed;
    }

    /** Returns EU buffered since the last tick awaiting consumption. */
    public double energyIn() {
        return energyIn;
    }

    /** Returns EU consumed during the most recent tick (for GUI EU/t readout). */
    public double lastTickPower() {
        return lastTickPower;
    }

    /** Returns the current idle tick counter, always in {@code [0, MAX_IDLE_TICKS]}. */
    public int idleTicks() {
        return idleTicks;
    }

    /** Returns normalized craft progress in {@code [0.0, 1.0]}. */
    public double progress() {
        if (!working || totalEu <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, energyUsed / totalEu));
    }

    /** Returns integer progress percentage in {@code [0, 100]}. */
    public int progressPercent() {
        return (int) Math.floor(progress() * 100.0);
    }

    /**
     * Scales craft progress to a GUI bar length.
     *
     * @param barLength bar pixel length
     * @return scaled pixel count in {@code [0, barLength]}
     */
    public int scaledProgress(int barLength) {
        if (barLength <= 0) {
            return 0;
        }
        return (int) Math.min(barLength, Math.floor(progress() * barLength));
    }

    /**
     * Restores persisted engine state from NBT/serialization with strict bounds validation.
     *
     * @param working         whether a craft was in progress
     * @param activeRecipeKey active recipe key
     * @param totalEu         required EU for the active craft
     * @param energyUsed      EU already consumed
     * @param energyIn        buffered EU awaiting tick consumption
     * @param idleTicks       idle counter
     */
    public void restoreState(
            boolean working,
            String activeRecipeKey,
            double totalEu,
            double energyUsed,
            double energyIn,
            int idleTicks
    ) {
        this.idleTicks = Math.max(0, Math.min(MAX_IDLE_TICKS, idleTicks));
        this.lastTickPower = 0.0;
        this.outputBlocked = false;
        if (working && totalEu > 0.0 && Double.isFinite(totalEu)) {
            this.working = true;
            this.activeRecipeKey = activeRecipeKey;
            this.totalEu = totalEu;
            this.energyUsed = sanitizeNonNegative(energyUsed, totalEu);
            this.energyIn = sanitizeNonNegative(energyIn, Math.max(0.0, totalEu - this.energyUsed));
        } else {
            resetWork();
        }
    }

    private void resetWork() {
        this.working = false;
        this.activeRecipeKey = null;
        this.totalEu = 0.0;
        this.energyUsed = 0.0;
        this.energyIn = 0.0;
        this.outputBlocked = false;
    }

    private void incrementIdleTicks() {
        if (idleTicks < MAX_IDLE_TICKS) {
            idleTicks++;
        }
    }

    private static double sanitizeNonNegative(double value, double max) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0.0;
        }
        return Math.min(value, max);
    }
}
