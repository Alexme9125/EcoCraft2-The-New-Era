package com.ecocraft2.common.solar;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;

import java.util.List;
import java.util.Objects;

/**
 * Per-player / per-stack solar helmet state machine.
 *
 * <p>Stores sky-check timing and sky condition strictly per instance (fixing the upstream
 * static shared-state bug across multiplayer players), enforces exact charging priority
 * ({@code FEET -> LEGS -> CHEST -> OFFHAND -> MAIN -> SELF}), and evaluates Quantum Helmet
 * oxygen/cure/auto-feed thresholds.
 */
public final class SolarHelmetEngine {

    /** Air supply threshold at or below which underwater oxygen refill triggers (out of 300). */
    public static final int OXYGEN_REFILL_TRIGGER_AIR = 100;

    /** Target air supply restored when underwater oxygen refill activates. */
    public static final int OXYGEN_REFILL_TARGET_AIR = 300;

    /** EU cost for a single underwater oxygen refill. */
    public static final double OXYGEN_REFILL_EU_COST = 1_000.0;

    /** EU cost to cleanse Poison status effect. */
    public static final double CURE_POISON_EU_COST = 10_000.0;

    /** EU cost to cleanse Wither status effect. */
    public static final double CURE_WITHER_EU_COST = 25_000.0;

    /** EU cost to cleanse Radiation status effect. */
    public static final double CURE_RADIATION_EU_COST = 10_000.0;

    /** Food level threshold at or below which auto-feeding from tin cans activates (out of 20). */
    public static final int AUTO_FEED_TRIGGER_FOOD = 18;

    /** EU cost per auto-feed activation. */
    public static final double AUTO_FEED_EU_COST = 1_000.0;

    /** Ordered charging targets for solar helmet EU distribution. */
    public enum ChargeTargetSlot {
        FEET,
        LEGS,
        CHEST,
        OFFHAND,
        MAIN,
        SELF
    }

    /** Full charging priority order: feet -> legs -> chest -> offhand -> main -> self. */
    public static final List<ChargeTargetSlot> FULL_CHARGE_ORDER = List.of(
            ChargeTargetSlot.FEET,
            ChargeTargetSlot.LEGS,
            ChargeTargetSlot.CHEST,
            ChargeTargetSlot.OFFHAND,
            ChargeTargetSlot.MAIN,
            ChargeTargetSlot.SELF
    );

    /** Armor-only charging priority order: feet -> legs -> chest -> self. */
    public static final List<ChargeTargetSlot> ARMOR_ONLY_CHARGE_ORDER = List.of(
            ChargeTargetSlot.FEET,
            ChargeTargetSlot.LEGS,
            ChargeTargetSlot.CHEST,
            ChargeTargetSlot.SELF
    );

    /**
     * Callback invoked by {@link SolarHelmetEngine} to route generated EU to a target slot group.
     */
    @FunctionalInterface
    public interface HelmetChargeRouter {

        /**
         * Attempts to charge the given slot category with up to {@code offerEu} at {@code helmetTier}.
         *
         * @param slot       target slot category
         * @param offerEu    EU offered
         * @param helmetTier helmet electric tier
         * @return actual EU accepted (clamped by engine to {@code [0, offerEu]})
         */
        double chargeSlot(ChargeTargetSlot slot, double offerEu, int helmetTier);
    }

    /**
     * Summary of one solar helmet generation tick.
     *
     * @param skyCondition   current per-instance sky condition
     * @param generatedEu    total EU produced this tick
     * @param distributedEu  EU sent to armor/inventory slots (excluding self)
     * @param selfChargedEu  EU stored into the helmet's own battery
     * @param unconsumedEu   excess EU that could not be stored anywhere
     */
    public record GenerationResult(
            SkyCondition skyCondition,
            double generatedEu,
            double distributedEu,
            double selfChargedEu,
            double unconsumedEu
    ) {
    }

    /**
     * Plan of Quantum Helmet abilities to execute on the current tick within available EU.
     *
     * @param refillOxygen  whether to refill air to {@value #OXYGEN_REFILL_TARGET_AIR}
     * @param cureWither    whether to clear Wither
     * @param curePoison    whether to clear Poison
     * @param cureRadiation whether to clear Radiation
     * @param autoFeed      whether to feed the player from inventory food cans
     * @param totalEuCost   total EU to deduct from the helmet
     */
    public record QuantumAbilityPlan(
            boolean refillOxygen,
            boolean cureWither,
            boolean curePoison,
            boolean cureRadiation,
            boolean autoFeed,
            double totalEuCost
    ) {
        /** Empty plan with no triggered abilities and zero EU cost. */
        public static final QuantumAbilityPlan NONE = new QuantumAbilityPlan(false, false, false, false, false, 0.0);

        /** Returns {@code true} if any quantum ability was triggered. */
        public boolean hasAnyAction() {
            return refillOxygen || cureWither || curePoison || cureRadiation || autoFeed;
        }
    }

