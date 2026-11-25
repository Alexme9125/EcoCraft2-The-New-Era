package com.ecocraft2.common.gas;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;
import com.ecocraft2.common.solar.ChargeSink;
import com.ecocraft2.common.solar.SolarTier;

import java.util.Objects;

/**
 * Pure-Java state machine for the Gas Generator ({@code ecc2:gas_generator}).
 *
 * <p>Burns {@link GasFuelSpec} fuels from a 16,000 mB tank into a 16,000 EU internal buffer
 * with MV Tier 2 (128 EU/t) output packets. Uses exact combustion chamber accounting
 * ({@code pendingEu} / fractional mB remainder) so zero EU is ever lost across fractional mB/t
 * burn rates (e.g. 3.2 mB/t Water Gas or 6.4 mB/t Natural Gas).
 */
public final class GasGeneratorEngine {

    /** Gas fuel tank capacity in mB. */
    public static final int GAS_TANK_CAPACITY_MB = Ecc2ConfigDefaults.GAS_GENERATOR_TANK_CAPACITY_MB;

    /** Internal EU storage buffer capacity. */
    public static final double EU_CAPACITY = Ecc2ConfigDefaults.GAS_GENERATOR_EU_CAPACITY;

    /** Output IC2 voltage tier (Tier 2 = MV = 128 EU/t packet limit). */
    public static final int OUTPUT_TIER = Ecc2ConfigDefaults.GAS_GENERATOR_OUTPUT_TIER;

    private static final double EPSILON = 1e-12;

    private GasFuelSpec currentFuel;
    private int gasMb;
    private double pendingEu;
    private double storedEu;
    private double lastTickGeneratedEu;
    private boolean active;

    /**
     * Advances the Gas Generator by one tick without charging an internal battery slot.
     *
     * @return EU generated during this tick
     */
    public double tick() {
        return tick(null);
    }

    /**
     * Advances the Gas Generator by one tick: burns fuel into the EU buffer (only up to available buffer room)
     * and optionally charges the internal charging slot prior to grid draw.
     *
     * @param chargeSlot optional electric item charging slot (may be {@code null})
     * @return EU generated from fuel combustion during this tick
     */
    public double tick(ChargeSink chargeSlot) {
        double generated = 0.0;
        if (currentFuel != null && storedEu < EU_CAPACITY - EPSILON) {
            double euPerMb = currentFuel.euPerMb();
            double totalAvailableEu = pendingEu + gasMb * euPerMb;
            double room = EU_CAPACITY - storedEu;
            double desiredEu = Math.min(currentFuel.euPerTick(), room);
            double actualEu = Math.min(desiredEu, totalAvailableEu);

            if (actualEu > EPSILON) {
                if (pendingEu + EPSILON < actualEu) {
                    double deficitEu = actualEu - pendingEu;
                    int mbNeeded = (int) Math.ceil((deficitEu / euPerMb) - EPSILON);
                    mbNeeded = Math.min(mbNeeded, gasMb);
                    gasMb -= mbNeeded;
                    pendingEu += mbNeeded * euPerMb;
                }
                pendingEu = Math.max(0.0, pendingEu - actualEu);
                if (pendingEu < EPSILON) {
                    pendingEu = 0.0;
                }
                storedEu = Math.min(EU_CAPACITY, storedEu + actualEu);
                generated = actualEu;
            }

            if (gasMb == 0 && pendingEu <= EPSILON) {
                pendingEu = 0.0;
                currentFuel = null;
            }
        }

        this.lastTickGeneratedEu = generated;
        this.active = generated > EPSILON;

        if (chargeSlot != null && storedEu > 0.0) {
            chargeSlot(chargeSlot);
        }
        return generated;
    }

    /**
     * Charges an electric item slot using up to {@link #offeredEnergy()} at {@value #OUTPUT_TIER}.
     *
     * @param sink target charging slot
     * @return actual EU transferred
     */
    public double chargeSlot(ChargeSink sink) {
        Objects.requireNonNull(sink, "sink");
        double offer = offeredEnergy();
        if (offer <= 0.0) {
            return 0.0;
        }
        double raw = sink.charge(offer, OUTPUT_TIER);
        double accepted = (!Double.isFinite(raw) || raw <= 0.0) ? 0.0 : Math.min(raw, offer);
        storedEu = Math.max(0.0, storedEu - accepted);
        return accepted;
    }

    /** Returns the MV Tier 2 output packet limit (128 EU/t). */
    public double packetSize() {
        return SolarTier.packetSize(OUTPUT_TIER);
    }

