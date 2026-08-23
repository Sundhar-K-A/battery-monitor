package com.example.chargetrack.domain.analytics

import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.ChargingSession
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure, deterministic calculation engine for session summary analytics.
 *
 * ## Principles
 * - Accumulates 32-bit Integer metrics (`currentNowUa`, `voltageMv`, `temperatureDeciC`) as 64-bit `Long`
 *   to guarantee overflow safety across multi-thousand sample sessions.
 * - Distinguishes the **analytical power subset** (non-null, non-outlier) from raw observational counts.
 * - Enforces positive peak power before evaluating sustained multi-sample taper points.
 * - Strict contiguous 1% filtering for pacing (excludes gap/insufficient transitions).
 * - Avoids double-counting in corruption ratios.
 */
object SessionSummaryAnalyticsCalculator {

    const val DEFAULT_TAPER_CONSECUTIVE_SAMPLE_COUNT = 5
    const val DEFAULT_TAPER_POWER_RATIO_THRESHOLD = 0.80

    /**
     * Computes the complete immutable [SessionSummary] from raw source data.
     *
     * @param session The root [ChargingSession] entity.
     * @param standardTest Optional [StandardTestEntity] containing target start/end percentages.
     * @param samples Chronologically ordered list of [BatterySample] records (`ORDER BY elapsedMs ASC`).
     * @param transitions List of [ChargeTransition] records for this session.
     * @param explicitDurationMs Authoritative monotonic duration if available from session state machine.
     * @param taperConsecutiveSamples Number of qualifying consecutive samples required to confirm taper (default 5).
     * @param taperPowerRatio Power threshold relative to peak for qualifying as taper (default 0.80).
     */
    fun calculateSummary(
        session: ChargingSession,
        standardTest: StandardTestEntity? = null,
        samples: List<BatterySample>,
        transitions: List<ChargeTransition>,
        explicitDurationMs: Long? = null,
        taperConsecutiveSamples: Int = DEFAULT_TAPER_CONSECUTIVE_SAMPLE_COUNT,
        taperPowerRatio: Double = DEFAULT_TAPER_POWER_RATIO_THRESHOLD,
    ): SessionSummary {
        val totalSampleCount = samples.size

        // 1. Authoritative Monotonic Duration
        val durationMs = explicitDurationMs ?: if (samples.isNotEmpty()) {
            (samples.last().elapsedMs - samples.first().elapsedMs).coerceAtLeast(0L)
        } else {
            null
        }

        // 2. Battery Percentage & Signed Gain
        val startPercent = session.startPercent
        val lastSampleWithPercent = samples.lastOrNull { it.percent != null }
        val endPercent = session.endPercent ?: lastSampleWithPercent?.percent
        val percentGained = if (endPercent != null) endPercent - startPercent else null

        // 3. Standard Test Completion (Supports arbitrary target ranges)
        val isCompleteStandardTest = if (session.testType == TestType.STANDARD && standardTest != null) {
            startPercent <= standardTest.targetStartPercent && (endPercent ?: 0) >= standardTest.targetEndPercent
        } else {
            null
        }

        // 4. Quality Flag Breakdown & Missing Required Values
        val missingValueSampleCount = samples.count { it.voltageMv == null || it.currentNowUa == null }
        val gapSampleCount = samples.count { QualityFlag.GAP_DETECTED in it.qualityFlags }
        val jitterSampleCount = samples.count { QualityFlag.PERCENTAGE_JITTER in it.qualityFlags }
        val outlierSampleCount = samples.count { QualityFlag.OUTLIER in it.qualityFlags }
        val allQualityFlags = samples.flatMap { it.qualityFlags }.toSet()

        // 5. Transitions Breakdown
        val totalTransitionCount = transitions.size
        val contiguousTransitions = transitions.filter {
            it.toPercent == it.fromPercent + 1 && it.quality != DataQuality.INSUFFICIENT
        }
        val contiguousOnePercentTransitionCount = contiguousTransitions.size
        val degradedTransitionCount = transitions.count { it.quality == DataQuality.DEGRADED }
        val insufficientTransitionCount = transitions.count { it.quality == DataQuality.INSUFFICIENT }

        // 6. Estimated Battery-Side Power (Analytical Subset: non-null, excludes OUTLIERs)
        val analyticalPowerSamples = samples.filter {
            it.derivedPowerUw != null && QualityFlag.OUTLIER !in it.qualityFlags
        }
        val validPowerSampleCount = analyticalPowerSamples.size
        val powerValues = analyticalPowerSamples.map { it.derivedPowerUw!! }

        val peakPowerUw = powerValues.maxOrNull()
        val averagePowerUw = if (powerValues.isNotEmpty()) {
            (powerValues.sumOf { it }.toDouble() / powerValues.size).roundToLong()
        } else {
            null
        }
        val medianPowerUw = calculateMedianLong(powerValues)

        // 7. Current & Voltage Metrics (64-bit Long Accumulation prevents 32-bit overflow)
        val validCurrentSamples = samples.filter { it.currentNowUa != null }
        val minCurrentUa = validCurrentSamples.minOfOrNull { it.currentNowUa!! }
        val maxCurrentUa = validCurrentSamples.maxOfOrNull { it.currentNowUa!! }
        val averageCurrentUa = if (validCurrentSamples.isNotEmpty()) {
            val sumCurrent = validCurrentSamples.sumOf { it.currentNowUa!!.toLong() }
            (sumCurrent.toDouble() / validCurrentSamples.size).roundToInt()
        } else {
            null
        }

        val validVoltageSamples = samples.filter { it.voltageMv != null }
        val averageVoltageMv = if (validVoltageSamples.isNotEmpty()) {
            val sumVoltage = validVoltageSamples.sumOf { it.voltageMv!!.toLong() }
            (sumVoltage.toDouble() / validVoltageSamples.size).roundToInt()
        } else {
            null
        }

        // 8. Temperature Metrics (64-bit Long Accumulation)
        val validTempSamples = samples.filter { it.temperatureDeciC != null }
        val startTemperatureDeciC = validTempSamples.firstOrNull()?.temperatureDeciC
        val endTemperatureDeciC = validTempSamples.lastOrNull()?.temperatureDeciC
        val peakTemperatureDeciC = validTempSamples.maxOfOrNull { it.temperatureDeciC!! }
        val averageTemperatureDeciC = if (validTempSamples.isNotEmpty()) {
            val sumTemp = validTempSamples.sumOf { it.temperatureDeciC!!.toLong() }
            (sumTemp.toDouble() / validTempSamples.size).roundToInt()
        } else {
            null
        }

        // 9. Pacing Metrics (Contiguous 1% transitions only)
        val averageTimePerOnePercentMs = if (contiguousTransitions.isNotEmpty()) {
            val sumDuration = contiguousTransitions.sumOf { it.durationMs }
            (sumDuration.toDouble() / contiguousTransitions.size).roundToLong()
        } else {
            null
        }
        val medianTimePerOnePercentMs = calculateMedianLong(contiguousTransitions.map { it.durationMs })

        // 10. Sustained Taper Point Detection
        val chargingTaperStartPercent = detectTaperStartPercent(
            analyticalSamples = analyticalPowerSamples,
            peakPowerUw = peakPowerUw,
            consecutiveSamples = taperConsecutiveSamples,
            ratioThreshold = taperPowerRatio,
        )

        // 11. Overall Session Data Quality
        val corruptedOrIncompleteSampleCount = samples.count {
            QualityFlag.MISSING_REQUIRED_VALUE in it.qualityFlags ||
                QualityFlag.GAP_DETECTED in it.qualityFlags
        }
        val corruptionRatio = if (totalSampleCount > 0) {
            corruptedOrIncompleteSampleCount.toDouble() / totalSampleCount
        } else {
            0.0
        }

        val overallQuality = when {
            totalSampleCount < 3 || validPowerSampleCount < 3 || corruptionRatio > 0.50 -> {
                DataQuality.INSUFFICIENT
            }

            missingValueSampleCount > 0 ||
                gapSampleCount > 0 ||
                jitterSampleCount > 0 ||
                outlierSampleCount > 0 ||
                insufficientTransitionCount > 0 ||
                degradedTransitionCount > 0 -> {
                DataQuality.DEGRADED
            }

            else -> {
                DataQuality.GOOD
            }
        }

        return SessionSummary(
            sessionId = session.id,
            testType = session.testType,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            endReason = session.endReason,
            durationMs = durationMs,
            startPercent = startPercent,
            endPercent = endPercent,
            percentGained = percentGained,
            isCompleteStandardTest = isCompleteStandardTest,
            totalSampleCount = totalSampleCount,
            validPowerSampleCount = validPowerSampleCount,
            missingValueSampleCount = missingValueSampleCount,
            gapSampleCount = gapSampleCount,
            jitterSampleCount = jitterSampleCount,
            outlierSampleCount = outlierSampleCount,
            totalTransitionCount = totalTransitionCount,
            contiguousOnePercentTransitionCount = contiguousOnePercentTransitionCount,
            degradedTransitionCount = degradedTransitionCount,
            insufficientTransitionCount = insufficientTransitionCount,
            averagePowerUw = averagePowerUw,
            medianPowerUw = medianPowerUw,
            peakPowerUw = peakPowerUw,
            minCurrentUa = minCurrentUa,
            maxCurrentUa = maxCurrentUa,
            averageCurrentUa = averageCurrentUa,
            averageVoltageMv = averageVoltageMv,
            startTemperatureDeciC = startTemperatureDeciC,
            endTemperatureDeciC = endTemperatureDeciC,
            averageTemperatureDeciC = averageTemperatureDeciC,
            peakTemperatureDeciC = peakTemperatureDeciC,
            averageTimePerOnePercentMs = averageTimePerOnePercentMs,
            medianTimePerOnePercentMs = medianTimePerOnePercentMs,
            chargingTaperStartPercent = chargingTaperStartPercent,
            overallQuality = overallQuality,
            qualityFlags = allQualityFlags,
        )
    }

