package com.example.chargetrack.domain.comparison

import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest
import com.example.chargetrack.ui.charts.model.ChartDataPoint
import com.example.chargetrack.ui.charts.model.ChartSegment
import com.example.chargetrack.ui.charts.model.ChartSeries
import com.example.chargetrack.ui.charts.model.TooltipData
import kotlin.math.abs

/**
 * Data bundle representing a standard test session and all its associated telemetry.
 */
data class StandardTestDataBundle(
    val session: ChargingSession,
    val summary: SessionSummary,
    val standardTest: StandardTest?,
    val setup: ChargingSetup?,
    val software: SoftwareSnapshot?,
    val transitions: List<ChargeTransition>,
    val samples: List<BatterySample>,
)

object StandardTestComparisonCalculator {

    /**
     * Calculates pairwise delta analytics between a primary/baseline test and a candidate test.
     */
    fun calculatePairwiseComparison(
        primary: StandardTestDataBundle,
        compared: StandardTestDataBundle,
    ): TestComparisonResult {
        val conditions = ComparisonCondition.evaluate(
            primaryTest = primary.standardTest,
            comparedTest = compared.standardTest,
            primarySetup = primary.setup,
            comparedSetup = compared.setup,
            primarySoftware = primary.software,
            comparedSoftware = compared.software,
            primaryStartTempDeciC = primary.summary.startTemperatureDeciC,
            comparedStartTempDeciC = compared.summary.startTemperatureDeciC,
        )

        // 1. Duration Delta
        val pDur = primary.summary.durationMs
        val cDur = compared.summary.durationMs
        val durDelta = if (pDur != null && cDur != null) cDur - pDur else null
        val durPercent = if (pDur != null && pDur > 0 && durDelta != null) {
            (durDelta.toDouble() / pDur) * 100.0
        } else null

        // 2. Average Power Delta (Signed delta = B - A)
        val pAvgP = primary.summary.averagePowerUw
        val cAvgP = compared.summary.averagePowerUw
        val avgPDelta = if (pAvgP != null && cAvgP != null) cAvgP - pAvgP else null
        val avgPPercent = if (pAvgP != null && pAvgP != 0L && avgPDelta != null) {
            (avgPDelta.toDouble() / abs(pAvgP)) * 100.0
        } else null

        // 3. Peak Power Delta
        val pPeak = primary.summary.peakPowerUw
        val cPeak = compared.summary.peakPowerUw
        val peakDelta = if (pPeak != null && cPeak != null) cPeak - pPeak else null

        // 4. Max Temperature Delta
        val pMaxT = primary.summary.peakTemperatureDeciC
        val cMaxT = compared.summary.peakTemperatureDeciC
        val maxTDelta = if (pMaxT != null && cMaxT != null) cMaxT - pMaxT else null

        // 5. Start Temperature Delta
        val pStartT = primary.summary.startTemperatureDeciC
        val cStartT = compared.summary.startTemperatureDeciC
        val startTDelta = if (pStartT != null && cStartT != null) cStartT - pStartT else null

        // 6. Per-1% Transition Deltas
        val perPercentDeltas = calculatePerPercentDeltas(primary.transitions, compared.transitions)

        return TestComparisonResult(
            primarySessionId = primary.session.id,
            comparedSessionId = compared.session.id,
            conditions = conditions,
            durationDeltaMs = durDelta,
            durationDeltaPercent = durPercent,
            averagePowerDeltaUw = avgPDelta,
            averagePowerDeltaPercent = avgPPercent,
            peakPowerDeltaUw = peakDelta,
            maxTempDeltaDeciC = maxTDelta,
            startTempDeltaDeciC = startTDelta,
            perPercentDeltas = perPercentDeltas,
        )
    }

    /**
     * Matches contiguous 1% transitions ($p \rightarrow p+1$) between two sessions.
     *
     * Rules:
     * - `GOOD + GOOD` -> valid comparison
     * - `GOOD + DEGRADED` / `DEGRADED + DEGRADED` -> valid comparison with quality preserved
     * - `INSUFFICIENT` on either side -> excluded from normal 1% delta
     * - Missing transition on either side -> excluded
     */
    fun calculatePerPercentDeltas(
        primaryTransitions: List<ChargeTransition>,
        comparedTransitions: List<ChargeTransition>,
    ): List<PercentTransitionDelta> {
        val primaryMap = primaryTransitions
            .filter { it.toPercent == it.fromPercent + 1 }
            .associateBy { it.fromPercent }

        val comparedMap = comparedTransitions
            .filter { it.toPercent == it.fromPercent + 1 }
            .associateBy { it.fromPercent }

        val allPercents = (primaryMap.keys + comparedMap.keys).sorted()

        return allPercents.map { p ->
            val pTrans = primaryMap[p]
            val cTrans = comparedMap[p]

            val isBothPresent = pTrans != null && cTrans != null
            val isNeitherInsufficient = pTrans?.quality != DataQuality.INSUFFICIENT &&
                cTrans?.quality != DataQuality.INSUFFICIENT

            val isComparable = isBothPresent && isNeitherInsufficient

            val deltaMs = if (isComparable) {
                cTrans!!.durationMs - pTrans!!.durationMs
            } else null

            PercentTransitionDelta(
                percent = p,
                primaryDurationMs = pTrans?.durationMs,
                comparedDurationMs = cTrans?.durationMs,
                deltaMs = deltaMs,
                primaryQuality = pTrans?.quality,
                comparedQuality = cTrans?.quality,
                isComparable = isComparable,
            )
        }
    }

