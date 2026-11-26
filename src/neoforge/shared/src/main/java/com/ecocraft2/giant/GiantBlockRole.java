package com.ecocraft2.giant;

import com.ecocraft2.common.giant.MultiblockBounds;
import net.minecraft.util.StringRepresentable;

/**
 * Blockstate {@code role} property values matching {@code assets-gen/generate_data.py} verbatim:
 * {@code unformed}, {@code core}, {@code corner}, {@code front}, {@code shell}.
 */
public enum GiantBlockRole implements StringRepresentable {
    UNFORMED("unformed"),
    CORE("core"),
    CORNER("corner"),
    FRONT("front"),
    SHELL("shell");

    private final String serializedName;

    GiantBlockRole(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public static GiantBlockRole fromBoundsRole(MultiblockBounds.BlockRole role) {
        return switch (role) {
            case CORE -> CORE;
            case CORNER -> CORNER;
            case SHELL -> SHELL;
            case OUTSIDE -> UNFORMED;
        };
    }
}
