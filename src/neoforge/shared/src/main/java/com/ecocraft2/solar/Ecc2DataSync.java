package com.ecocraft2.solar;

import java.util.Locale;

/**
 * Utility helpers for lossless 32-bit integer packing over Minecraft's 16-bit {@code ContainerData}
 * channels and compact EU / KU number formatting in GUIs and tooltips.
 */
public final class Ecc2DataSync {

    private Ecc2DataSync() {}

    /** Returns the lower 16 bits of {@code value} as an unsigned 16-bit int. */
    public static int lo16(int value) {
        return value & 0xFFFF;
    }

    /** Returns the upper 16 bits of {@code value} as an unsigned 16-bit int. */
    public static int hi16(int value) {
        return (value >>> 16) & 0xFFFF;
    }

    /** Reassembles a 32-bit integer from its low and high 16-bit halves. */
    public static int combine32(int lo, int hi) {
        return (lo & 0xFFFF) | ((hi & 0xFFFF) << 16);
    }

    /** Formats EU/energy quantities cleanly for compact LCD displays. */
    public static String formatCompact(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return "0";
        }
        if (value >= 1_000_000_000.0) {
            return String.format(Locale.ROOT, "%.2fG", value / 1_000_000_000.0);
        }
        if (value >= 1_000_000.0) {
            return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
        }
        if (value >= 10_000.0) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        return String.format(Locale.ROOT, "%,d", Math.round(value));
    }

    /** Formats an integer with thousands separators. */
    public static String formatInt(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
