package com.example.chargetrack.domain.degradation

import java.time.Instant

/**
 * A single benchmark test observation within a comparison group's charging-performance trend.
 *
 * All metrics are calculated strictly from the benchmark-only window
 * ([benchmarkStartedElapsedMs] .. [benchmarkEndedElapsedMs]).
 */
data class BenchmarkTrendPoint(
    val testId: String,
    val sessionId: String,
    val timestamp: Instant,
    val benchmarkDurationMs: Long,
    val benchmarkAveragePowerUw: Long,
    val benchmarkPeakPowerUw: Long,
    val benchmarkMaxTempDeciC: Int?,
    val benchmarkAvgTempDeciC: Int?,
    val isBaseline: Boolean,
)

/**
 * Group-scoped longitudinal trend analysis across comparable Standard Tests.
 */
data class GroupTrendAnalysis(
    val comparisonGroupKey: String,
    val baselinePoint: BenchmarkTrendPoint?,
    val points: List<BenchmarkTrendPoint>, // Sorted chronologically
    val latestDurationChangeFromBaselineMs: Long?,
    val latestDurationChangePercent: Double?,
    val latestPowerChangeFromBaselineUw: Long?,
    val latestPowerChangePercent: Double?,
)
