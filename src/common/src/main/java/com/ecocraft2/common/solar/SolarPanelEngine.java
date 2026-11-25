package com.ecocraft2.common.solar;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;

import java.util.List;
import java.util.Objects;

/**
 * Pure-Java state machine for Advanced Solar Panels 2 generators.
 *
 * <p>Key guarantees:
 * <ul>
 *     <li>Refreshes sky visibility every {@code checkSkyInterval} ticks (default 128), starting on tick 0.</li>
 *     <li>Clamps internal {@code storage} strictly to {@code [0, maxStorage]} (fixes negative storage bugs).</li>
 *     <li>Charges internal charging slots BEFORE offering remaining EU to the power grid.</li>
 *     <li>{@link #offeredEnergy()} equals {@code min(storage, SolarTier.packetSize(tier))}.</li>
 * </ul>
 */
public final class SolarPanelEngine {

    private final int dayPower;
    private final int nightPower;
    private final int maxStorage;
    private final int tier;
    private final int checkSkyInterval;

    private double storage;
    private SkyCondition skyCondition = SkyCondition.NONE;
    private int currentGenerating;
    private int tickCounter;

    /**
     * Creates a panel engine with default settings from the given {@link SolarTier}.
     *
     * @param tier preset solar tier
     */
    public SolarPanelEngine(SolarTier tier) {
        this(
                Objects.requireNonNull(tier, "tier").defaultDayPower(),
                tier.defaultNightPower(),
                tier.defaultMaxStorage(),
                tier.defaultTier(),
                Ecc2ConfigDefaults.SOLAR_SKY_CHECK_INTERVAL_TICKS
        );
    }

    /**
     * Creates a panel engine with custom configured power, storage, and output tier,
     * using the default 128-tick sky refresh cadence.
     *
     * @param dayPower   daytime EU/t ({@code >= 0})
     * @param nightPower nighttime/rain EU/t ({@code >= 0})
     * @param maxStorage maximum internal EU storage ({@code >= 0})
     * @param tier       IC2 voltage output tier ({@code >= 1})
     */
    public SolarPanelEngine(int dayPower, int nightPower, int maxStorage, int tier) {
        this(dayPower, nightPower, maxStorage, tier, Ecc2ConfigDefaults.SOLAR_SKY_CHECK_INTERVAL_TICKS);
    }

