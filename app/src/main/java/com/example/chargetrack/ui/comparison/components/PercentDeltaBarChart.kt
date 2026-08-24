package com.example.chargetrack.ui.comparison.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.domain.comparison.PercentTransitionDelta
import java.util.Locale
import kotlin.math.abs

private val GreenFaster = Color(0xFF4CAF50)
private val AmberSlower = Color(0xFFFFB300)
private val RedGap = Color(0xFFEF5350)
private val ZeroLineColor = Color(0xFF8C9BAE)
private val CardBackground = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)

@Composable
fun PercentDeltaBarChart(
    deltas: List<PercentTransitionDelta>,
    modifier: Modifier = Modifier,
) {
    val comparableDeltas = deltas.filter { it.isComparable && it.deltaMs != null }
    if (comparableDeltas.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("No contiguous transition deltas available", color = Color(0xFF8C9BAE), fontSize = 13.sp)
        }
        return
    }

    var selectedDelta by remember { mutableStateOf<PercentTransitionDelta?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    val maxAbsDeltaSec = comparableDeltas.maxOfOrNull { abs(it.deltaMs!! / 1000f) }?.coerceAtLeast(2f) ?: 5f
    val barWidthDp = 24.dp
    val spacingDp = 8.dp
    val totalWidthDp = (barWidthDp + spacingDp) * deltas.size + 40.dp

    Column(modifier = modifier.fillMaxWidth()) {
        // Selected Inspection Card
        selectedDelta?.let { sel ->
            val deltaSec = sel.deltaMs?.let { it / 1000.0 }
            val fasterSlower = if (deltaSec != null) {
                if (deltaSec < 0) "faster" else if (deltaSec > 0) "slower" else "identical"
            } else "N/A"
            val deltaColor = if (deltaSec != null) {
                if (deltaSec < 0) GreenFaster else if (deltaSec > 0) AmberSlower else Color.White
            } else Color.White

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Step ${sel.percent}→${sel.percent + 1}%: ",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = deltaSec?.let { String.format(Locale.US, "%+.1fs (%s)", it, fasterSlower) } ?: "Gap",
                        color = deltaColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(totalWidthDp)
                    .height(200.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val padLeft = 20.dp.toPx()
                            val stepPx = (barWidthDp + spacingDp).toPx()
                            val idx = ((offset.x - padLeft) / stepPx).toInt()
                            if (idx in deltas.indices) {
                                selectedDelta = deltas[idx]
                            }
                        }
                    }
            ) {
                val padLeft = 20.dp.toPx()
                val padTop = 16.dp.toPx()
                val padBottom = 28.dp.toPx()
                val chartH = size.height - padTop - padBottom
                val zeroY = padTop + chartH / 2f

                // Draw Zero Reference Line
                drawLine(
                    color = ZeroLineColor,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.dp.toPx(),
                )

                deltas.forEachIndexed { i, item ->
                    val barX = padLeft + i * (barWidthDp + spacingDp).toPx()
                    val deltaSec = item.deltaMs?.let { it / 1000f }

                    if (item.isComparable && deltaSec != null) {
                        val barColor = if (deltaSec < 0) GreenFaster else AmberSlower
                        val barHeightPx = (abs(deltaSec) / maxAbsDeltaSec) * (chartH / 2f)

                        val barY = if (deltaSec < 0) zeroY else zeroY - barHeightPx

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(barX, barY),
                            size = Size(barWidthDp.toPx(), barHeightPx.coerceAtLeast(2.dp.toPx())),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    } else {
                        // Gap indicator
                        drawRoundRect(
                            color = RedGap.copy(alpha = 0.3f),
                            topLeft = Offset(barX, zeroY - 10.dp.toPx()),
                            size = Size(barWidthDp.toPx(), 20.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )
                    }

                    // Percentage label under zero line
                    if (i % 5 == 0 || i == deltas.lastIndex) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "${item.percent}%",
                            topLeft = Offset(barX - 4.dp.toPx(), size.height - padBottom + 4.dp.toPx()),
                            style = TextStyle(color = ZeroLineColor, fontSize = 9.sp),
                        )
                    }
                }
            }
        }
    }
}
