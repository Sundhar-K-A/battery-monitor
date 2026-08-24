package com.example.chargetrack.ui.comparison.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.ui.charts.model.ChartDataPoint
import com.example.chargetrack.ui.charts.model.ChartSeries
import java.util.Locale
import kotlin.math.abs

private val GridColor = Color(0xFF222B38)
private val AxisTextColor = Color(0xFF8C9BAE)
private val CrosshairColor = Color(0x80FFFFFF)
private val TooltipBg = Color(0xF0161B24)
private val TooltipBorder = Color(0xFF2A3241)

data class ScrubberValue(
    val seriesName: String,
    val color: Color,
    val point: ChartDataPoint,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlignedMultiLineChart(
    seriesList: List<ChartSeries>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (seriesList.isEmpty() || seriesList.all { it.isEmpty }) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("No comparison data available", color = AxisTextColor, fontSize = 13.sp)
        }
        return
    }

    var touchX by remember { mutableStateOf<Float?>(null) }
    val textMeasurer = rememberTextMeasurer()

    // Global X and Y bounds
    val allPoints = seriesList.flatMap { s -> s.segments.flatMap { it.points } }
    val globalMinX = allPoints.minOfOrNull { it.x } ?: 20f
    val globalMaxX = allPoints.maxOfOrNull { it.x } ?: 80f
    val globalMinY = allPoints.minOfOrNull { it.y } ?: 0f
    val globalMaxY = allPoints.maxOfOrNull { it.y } ?: 100f

    val yRange = (globalMaxY - globalMinY).coerceAtLeast(1f)
    val xRange = (globalMaxX - globalMinX).coerceAtLeast(1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            touchX = offset.x
                            tryAwaitRelease()
                            touchX = null
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> touchX = offset.x },
                        onDragEnd = { touchX = null },
                        onDragCancel = { touchX = null },
                        onDrag = { change, _ -> touchX = change.position.x }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padLeft = 40.dp.toPx()
                val padRight = 16.dp.toPx()
                val padTop = 16.dp.toPx()
                val padBottom = 28.dp.toPx()

                val chartW = size.width - padLeft - padRight
                val chartH = size.height - padTop - padBottom

                fun mapX(x: Float): Float = padLeft + ((x - globalMinX) / xRange) * chartW
                fun mapY(y: Float): Float = padTop + chartH - ((y - globalMinY) / yRange) * chartH
                fun unmapX(px: Float): Float = globalMinX + ((px - padLeft) / chartW) * xRange

                // 1. Grid & Y Axis
                val ySteps = 4
                for (i in 0..ySteps) {
                    val frac = i / ySteps.toFloat()
                    val yVal = globalMinY + frac * yRange
                    val py = padTop + chartH - frac * chartH

                    drawLine(
                        color = GridColor,
                        start = Offset(padLeft, py),
                        end = Offset(size.width - padRight, py),
                        strokeWidth = 1.dp.toPx(),
                    )

                    val yText = String.format(Locale.US, "%.0f", yVal)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = yText,
                        topLeft = Offset(4.dp.toPx(), py - 7.dp.toPx()),
                        style = TextStyle(color = AxisTextColor, fontSize = 10.sp),
                    )
                }

                // 2. X Axis Ticks
                val xSteps = 4
                for (i in 0..xSteps) {
                    val frac = i / xSteps.toFloat()
                    val xVal = globalMinX + frac * xRange
                    val px = padLeft + frac * chartW

                    drawLine(
                        color = GridColor,
                        start = Offset(px, padTop),
                        end = Offset(px, padTop + chartH),
                        strokeWidth = 1.dp.toPx(),
                    )

                    val xText = String.format(Locale.US, "%.0f%%", xVal)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = xText,
                        topLeft = Offset(px - 10.dp.toPx(), size.height - padBottom + 6.dp.toPx()),
                        style = TextStyle(color = AxisTextColor, fontSize = 10.sp),
                    )
                }

                // 3. Draw each series strictly preserving chronological path
                seriesList.forEachIndexed { idx, series ->
                    val strokeColor = colors.getOrElse(idx) { CurveColorPalette[0] }

                    for (segment in series.segments) {
                        if (segment.points.size < 2) continue

                        val path = Path()
                        val first = segment.points.first()
                        path.moveTo(mapX(first.x), mapY(first.y))

                        for (k in 1 until segment.points.size) {
                            val pt = segment.points[k]
                            path.lineTo(mapX(pt.x), mapY(pt.y))
                        }

                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                    }
                }

                // 4. Touch Scrubbing Crosshair
                val currentTouch = touchX
                if (currentTouch != null && currentTouch in padLeft..(size.width - padRight)) {
                    drawLine(
                        color = CrosshairColor,
                        start = Offset(currentTouch, padTop),
                        end = Offset(currentTouch, padTop + chartH),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    )

                    val touchedXVal = unmapX(currentTouch)

                    seriesList.forEachIndexed { idx, series ->
                        val strokeColor = colors.getOrElse(idx) { CurveColorPalette[0] }
                        val nearest = series.segments.flatMap { it.points }
                            .minByOrNull { abs(it.x - touchedXVal) }

                        if (nearest != null) {
                            val nx = mapX(nearest.x)
                            val ny = mapY(nearest.y)
                            drawCircle(
                                color = strokeColor,
                                radius = 4.dp.toPx(),
                                center = Offset(nx, ny),
                            )
                        }
                    }
                }
            }

            // Floating Multi-Series Tooltip
            val currentTouch = touchX
            if (currentTouch != null) {
                val padLeft = 40.dp.value
                val padRight = 16.dp.value
                val chartW = 320f // Approximate fallback

                val touchedXVal = globalMinX + ((currentTouch / 360f) * xRange)
                val scrubberValues = seriesList.mapIndexedNotNull { idx, series ->
                    val nearest = series.segments.flatMap { it.points }
                        .minByOrNull { abs(it.x - touchedXVal) }
                    nearest?.let {
                        ScrubberValue(
                            seriesName = series.name,
                            color = colors.getOrElse(idx) { CurveColorPalette[0] },
                            point = it,
                        )
                    }
                }

                if (scrubberValues.isNotEmpty()) {
                    val commonPercent = scrubberValues.first().point.tooltip.percent ?: 50
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .border(1.dp, TooltipBorder, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = TooltipBg),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Battery: $commonPercent%",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            scrubberValues.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 1.dp),
                                ) {
                                    Surface(
                                        color = item.color,
                                        shape = CircleShape,
                                        modifier = Modifier.size(6.dp),
                                    ) {}
                                    Spacer(Modifier.width(4.dp))
                                    val unit = seriesList.firstOrNull()?.yUnit ?: ""
                                    Text(
                                        text = String.format(Locale.US, "%.1f %s", item.point.y, unit),
                                        color = item.color,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Textual Legends for Accessibility & Clarity
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            seriesList.forEachIndexed { idx, series ->
                val strokeColor = colors.getOrElse(idx) { CurveColorPalette[0] }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = strokeColor,
                        shape = CircleShape,
                        modifier = Modifier.size(8.dp),
                    ) {}
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = series.name,
                        color = Color(0xFFE0E6ED),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
