package com.ecocraft2.common.solar;

import com.ecocraft2.common.config.Ecc2ConfigDefaults;

/**
 * Pure-Java state machine for the creative Quantum Generator.
 *
 * <p>Supports configurable non-negative EU/t production, voltage tiers 1 through 6
 * (where tier 6 = MAX = 32,768 EU/packet), redstone-inverted active control
 * (defaulting to active when unpowered, including non-player placement), exact
 * multi-packet splitting, and strict GUI button event validation.
 */
public final class QuantumGeneratorEngine {

    /** Minimum configurable voltage tier (Tier 1 = LV = 32 EU/packet). */
    public static final int MIN_TIER = 1;

    /** Maximum configurable voltage tier (Tier 6 = MAX = 32,768 EU/packet). */
    public static final int MAX_TIER = 6;

    /** Hard upper bound on packets emitted per tick to prevent IC2 EnergyNetwork main-thread stalls. */
    public static final int MAX_PACKETS_PER_TICK = 64;

    /** Hard upper bound on configured EU/t production (64 packets * Tier 6 32,768 EU/packet). */
    public static final int MAX_PRODUCTION = MAX_PACKETS_PER_TICK * 32_768;

    // Normal click production buttons (0..5)
    public static final int EVENT_SUB_100 = 0;
    public static final int EVENT_SUB_10 = 1;
    public static final int EVENT_SUB_1 = 2;
    public static final int EVENT_ADD_1 = 3;
    public static final int EVENT_ADD_10 = 4;
    public static final int EVENT_ADD_100 = 5;

    // Shift-click production buttons (10..15)
    public static final int EVENT_SUB_500 = 10;
    public static final int EVENT_SUB_50 = 11;
    public static final int EVENT_SUB_5 = 12;
    public static final int EVENT_ADD_5 = 13;
    public static final int EVENT_ADD_50 = 14;
    public static final int EVENT_ADD_500 = 15;

    // Tier selection buttons (20..25 -> Tier 1..6)
    public static final int EVENT_TIER_1 = 20;
    public static final int EVENT_TIER_2 = 21;
    public static final int EVENT_TIER_3 = 22;
    public static final int EVENT_TIER_4 = 23;
    public static final int EVENT_TIER_5 = 24;
    public static final int EVENT_TIER_6_MAX = 25;

    private int production;
    private int tier;
    private boolean active;

    /**
     * Creates a Quantum Generator with default production (512 EU/t), default tier (3),
     * and active state {@code true} (unpowered).
     */
    public QuantumGeneratorEngine() {
        this(
                Ecc2ConfigDefaults.QUANTUM_GENERATOR_DEFAULT_PRODUCTION,
                Ecc2ConfigDefaults.QUANTUM_GENERATOR_DEFAULT_TIER
        );
    }

    /**
     * Creates a Quantum Generator with the specified production and tier, clamped to valid ranges.
     * Defaults to {@code active = true} so non-player placement works without requiring a player click.
     *
     * @param production initial EU/t production (clamped {@code >= 0})
     * @param tier       initial output tier (clamped to {@code 1..6})
     */
    public QuantumGeneratorEngine(int production, int tier) {
        this.production = clampProduction(production);
        this.tier = clampTier(tier);
        this.active = true;
    }

    /** Returns the configured EU/t production (always in {@code [0, MAX_PRODUCTION]}). */
    public int production() {
        return production;
    }

    /**
     * Sets total EU/t production, clamped to {@code [0, MAX_PRODUCTION]}.
     *
     * @param production new EU/t production
     */
    public void setProduction(int production) {
        this.production = clampProduction(production);
    }

    /**
     * Adjusts EU/t production by {@code delta}, saturating cleanly within {@code [0, MAX_PRODUCTION]}.
     *
     * @param delta signed EU/t adjustment
     */
    public void adjustProduction(int delta) {
        long next = (long) this.production + delta;
        if (next <= 0L) {
            this.production = 0;
        } else if (next >= MAX_PRODUCTION) {
            this.production = MAX_PRODUCTION;
        } else {
            this.production = (int) next;
        }
    }

    /** Returns the configured output tier in {@code [1, 6]}. */
    public int tier() {
        return tier;
    }

    /**
     * Sets the output tier, clamped to {@code [1, 6]}.
     *
     * @param tier desired output tier
     */
    public void setTier(int tier) {
        this.tier = clampTier(tier);
    }

    /** Returns the single-packet EU cap for the current tier (32..32,768). */
    public double tierPacketSize() {
        return SolarTier.packetSize(tier);
    }

    /** Returns {@code true} if the generator is currently active (not suppressed by redstone). */
    public boolean isActive() {
        return active;
    }

