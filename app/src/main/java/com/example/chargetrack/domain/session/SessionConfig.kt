package com.example.chargetrack.domain.session

/**
 * Configurable parameters for the charging session engine.
 *
 * All timeouts are specified in milliseconds and are injectable for testing and future tuning.
 *
 * @property expectedSampleIntervalMs Nominal interval between measurement samples (default: 5,000 ms).
 * @property measurementGapTimeoutMs Inactivity gap duration beyond which a session is finalized with MEASUREMENT_LOST (default: 30,000 ms).
 * @property unplugDebounceMs Transient disconnect grace period before finalizing as UNPLUGGED (default: 5,000 ms).
 */
data class SessionConfig(
    val expectedSampleIntervalMs: Long = 5_000L,
    val measurementGapTimeoutMs: Long = 30_000L,
    val unplugDebounceMs: Long = 5_000L,
)
