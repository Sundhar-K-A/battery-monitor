package com.example.chargetrack.ui.degradation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TrendDataPoint(
    val timestamp: Instant,
    val value: Double,
    val isBaseline: Boolean = false,
)

private val CardBackground = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)
private val GridLineColor = Color(0xFF232A38)
private val TextSecondary = Color(0xFF8C9BAE)
private val BaselineGold = Color(0xFFFFD54F)

@Composable
fun LongitudinalTrendChart(
    title: String,
    unit: String,
    points: List<TrendDataPoint>,
    seriesColor: Color,
    referenceLineValue: Double? = null,
    referenceLineLabel: String? = null,
    firmwareTransitionTimestamps: List<Instant> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFE2E8F0),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Unit: $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No trend points recorded", color = TextSecondary, fontSize = 12.sp)
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val paddingLeft = 40.dp.toPx()
                    val paddingRight = 16.dp.toPx()
                    val paddingTop = 16.dp.toPx()
                    val paddingBottom = 24.dp.toPx()

                    val chartWidth = size.width - paddingLeft - paddingRight
                    val chartHeight = size.height - paddingTop - paddingBottom

                    if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

                    val allValues = points.map { it.value } + listOfNotNull(referenceLineValue)
                    val rawMin = allValues.minOrNull() ?: 0.0
                    val rawMax = allValues.maxOrNull() ?: 100.0
                    val rangeMargin = if (rawMax > rawMin) (rawMax - rawMin) * 0.15 else 10.0
                    val minY = (rawMin - rangeMargin).coerceAtLeast(0.0)
                    val maxY = rawMax + rangeMargin

                    val minX = points.first().timestamp.toEpochMilli().toDouble()
                    val maxX = points.last().timestamp.toEpochMilli().toDouble()
                    val spanX = if (maxX > minX) maxX - minX else 1.0

                    fun toX(t: Instant): Float {
                        if (points.size == 1) return paddingLeft + chartWidth / 2f
                        val fraction = (t.toEpochMilli() - minX) / spanX
                        return paddingLeft + (fraction * chartWidth).toFloat()
                    }

                    fun toY(v: Double): Float {
                        val fraction = (v - minY) / (maxY - minY)
                        return paddingTop + chartHeight - (fraction * chartHeight).toFloat()
                    }

                    // 1. Grid lines
                    val steps = 3
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#8C9BAE")
                        textSize = 22f
                        isAntiAlias = true
                    }

                    for (i in 0..steps) {
                        val yFraction = i.toFloat() / steps
                        val yVal = minY + (maxY - minY) * (1f - yFraction)
                        val cy = paddingTop + yFraction * chartHeight

                        drawLine(
                            color = GridLineColor,
                            start = Offset(paddingLeft, cy),
                            end = Offset(paddingLeft + chartWidth, cy),
                            strokeWidth = 1.dp.toPx(),
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            "%.1f".format(Locale.US, yVal),
                            4f,
                            cy + 8f,
                            textPaint,
                        )
                    }

                    // 2. Reference line if present
                    referenceLineValue?.let { ref ->
                        val refY = toY(ref)
                        drawLine(
                            color = Color(0xFF64748B),
                            start = Offset(paddingLeft, refY),
                            end = Offset(paddingLeft + chartWidth, refY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                        )
                        referenceLineLabel?.let { lbl ->
                            drawContext.canvas.nativeCanvas.drawText(
                                lbl,
                                paddingLeft + 8f,
                                refY - 6f,
                                textPaint,
                            )
                        }
                    }

                    // 2.5 Firmware update transition vertical markers
                    val fwMarkerPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#00E5FF")
                        textSize = 20f
                        isAntiAlias = true
                    }
                    firmwareTransitionTimestamps.forEach { t ->
                        val x = toX(t)
                        if (x in paddingLeft..(paddingLeft + chartWidth)) {
                            drawLine(
                                color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                                start = Offset(x, paddingTop),
                                end = Offset(x, paddingTop + chartHeight),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                "FW Update",
                                x + 4f,
                                paddingTop + 18f,
                                fwMarkerPaint,
                            )
                        }
                    }

                    // 3. Draw trend line
                    if (points.size > 1) {
                        val path = Path()
                        points.forEachIndexed { index, p ->
                            val x = toX(p.timestamp)
                            val y = toY(p.value)
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = seriesColor,
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                    }

                    // 4. Draw markers
                    val dateFormatter = DateTimeFormatter.ofPattern("MM/dd", Locale.US)
                        .withZone(ZoneId.systemDefault())

                    points.forEach { p ->
                        val x = toX(p.timestamp)
                        val y = toY(p.value)

                        if (p.isBaseline) {
                            drawCircle(color = BaselineGold, radius = 6.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = Offset(x, y))
                        } else {
                            drawCircle(color = seriesColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = Color(0xFF161B24), radius = 2.dp.toPx(), center = Offset(x, y))
                        }

                        // X-axis label
                        val dateText = dateFormatter.format(p.timestamp)
                        drawContext.canvas.nativeCanvas.drawText(
                            dateText,
                            x - 20f,
                            size.height - 4f,
                            textPaint,
                        )
                    }
                }
            }
        }
    }
}
