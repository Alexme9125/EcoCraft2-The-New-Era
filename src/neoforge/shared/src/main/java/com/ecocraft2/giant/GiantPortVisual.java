package com.ecocraft2.giant;

import com.ecocraft2.common.giant.PortAccessMode;
import net.minecraft.util.StringRepresentable;

/**
 * Blockstate {@code port_mode} visual indicator property for the 4 lower-corner blocks of a formed
 * 3x3x3 Giant Machine, selecting between {@code giant_port_full}, {@code giant_port_input}, {@code
 * giant_port_output}, and {@code giant_port_blocked}.
 */
public enum GiantPortVisual implements StringRepresentable {
    FULL("full"),
    INPUT("input"),
    OUTPUT("output"),
    BLOCKED("blocked");

    private final String serializedName;

    GiantPortVisual(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Combines the two outward horizontal port modes of a lower-corner block into one visual state. */
    public static GiantPortVisual combine(PortAccessMode a, PortAccessMode b) {
        boolean allowIn = (a != null && a.allowsInput()) || (b != null && b.allowsInput());
        boolean allowOut = (a != null && a.allowsOutput()) || (b != null && b.allowsOutput());
        if (allowIn && allowOut) {
            return FULL;
        }
        if (allowIn) {
            return INPUT;
        }
        if (allowOut) {
            return OUTPUT;
        }
        return BLOCKED;
    }
}
