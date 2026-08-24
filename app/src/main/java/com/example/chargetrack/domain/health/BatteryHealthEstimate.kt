package com.example.chargetrack.domain.health

import java.time.Instant

/**
 * Result of ChargeTrack's multi-session battery health estimation.
 */
sealed interface BatteryHealthEstimate {

    /**
     * Sufficient historical full-charge observations exist (>= 3) to compute an estimate.
     *
     * @param displayedHealthPercentage The user-facing estimated percentage, capped at 100%.
     * @param rawHealthPercentage       The unclipped mathematical ratio ((medianCapacity / referenceCapacity) * 100).
     * @param medianCapacityMah         The robust median of historical observed full-charge capacities in mAh.
     * @param referenceCapacityMah      The reference design baseline capacity (e.g. 7000 mAh typical for iQOO 15).
     * @param observationCount          The number of qualifying full-charge sessions used.
     * @param lastObservationAt         Timestamp of the most recent full-charge observation.
     */
    data class Calculated(
        val displayedHealthPercentage: Int,
        val rawHealthPercentage: Double,
        val medianCapacityMah: Int,
        val referenceCapacityMah: Int,
        val observationCount: Int,
        val lastObservationAt: Instant,
    ) : BatteryHealthEstimate

    /**
     * Insufficient full-charge observations exist to present an estimate (< 3).
     *
     * @param observationCount     The current number of qualifying observations available.
     * @param requiredCount        The minimum number of observations required (default 3).
     * @param referenceCapacityMah The reference capacity if known from device profile.
     */
    data class InsufficientData(
        val observationCount: Int,
        val requiredCount: Int = 3,
        val referenceCapacityMah: Int? = null,
    ) : BatteryHealthEstimate

    /**
     * Device reference profile or measurement data is unavailable.
     */
    data object Unavailable : BatteryHealthEstimate
}
