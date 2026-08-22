package com.example.chargetrack.domain.enums

/**
 * Records why a charging session ended.
 * Every completed session must have an explicit end reason — never left null silently.
 */
enum class SessionEndReason {
    /** The device reported charging stopped (charger fully charged or paused). */
    CHARGING_STOPPED,
    /** The charger/cable was physically disconnected. */
    UNPLUGGED,
    /** The user explicitly stopped the monitoring session from the UI. */
    USER_STOPPED,
    /** Measurement was lost for longer than the configured gap threshold. */
    MEASUREMENT_LOST,
    /** The device restarted or powered off, ending the session. */
    DEVICE_RESTARTED,
    /** End reason could not be determined. */
    UNKNOWN
}
