package com.ecocraft2.common.gas;

/**
 * Result of ticking a gas or biogas processing engine.
 */
public enum GasMachineOutcome {
    /** Missing required input items or input fluid; progress reset to 0. */
    IDLE,
    /** Actively making progress on the current operation. */
    WORKING,
    /** Operation completed cleanly; caller should consume input item(s) and emit any item output. */
    COMPLETED,
    /** Valid inputs present, but paused awaiting EU or gas fuel (progress preserved). */
    NO_POWER,
    /** Valid inputs present, but paused because output tank or output item slot lacks capacity (progress preserved). */
    OUTPUT_FULL
}