    /**
     * Updates active state from a redstone powered flag (inverted: redstone power stops output).
     *
     * @param redstonePowered {@code true} if block receives redstone power
     */
    public void updateRedstone(boolean redstonePowered) {
        this.active = !redstonePowered;
    }

    /**
     * Updates active state from a redstone signal strength (inverted: signal {@code > 0} stops output).
     *
     * @param signal redstone signal strength ({@code 0..15})
     */
    public void updateRedstoneSignal(int signal) {
        this.active = signal <= 0;
    }

    /**
     * Computes the number of packets emitted per tick, bounded by {@link #MAX_PACKETS_PER_TICK}:
     * {@code min(MAX_PACKETS_PER_TICK, production > packetSize(tier) ? ceil(production / packetSize(tier)) : 1)}.
     *
     * @return packet count in {@code [1, MAX_PACKETS_PER_TICK]}
     */
    public int packetCount() {
        long packetCap = (long) tierPacketSize();
        if (production > packetCap) {
            long raw = ((long) production + packetCap - 1L) / packetCap;
            return (int) Math.min(MAX_PACKETS_PER_TICK, raw);
        }
        return 1;
    }

    /**
     * Computes the exact EU carried by each emitted packet:
     * {@code active ? min(tierPacketSize(), (double) production / packetCount()) : 0.0}.
     *
     * <p>Guaranteed to satisfy {@code offeredPerPacket() <= tierPacketSize()}.
     *
     * @return EU per packet
     */
    public double offeredPerPacket() {
        if (!active || production <= 0) {
            return 0.0;
        }
        return Math.min(tierPacketSize(), (double) production / packetCount());
    }

    /**
     * Returns total EU offered per tick across all packets, respecting both the tier packet limit
     * and {@link #MAX_PACKETS_PER_TICK}.
     */
    public double totalOfferedEnergy() {
        return active ? offeredPerPacket() * packetCount() : 0.0;
    }

    /**
     * Checks whether a GUI button event ID is valid.
     *
     * @param eventId button event ID
     * @return {@code true} iff {@code eventId} is in {@code 0..5}, {@code 10..15}, or {@code 20..25}
     */
    public static boolean isValidButtonEvent(int eventId) {
        return (eventId >= 0 && eventId <= 5)
                || (eventId >= 10 && eventId <= 15)
                || (eventId >= 20 && eventId <= 25);
    }

    /**
     * Validates and applies a GUI button event.
     *
     * <ul>
     *     <li>{@code 0..5}: {@code -100, -10, -1, +1, +10, +100} EU/t</li>
     *     <li>{@code 10..15}: {@code -500, -50, -5, +5, +50, +500} EU/t</li>
     *     <li>{@code 20..25}: set tier {@code 1..6}</li>
     *     <li>Any other value: rejected with {@code false} and zero state mutation</li>
     * </ul>
     *
     * @param eventId button event ID from client packet
     * @return {@code true} if valid and applied, {@code false} if rejected
     */
    public boolean handleButtonEvent(int eventId) {
        return switch (eventId) {
            case EVENT_SUB_100 -> {
                adjustProduction(-100);
                yield true;
            }
            case EVENT_SUB_10 -> {
                adjustProduction(-10);
                yield true;
            }
            case EVENT_SUB_1 -> {
                adjustProduction(-1);
                yield true;
            }
            case EVENT_ADD_1 -> {
                adjustProduction(1);
                yield true;
            }
            case EVENT_ADD_10 -> {
                adjustProduction(10);
                yield true;
            }
            case EVENT_ADD_100 -> {
                adjustProduction(100);
                yield true;
            }
            case EVENT_SUB_500 -> {
                adjustProduction(-500);
                yield true;
            }
            case EVENT_SUB_50 -> {
                adjustProduction(-50);
                yield true;
            }
            case EVENT_SUB_5 -> {
                adjustProduction(-5);
                yield true;
            }
            case EVENT_ADD_5 -> {
                adjustProduction(5);
                yield true;
            }
            case EVENT_ADD_50 -> {
                adjustProduction(50);
                yield true;
            }
            case EVENT_ADD_500 -> {
                adjustProduction(500);
                yield true;
            }
            case EVENT_TIER_1, EVENT_TIER_2, EVENT_TIER_3, EVENT_TIER_4, EVENT_TIER_5, EVENT_TIER_6_MAX -> {
                setTier(eventId - EVENT_TIER_1 + MIN_TIER);
                yield true;
            }
            default -> false;
        };
    }

    private static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    private static int clampProduction(int production) {
        return Math.max(0, Math.min(MAX_PRODUCTION, production));
    }
}
