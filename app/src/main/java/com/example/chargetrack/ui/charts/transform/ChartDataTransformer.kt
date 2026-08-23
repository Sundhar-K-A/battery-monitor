package com.example.chargetrack.ui.charts.transform

import androidx.compose.ui.graphics.Color
import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.StandardTest
import com.example.chargetrack.ui.charts.model.ChartDataPoint
import com.example.chargetrack.ui.charts.model.ChartSegment
import com.example.chargetrack.ui.charts.model.ChartSeries
import com.example.chargetrack.ui.charts.model.TimePerPercentBar
import com.example.chargetrack.ui.charts.model.TooltipData

/**
 * Transforms raw [BatterySample]s and [ChargeTransition]s into renderable [ChartSeries] and [TimePerPercentBar] models.
 *
 * ## Principles & Architectural Guarantees:
 * - **Prompt 13 is Authoritative**: Uses [SessionSummary] as the single source of truth for peak power and taper start.
 * - **Prompt 14 Benchmark Anchors**: Anchors benchmark start/end to persisted boundaries.
 * - **Null Safety / No Zero Fakes**: Null measurements split lines into disjoint [ChartSegment]s.
 * - **Repeated % Preservation**: Observations at the same battery percentage are NOT averaged or collapsed.
 * - **Negative Values & Zero Lines**: Signed power and current are preserved without clamping.
 * - **Transient Downsampling**: Uses [AnchorPreservingLttb] strictly for rendering when sample count > 500.
 */
object ChartDataTransformer {

    // Color tokens
    val AmberPower = Color(0xFFFFB300)
    val BluePercent = Color(0xFF29B6F6)
    val CoralTemp = Color(0xFFFF7043)
    val PurpleCurrent = Color(0xFFAB47BC)
    val GreenQuality = Color(0xFF4CAF50)
    val AmberDegraded = Color(0xFFFFC107)
    val RedInsufficient = Color(0xFFEF5350)

