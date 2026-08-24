package com.example.chargetrack.domain.degradation

/**
 * Confidence level in the longitudinal capacity degradation estimate.
 */
enum class DegradationConfidence {
    /** Insufficient full-charge observations (< 3) to compute an estimate. */
    INSUFFICIENT,

    /**
     * Preliminary estimate:
     * - 3 to 5 observations (early indicator), OR
     * - 6+ observations with high variance across sessions.
     */
    PRELIMINARY,

    /** High confidence: 6 or more consistent observations (CV <= 0.05 AND spread <= 8%). */
    HIGH,
}