    private final SolarHelmetSpec spec;
    private final int dayPower;
    private final int nightPower;
    private final int checkSkyInterval;

    private SkyCondition skyCondition = SkyCondition.NONE;
    private int currentGenerating;
    private int tickCounter;

    /**
     * Creates a helmet engine with default power rates and 128-tick sky refresh cadence.
     *
     * @param spec helmet specification
     */
    public SolarHelmetEngine(SolarHelmetSpec spec) {
        this(
                Objects.requireNonNull(spec, "spec"),
                spec.defaultDayPower(),
                spec.defaultNightPower(),
                Ecc2ConfigDefaults.SOLAR_SKY_CHECK_INTERVAL_TICKS
        );
    }

    /**
     * Creates a helmet engine with custom configured day/night rates and sky check interval.
     *
     * @param spec             helmet specification
     * @param dayPower         daytime EU/t ({@code >= 0})
     * @param nightPower       nighttime/rain EU/t ({@code >= 0})
     * @param checkSkyInterval sky check interval in ticks ({@code >= 1})
     */
    public SolarHelmetEngine(SolarHelmetSpec spec, int dayPower, int nightPower, int checkSkyInterval) {
        this.spec = Objects.requireNonNull(spec, "spec");
        if (dayPower < 0) {
            throw new IllegalArgumentException("dayPower must be >= 0");
        }
        if (nightPower < 0) {
            throw new IllegalArgumentException("nightPower must be >= 0");
        }
        if (checkSkyInterval < 1) {
            throw new IllegalArgumentException("checkSkyInterval must be >= 1");
        }
        this.dayPower = dayPower;
        this.nightPower = nightPower;
        this.checkSkyInterval = checkSkyInterval;
    }

    /**
     * Advances solar generation by one tick and routes EU in strict priority order.
     *
     * @param hasSky          whether the player's dimension has sky light
     * @param seesSky         whether the player's head position sees the sky
     * @param isDay           whether the world is daytime
     * @param isRaining       whether global rain is active
     * @param canRain         whether the local biome can rain
     * @param chargeArmorOnly when {@code true}, skips {@code OFFHAND} and {@code MAIN} slots
     * @param router          callback that applies charging to each slot category
     * @return {@link GenerationResult} breakdown for this tick
     */
    public GenerationResult tickGeneration(
            boolean hasSky,
            boolean seesSky,
            boolean isDay,
            boolean isRaining,
            boolean canRain,
            boolean chargeArmorOnly,
            HelmetChargeRouter router
    ) {
        Objects.requireNonNull(router, "router");
        if (tickCounter == 0) {
            skyCondition = SkyCondition.evaluate(hasSky, seesSky, isDay, isRaining, canRain);
            currentGenerating = skyCondition.generationFor(dayPower, nightPower);
        }
        tickCounter = (tickCounter + 1) % checkSkyInterval;

        double generated = currentGenerating;
        if (generated <= 0.0) {
            return new GenerationResult(skyCondition, 0.0, 0.0, 0.0, 0.0);
        }

        double remaining = generated;
        double distributed = 0.0;
        double selfCharged = 0.0;

        List<ChargeTargetSlot> order = chargeArmorOnly ? ARMOR_ONLY_CHARGE_ORDER : FULL_CHARGE_ORDER;
        for (ChargeTargetSlot slot : order) {
            if (remaining <= 0.0) {
                break;
            }
            double raw = router.chargeSlot(slot, remaining, spec.tier());
            double accepted = sanitizeAccepted(raw, remaining);
            remaining -= accepted;
            if (slot == ChargeTargetSlot.SELF) {
                selfCharged += accepted;
            } else {
                distributed += accepted;
            }
        }

        return new GenerationResult(skyCondition, generated, distributed, selfCharged, Math.max(0.0, remaining));
    }