    /**
     * Builds multi-curve series for Power vs Battery %, strictly preserving each session's
     * chronological sample sequence without sorting by X or collapsing points at the same percentage.
     */
    fun buildAlignedPowerSeries(bundle: StandardTestDataBundle, seriesName: String): ChartSeries {
        val validSamples = bundle.samples.filter { it.percent != null && it.derivedPowerUw != null }
        if (validSamples.isEmpty()) {
            return ChartSeries(name = seriesName, yUnit = "W", xUnit = "%", segments = emptyList())
        }

        val segments = mutableListOf<ChartSegment>()
        var currentPoints = mutableListOf<ChartDataPoint>()

        for (sample in bundle.samples) {
            val p = sample.percent
            val powerUw = sample.derivedPowerUw

            if (p != null && powerUw != null) {
                val powerW = (powerUw / 1_000_000.0).toFloat()
                val tooltip = TooltipData(
                    elapsedMs = sample.elapsedMs,
                    percent = p,
                    powerW = powerUw / 1_000_000.0,
                    voltageV = sample.voltageMv?.let { it / 1000.0 },
                    currentA = sample.currentNowUa?.let { it / 1_000_000.0 },
                    temperatureC = sample.temperatureDeciC?.let { it / 10.0 },
                    qualityFlags = sample.qualityFlags,
                )
                currentPoints.add(
                    ChartDataPoint(
                        x = p.toFloat(),
                        y = powerW,
                        tooltip = tooltip,
                    )
                )
            } else {
                if (currentPoints.isNotEmpty()) {
                    segments.add(ChartSegment(currentPoints.toList()))
                    currentPoints = mutableListOf()
                }
            }
        }

        if (currentPoints.isNotEmpty()) {
            segments.add(ChartSegment(currentPoints.toList()))
        }

        val allPoints = segments.flatMap { it.points }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: 100f
        val minY = allPoints.minOfOrNull { it.y } ?: 0f
        val maxY = allPoints.maxOfOrNull { it.y } ?: 100f

        return ChartSeries(
            name = seriesName,
            yUnit = "W",
            xUnit = "%",
            segments = segments,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            hasZeroLine = minY < 0f && maxY > 0f,
        )
    }

    /**
     * Builds multi-curve series for Temperature vs Battery %, strictly preserving chronological order.
     */
    fun buildAlignedTemperatureSeries(bundle: StandardTestDataBundle, seriesName: String): ChartSeries {
        val validSamples = bundle.samples.filter { it.percent != null && it.temperatureDeciC != null }
        if (validSamples.isEmpty()) {
            return ChartSeries(name = seriesName, yUnit = "°C", xUnit = "%", segments = emptyList())
        }

        val segments = mutableListOf<ChartSegment>()
        var currentPoints = mutableListOf<ChartDataPoint>()

        for (sample in bundle.samples) {
            val p = sample.percent
            val tempDeciC = sample.temperatureDeciC

            if (p != null && tempDeciC != null) {
                val tempC = (tempDeciC / 10.0).toFloat()
                val tooltip = TooltipData(
                    elapsedMs = sample.elapsedMs,
                    percent = p,
                    powerW = sample.derivedPowerUw?.let { it / 1_000_000.0 },
                    voltageV = sample.voltageMv?.let { it / 1000.0 },
                    currentA = sample.currentNowUa?.let { it / 1_000_000.0 },
                    temperatureC = tempDeciC / 10.0,
                    qualityFlags = sample.qualityFlags,
                )
                currentPoints.add(
                    ChartDataPoint(
                        x = p.toFloat(),
                        y = tempC,
                        tooltip = tooltip,
                    )
                )
            } else {
                if (currentPoints.isNotEmpty()) {
                    segments.add(ChartSegment(currentPoints.toList()))
                    currentPoints = mutableListOf()
                }
            }
        }

        if (currentPoints.isNotEmpty()) {
            segments.add(ChartSegment(currentPoints.toList()))
        }

        val allPoints = segments.flatMap { it.points }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: 100f
        val minY = allPoints.minOfOrNull { it.y } ?: 0f
        val maxY = allPoints.maxOfOrNull { it.y } ?: 100f

        return ChartSeries(
            name = seriesName,
            yUnit = "°C",
            xUnit = "%",
            segments = segments,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
        )
    }
}
