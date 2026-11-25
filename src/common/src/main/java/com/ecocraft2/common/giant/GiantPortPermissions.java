package com.ecocraft2.common.giant;

import java.util.Objects;

/**
 * Compact 16-bit packed permission table governing all 8 {@link GiantCornerPort} faces
 * (2 bits per port, defaulting to {@code 0x0000} = all ports {@link PortAccessMode#FULL}).
 */
public final class GiantPortPermissions {

    /** Bitmask covering all 16 bits (8 ports x 2 bits). */
    public static final int ALL_BITS_MASK = 0xFFFF;

    private int packed;

    /** Creates a permission table with all 8 ports set to {@link PortAccessMode#FULL}. */
    public GiantPortPermissions() {
        this.packed = 0;
    }

    /**
     * Creates a permission table initialized from a packed 16-bit integer.
     *
     * @param packed packed 16-bit mask (upper 16 bits are ignored)
     */
    public GiantPortPermissions(int packed) {
        this.packed = packed & ALL_BITS_MASK;
    }

    /** Returns the 16-bit packed representation in {@code [0, 0xFFFF]}. */
    public int packed() {
        return packed;
    }

    /**
     * Replaces the entire 16-bit packed permission mask.
     *
     * @param packed new packed mask (masked to 16 bits)
     */
    public void setPacked(int packed) {
        this.packed = packed & ALL_BITS_MASK;
    }

    /** Returns the {@link PortAccessMode} configured for {@code port}. */
    public PortAccessMode get(GiantCornerPort port) {
        Objects.requireNonNull(port, "port");
        return PortAccessMode.byCode((packed >>> port.bitIndex()) & 0x3);
    }

    /**
     * Sets the {@link PortAccessMode} for {@code port} without affecting any other port.
     *
     * @param port target corner port
     * @param mode new access mode
     */
    public void set(GiantCornerPort port, PortAccessMode mode) {
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(mode, "mode");
        int shift = port.bitIndex();
        packed = (packed & ~(0x3 << shift)) | ((mode.code() & 0x3) << shift);
    }

    /**
     * Cycles {@code port} to its next {@link PortAccessMode} and returns the new mode.
     *
     * @param port target corner port
     * @return updated mode
     */
    public PortAccessMode cycle(GiantCornerPort port) {
        PortAccessMode next = get(port).next();
        set(port, next);
        return next;
    }

    /** Returns {@code true} if {@code port} permits input insertion. */
    public boolean allowsInput(GiantCornerPort port) {
        return get(port).allowsInput();
    }

    /** Returns {@code true} if {@code port} permits output extraction. */
    public boolean allowsOutput(GiantCornerPort port) {
        return get(port).allowsOutput();
    }

    /** Returns {@code true} if pipes/conduits may connect to {@code port}. */
    public boolean connects(GiantCornerPort port) {
        return get(port).connects();
    }

    /**
     * Resolves input permission for a world face on a multiblock position relative to core.
     * Always returns {@code false} for non-corner or non-outward faces.
     */
    public boolean allowsInput(BlockFace structureFacing, int dx, int dy, int dz, BlockFace worldFace) {
        return GiantCornerPort.fromOffsetAndFace(structureFacing, dx, dy, dz, worldFace)
                .map(this::allowsInput)
                .orElse(false);
    }

    /**
     * Resolves output permission for a world face on a multiblock position relative to core.
     * Always returns {@code false} for non-corner or non-outward faces.
     */
    public boolean allowsOutput(BlockFace structureFacing, int dx, int dy, int dz, BlockFace worldFace) {
        return GiantCornerPort.fromOffsetAndFace(structureFacing, dx, dy, dz, worldFace)
                .map(this::allowsOutput)
                .orElse(false);
    }

    /**
     * Resolves conduit connection permission for a world face on a multiblock position relative to core.
     * Always returns {@code false} for non-corner or non-outward faces.
     */
    public boolean connects(BlockFace structureFacing, int dx, int dy, int dz, BlockFace worldFace) {
        return GiantCornerPort.fromOffsetAndFace(structureFacing, dx, dy, dz, worldFace)
                .map(this::connects)
                .orElse(false);
    }

    /** Returns an independent copy of this permission table. */
    public GiantPortPermissions copy() {
        return new GiantPortPermissions(packed);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof GiantPortPermissions other && this.packed == other.packed;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(packed);
    }

    @Override
    public String toString() {
        return "GiantPortPermissions[0x" + Integer.toHexString(packed) + "]";
    }
}
