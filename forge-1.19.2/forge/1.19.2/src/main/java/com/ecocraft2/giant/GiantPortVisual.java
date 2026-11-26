package com.ecocraft2.giant;

import com.ecocraft2.common.giant.PortAccessMode;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Visual state for the 4 lower-corner port blocks of a formed 3x3x3 Giant Machine. */
public enum GiantPortVisual implements StringRepresentable {
    FULL,
    INPUT,
    OUTPUT,
    BLOCKED;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Combines the two horizontal port modes belonging to one lower corner block. */
    public static GiantPortVisual combine(PortAccessMode a, PortAccessMode b) {
        boolean in = a.allowsInput() || b.allowsInput();
        boolean out = a.allowsOutput() || b.allowsOutput();
        if (in && out) {
            return FULL;
        }
        if (in) {
            return INPUT;
        }
        if (out) {
            return OUTPUT;
        }
        return BLOCKED;
    }
}
