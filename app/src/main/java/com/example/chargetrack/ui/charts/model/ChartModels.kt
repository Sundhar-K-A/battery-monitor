package com.example.chargetrack.ui.charts.model

import androidx.compose.ui.graphics.Color
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag

/**
 * Inspection metadata displayed when a user scrubs or taps a data point on a chart.
 */
data class TooltipData(
    val elapsedMs: Long,
    val percent: Int? = null,
    val powerW: Double? = null,
    val voltageV: Double? = null,
    val currentA: Double? = null,
    val temperatureC: Double? = null,
    val qualityFlags: Set<QualityFlag> = emptySet(),
    val isOutlier: Boolean = false,
    val isPeak: Boolean = false,
    val isTaperStart: Boolean = false,
    val isBenchmarkStart: Boolean = false,
    val isBenchmarkEnd: Boolean = false,
)

/**
 * A single coordinate point on a line or scatter chart.
 */
data class ChartDataPoint(
    val x: Float,
    val y: Float,
    val tooltip: TooltipData,
    val isAnchor: Boolean = false,
    val isOutlier: Boolean = false,
)

/**
 * A contiguous sequence of valid data points forming a continuous line without gaps.
 * Missing/null samples in telemetry split data into multiple disjoint segments.
 */
data class ChartSegment(
    val points: List<ChartDataPoint>,
)

/**
 * Container representing an entire plotted series on a chart.
 */
data class ChartSeries(
    val name: String,
    val yUnit: String,
    val xUnit: String,
    val segments: List<ChartSegment>,
    val strokeColor: Color = Color(0xFFFFB300),
    val fillColor: Color = Color(0x33FFB300),
    val hasZeroLine: Boolean = false,
    val minX: Float = 0f,
    val maxX: Float = 0f,
    val minY: Float = 0f,
    val maxY: Float = 0f,
    val peakPoint: ChartDataPoint? = null,
    val taperPoint: ChartDataPoint? = null,
    val benchmarkStartPoint: ChartDataPoint? = null,
    val benchmarkEndPoint: ChartDataPoint? = null,
) {
    val isEmpty: Boolean
        get() = segments.isEmpty() || segments.all { it.points.isEmpty() }

    val totalPoints: Int
        get() = segments.sumOf { it.points.size }
}

/**
 * Data item representing a 1% transition bar or multi-% gap transition.
 */
data class TimePerPercentBar(
    val fromPercent: Int,
    val toPercent: Int,
    val durationSeconds: Double,
    val quality: DataQuality,
    val isGap: Boolean = false,
    val sampleCount: Int = 0,
    val averagePowerW: Double? = null,
    val maxTempC: Double? = null,
) {
    val label: String
        get() = if (isGap) "$fromPercent→$toPercent% (GAP)" else "$fromPercent→$toPercent%"
}