    /**
     * 1. Battery % vs Time ($t \rightarrow \%$)
     */
    fun buildBatteryPercentVsTime(
        samples: List<BatterySample>,
        summary: SessionSummary? = null,
        standardTest: StandardTest? = null,
    ): ChartSeries {
        if (samples.isEmpty()) {
            return emptySeries("Battery Level vs Time", "%", "Time (min)", BluePercent)
        }

        val anchorMap = computeAnchorMap(samples, summary, standardTest)
        val rawSegments = mutableListOf<MutableList<ChartDataPoint>>()
        var currentSegment = mutableListOf<ChartDataPoint>()

        for (i in samples.indices) {
            val sample = samples[i]
            val pct = sample.percent
            if (pct == null) {
                if (currentSegment.isNotEmpty()) {
                    rawSegments.add(currentSegment)
                    currentSegment = mutableListOf()
                }
            } else {
                val flags = anchorMap[i] ?: AnchorFlags()
                val point = ChartDataPoint(
                    x = (sample.elapsedMs / 60_000f), // in minutes
                    y = pct.toFloat(),
                    tooltip = buildTooltipData(sample, flags),
                    isAnchor = flags.isAnyAnchor,
                    isOutlier = QualityFlag.OUTLIER in sample.qualityFlags,
                )
                currentSegment.add(point)
            }
        }
        if (currentSegment.isNotEmpty()) {
            rawSegments.add(currentSegment)
        }

        val downsampledSegments = rawSegments.map { segment ->
            ChartSegment(AnchorPreservingLttb.downsample(segment))
        }

        val allPoints = downsampledSegments.flatMap { it.points }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: 0f
        val minY = (allPoints.minOfOrNull { it.y } ?: 0f).coerceAtLeast(0f)
        val maxY = (allPoints.maxOfOrNull { it.y } ?: 100f).coerceAtMost(100f)

        return ChartSeries(
            name = "Battery % vs Time",
            yUnit = "%",
            xUnit = "min",
            segments = downsampledSegments,
            strokeColor = BluePercent,
            fillColor = BluePercent.copy(alpha = 0.15f),
            hasZeroLine = false,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            benchmarkStartPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkStart },
            benchmarkEndPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkEnd },
        )
    }

    /**
     * 2. Estimated battery-side power vs Battery % ($\% \rightarrow \text{Power (W)}$)
     * Multiple 5-second observations at the same percentage are NOT averaged or collapsed.
     */
    fun buildPowerVsBatteryPercent(
        samples: List<BatterySample>,
        summary: SessionSummary? = null,
        standardTest: StandardTest? = null,
    ): ChartSeries {
        if (samples.isEmpty()) {
            return emptySeries("Estimated battery-side power vs Battery %", "W", "%", AmberPower)
        }

        val anchorMap = computeAnchorMap(samples, summary, standardTest)
        val rawSegments = mutableListOf<MutableList<ChartDataPoint>>()
        var currentSegment = mutableListOf<ChartDataPoint>()

        for (i in samples.indices) {
            val sample = samples[i]
            val pct = sample.percent
            val powerUw = sample.derivedPowerUw
            if (pct == null || powerUw == null) {
                if (currentSegment.isNotEmpty()) {
                    rawSegments.add(currentSegment)
                    currentSegment = mutableListOf()
                }
            } else {
                val flags = anchorMap[i] ?: AnchorFlags()
                val powerW = (powerUw.toDouble() / 1_000_000.0).toFloat()
                val point = ChartDataPoint(
                    x = pct.toFloat(),
                    y = powerW,
                    tooltip = buildTooltipData(sample, flags),
                    isAnchor = flags.isAnyAnchor,
                    isOutlier = QualityFlag.OUTLIER in sample.qualityFlags,
                )
                currentSegment.add(point)
            }
        }
        if (currentSegment.isNotEmpty()) {
            rawSegments.add(currentSegment)
        }

        val downsampledSegments = rawSegments.map { segment ->
            ChartSegment(AnchorPreservingLttb.downsample(segment))
        }

        val allPoints = downsampledSegments.flatMap { it.points }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: 100f
        val rawMinY = allPoints.minOfOrNull { it.y } ?: 0f
        val rawMaxY = allPoints.maxOfOrNull { it.y } ?: 0f

        return ChartSeries(
            name = "Estimated battery-side power vs Battery %",
            yUnit = "W",
            xUnit = "%",
            segments = downsampledSegments,
            strokeColor = AmberPower,
            fillColor = AmberPower.copy(alpha = 0.15f),
            hasZeroLine = rawMinY < 0f && rawMaxY > 0f,
            minX = minX,
            maxX = maxX,
            minY = rawMinY,
            maxY = rawMaxY,
            peakPoint = allPoints.firstOrNull { it.tooltip.isPeak },
            taperPoint = allPoints.firstOrNull { it.tooltip.isTaperStart },
            benchmarkStartPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkStart },
            benchmarkEndPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkEnd },
        )
    }

    /**
     * 3. Power vs Time ($t \rightarrow \text{Power (W)}$)
     */
    fun buildPowerVsTime(
        samples: List<BatterySample>,
        summary: SessionSummary? = null,
        standardTest: StandardTest? = null,
    ): ChartSeries {
        if (samples.isEmpty()) {
            return emptySeries("Power vs Time", "W", "Time (min)", AmberPower)
        }

        val anchorMap = computeAnchorMap(samples, summary, standardTest)
        val rawSegments = mutableListOf<MutableList<ChartDataPoint>>()
        var currentSegment = mutableListOf<ChartDataPoint>()

        for (i in samples.indices) {
            val sample = samples[i]
            val powerUw = sample.derivedPowerUw
            if (powerUw == null) {
                if (currentSegment.isNotEmpty()) {
                    rawSegments.add(currentSegment)
                    currentSegment = mutableListOf()
                }
            } else {
                val flags = anchorMap[i] ?: AnchorFlags()
                val powerW = (powerUw.toDouble() / 1_000_000.0).toFloat()
                val point = ChartDataPoint(
                    x = (sample.elapsedMs / 60_000f),
                    y = powerW,
                    tooltip = buildTooltipData(sample, flags),
                    isAnchor = flags.isAnyAnchor,
                    isOutlier = QualityFlag.OUTLIER in sample.qualityFlags,
                )
                currentSegment.add(point)
            }
        }
        if (currentSegment.isNotEmpty()) {
            rawSegments.add(currentSegment)
        }

        val downsampledSegments = rawSegments.map { segment ->
            ChartSegment(AnchorPreservingLttb.downsample(segment))
        }

        val allPoints = downsampledSegments.flatMap { it.points }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: 0f
        val rawMinY = allPoints.minOfOrNull { it.y } ?: 0f
        val rawMaxY = allPoints.maxOfOrNull { it.y } ?: 0f

        return ChartSeries(
            name = "Power vs Time",
            yUnit = "W",
            xUnit = "min",
            segments = downsampledSegments,
            strokeColor = AmberPower,
            fillColor = AmberPower.copy(alpha = 0.15f),
            hasZeroLine = rawMinY < 0f && rawMaxY > 0f,
            minX = minX,
            maxX = maxX,
            minY = rawMinY,
            maxY = rawMaxY,
            peakPoint = allPoints.firstOrNull { it.tooltip.isPeak },
            taperPoint = allPoints.firstOrNull { it.tooltip.isTaperStart },
            benchmarkStartPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkStart },
            benchmarkEndPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkEnd },
        )
    }

    /**
     * 4. Temperature vs Battery % ($\% \rightarrow \text{Temp (°C)}$)
     */
    fun buildTemperatureVsBatteryPercent(
        samples: List<BatterySample>,
        summary: SessionSummary? = null,
        standardTest: StandardTest? = null,
    ): ChartSeries {
        if (samples.isEmpty()) {
            return emptySeries("Temperature vs Battery %", "°C", "%", CoralTemp)
        }

        val anchorMap = computeAnchorMap(samples, summary, standardTest)
        val rawSegments = mutableListOf<MutableList<ChartDataPoint>>()
        var currentSegment = mutableListOf<ChartDataPoint>()

        for (i in samples.indices) {
            val sample = samples[i]
            val pct = sample.percent
            val tempDeciC = sample.temperatureDeciC
            if (pct == null || tempDeciC == null) {
                if (currentSegment.isNotEmpty()) {
                    rawSegments.add(currentSegment)
                    currentSegment = mutableListOf()
                }
            } else {
                val flags = anchorMap[i] ?: AnchorFlags()
                val tempC = (tempDeciC.toDouble() / 10.0).toFloat()
                val point = ChartDataPoint(
                    x = pct.toFloat(),
                    y = tempC,
                    tooltip = buildTooltipData(sample, flags),
                    isAnchor = flags.isAnyAnchor,
                    isOutlier = QualityFlag.OUTLIER in sample.qualityFlags,
                )
                currentSegment.add(point)
            }
        }
        if (currentSegment.isNotEmpty()) {
            rawSegments.add(currentSegment)
        }

        val downsampledSegments = rawSegments.map { segment ->
            ChartSegment(AnchorPreservingLttb.downsample(segment))
        }

        val allPoints = downsampledSegments.flatMap { it.points }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: 100f
        val rawMinY = allPoints.minOfOrNull { it.y } ?: 20f
        val rawMaxY = allPoints.maxOfOrNull { it.y } ?: 45f

        return ChartSeries(
            name = "Temperature vs Battery %",
            yUnit = "°C",
            xUnit = "%",
            segments = downsampledSegments,
            strokeColor = CoralTemp,
            fillColor = CoralTemp.copy(alpha = 0.15f),
            hasZeroLine = false,
            minX = minX,
            maxX = maxX,
            minY = rawMinY,
            maxY = rawMaxY,
            benchmarkStartPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkStart },
            benchmarkEndPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkEnd },
        )
    }

    /**
     * 5. Current vs Battery % ($\% \rightarrow \text{Current (A)}$)
     */
    fun buildCurrentVsBatteryPercent(
        samples: List<BatterySample>,
        summary: SessionSummary? = null,
        standardTest: StandardTest? = null,
    ): ChartSeries {
        if (samples.isEmpty()) {
            return emptySeries("Current vs Battery %", "A", "%", PurpleCurrent)
        }

        val anchorMap = computeAnchorMap(samples, summary, standardTest)
        val rawSegments = mutableListOf<MutableList<ChartDataPoint>>()
        var currentSegment = mutableListOf<ChartDataPoint>()

        for (i in samples.indices) {
            val sample = samples[i]
            val pct = sample.percent
            val currentUa = sample.currentNowUa
            if (pct == null || currentUa == null) {
                if (currentSegment.isNotEmpty()) {
                    rawSegments.add(currentSegment)
                    currentSegment = mutableListOf()
                }
            } else {
                val flags = anchorMap[i] ?: AnchorFlags()
                val currentA = (currentUa.toDouble() / 1_000_000.0).toFloat()
                val point = ChartDataPoint(
                    x = pct.toFloat(),
                    y = currentA,
                    tooltip = buildTooltipData(sample, flags),
                    isAnchor = flags.isAnyAnchor,
                    isOutlier = QualityFlag.OUTLIER in sample.qualityFlags,
                )
                currentSegment.add(point)
            }
        }
        if (currentSegment.isNotEmpty()) {
            rawSegments.add(currentSegment)
        }

        val downsampledSegments = rawSegments.map { segment ->
            ChartSegment(AnchorPreservingLttb.downsample(segment))
        }

        val allPoints = downsampledSegments.flatMap { it.points }
        val minX = allPoints.minOfOrNull { it.x } ?: 0f
        val maxX = allPoints.maxOfOrNull { it.x } ?: 100f
        val rawMinY = allPoints.minOfOrNull { it.y } ?: 0f
        val rawMaxY = allPoints.maxOfOrNull { it.y } ?: 0f

        return ChartSeries(
            name = "Current vs Battery %",
            yUnit = "A",
            xUnit = "%",
            segments = downsampledSegments,
            strokeColor = PurpleCurrent,
            fillColor = PurpleCurrent.copy(alpha = 0.15f),
            hasZeroLine = rawMinY < 0f && rawMaxY > 0f,
            minX = minX,
            maxX = maxX,
            minY = rawMinY,
            maxY = rawMaxY,
            benchmarkStartPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkStart },
            benchmarkEndPoint = allPoints.firstOrNull { it.tooltip.isBenchmarkEnd },
        )
    }

    /**
     * 6. Time per 1% ($1\%\text{ step} \rightarrow \text{Duration (s)}$)
     */
    fun buildTimePerPercentBars(transitions: List<ChargeTransition>): List<TimePerPercentBar> {
        return transitions.map { transition ->
            val span = transition.toPercent - transition.fromPercent
            val isGap = span > 1 || transition.quality == DataQuality.INSUFFICIENT
            TimePerPercentBar(
                fromPercent = transition.fromPercent,
                toPercent = transition.toPercent,
                durationSeconds = transition.durationMs / 1000.0,
                quality = transition.quality,
                isGap = isGap,
                sampleCount = transition.sampleCount,
                averagePowerW = transition.averagePowerUw?.let { it.toDouble() / 1_000_000.0 },
                maxTempC = transition.maxTemperatureDeciC?.let { it.toDouble() / 10.0 },
            )
        }
    }

    private data class AnchorFlags(
        val isFirst: Boolean = false,
        val isLast: Boolean = false,
        val isPeak: Boolean = false,
        val isTaperStart: Boolean = false,
        val isBenchmarkStart: Boolean = false,
        val isBenchmarkEnd: Boolean = false,
        val isPercentStepArrival: Boolean = false,
    ) {
        val isAnyAnchor: Boolean
            get() = isFirst || isLast || isPeak || isTaperStart || isBenchmarkStart || isBenchmarkEnd || isPercentStepArrival
    }

    /**
     * Maps authoritative [SessionSummary] peak/taper and [StandardTest] boundary values to exact sample indices.
     */
    private fun computeAnchorMap(
        samples: List<BatterySample>,
        summary: SessionSummary?,
        standardTest: StandardTest?,
    ): Map<Int, AnchorFlags> {
        if (samples.isEmpty()) return emptyMap()

        val map = mutableMapOf<Int, AnchorFlags>()

        fun update(idx: Int, transform: (AnchorFlags) -> AnchorFlags) {
            if (idx in samples.indices) {
                val current = map[idx] ?: AnchorFlags()
                map[idx] = transform(current)
            }
        }

        // 1. First & Last samples
        update(0) { it.copy(isFirst = true) }
        update(samples.size - 1) { it.copy(isLast = true) }

        // 2. Authoritative peak power sample (from Prompt 13 SessionSummary)
        summary?.peakPowerUw?.let { peakUw ->
            val peakIdx = samples.indexOfFirst {
                it.derivedPowerUw == peakUw && QualityFlag.OUTLIER !in it.qualityFlags
            }
            if (peakIdx >= 0) {
                update(peakIdx) { it.copy(isPeak = true) }
            }
        }

        // 3. Authoritative taper start sample (from Prompt 13 SessionSummary)
        summary?.chargingTaperStartPercent?.let { taperPct ->
            val taperIdx = samples.indexOfFirst {
                it.percent != null && it.percent >= taperPct && QualityFlag.OUTLIER !in it.qualityFlags
            }
            if (taperIdx >= 0) {
                update(taperIdx) { it.copy(isTaperStart = true) }
            }
        }

        // 4. Benchmark Start Boundary (from Prompt 14 StandardTest)
        standardTest?.benchmarkStartedElapsedMs?.let { startMs ->
            val startIdx = samples.indexOfFirst { it.elapsedMs >= startMs }
            if (startIdx >= 0) {
                update(startIdx) { it.copy(isBenchmarkStart = true) }
            }
        }

        // 5. Benchmark End Boundary (from Prompt 14 StandardTest)
        standardTest?.benchmarkEndedElapsedMs?.let { endMs ->
            val endIdx = samples.indexOfFirst { it.elapsedMs >= endMs }
            if (endIdx >= 0) {
                update(endIdx) { it.copy(isBenchmarkEnd = true) }
            }
        }

        // 6. Integer % arrival boundaries
        var lastPct: Int? = null
        for (i in samples.indices) {
            val p = samples[i].percent
            if (p != null && p != lastPct) {
                update(i) { it.copy(isPercentStepArrival = true) }
                lastPct = p
            }
        }

        return map
    }

    private fun buildTooltipData(sample: BatterySample, flags: AnchorFlags): TooltipData {
        return TooltipData(
            elapsedMs = sample.elapsedMs,
            percent = sample.percent,
            powerW = sample.derivedPowerUw?.let { it.toDouble() / 1_000_000.0 },
            voltageV = sample.voltageMv?.let { it.toDouble() / 1_000.0 },
            currentA = sample.currentNowUa?.let { it.toDouble() / 1_000_000.0 },
            temperatureC = sample.temperatureDeciC?.let { it.toDouble() / 10.0 },
            qualityFlags = sample.qualityFlags,
            isOutlier = QualityFlag.OUTLIER in sample.qualityFlags,
            isPeak = flags.isPeak,
            isTaperStart = flags.isTaperStart,
            isBenchmarkStart = flags.isBenchmarkStart,
            isBenchmarkEnd = flags.isBenchmarkEnd,
        )
    }

    private fun emptySeries(name: String, yUnit: String, xUnit: String, color: Color): ChartSeries {
        return ChartSeries(
            name = name,
            yUnit = yUnit,
            xUnit = xUnit,
            segments = emptyList(),
            strokeColor = color,
            fillColor = color.copy(alpha = 0.15f),
        )
    }
}
