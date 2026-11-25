package com.ecocraft2.common.giant;

/**
 * Pure-Java 3D cardinal direction matching Minecraft's right-handed world axes
 * ({@code NORTH = -Z}, {@code SOUTH = +Z}, {@code WEST = -X}, {@code EAST = +X},
 * {@code UP = +Y}, {@code DOWN = -Y}).
 */
public enum BlockFace {

    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private static final BlockFace[] HORIZONTALS = {NORTH, EAST, SOUTH, WEST};

    private final int stepX;
    private final int stepY;
    private final int stepZ;

    BlockFace(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    /** Returns the X unit offset ({@code -1, 0, +1}). */
    public int stepX() {
        return stepX;
    }

    /** Returns the Y unit offset ({@code -1, 0, +1}). */
    public int stepY() {
        return stepY;
    }

    /** Returns the Z unit offset ({@code -1, 0, +1}). */
    public int stepZ() {
        return stepZ;
    }

    /** Returns {@code true} if this face is horizontal ({@code NORTH, SOUTH, EAST, WEST}). */
    public boolean isHorizontal() {
        return stepY == 0;
    }

    /** Returns the opposite cardinal direction. */
    public BlockFace opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /**
     * Rotates a horizontal face 90 degrees clockwise when viewed from above ({@code +Y}).
     * ({@code NORTH -> EAST -> SOUTH -> WEST -> NORTH}).
     *
     * @return rotated horizontal face
     * @throws IllegalStateException if invoked on {@link #UP} or {@link #DOWN}
     */
    public BlockFace clockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> throw new IllegalStateException("Cannot rotate vertical face clockwise: " + this);
        };
    }

    /**
     * Rotates a horizontal face 90 degrees counter-clockwise when viewed from above ({@code +Y}).
     * ({@code NORTH -> WEST -> SOUTH -> EAST -> NORTH}).
     *
     * @return rotated horizontal face
     * @throws IllegalStateException if invoked on {@link #UP} or {@link #DOWN}
     */
    public BlockFace counterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case WEST -> SOUTH;
            case SOUTH -> EAST;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("Cannot rotate vertical face counter-clockwise: " + this);
        };
    }

    /** Returns a copy of the four horizontal directions in clockwise order. */
    public static BlockFace[] horizontals() {
        return HORIZONTALS.clone();
    }
}
