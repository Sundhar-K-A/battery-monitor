package com.example.chargetrack.domain.enums

/**
 * Fine-grained quality flags for individual [BatterySample] records.
 *
 * Multiple flags may be present simultaneously on the same sample without creating
 * a contradictory state (unlike [DataQuality] which is mutually exclusive at summary level).
 */
enum class QualityFlag {
    /** A measurement gap larger than the configured sampling interval was detected before this sample. */
    GAP_DETECTED,
    /** A required measurement field (voltage or current) was unavailable from the device. */
    MISSING_REQUIRED_VALUE,
    /** A value is outside a reasonable physical range (including net discharge while plugged in). */
    OUTLIER,
    /** Battery percentage went backwards or fluctuated unexpectedly around a transition boundary. */
    PERCENTAGE_JITTER,
    /** The monitoring foreground service was interrupted or restarted during this sample window. */
    SERVICE_INTERRUPTED
}