    /** Returns EU available for grid emission this tick: {@code min(storedEu, 128.0)}. */
    public double offeredEnergy() {
        return Math.min(storedEu, packetSize());
    }

    /**
     * Draws energy for the power grid, strictly clamping storage {@code >= 0}.
     *
     * @param amount requested EU
     * @return actual EU drawn
     */
    public double drawEnergy(double amount) {
        if (amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        double drawn = Math.min(storedEu, amount);
        storedEu = Math.max(0.0, storedEu - drawn);
        return drawn;
    }

    /**
     * Fills combustible gas into the 16,000 mB tank. Refuses mixing a different gas while fuel remains.
     *
     * @param fuel     gas fuel type
     * @param amountMb volume to insert in mB
     * @param simulate if {@code true}, computes accepted mB without mutating state
     * @return accepted mB
     */
    public int fillGas(GasFuelSpec fuel, int amountMb, boolean simulate) {
        if (fuel == null || amountMb <= 0) {
            return 0;
        }
        boolean hasFuel = gasMb > 0 || pendingEu > EPSILON;
        if (hasFuel && currentFuel != null && currentFuel != fuel) {
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
     * Drains uncombusted gas from the tank.
     *
     * @param maxDrainMb maximum mB to drain
     * @param simulate   if {@code true}, computes drained mB without mutating state
     * @return drained mB
     */
    public int drainGas(int maxDrainMb, boolean simulate) {
        if (maxDrainMb <= 0 || gasMb <= 0) {
            return 0;
        }
        int drained = Math.min(gasMb, maxDrainMb);
        if (!simulate) {
            gasMb -= drained;
            if (gasMb == 0 && pendingEu <= EPSILON) {
                pendingEu = 0.0;
                currentFuel = null;
            }
        }
        return drained;
    }

    /** Returns the active {@link GasFuelSpec}, or {@code null} when completely empty. */
    public GasFuelSpec currentFuel() {
        return currentFuel;
    }

    /** Returns integer gas volume remaining in the tank ({@code 0..16,000} mB). */
    public int gasMb() {
        return gasMb;
    }

    /** Returns remaining combustion-chamber EU from the last drawn mB (for exact persistence). */
    public double pendingEu() {
        return pendingEu;
    }

    /** Returns remaining fractional mB in the combustion chamber in {@code [0.0, 1.0)}. */
    public double fractionalMbRemaining() {
        if (currentFuel == null || pendingEu <= 0.0) {
            return 0.0;
        }
        return pendingEu / currentFuel.euPerMb();
    }

    /** Returns total remaining EU potential across both tank and combustion chamber. */
    public double totalRemainingPotentialEu() {
        if (currentFuel == null) {
            return 0.0;
        }
        return pendingEu + gasMb * currentFuel.euPerMb();
    }

    /** Returns current EU stored in the 16,000 EU buffer. */
    public double storedEu() {
        return storedEu;
    }

    /** Sets stored EU clamped to {@code [0, 16,000]}. */
    public void setStoredEu(double eu) {
        if (!Double.isFinite(eu) || eu <= 0.0) {
            this.storedEu = 0.0;
        } else {
            this.storedEu = Math.min(EU_CAPACITY, eu);
        }
    }

    /** Returns EU produced from gas combustion on the most recent tick. */
    public double lastTickGeneratedEu() {
        return lastTickGeneratedEu;
    }

    /** Returns {@code true} if fuel was combusted on the most recent tick. */
    public boolean isActive() {
        return active;
    }

    /**
     * Restores persisted generator state from NBT/serialization.
     *
     * @param fuel      saved gas fuel type
     * @param gasMb     saved tank volume in mB
     * @param pendingEu saved combustion-chamber pending EU
     * @param storedEu  saved EU buffer
     */
    public void restoreState(GasFuelSpec fuel, int gasMb, double pendingEu, double storedEu) {
        setStoredEu(storedEu);
        int clampedGas = Math.max(0, Math.min(GAS_TANK_CAPACITY_MB, gasMb));
        double clampedPending = (fuel != null && Double.isFinite(pendingEu) && pendingEu > 0.0)
                ? Math.min(pendingEu, fuel.euPerMb())
                : 0.0;
        if (fuel != null && (clampedGas > 0 || clampedPending > EPSILON)) {
            this.currentFuel = fuel;
            this.gasMb = clampedGas;
            this.pendingEu = clampedPending;
        } else {
            this.currentFuel = null;
            this.gasMb = 0;
            this.pendingEu = 0.0;
        }
        this.lastTickGeneratedEu = 0.0;
        this.active = false;
    }
}
