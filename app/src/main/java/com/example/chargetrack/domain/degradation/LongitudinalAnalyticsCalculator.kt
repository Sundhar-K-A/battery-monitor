package com.example.chargetrack.domain.degradation

import com.example.chargetrack.domain.health.FullChargeCapacityObservation
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.StandardTest
import java.time.Instant
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Pure domain calculator for longitudinal charging-performance trends and
 * experimental capacity degradation analysis.
 */
object LongitudinalAnalyticsCalculator {

    /** Maximum allowed coefficient of variation for consistent capacity observations (5%). */
    const val MAX_CONSISTENT_CV: Double = 0.05

    /** Maximum allowed observation spread relative to reference capacity (8%). */
    const val MAX_CONSISTENT_SPREAD_RATIO: Double = 0.08

    /** Minimum full-charge observations required for high confidence. */
    const val HIGH_CONFIDENCE_MIN_COUNT: Int = 6

    /** Minimum full-charge observations required before displaying an estimate. */
    const val MIN_OBSERVATIONS_REQUIRED: Int = 3

    /**
     * Computes the longitudinal charging-performance trend across a series of Standard Tests
     * in the same comparison group.
     *
     * ## Principles
     * 1. Metrics are calculated strictly from the benchmark window (`benchmarkStartedElapsedMs` .. `benchmarkEndedElapsedMs`).
     * 2. Tests with `averagePowerUw <= 0` are excluded from the performance trend (net-discharge),
     *    without modifying the underlying signed power values.
     * 3. Points are strictly ordered chronologically.
     * 4. Baseline deltas are computed relative to the designated group baseline.
     */
    fun calculatePerformanceTrend(
        groupKey: String,
        testsWithMetadata: List<StandardTestPerformanceInput>,
        designatedBaselineTestId: String? = null,
    ): GroupTrendAnalysis {
        val eligiblePoints = mutableListOf<BenchmarkTrendPoint>()

        for (input in testsWithMetadata) {
            val test = input.test
            val startElapsed = test.benchmarkStartedElapsedMs ?: continue
            val endElapsed = test.benchmarkEndedElapsedMs ?: continue
            val durationMs = endElapsed - startElapsed
            if (durationMs <= 0) continue

            // Benchmark-only samples
            val benchmarkSamples = input.samples.filter { it.elapsedMs in startElapsed..endElapsed }
            val powers = benchmarkSamples.mapNotNull { sample ->
                sample.derivedPowerUw ?: (
                    if (sample.voltageMv != null && sample.currentNowUa != null) {
                        sample.voltageMv.toLong() * sample.currentNowUa
                    } else null
                )
            }

            val avgPowerUw = if (powers.isNotEmpty()) {
                powers.average().toLong()
            } else {
                0L
            }

            // Exclude net-discharging anomalous sessions from charging trend
            if (avgPowerUw <= 0) continue

            val peakPowerUw = powers.maxOrNull() ?: 0L
            val maxTempDeciC = benchmarkSamples.mapNotNull { it.temperatureDeciC }.maxOrNull()
            val avgTempDeciC = benchmarkSamples.mapNotNull { it.temperatureDeciC }.let {
                if (it.isNotEmpty()) it.average().toInt() else null
            }

            val isBaseline = if (designatedBaselineTestId != null) {
                test.id == designatedBaselineTestId
            } else {
                test.isBaseline
            }

            eligiblePoints.add(
                BenchmarkTrendPoint(
                    testId = test.id,
                    sessionId = test.sessionId,
                    timestamp = input.sessionStartedAt,
                    benchmarkDurationMs = durationMs,
                    benchmarkAveragePowerUw = avgPowerUw,
                    benchmarkPeakPowerUw = peakPowerUw,
                    benchmarkMaxTempDeciC = maxTempDeciC,
                    benchmarkAvgTempDeciC = avgTempDeciC,
                    isBaseline = isBaseline,
                )
            )
        }

        val sortedPoints = eligiblePoints.sortedBy { it.timestamp }
        val baselinePoint = sortedPoints.find { it.isBaseline }

        var durationChangeMs: Long? = null
        var durationChangePct: Double? = null
        var powerChangeUw: Long? = null
        var powerChangePct: Double? = null

        if (baselinePoint != null && sortedPoints.isNotEmpty()) {
            val latest = sortedPoints.last()
            durationChangeMs = latest.benchmarkDurationMs - baselinePoint.benchmarkDurationMs
            if (baselinePoint.benchmarkDurationMs > 0) {
                durationChangePct = (durationChangeMs.toDouble() / baselinePoint.benchmarkDurationMs) * 100.0
            }

            powerChangeUw = latest.benchmarkAveragePowerUw - baselinePoint.benchmarkAveragePowerUw
            if (baselinePoint.benchmarkAveragePowerUw > 0) {
                powerChangePct = (powerChangeUw.toDouble() / baselinePoint.benchmarkAveragePowerUw) * 100.0
            }
        }

        return GroupTrendAnalysis(
            comparisonGroupKey = groupKey,
            baselinePoint = baselinePoint,
            points = sortedPoints,
            latestDurationChangeFromBaselineMs = durationChangeMs,
            latestDurationChangePercent = durationChangePct,
            latestPowerChangeFromBaselineUw = powerChangeUw,
            latestPowerChangePercent = powerChangePct,
        )
    }

