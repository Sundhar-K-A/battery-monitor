package com.example.chargetrack.domain.analytics

import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import java.time.Instant

/**
 * Immutable session summary metrics computed from raw samples and charge transitions.
 *
 * ## Principles
 * - Preserves "Estimated battery-side power" nomenclature.
 * - Retains raw signed metrics (e.g. net discharge power and negative percentage change).
 * - Distinguishes analytical subsets (excluding [QualityFlag.OUTLIER]) from raw observational counts.
 * - Uses authoritative monotonic duration, completely independent of wall-clock shifts.
 */
data class SessionSummary(
    val sessionId: String,
    val testType: TestType,
    val startedAt: Instant,
    val endedAt: Instant?,
    val endReason: SessionEndReason?,
    /** Monotonic duration in milliseconds. Null if no authoritative monotonic duration could be determined. */
    val durationMs: Long?,
    val startPercent: Int,
    val endPercent: Int?,
    /** Signed percentage gained: endPercent - startPercent (can be negative if discharge occurred). */
    val percentGained: Int?,
    /** True if Standard Test target range was fully satisfied; false if incomplete; null if free-form session. */
    val isCompleteStandardTest: Boolean?,

    // Sample counts & quality breakdown
    val totalSampleCount: Int,
    val validPowerSampleCount: Int,
    /** Number of raw samples where voltageMv == null OR currentNowUa == null. */
    val missingValueSampleCount: Int,
    val gapSampleCount: Int,
    val jitterSampleCount: Int,
    val outlierSampleCount: Int,

    // Transition counts
    val totalTransitionCount: Int,
    val contiguousOnePercentTransitionCount: Int,
    val degradedTransitionCount: Int,
    val insufficientTransitionCount: Int,

    // Estimated battery-side power metrics (µW) — labelled "Estimated battery-side power"
    val averagePowerUw: Long?,
    val medianPowerUw: Long?,
    val peakPowerUw: Long?,

    // Current metrics (µA)
    val minCurrentUa: Int?,
    val maxCurrentUa: Int?,
    val averageCurrentUa: Int?,

    // Voltage metrics (mV)
    val averageVoltageMv: Int?,

    // Temperature metrics (tenths of °C, e.g. 350 = 35.0 °C)
    val startTemperatureDeciC: Int?,
    val endTemperatureDeciC: Int?,
    val averageTemperatureDeciC: Int?,
    val peakTemperatureDeciC: Int?,

    // Transition timing (contiguous 1% transitions only)
    val averageTimePerOnePercentMs: Long?,
    val medianTimePerOnePercentMs: Long?,

    // Charging curve dynamics
    val chargingTaperStartPercent: Int?,

    // Overall data quality
    val overallQuality: DataQuality,
    val qualityFlags: Set<QualityFlag>,
)
