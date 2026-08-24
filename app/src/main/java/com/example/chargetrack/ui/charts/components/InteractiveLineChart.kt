package com.example.chargetrack.ui.charts.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.ui.charts.model.ChartDataPoint
import com.example.chargetrack.ui.charts.model.ChartSeries
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val GridColor = Color(0xFF222834)
private val AxisTextColor = Color(0xFF8C9BAE)
private val ZeroLineColor = Color(0xFF4E5D78)
private val CrosshairColor = Color(0xFFB0BEC5)
private val TooltipBg = Color(0xFF1E2430)
private val TooltipBorder = Color(0xFF374357)

@Composable
fun InteractiveLineChart(
    series: ChartSeries,
    modifier: Modifier = Modifier,
) {
    if (series.isEmpty) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No chart data available for this session",
                color = AxisTextColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    var selectedPoint by remember { mutableStateOf<ChartDataPoint?>(null) }
    var touchCanvasX by remember { mutableStateOf<Float?>(null) }
    var touchCanvasY by remember { mutableStateOf<Float?>(null) }

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(series) {
                detectTapGestures(
                    onPress = { offset ->
                        // find closest point
                        val pt = findClosestPoint(offset.x, size.width.toFloat(), series)
                        selectedPoint = pt
                    },
                    onTap = { offset ->
                        val pt = findClosestPoint(offset.x, size.width.toFloat(), series)
                        selectedPoint = pt
                    }
                )
            }
            .pointerInput(series) {
                detectDragGestures(
                    onDragStart = { offset ->
                        selectedPoint = findClosestPoint(offset.x, size.width.toFloat(), series)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        selectedPoint = findClosestPoint(change.position.x, size.width.toFloat(), series)
                    },
                    onDragEnd = {
                        // keep selection for inspection
                    },
                    onDragCancel = {}
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingLeft = 48.dp.toPx()
            val paddingRight = 16.dp.toPx()
            val paddingTop = 20.dp.toPx()
            val paddingBottom = 28.dp.toPx()

            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom

            val minX = series.minX
            val maxX = if (series.maxX > series.minX) series.maxX else series.minX + 1f
            val minY = series.minY
            val maxY = if (series.maxY > series.minY) series.maxY else series.minY + 1f

            val spanX = maxX - minX
            val spanY = maxY - minY

            fun toCanvasX(x: Float): Float = paddingLeft + ((x - minX) / spanX) * chartWidth
            fun toCanvasY(y: Float): Float = paddingTop + chartHeight - ((y - minY) / spanY) * chartHeight

            // 1. Draw horizontal grid lines and Y-axis labels
            val yStepCount = 4
            for (i in 0..yStepCount) {
                val yVal = minY + (spanY * i / yStepCount)
                val canvasY = toCanvasY(yVal)

                drawLine(
                    color = GridColor,
                    start = Offset(paddingLeft, canvasY),
                    end = Offset(size.width - paddingRight, canvasY),
                    strokeWidth = 1.dp.toPx(),
                )

                val labelText = String.format(Locale.US, "%.1f", yVal)
                val textLayout = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(color = AxisTextColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(paddingLeft - textLayout.size.width - 6.dp.toPx(), canvasY - textLayout.size.height / 2f)
                )
            }

            // 2. Draw Zero reference dashed line if data crosses zero
            if (series.hasZeroLine && minY < 0f && maxY > 0f) {
                val zeroY = toCanvasY(0f)
                drawLine(
                    color = ZeroLineColor,
                    start = Offset(paddingLeft, zeroY),
                    end = Offset(size.width - paddingRight, zeroY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // 3. Draw X-axis ticks & labels
            val xStepCount = 4
            for (i in 0..xStepCount) {
                val xVal = minX + (spanX * i / xStepCount)
                val canvasX = toCanvasX(xVal)

                val labelText = String.format(Locale.US, "%.0f", xVal)
                val textLayout = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(color = AxisTextColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(canvasX - textLayout.size.width / 2f, size.height - paddingBottom + 6.dp.toPx())
                )
            }

            // 4. Draw Segments & Bounded Gradient Fills
            val baselineY = toCanvasY(0f.coerceIn(minY, maxY))

            for (segment in series.segments) {
                if (segment.points.isEmpty()) continue

                val linePath = Path()
                val fillPath = Path()

                val first = segment.points.first()
                val firstX = toCanvasX(first.x)
                val firstY = toCanvasY(first.y)

                linePath.moveTo(firstX, firstY)
                fillPath.moveTo(firstX, baselineY)
                fillPath.lineTo(firstX, firstY)

                for (idx in 1 until segment.points.size) {
                    val pt = segment.points[idx]
                    val px = toCanvasX(pt.x)
                    val py = toCanvasY(pt.y)
                    linePath.lineTo(px, py)
                    fillPath.lineTo(px, py)
                }

                val last = segment.points.last()
                val lastX = toCanvasX(last.x)
                fillPath.lineTo(lastX, baselineY)
                fillPath.close()

                // Draw gradient area under curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(series.fillColor, Color.Transparent),
                        startY = paddingTop,
                        endY = baselineY,
                    )
                )

                // Draw line stroke
                drawPath(
                    path = linePath,
                    color = series.strokeColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                )
            }

            // 5. Draw highlighted Peak and Taper markers
            series.peakPoint?.let { peak ->
                val px = toCanvasX(peak.x)
                val py = toCanvasY(peak.y)
                drawCircle(color = Color(0xFFFFD54F), radius = 5.dp.toPx(), center = Offset(px, py))
                drawCircle(color = Color(0xFF000000), radius = 2.5.dp.toPx(), center = Offset(px, py))
            }

            series.taperPoint?.let { taper ->
                val tx = toCanvasX(taper.x)
                val ty = toCanvasY(taper.y)
                drawCircle(color = Color(0xFFFF9800), radius = 4.5.dp.toPx(), center = Offset(tx, ty), style = Stroke(width = 2.dp.toPx()))
            }

            // 6. Draw Selected Crosshair
            selectedPoint?.let { pt ->
                val selX = toCanvasX(pt.x)
                val selY = toCanvasY(pt.y)
                touchCanvasX = selX
                touchCanvasY = selY

                // Vertical guideline
                drawLine(
                    color = CrosshairColor.copy(alpha = 0.6f),
                    start = Offset(selX, paddingTop),
                    end = Offset(selX, size.height - paddingBottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )

                // Highlight dot
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(selX, selY))
                drawCircle(color = series.strokeColor, radius = 4.dp.toPx(), center = Offset(selX, selY))
            }
        }

        // 7. Floating Tooltip inspection bubble
        selectedPoint?.let { pt ->
            TooltipCard(
                point = pt,
                series = series,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TooltipCard(
    point: ChartDataPoint,
    series: ChartSeries,
    modifier: Modifier = Modifier,
) {
    val tt = point.tooltip
    val elapsedSec = (tt.elapsedMs / 1000)
    val elapsedFmt = String.format(Locale.US, "%02d:%02d", elapsedSec / 60, elapsedSec % 60)

    Card(
        modifier = modifier.border(1.dp, TooltipBorder, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = TooltipBg),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "t = $elapsedFmt",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                tt.percent?.let { p ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$p%",
                        color = Color(0xFF29B6F6),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
                if (tt.isPeak) {
                    Spacer(Modifier.width(6.dp))
                    Text("PEAK", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
                if (tt.isTaperStart) {
                    Spacer(Modifier.width(6.dp))
                    Text("TAPER", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                tt.powerW?.let { w ->
                    Text(
                        text = String.format(Locale.US, "%.1f W", w),
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                tt.voltageV?.let { v ->
                    Text(
                        text = String.format(Locale.US, "%.2f V", v),
                        color = Color(0xFF8C9BAE),
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                tt.currentA?.let { a ->
                    Text(
                        text = String.format(Locale.US, "%.2f A", a),
                        color = Color(0xFFAB47BC),
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                tt.temperatureC?.let { c ->
                    Text(
                        text = String.format(Locale.US, "%.1f °C", c),
                        color = Color(0xFFFF7043),
                        fontSize = 10.sp,
                    )
                }
            }

            if (tt.qualityFlags.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Flags: ${tt.qualityFlags.joinToString { it.name }}",
                    color = Color(0xFFEF5350),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun findClosestPoint(touchX: Float, totalWidth: Float, series: ChartSeries): ChartDataPoint? {
    val allPoints = series.segments.flatMap { it.points }
    if (allPoints.isEmpty()) return null

    val paddingLeft = 48f
    val paddingRight = 16f
    val chartWidth = (totalWidth - paddingLeft - paddingRight).coerceAtLeast(1f)

    val spanX = (series.maxX - series.minX).coerceAtLeast(1f)
    val normTouchX = series.minX + ((touchX - paddingLeft) / chartWidth) * spanX

    return allPoints.minByOrNull { abs(it.x - normTouchX) }
}
