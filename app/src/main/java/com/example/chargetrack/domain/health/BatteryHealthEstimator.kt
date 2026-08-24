package com.example.chargetrack.domain.health

import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.model.BatterySample
import java.time.Instant
import kotlin.math.min
import kotlin.math.round

/**
 * Authoritative pure calculator for deriving single-session full-charge capacity observations
 * and estimating multi-session battery health.
 */
object BatteryHealthEstimator {

    /** Minimum physically plausible capacity ratio relative to reference (40%). */
    const val MIN_CAPACITY_RATIO: Double = 0.40

    /** Maximum physically plausible capacity ratio relative to reference (130%). */
    const val MAX_CAPACITY_RATIO: Double = 1.30

    /** Minimum number of qualifying full-charge sessions required before displaying an estimate. */
    const val MIN_OBSERVATIONS_REQUIRED: Int = 3

    /**
     * Extracts a single full-charge capacity observation from a session's telemetry.
     *
     * ## Rules
     * 1. Sessions with [ChargingMode.BYPASS] are rejected (bypass charging does not charge cell).
     * 2. Battery must reach `percent == 100`. (BATTERY_STATUS_FULL at <100% does NOT qualify).
     * 3. The 100% window is defined from the first qualifying `percent == 100` sample to the end of the session.
     * 4. Must contain valid positive `chargeCounterUah > 0` samples.
     * 5. Derives exactly one observation using the median of `chargeCounterUah` in that window.
     * 6. Validates against [MIN_CAPACITY_RATIO] and [MAX_CAPACITY_RATIO] when reference capacity is provided.
     */
    fun extractSessionObservation(
        sessionId: String,
        sessionTimestamp: Instant,
        samples: List<BatterySample>,
        chargingMode: ChargingMode?,
        referenceCapacityMah: Int?,
    ): FullChargeCapacityObservation? {
        if (chargingMode == ChargingMode.BYPASS) return null

        // 1. Mandatory 100% endpoint window
        val first100Index = samples.indexOfFirst { it.percent == 100 }
        if (first100Index < 0) return null

        val window100Samples = samples.subList(first100Index, samples.size)
        val validCounters = window100Samples.mapNotNull { it.chargeCounterUah }.filter { it > 0 }
        if (validCounters.isEmpty()) return null

        // 2. Exact median of 100% window
        val sorted = validCounters.sorted()
        val medianUah = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2].toLong()
        } else {
            (sorted[sorted.size / 2 - 1].toLong() + sorted[sorted.size / 2].toLong()) / 2
        }

        val capacityMah = (medianUah / 1000).toInt()

        // 3. Plausibility check against named boundaries
        if (referenceCapacityMah != null && referenceCapacityMah > 0) {
            val minPlausible = (referenceCapacityMah * MIN_CAPACITY_RATIO).toInt()
            val maxPlausible = (referenceCapacityMah * MAX_CAPACITY_RATIO).toInt()
            if (capacityMah !in minPlausible..maxPlausible) return null
        }

        return FullChargeCapacityObservation(
            sessionId = sessionId,
            timestamp = sessionTimestamp,
            capacityMah = capacityMah,
            rawMedianUah = medianUah,
            sampleCountAtFull = validCounters.size,
        )
    }

    /**
     * Computes the estimated battery health from historical qualifying full-charge observations.
     *
     * ## Principles
     * 1. Requires at least [MIN_OBSERVATIONS_REQUIRED] (3) observations to avoid declaring degradation on single sessions.
     * 2. Uses median of historical observations for robustness against noise.
     * 3. Preserves raw observed capacity internally while capping displayed health at 100%.
     */
    fun calculateHealth(
        observations: List<FullChargeCapacityObservation>,
        referenceCapacityMah: Int?,
    ): BatteryHealthEstimate {
        if (referenceCapacityMah == null || referenceCapacityMah <= 0) {
            return BatteryHealthEstimate.Unavailable
        }

        if (observations.size < MIN_OBSERVATIONS_REQUIRED) {
            return BatteryHealthEstimate.InsufficientData(
                observationCount = observations.size,
                requiredCount = MIN_OBSERVATIONS_REQUIRED,
                referenceCapacityMah = referenceCapacityMah,
            )
        }

        val sortedCapacities = observations.map { it.capacityMah }.sorted()
        val medianCapacityMah = if (sortedCapacities.size % 2 == 1) {
            sortedCapacities[sortedCapacities.size / 2]
        } else {
            (sortedCapacities[sortedCapacities.size / 2 - 1] + sortedCapacities[sortedCapacities.size / 2]) / 2
        }

        val rawHealthPct = (medianCapacityMah.toDouble() / referenceCapacityMah) * 100.0
        val displayedHealthPct = min(100, round(rawHealthPct).toInt())

        return BatteryHealthEstimate.Calculated(
            displayedHealthPercentage = displayedHealthPct,
            rawHealthPercentage = rawHealthPct,
            medianCapacityMah = medianCapacityMah,
            referenceCapacityMah = referenceCapacityMah,
            observationCount = observations.size,
            lastObservationAt = observations.maxOf { it.timestamp },
        )
    }
}
