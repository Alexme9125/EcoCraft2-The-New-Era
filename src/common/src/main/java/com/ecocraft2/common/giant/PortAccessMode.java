package com.ecocraft2.common.giant;

/**
 * Per-port item and fluid automation permission modes for Giant Machine lower-corner ports.
 */
public enum PortAccessMode {

    /** Bidirectional input and output allowed; pipes/hoppers connect. */
    FULL(0, true, true, true),

    /** Input only; pipes/hoppers connect. */
    INPUT(1, true, false, true),

    /** Output only; pipes/hoppers connect. */
    OUTPUT(2, false, true, true),

    /** Completely disabled; neither input nor output allowed and pipes do not connect. */
    BLOCKED(3, false, false, false);

    private static final PortAccessMode[] BY_CODE = values();

    private final int code;
    private final boolean allowsInput;
    private final boolean allowsOutput;
    private final boolean connects;

    PortAccessMode(int code, boolean allowsInput, boolean allowsOutput, boolean connects) {
        this.code = code;
        this.allowsInput = allowsInput;
        this.allowsOutput = allowsOutput;
        this.connects = connects;
    }

    /** Returns the 2-bit encoded integer code ({@code 0..3}). */
    public int code() {
        return code;
    }

    /** Returns {@code true} if items/fluids may be inserted through this port. */
    public boolean allowsInput() {
        return allowsInput;
    }

    /** Returns {@code true} if items/fluids may be extracted through this port. */
    public boolean allowsOutput() {
        return allowsOutput;
    }

    /** Returns {@code true} if pipes/automation conduits should connect visually and functionally. */
    public boolean connects() {
        return connects;
    }

    /** Cycles to the next permission mode: {@code FULL -> INPUT -> OUTPUT -> BLOCKED -> FULL}. */
    public PortAccessMode next() {
        return BY_CODE[(code + 1) & 0x3];
    }

    /**
     * Decodes a 2-bit integer code into a {@link PortAccessMode}.
     *
     * @param code raw integer (masked with {@code 0x3})
     * @return decoded mode
     */
    public static PortAccessMode byCode(int code) {
        return BY_CODE[code & 0x3];
    }
}
