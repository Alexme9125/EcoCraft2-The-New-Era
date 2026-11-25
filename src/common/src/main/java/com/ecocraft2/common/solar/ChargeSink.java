package com.ecocraft2.common.solar;

/**
 * Pure-Java callback representing an electric item or charging slot that can receive EU.
 */
@FunctionalInterface
public interface ChargeSink {

    /**
     * Attempts to transfer up to {@code availableEu} into this sink at the source's power {@code tier}.
     *
     * @param availableEu maximum EU offered (always {@code >= 0})
     * @param tier        charging source IC2 tier
     * @return actual EU accepted (clamped by callers to {@code [0, availableEu]})
     */
    double charge(double availableEu, int tier);
}
