package com.example.chargetrack.domain.comparison

import com.example.chargetrack.domain.enums.DataQuality

/**
 * Duration delta for a specific contiguous $p \rightarrow p+1$ percentage step.
 */
data class PercentTransitionDelta(
    val percent: Int,
    val primaryDurationMs: Long?,
    val comparedDurationMs: Long?,
    val deltaMs: Long?,
    val primaryQuality: DataQuality?,
    val comparedQuality: DataQuality?,
    val isComparable: Boolean,
)

/**
 * Authoritative pairwise comparison results between two standard test sessions.
 *
 * ## Principles
 * - Preserves signed deltas ($\Delta = B - A$).
 * - Uses neutral terminology ("change from baseline").
 */
data class TestComparisonResult(
    val primarySessionId: String,
    val comparedSessionId: String,
    val conditions: ComparisonCondition,
    val durationDeltaMs: Long?,
    val durationDeltaPercent: Double?,
    val averagePowerDeltaUw: Long?,
    val averagePowerDeltaPercent: Double?,
    val peakPowerDeltaUw: Long?,
    val maxTempDeltaDeciC: Int?,
    val startTempDeltaDeciC: Int?,
    val perPercentDeltas: List<PercentTransitionDelta>,
)