    /**
     * Evaluates which Quantum Helmet abilities should trigger given current player state and helmet EU.
     *
     * <p>Priority order when energy is limited: oxygen refill -> cure Wither -> cure Poison ->
     * cure Radiation -> auto-feed. Always returns {@link QuantumAbilityPlan#NONE} for non-quantum helmets.
     *
     * @param storedEu       current EU stored in the helmet
     * @param currentAir     player's current air supply (vanilla range {@code 0..300})
     * @param hasWither      whether player has Wither effect
     * @param hasPoison      whether player has Poison effect
     * @param hasRadiation   whether player has Radiation effect
     * @param foodLevel      player's current hunger level (vanilla range {@code 0..20})
     * @param hasCanFood     whether player has filled food cans available in inventory
     * @return immutable {@link QuantumAbilityPlan}
     */
    public QuantumAbilityPlan evaluateQuantumAbilities(
            double storedEu,
            int currentAir,
            boolean hasWither,
            boolean hasPoison,
            boolean hasRadiation,
            int foodLevel,
            boolean hasCanFood
    ) {
        if (!spec.hasQuantumAbilities() || !Double.isFinite(storedEu) || storedEu <= 0.0) {
            return QuantumAbilityPlan.NONE;
        }

        double available = storedEu;
        double cost = 0.0;

        boolean refillAir = false;
        if (currentAir <= OXYGEN_REFILL_TRIGGER_AIR && available >= OXYGEN_REFILL_EU_COST) {
            refillAir = true;
            available -= OXYGEN_REFILL_EU_COST;
            cost += OXYGEN_REFILL_EU_COST;
        }

        boolean cureWither = false;
        if (hasWither && available >= CURE_WITHER_EU_COST) {
            cureWither = true;
            available -= CURE_WITHER_EU_COST;
            cost += CURE_WITHER_EU_COST;
        }

        boolean curePoison = false;
        if (hasPoison && available >= CURE_POISON_EU_COST) {
            curePoison = true;
            available -= CURE_POISON_EU_COST;
            cost += CURE_POISON_EU_COST;
        }

        boolean cureRadiation = false;
        if (hasRadiation && available >= CURE_RADIATION_EU_COST) {
            cureRadiation = true;
            available -= CURE_RADIATION_EU_COST;
            cost += CURE_RADIATION_EU_COST;
        }

        boolean autoFeed = false;
        if (hasCanFood && foodLevel <= AUTO_FEED_TRIGGER_FOOD && available >= AUTO_FEED_EU_COST) {
            autoFeed = true;
            cost += AUTO_FEED_EU_COST;
        }

        if (cost <= 0.0) {
            return QuantumAbilityPlan.NONE;
        }
        return new QuantumAbilityPlan(refillAir, cureWither, curePoison, cureRadiation, autoFeed, cost);
    }

    /**
     * Computes EU cost required to absorb incoming damage with this helmet.
     *
     * @param damageAmount incoming damage points
     * @param storedEu     currently stored EU
     * @return EU required (at most {@code storedEu}, or {@code 0.0} if not enough EU for any absorption)
     */
    public double calculateDamageAbsorptionEu(double damageAmount, double storedEu) {
        if (damageAmount <= 0.0 || !Double.isFinite(damageAmount) || storedEu <= 0.0 || !Double.isFinite(storedEu)) {
            return 0.0;
        }
        double required = Math.ceil(damageAmount * spec.damageAbsorptionRatio()) * spec.euPerDamage();
        return storedEu >= required ? required : 0.0;
    }

    /** Returns the helmet specification. */
    public SolarHelmetSpec spec() {
        return spec;
    }

    /** Returns the configured daytime EU/t. */
    public int dayPower() {
        return dayPower;
    }

    /** Returns the configured nighttime/rain EU/t. */
    public int nightPower() {
        return nightPower;
    }

    /** Returns the last evaluated per-instance {@link SkyCondition}. */
    public SkyCondition skyCondition() {
        return skyCondition;
    }

    /** Returns the current EU/t generation rate for this helmet instance. */
    public int currentGenerating() {
        return currentGenerating;
    }

    /** Returns the current sky check tick counter. */
    public int tickCounter() {
        return tickCounter;
    }

    /**
     * Restores persisted per-stack/per-player sky state.
     *
     * @param skyCondition saved sky condition
     * @param tickCounter  saved tick counter
     */
    public void restoreState(SkyCondition skyCondition, int tickCounter) {
        this.skyCondition = Objects.requireNonNull(skyCondition, "skyCondition");
        this.currentGenerating = this.skyCondition.generationFor(dayPower, nightPower);
        this.tickCounter = Math.floorMod(tickCounter, checkSkyInterval);
    }

    private static double sanitizeAccepted(double accepted, double maxAllowed) {
        if (!Double.isFinite(accepted) || accepted <= 0.0) {
            return 0.0;
        }
        return Math.min(accepted, maxAllowed);
    }
}