    /**
     * Computes the longitudinal capacity degradation analysis from historical full-charge observations.
     *
     * ## Principles
     * 1. Requires at least [MIN_OBSERVATIONS_REQUIRED] (3) observations to compute an estimate.
     * 2. Evaluates dual consistency conditions: CV <= 0.05 AND spread <= 8% of reference.
     * 3. Assigns confidence:
     *    - N < 3: INSUFFICIENT
     *    - 3 <= N <= 5: PRELIMINARY
     *    - N >= 6 AND consistent: HIGH
     *    - N >= 6 AND high variance: PRELIMINARY
     * 4. Preserves raw capacity while capping user-facing health at 100%.
     */
    fun calculateCapacityTrend(
        observations: List<FullChargeCapacityObservation>,
        referenceCapacityMah: Int?,
    ): CapacityDegradationAnalysis {
        val refCap = referenceCapacityMah ?: 0
        if (refCap <= 0) {
            return CapacityDegradationAnalysis(
                referenceCapacityMah = 0,
                estimatedCapacityMah = null,
                estimatedHealthPercent = null,
                latestObservation = null,
                confidence = DegradationConfidence.INSUFFICIENT,
                isConsistent = false,
                coefficientOfVariation = null,
                spreadPercent = null,
                observationCount = 0,
                points = emptyList(),
                changeFromReferenceMah = null,
                changeFromReferencePercent = null,
            )
        }

        val sortedObs = observations.sortedBy { it.timestamp }
        val points = sortedObs.map { obs ->
            CapacityTrendPoint(
                sessionId = obs.sessionId,
                timestamp = obs.timestamp,
                observedCapacityMah = obs.capacityMah,
                retentionPercent = (obs.capacityMah.toDouble() / refCap) * 100.0,
            )
        }

        val latestObs = points.lastOrNull()
        val count = points.size

        if (count < MIN_OBSERVATIONS_REQUIRED) {
            return CapacityDegradationAnalysis(
                referenceCapacityMah = refCap,
                estimatedCapacityMah = null,
                estimatedHealthPercent = null,
                latestObservation = latestObs,
                confidence = DegradationConfidence.INSUFFICIENT,
                isConsistent = false,
                coefficientOfVariation = null,
                spreadPercent = null,
                observationCount = count,
                points = points,
                changeFromReferenceMah = null,
                changeFromReferencePercent = null,
            )
        }

        val capacities = points.map { it.observedCapacityMah }
        val sortedCapacities = capacities.sorted()
        val medianCapacityMah = if (count % 2 == 1) {
            sortedCapacities[count / 2]
        } else {
            (sortedCapacities[count / 2 - 1] + sortedCapacities[count / 2]) / 2
        }

        val estimatedHealthPct = min(100, round((medianCapacityMah.toDouble() / refCap) * 100.0).toInt())

        // Consistency evaluation (Mean, Sample Variance, CV, Spread)
        val mean = capacities.average()
        val variance = if (count > 1) {
            capacities.sumOf { (it - mean) * (it - mean) } / (count - 1)
        } else 0.0
        val stdDev = sqrt(variance)
        val cv = if (mean > 0) stdDev / mean else 0.0

        val maxCap = sortedCapacities.last()
        val minCap = sortedCapacities.first()
        val spread = (maxCap - minCap).toDouble() / refCap

        val isConsistent = (cv <= MAX_CONSISTENT_CV) && (spread <= MAX_CONSISTENT_SPREAD_RATIO)

        val confidence = when {
            count < MIN_OBSERVATIONS_REQUIRED -> DegradationConfidence.INSUFFICIENT
            count < HIGH_CONFIDENCE_MIN_COUNT -> DegradationConfidence.PRELIMINARY
            isConsistent -> DegradationConfidence.HIGH
            else -> DegradationConfidence.PRELIMINARY
        }

        val changeMah = medianCapacityMah - refCap
        val changePct = (changeMah.toDouble() / refCap) * 100.0

        return CapacityDegradationAnalysis(
            referenceCapacityMah = refCap,
            estimatedCapacityMah = medianCapacityMah,
            estimatedHealthPercent = estimatedHealthPct,
            latestObservation = latestObs,
            confidence = confidence,
            isConsistent = isConsistent,
            coefficientOfVariation = cv,
            spreadPercent = spread * 100.0,
            observationCount = count,
            points = points,
            changeFromReferenceMah = changeMah,
            changeFromReferencePercent = changePct,
        )
    }
}

/**
 * Data bundle for inputting a Standard Test into longitudinal performance analysis.
 */
data class StandardTestPerformanceInput(
    val test: StandardTest,
    val sessionStartedAt: Instant,
    val samples: List<BatterySample>,
)
