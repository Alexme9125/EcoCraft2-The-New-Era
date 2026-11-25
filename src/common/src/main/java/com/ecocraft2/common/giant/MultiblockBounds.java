package com.ecocraft2.common.giant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Validated bounding box and bottom-center core coordinate of a 3x3x3 Giant Machine multiblock.
 *
 * @param min  minimum corner {@code (core.x - 1, core.y, core.z - 1)}
 * @param max  maximum corner {@code (core.x + 1, core.y + 2, core.z + 1)}
 * @param core bottom-center logical core coordinate
 */
public record MultiblockBounds(BlockPos3 min, BlockPos3 max, BlockPos3 core) {

    /** Structural role of a block position relative to a formed 3x3x3 multiblock. */
    public enum BlockRole {
        /** Position lies outside the 3x3x3 bounding box. */
        OUTSIDE,
        /** Bottom-center core block {@code (0, 0, 0)}. */
        CORE,
        /** One of the 4 lower-layer corner blocks {@code (±1, 0, ±1)} carrying the 8 corner access ports. */
        CORNER,
        /** Any of the remaining 22 shell/interior casing blocks. */
        SHELL
    }

    public MultiblockBounds {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        Objects.requireNonNull(core, "core");
        if (max.x() - min.x() != 2 || max.y() - min.y() != 2 || max.z() - min.z() != 2) {
            throw new IllegalArgumentException("MultiblockBounds must be an exact 3x3x3 cube: min=" + min + ", max=" + max);
        }
        if (core.x() != min.x() + 1 || core.y() != min.y() || core.z() != min.z() + 1) {
            throw new IllegalArgumentException("core must be the bottom-center coordinate: expected ("
                    + (min.x() + 1) + "," + min.y() + "," + (min.z() + 1) + ") but got " + core);
        }
    }

    /** Returns {@code true} if {@code pos} is one of the 27 blocks inside this multiblock. */
    public boolean contains(BlockPos3 pos) {
        Objects.requireNonNull(pos, "pos");
        return pos.x() >= min.x() && pos.x() <= max.x()
                && pos.y() >= min.y() && pos.y() <= max.y()
                && pos.z() >= min.z() && pos.z() <= max.z();
    }

    /**
     * Converts a world position into an offset relative to the bottom-center {@link #core()}.
     * For blocks inside the multiblock, {@code dx in [-1, 1]}, {@code dy in [0, 2]}, {@code dz in [-1, 1]}.
     *
     * @param worldPos world block coordinate
     * @return relative offset from core
     */
    public BlockPos3 relativeToCore(BlockPos3 worldPos) {
        return Objects.requireNonNull(worldPos, "worldPos").subtract(core);
    }

    /** Returns the structural {@link BlockRole} of {@code worldPos}. */
    public BlockRole roleOf(BlockPos3 worldPos) {
        if (!contains(worldPos)) {
            return BlockRole.OUTSIDE;
        }
        BlockPos3 rel = relativeToCore(worldPos);
        if (rel.x() == 0 && rel.y() == 0 && rel.z() == 0) {
            return BlockRole.CORE;
        }
        if (GiantCornerPort.isCornerOffset(rel.x(), rel.y(), rel.z())) {
            return BlockRole.CORNER;
        }
        return BlockRole.SHELL;
    }

    /**
     * Returns {@code true} if {@code face} of {@code worldPos} lies on the outer surface of the 3x3x3 cube.
     * Used for EU cable attachment across all exterior faces of the giant machine.
     *
     * @param worldPos block coordinate inside the multiblock
     * @param face     block face to inspect
     * @return {@code true} if {@code worldPos} is inside and {@code worldPos.offset(face)} is outside
     */
    public boolean isExteriorFace(BlockPos3 worldPos, BlockFace face) {
        Objects.requireNonNull(worldPos, "worldPos");
        Objects.requireNonNull(face, "face");
        return contains(worldPos) && !contains(worldPos.offset(face));
    }

    /** Returns all 27 world coordinates in deterministic bottom-to-top order. */
    public List<BlockPos3> allPositions() {
        List<BlockPos3> positions = new ArrayList<>(27);
        for (int y = min.y(); y <= max.y(); y++) {
            for (int z = min.z(); z <= max.z(); z++) {
                for (int x = min.x(); x <= max.x(); x++) {
                    positions.add(new BlockPos3(x, y, z));
                }
            }
        }
        return Collections.unmodifiableList(positions);
    }
}