    /**
     * Creates a panel engine with custom parameters and sky check interval.
     *
     * @param dayPower         daytime EU/t ({@code >= 0})
     * @param nightPower       nighttime/rain EU/t ({@code >= 0})
     * @param maxStorage       maximum internal EU storage ({@code >= 0})
     * @param tier             IC2 voltage output tier ({@code >= 1})
     * @param checkSkyInterval ticks between sky checks ({@code >= 1})
     */
    public SolarPanelEngine(int dayPower, int nightPower, int maxStorage, int tier, int checkSkyInterval) {
        if (dayPower < 0) {
            throw new IllegalArgumentException("dayPower must be >= 0");
        }
        if (nightPower < 0) {
            throw new IllegalArgumentException("nightPower must be >= 0");
        }
        if (maxStorage < 0) {
            throw new IllegalArgumentException("maxStorage must be >= 0");
        }
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be >= 1");
        }
        if (checkSkyInterval < 1) {
            throw new IllegalArgumentException("checkSkyInterval must be >= 1");
        }
        this.dayPower = dayPower;
        this.nightPower = nightPower;
        this.maxStorage = maxStorage;
        this.tier = tier;
        this.checkSkyInterval = checkSkyInterval;
    }

    /**
     * Advances the solar panel by one tick without charging slots.
     *
     * @param hasSky    whether the dimension has a sky
     * @param seesSky   whether the block can see the sky
     * @param isDay     whether the world is currently daytime
     * @param isRaining whether rain is active globally
     * @param canRain   whether the biome can experience rain
     */
    public void tick(boolean hasSky, boolean seesSky, boolean isDay, boolean isRaining, boolean canRain) {
        tick(hasSky, seesSky, isDay, isRaining, canRain, List.of());
    }

    /**
     * Advances the solar panel by one tick, refreshing sky state on tick 0 and every
     * {@code checkSkyInterval} ticks, generating EU, and charging item slots prior to grid export.
     *
     * @param hasSky      whether the dimension has a sky
     * @param seesSky     whether the block can see the sky
     * @param isDay       whether the world is currently daytime
     * @param isRaining   whether rain is active globally
     * @param canRain     whether the biome can experience rain
     * @param chargeSinks ordered charging slots to feed before grid export
     * @return total EU transferred to charging slots during this tick
     */
    public double tick(
            boolean hasSky,
            boolean seesSky,
            boolean isDay,
            boolean isRaining,
            boolean canRain,
            List<? extends ChargeSink> chargeSinks
    ) {
        if (tickCounter == 0) {
            refreshSky(hasSky, seesSky, isDay, isRaining, canRain);
        }
        tickCounter = (tickCounter + 1) % checkSkyInterval;

        if (currentGenerating > 0) {
            storage = clampStorage(storage + currentGenerating);
        }

        double chargedTotal = 0.0;
        if (chargeSinks != null && !chargeSinks.isEmpty() && storage > 0.0) {
            for (ChargeSink sink : chargeSinks) {
                if (sink == null || storage <= 0.0) {
                    continue;
                }
                chargedTotal += chargeSlot(sink);
            }
        }
        return chargedTotal;
    }

    /**
     * Forces an immediate sky visibility check without altering the tick counter.
     *
     * @param hasSky    whether the dimension has a sky
     * @param seesSky   whether the block can see the sky
     * @param isDay     whether the world is currently daytime
     * @param isRaining whether rain is active globally
     * @param canRain   whether the biome can experience rain
     * @return updated {@link SkyCondition}
     */
    public SkyCondition refreshSky(boolean hasSky, boolean seesSky, boolean isDay, boolean isRaining, boolean canRain) {
        this.skyCondition = SkyCondition.evaluate(hasSky, seesSky, isDay, isRaining, canRain);
        this.currentGenerating = this.skyCondition.generationFor(dayPower, nightPower);
        return this.skyCondition;
    }

    /**
     * Charges a single electric item slot using up to {@link #offeredEnergy()} at this panel's tier.
     *
     * @param sink target charging slot
     * @return actual EU transferred and deducted from storage
     */
    public double chargeSlot(ChargeSink sink) {
        Objects.requireNonNull(sink, "sink");
        double offer = offeredEnergy();
        if (offer <= 0.0) {
            return 0.0;
        }
        double rawAccepted = sink.charge(offer, tier);
        double accepted = sanitizeTransfer(rawAccepted, offer);
        storage = clampStorage(storage - accepted);
        return accepted;
    }

    /**
     * Returns the maximum EU available for grid emission this tick: {@code min(storage, packetSize(tier))}.
     */
    public double offeredEnergy() {
        return Math.min(storage, packetSize());
    }

    /**
     * Draws energy from internal storage for the power grid. Storage is strictly clamped {@code >= 0}.
     *
     * @param amount requested EU
     * @return actual EU drawn
     */
    public double drawEnergy(double amount) {
        if (amount <= 0.0 || !Double.isFinite(amount)) {
            return 0.0;
        }
        double drawn = Math.min(storage, amount);
        storage = clampStorage(storage - drawn);
        return drawn;
    }

    /** Returns the single-packet EU limit for this panel's voltage tier. */
    public double packetSize() {
        return SolarTier.packetSize(tier);
    }

    /** Returns current stored EU, always in {@code [0, maxStorage]}. */
    public double storage() {
        return storage;
    }

    /**
     * Sets stored EU (for NBT deserialization), clamped strictly to {@code [0, maxStorage]}.
     *
     * @param eu stored EU value
     */
    public void setStorage(double eu) {
        this.storage = clampStorage(eu);
    }

    /** Returns the configured daytime EU/t. */
    public int dayPower() {
        return dayPower;
    }

    /** Returns the configured nighttime/rain EU/t. */
    public int nightPower() {
        return nightPower;
    }

    /** Returns the maximum EU storage capacity. */
    public int maxStorage() {
        return maxStorage;
    }

    /** Returns the IC2 voltage output tier. */
    public int tier() {
        return tier;
    }

    /** Returns the sky check interval in ticks. */
    public int checkSkyInterval() {
        return checkSkyInterval;
    }

    /** Returns the last evaluated {@link SkyCondition}. */
    public SkyCondition skyCondition() {
        return skyCondition;
    }

    /**
     * Restores the cached {@link SkyCondition} (for client sync or NBT load) and updates generation rate.
     *
     * @param condition sky condition
     */
    public void setSkyCondition(SkyCondition condition) {
        this.skyCondition = Objects.requireNonNull(condition, "condition");
        this.currentGenerating = this.skyCondition.generationFor(dayPower, nightPower);
    }

    /** Returns the current generation rate in EU/t. */
    public int currentGenerating() {
        return currentGenerating;
    }

    /** Returns the current sky refresh tick counter in {@code [0, checkSkyInterval - 1]}. */
    public int tickCounter() {
        return tickCounter;
    }

    /**
     * Sets the sky refresh tick counter, normalized into {@code [0, checkSkyInterval - 1]}.
     *
     * @param tickCounter tick counter value
     */
    public void setTickCounter(int tickCounter) {
        this.tickCounter = Math.floorMod(tickCounter, checkSkyInterval);
    }

    /**
     * Scales stored energy to an integer pixel gauge length for GUIs.
     *
     * @param barLength maximum bar length in pixels
     * @return scaled value in {@code [0, barLength]}
     */
    public int scaledEnergy(int barLength) {
        if (barLength <= 0 || maxStorage <= 0 || storage <= 0.0) {
            return 0;
        }
        return (int) Math.min(barLength, Math.floor((storage / maxStorage) * barLength));
    }

    private double clampStorage(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0.0;
        }
        return Math.min(maxStorage, value);
    }

    private static double sanitizeTransfer(double accepted, double maxAllowed) {
        if (!Double.isFinite(accepted) || accepted <= 0.0) {
            return 0.0;
        }
        return Math.min(accepted, maxAllowed);
    }
}
