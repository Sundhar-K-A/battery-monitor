package com.example.chargetrack.ui.comparison.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.domain.comparison.TestComparisonResult
import java.util.Locale

private val CardBackground = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)
private val SubtitleColor = Color(0xFF8C9BAE)
private val GreenPositive = Color(0xFF4CAF50)
private val AmberDelta = Color(0xFFFFB300)
private val RedNegative = Color(0xFFEF5350)

@Composable
fun ComparisonMetricsCard(
    result: TestComparisonResult,
    primaryLabel: String,
    comparedLabel: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Change from Reference ($comparedLabel vs $primaryLabel)",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))

            // 1. Duration Delta
            val durSec = result.durationDeltaMs?.let { it / 1000 }
            val durPercent = result.durationDeltaPercent
            val durText = if (durSec != null) {
                val sign = if (durSec > 0) "+" else ""
                val min = kotlin.math.abs(durSec) / 60
                val sec = kotlin.math.abs(durSec) % 60
                val pctStr = durPercent?.let { String.format(Locale.US, " (%.1f%%)", it) } ?: ""
                val fasterSlower = if (durSec < 0) "faster" else if (durSec > 0) "slower" else "identical"
                "$sign${min}m ${sec}s$pctStr · $fasterSlower"
            } else "—"
            val durColor = if (durSec != null) {
                if (durSec < 0) GreenPositive else if (durSec > 0) AmberDelta else Color.White
            } else SubtitleColor
            DeltaItem(label = "Benchmark Duration Delta", value = durText, valueColor = durColor)

            // 2. Average Power Delta
            val avgPWatts = result.averagePowerDeltaUw?.let { it / 1_000_000.0 }
            val avgPPct = result.averagePowerDeltaPercent
            val avgPText = if (avgPWatts != null) {
                val sign = if (avgPWatts > 0) "+" else ""
                val pctStr = avgPPct?.let { String.format(Locale.US, " (%.1f%%)", it) } ?: ""
                String.format(Locale.US, "%s%.1f W%s", sign, avgPWatts, pctStr)
            } else "—"
            val avgPColor = if (avgPWatts != null) {
                if (avgPWatts > 0) GreenPositive else if (avgPWatts < 0) AmberDelta else Color.White
            } else SubtitleColor
            DeltaItem(label = "Average Estimated Power Delta", value = avgPText, valueColor = avgPColor)

            // 3. Peak Power Delta
            val peakPWatts = result.peakPowerDeltaUw?.let { it / 1_000_000.0 }
            val peakPText = if (peakPWatts != null) {
                val sign = if (peakPWatts > 0) "+" else ""
                String.format(Locale.US, "%s%.1f W", sign, peakPWatts)
            } else "—"
            DeltaItem(label = "Peak Power Delta", value = peakPText, valueColor = Color.White)

            // 4. Max Temperature Delta
            val maxTDeciC = result.maxTempDeltaDeciC
            val maxTText = if (maxTDeciC != null) {
                val sign = if (maxTDeciC > 0) "+" else ""
                String.format(Locale.US, "%s%.1f °C", sign, maxTDeciC / 10.0)
            } else "—"
            val maxTColor = if (maxTDeciC != null) {
                if (maxTDeciC < 0) GreenPositive else if (maxTDeciC > 0) AmberDelta else Color.White
            } else SubtitleColor
            DeltaItem(label = "Max Temperature Delta", value = maxTText, valueColor = maxTColor)
        }
    }
}

@Composable
private fun DeltaItem(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = SubtitleColor, fontSize = 12.sp)
        Text(
            text = value,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}
