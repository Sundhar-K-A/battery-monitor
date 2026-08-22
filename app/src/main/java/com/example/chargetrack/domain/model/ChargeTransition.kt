package com.example.chargetrack.domain.model

import com.example.chargetrack.domain.enums.DataQuality
import java.time.Instant
import java.util.UUID

/**
 * A derived record representing the time taken to advance from one integer battery
 * percentage to the next during a charging session.
 *
 * Power and temperature fields are null if there were insufficient valid [BatterySample]
 * records to compute a meaningful aggregate.
 *
 * Invariants enforced at construction:
 * - [fromPercent] must be in 0..99; [toPercent] must be in 1..100.
 * - [toPercent] must be strictly greater than [fromPercent].
 * - [endedAt] must not precede [startedAt].
 * - [durationMs] must be non-negative.
 */
data class ChargeTransition(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    /** Starting battery percentage (0..99). */
    val fromPercent: Int,
    /** Ending battery percentage (1..100, strictly > [fromPercent]). */
    val toPercent: Int,
    val startedAt: Instant,
    val endedAt: Instant,
    /** Duration in milliseconds. */
    val durationMs: Long,
    /** Average estimated battery-side power in µW during this transition. Null if insufficient data. */
    val averagePowerUw: Long? = null,
    /** Median estimated battery-side power in µW during this transition. Null if insufficient data. */
    val medianPowerUw: Long? = null,
    /** Peak estimated battery-side power in µW during this transition. Null if insufficient data. */
    val peakPowerUw: Long? = null,
    /** Average battery temperature in tenths of °C during this transition. Null if unavailable. */
    val averageTemperatureDeciC: Int? = null,
    /** Maximum battery temperature in tenths of °C during this transition. Null if unavailable. */
    val maxTemperatureDeciC: Int? = null,
    /** Number of raw [BatterySample] records contributing to this transition. */
    val sampleCount: Int,
    /** Overall data quality for this transition summary. */
    val quality: DataQuality = DataQuality.GOOD
) {
    init {
        require(fromPercent in 0..99) { "fromPercent must be in 0..99, was $fromPercent" }
        require(toPercent in 1..100) { "toPercent must be in 1..100, was $toPercent" }
        require(toPercent > fromPercent) {
            "toPercent ($toPercent) must be > fromPercent ($fromPercent)"
        }
        require(!endedAt.isBefore(startedAt)) {
            "endedAt ($endedAt) must not precede startedAt ($startedAt)"
        }
        require(durationMs >= 0) { "durationMs must be non-negative, was $durationMs" }
        require(sampleCount >= 0) { "sampleCount must be non-negative, was $sampleCount" }
    }
}
