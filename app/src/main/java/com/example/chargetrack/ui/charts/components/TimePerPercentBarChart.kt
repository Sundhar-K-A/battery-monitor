package com.example.chargetrack.ui.charts.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.ui.charts.model.TimePerPercentBar
import java.util.Locale

private val GridColor = Color(0xFF222834)
private val AxisTextColor = Color(0xFF8C9BAE)
private val GoodBarColor = Color(0xFF4CAF50)
private val DegradedBarColor = Color(0xFFFFC107)
private val GapBarColor = Color(0xFFEF5350)
private val AvgLineColor = Color(0xFF29B6F6)
private val TooltipBg = Color(0xFF1E2430)
private val TooltipBorder = Color(0xFF374357)

@Composable
fun TimePerPercentBarChart(
    bars: List<TimePerPercentBar>,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No transition data available for this session",
                color = AxisTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    var selectedBar by remember { mutableStateOf<TimePerPercentBar?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val maxDuration = (bars.maxOfOrNull { it.durationSeconds } ?: 60.0).coerceAtLeast(10.0)
    val validContiguous = bars.filter { !it.isGap }
    val avgDuration = if (validContiguous.isNotEmpty()) {
        validContiguous.map { it.durationSeconds }.average()
    } else null

    val barWidthDp = 24.dp
    val barSpacingDp = 8.dp
    val totalChartWidthDp = (bars.size * (24 + 8) + 80).dp

    Column(modifier = modifier.fillMaxWidth()) {
        // Scrollable Bar Canvas
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .width(totalChartWidthDp)
                    .pointerInput(bars) {
                        detectTapGestures { offset ->
                            val paddingLeft = 48.dp.toPx()
                            val barSlotWidth = (barWidthDp + barSpacingDp).toPx()
                            val clickedIndex = ((offset.x - paddingLeft) / barSlotWidth).toInt()
                            if (clickedIndex in bars.indices) {
                                selectedBar = bars[clickedIndex]
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val paddingLeft = 48.dp.toPx()
                    val paddingRight = 24.dp.toPx()
                    val paddingTop = 20.dp.toPx()
                    val paddingBottom = 32.dp.toPx()

                    val chartHeight = size.height - paddingTop - paddingBottom
                    val barWidthPx = barWidthDp.toPx()
                    val barSpacingPx = barSpacingDp.toPx()

                    fun toCanvasY(dur: Double): Float {
                        return paddingTop + chartHeight - ((dur / maxDuration) * chartHeight).toFloat()
                    }

                    // 1. Horizontal grid lines
                    val ySteps = 4
                    for (i in 0..ySteps) {
                        val durVal = maxDuration * i / ySteps
                        val y = toCanvasY(durVal)

                        drawLine(
                            color = GridColor,
                            start = Offset(paddingLeft, y),
                            end = Offset(size.width - paddingRight, y),
                            strokeWidth = 1.dp.toPx(),
                        )

                        val label = String.format(Locale.US, "%.0fs", durVal)
                        val textLayout = textMeasurer.measure(
                            text = label,
                            style = TextStyle(color = AxisTextColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(paddingLeft - textLayout.size.width - 6.dp.toPx(), y - textLayout.size.height / 2f)
                        )
                    }

                    // 2. Average Duration reference line
                    avgDuration?.let { avg ->
                        val avgY = toCanvasY(avg)
                        drawLine(
                            color = AvgLineColor,
                            start = Offset(paddingLeft, avgY),
                            end = Offset(size.width - paddingRight, avgY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )

                        val avgText = String.format(Locale.US, "Avg: %.1fs", avg)
                        val textLayout = textMeasurer.measure(
                            text = avgText,
                            style = TextStyle(color = AvgLineColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(size.width - paddingRight - textLayout.size.width, avgY - textLayout.size.height - 2.dp.toPx())
                        )
                    }

                    // 3. Draw Bars
                    for (i in bars.indices) {
                        val bar = bars[i]
                        val barLeft = paddingLeft + i * (barWidthPx + barSpacingPx)
                        val barTop = toCanvasY(bar.durationSeconds)
                        val barHeight = (size.height - paddingBottom - barTop).coerceAtLeast(2.dp.toPx())

                        val color = when {
                            bar.isGap -> GapBarColor
                            bar.quality == DataQuality.DEGRADED -> DegradedBarColor
                            else -> GoodBarColor
                        }

                        val isSelected = selectedBar == bar
                        val barColor = if (isSelected) Color.White else color

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidthPx, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )

                        // X-axis label
                        val label = "${bar.toPercent}%"
                        val textLayout = textMeasurer.measure(
                            text = label,
                            style = TextStyle(color = AxisTextColor, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(barLeft + (barWidthPx - textLayout.size.width) / 2f, size.height - paddingBottom + 6.dp.toPx())
                        )
                    }
                }
            }

            // Floating Bar Tooltip
            selectedBar?.let { bar ->
                BarTooltipCard(
                    bar = bar,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BarTooltipCard(
    bar: TimePerPercentBar,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.border(1.dp, TooltipBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = TooltipBg),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bar.label,
                    color = if (bar.isGap) GapBarColor else Color(0xFF29B6F6),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "%.1f s", bar.durationSeconds),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = bar.quality.name,
                    color = when (bar.quality) {
                        DataQuality.GOOD -> GoodBarColor
                        DataQuality.DEGRADED -> DegradedBarColor
                        DataQuality.INSUFFICIENT -> GapBarColor
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Samples: ${bar.sampleCount}",
                    color = AxisTextColor,
                    fontSize = 10.sp,
                )
                bar.averagePowerW?.let { p ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.US, "Avg Power: %.1f W", p),
                        color = Color(0xFFFFB300),
                        fontSize = 10.sp,
                    )
                }
                bar.maxTempC?.let { t ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.US, "Max Temp: %.1f °C", t),
                        color = Color(0xFFFF7043),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
