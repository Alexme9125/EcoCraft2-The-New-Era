package com.ecocraft2.common.giant;

import java.util.Objects;

/**
 * Immutable pure-Java integer 3D block coordinate.
 *
 * @param x world or relative X coordinate
 * @param y world or relative Y coordinate
 * @param z world or relative Z coordinate
 */
public record BlockPos3(int x, int y, int z) {

    /**
     * Returns a coordinate shifted by {@code (dx, dy, dz)}.
     *
     * @param dx delta X
     * @param dy delta Y
     * @param dz delta Z
     * @return offset coordinate
     */
    public BlockPos3 offset(int dx, int dy, int dz) {
        return new BlockPos3(x + dx, y + dy, z + dz);
    }

    /**
     * Returns the adjacent coordinate in the given {@link BlockFace} direction.
     *
     * @param face cardinal direction
     * @return adjacent coordinate
     */
    public BlockPos3 offset(BlockFace face) {
        Objects.requireNonNull(face, "face");
        return new BlockPos3(x + face.stepX(), y + face.stepY(), z + face.stepZ());
    }

    /**
     * Returns the vector {@code this - other}.
     *
     * @param other reference origin
     * @return relative offset
     */
    public BlockPos3 subtract(BlockPos3 other) {
        Objects.requireNonNull(other, "other");
        return new BlockPos3(x - other.x, y - other.y, z - other.z);
    }
}
