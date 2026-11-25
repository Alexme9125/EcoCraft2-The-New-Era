package com.ecocraft2.giant;

import com.ecocraft2.common.giant.MultiblockBounds;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Blockstate role of each block inside a 3x3x3 Giant Machine multiblock. */
public enum GiantUnitRole implements StringRepresentable {
    UNFORMED,
    CORE,
    CORNER,
    SHELL;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GiantUnitRole fromBoundsRole(MultiblockBounds.BlockRole role) {
        return switch (role) {
            case OUTSIDE -> UNFORMED;
            case CORE -> CORE;
            case CORNER -> CORNER;
            case SHELL -> SHELL;
        };
    }
}