    /**
     * Detects the battery percentage at which charging power begins a sustained downward taper.
     *
     * Invariants:
     * - Requires positive peak power (`peakPowerUw > 0L`).
     * - Requires at least [consecutiveSamples] qualifying samples (e.g. 5) starting from index `j`
     *   through `j + consecutiveSamples - 1` that all remain below the taper power threshold
     *   without spiking above.
     */
    private fun detectTaperStartPercent(
        analyticalSamples: List<BatterySample>,
        peakPowerUw: Long?,
        consecutiveSamples: Int,
        ratioThreshold: Double,
    ): Int? {
        if (peakPowerUw == null || peakPowerUw <= 0L) return null
        if (analyticalSamples.size < consecutiveSamples) return null

        val peakThresholdUw = (peakPowerUw * ratioThreshold).roundToLong()
        val peakIndex = analyticalSamples.indexOfFirst { it.derivedPowerUw == peakPowerUw }
        if (peakIndex < 0) return null

        // Search for sustained window after peak power
        val maxStartIndex = analyticalSamples.size - consecutiveSamples
        for (j in (peakIndex + 1)..maxStartIndex) {
            val window = analyticalSamples.subList(j, j + consecutiveSamples)
            val isSustained = window.all { sample ->
                val power = sample.derivedPowerUw
                power != null && power <= peakThresholdUw
            }

            if (isSustained) {
                // Return battery percent at start of sustained taper
                val taperPercent = analyticalSamples[j].percent
                if (taperPercent != null) {
                    return taperPercent
                }
            }
        }

        return null
    }

    private fun calculateMedianLong(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val size = sorted.size
        return if (size % 2 == 1) {
            sorted[size / 2]
        } else {
            val mid1 = sorted[size / 2 - 1]
            val mid2 = sorted[size / 2]
            ((mid1.toDouble() + mid2.toDouble()) / 2.0).roundToLong()
        }
    }
}
