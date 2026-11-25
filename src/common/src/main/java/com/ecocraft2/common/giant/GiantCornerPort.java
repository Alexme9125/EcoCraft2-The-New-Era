package com.ecocraft2.common.giant;

import java.util.Objects;
import java.util.Optional;

/**
 * The eight horizontal lower-corner automation ports of a 3x3x3 Giant Machine.
 *
 * <p>Relative to the structure's horizontal {@code facing} (where {@code FRONT = facing},
 * {@code BACK = facing.opposite()}, {@code LEFT = facing.counterClockWise()},
 * {@code RIGHT = facing.clockWise()}), the four bottom-layer corners ({@code dy = 0})
 * each expose two outward horizontal faces, yielding a bijection with these 8 ports:
 * <ul>
 *     <li>Corner {@code FRONT + LEFT}: {@link #FRONT_LEFT} (front face) and {@link #LEFT_FRONT} (left face)</li>
 *     <li>Corner {@code FRONT + RIGHT}: {@link #FRONT_RIGHT} (front face) and {@link #RIGHT_FRONT} (right face)</li>
 *     <li>Corner {@code BACK + LEFT}: {@link #BACK_LEFT} (back face) and {@link #LEFT_BACK} (left face)</li>
 *     <li>Corner {@code BACK + RIGHT}: {@link #BACK_RIGHT} (back face) and {@link #RIGHT_BACK} (right face)</li>
 * </ul>
 */
public enum GiantCornerPort {

    FRONT_LEFT(RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.FRONT),
    FRONT_RIGHT(RelativeSide.FRONT, RelativeSide.RIGHT, RelativeSide.FRONT),
    BACK_LEFT(RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.BACK),
    BACK_RIGHT(RelativeSide.BACK, RelativeSide.RIGHT, RelativeSide.BACK),
    LEFT_FRONT(RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.LEFT),
    LEFT_BACK(RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.LEFT),
    RIGHT_FRONT(RelativeSide.FRONT, RelativeSide.RIGHT, RelativeSide.RIGHT),
    RIGHT_BACK(RelativeSide.BACK, RelativeSide.RIGHT, RelativeSide.RIGHT);

    private enum RelativeSide {
        FRONT,
        BACK,
        LEFT,
        RIGHT;

        BlockFace resolve(BlockFace structureFacing) {
            return switch (this) {
                case FRONT -> structureFacing;
                case BACK -> structureFacing.opposite();
                case LEFT -> structureFacing.counterClockWise();
                case RIGHT -> structureFacing.clockWise();
            };
        }
    }

    private final RelativeSide depthCorner;
    private final RelativeSide lateralCorner;
    private final RelativeSide outwardFace;

    GiantCornerPort(RelativeSide depthCorner, RelativeSide lateralCorner, RelativeSide outwardFace) {
        this.depthCorner = depthCorner;
        this.lateralCorner = lateralCorner;
        this.outwardFace = outwardFace;
    }

    /** Returns the starting bit index ({@code 0, 2, 4, ..., 14}) in a 16-bit {@link GiantPortPermissions} mask. */
    public int bitIndex() {
        return ordinal() * 2;
    }

    /**
     * Computes the corner block's relative offset {@code (dx, 0, dz)} from the bottom-center core
     * for a multiblock facing {@code structureFacing}.
     *
     * @param structureFacing horizontal front direction of the multiblock
     * @return relative offset {@code (±1, 0, ±1)}
     * @throws IllegalArgumentException if {@code structureFacing} is not horizontal
     */
    public BlockPos3 cornerOffset(BlockFace structureFacing) {
        requireHorizontal(structureFacing);
        BlockFace depth = depthCorner.resolve(structureFacing);
        BlockFace lateral = lateralCorner.resolve(structureFacing);
        return new BlockPos3(depth.stepX() + lateral.stepX(), 0, depth.stepZ() + lateral.stepZ());
    }

    /**
     * Computes the outward world {@link BlockFace} corresponding to this port.
     *
     * @param structureFacing horizontal front direction of the multiblock
     * @return outward horizontal world face
     * @throws IllegalArgumentException if {@code structureFacing} is not horizontal
     */
    public BlockFace worldFace(BlockFace structureFacing) {
        requireHorizontal(structureFacing);
        return outwardFace.resolve(structureFacing);
    }

    /**
     * Tests whether {@code (dx, dy, dz)} relative to the bottom-center core is one of the 4 bottom corners.
     */
    public static boolean isCornerOffset(int dx, int dy, int dz) {
        return dy == 0 && Math.abs(dx) == 1 && Math.abs(dz) == 1;
    }

    /**
     * Maps a multiblock facing, block offset relative to core {@code (dx, dy, dz)}, and world face
     * to its exact {@link GiantCornerPort}, or {@link Optional#empty()} if the face is not one of the
     * 8 lower-corner outward ports.
     *
     * @param structureFacing horizontal front direction of the multiblock
     * @param dx              relative X from core ({@code -1..1})
     * @param dy              relative Y from core ({@code 0..2})
     * @param dz              relative Z from core ({@code -1..1})
     * @param worldFace       world face being accessed
     * @return corresponding {@link GiantCornerPort} if valid
     */
    public static Optional<GiantCornerPort> fromOffsetAndFace(
            BlockFace structureFacing,
            int dx,
            int dy,
            int dz,
            BlockFace worldFace
    ) {
        requireHorizontal(structureFacing);
        Objects.requireNonNull(worldFace, "worldFace");
        if (!isCornerOffset(dx, dy, dz) || !worldFace.isHorizontal()) {
            return Optional.empty();
        }
        for (GiantCornerPort port : values()) {
            BlockPos3 offset = port.cornerOffset(structureFacing);
            if (offset.x() == dx && offset.z() == dz && port.worldFace(structureFacing) == worldFace) {
                return Optional.of(port);
            }
        }
        return Optional.empty();
    }

    private static void requireHorizontal(BlockFace face) {
        Objects.requireNonNull(face, "structureFacing");
        if (!face.isHorizontal()) {
            throw new IllegalArgumentException("structureFacing must be horizontal: " + face);
        }
    }
}
