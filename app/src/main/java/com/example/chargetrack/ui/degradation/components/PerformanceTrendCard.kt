package com.example.chargetrack.ui.degradation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.domain.degradation.BenchmarkTrendPoint
import com.example.chargetrack.domain.degradation.GroupTrendAnalysis
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardBackground = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)
private val AmberAccent = Color(0xFFFFB300)
private val BlueAccent = Color(0xFF29B6F6)
private val SubtitleColor = Color(0xFF8C9BAE)
private val GreenColor = Color(0xFF4CAF50)
private val RedWarning = Color(0xFFEF5350)

@Composable
fun PerformanceTrendCard(
    analysis: GroupTrendAnalysis,
    onOpenBaselineDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Speed, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Charging Performance Trend",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${analysis.points.size} tests",
                style = MaterialTheme.typography.labelSmall,
                color = SubtitleColor,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Baseline information banner
        val baseline = analysis.baselinePoint
        if (baseline != null) {
            val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.US)
                .withZone(ZoneId.systemDefault())

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2634), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Baseline Test: ${dateFormatter.format(baseline.timestamp)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${formatDuration(baseline.benchmarkDurationMs)} • ${"%.1f".format(baseline.benchmarkAveragePowerUw / 1_000_000.0)}W avg",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtitleColor,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenBaselineDialog,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                    ) {
                        Text("Change", fontSize = 11.sp)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2634), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("No baseline set for this group", color = SubtitleColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onOpenBaselineDialog, shape = RoundedCornerShape(8.dp)) {
                        Text("Set Baseline", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Neutral Deltas vs Baseline
        if (analysis.latestDurationChangeFromBaselineMs != null && analysis.latestDurationChangePercent != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val durDeltaMs = analysis.latestDurationChangeFromBaselineMs
                val durPct = analysis.latestDurationChangePercent
                val durColor = if (durDeltaMs > 0) RedWarning else GreenColor
                val durSign = if (durDeltaMs > 0) "+" else ""

                DeltaStatBox(
                    label = "Change from baseline (Duration)",
                    value = "%s%.1f min (%s%.1f%%)".format(durSign, durDeltaMs / 60000.0, durSign, durPct),
                    color = durColor,
                    modifier = Modifier.weight(1f),
                )

                val pwrDeltaUw = analysis.latestPowerChangeFromBaselineUw ?: 0L
                val pwrPct = analysis.latestPowerChangePercent ?: 0.0
                val pwrColor = if (pwrDeltaUw < 0) RedWarning else GreenColor
                val pwrSign = if (pwrDeltaUw > 0) "+" else ""

                DeltaStatBox(
                    label = "Change from baseline (Power)",
                    value = "%s%.1f W (%s%.1f%%)".format(pwrSign, pwrDeltaUw / 1_000_000.0, pwrSign, pwrPct),
                    color = pwrColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DeltaStatBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF0D0F14), RoundedCornerShape(8.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SubtitleColor, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
