package com.ecocraft2.common.solar;

/**
 * Sky illumination state for solar panels, solar helmets, and solar-assisted machines.
 *
 * <p>{@link #RAIN} produces the same EU/t as {@link #NIGHT} while preserving a distinct
 * state so GUIs can display a dedicated rain icon.
 */
public enum SkyCondition {

    /** No sky access or dimension lacks a sky light channel; generates 0 EU/t. */
    NONE,

    /** Sky is visible at night; generates nighttime EU/t. */
    NIGHT,

    /** Sky is visible during daytime rain in a rain-capable biome; generates nighttime EU/t. */
    RAIN,

    /** Clear daytime sky access (or rain in a non-raining biome); generates full daytime EU/t. */
    DAY;

    /** Returns {@code true} if this sky condition yields positive solar generation. */
    public boolean generatesPower() {
        return this != NONE;
    }

    /** Returns {@code true} if this sky condition yields full daytime power. */
    public boolean isFullSun() {
        return this == DAY;
    }

    /**
     * Evaluates the sky condition from world environment flags.
     *
     * @param hasSky    whether the dimension has a skylight channel
     * @param seesSky   whether the position has an unobstructed view of the sky
     * @param isDay     whether the world time is daytime
     * @param isRaining whether precipitation is active globally
     * @param canRain   whether the local biome actually experiences rain/snow precipitation
     * @return evaluated {@link SkyCondition}
     */
    public static SkyCondition evaluate(
            boolean hasSky,
            boolean seesSky,
            boolean isDay,
            boolean isRaining,
            boolean canRain
    ) {
        if (!hasSky || !seesSky) {
            return NONE;
        }
        if (!isDay) {
            return NIGHT;
        }
        if (isRaining && canRain) {
            return RAIN;
        }
        return DAY;
    }

    /**
     * Resolves EU/t generation for this sky condition.
     *
     * @param dayPower   daytime EU/t
     * @param nightPower nighttime and rain EU/t
     * @return generated EU/t
     */
    public int generationFor(int dayPower, int nightPower) {
        return switch (this) {
            case NONE -> 0;
            case NIGHT, RAIN -> nightPower;
            case DAY -> dayPower;
        };
    }
}
