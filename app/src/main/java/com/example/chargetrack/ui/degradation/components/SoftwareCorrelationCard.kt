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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.domain.correlation.FirmwareBuildBenchmarkSummary
import com.example.chargetrack.domain.correlation.FirmwareBuildComparison
import com.example.chargetrack.domain.correlation.SoftwareCorrelationAnalysis
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardBackground = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)
private val CyanAccent = Color(0xFF00E5FF)
private val AmberAccent = Color(0xFFFFB300)
private val SubtitleColor = Color(0xFF8C9BAE)
private val GreenColor = Color(0xFF4CAF50)
private val RedWarning = Color(0xFFEF5350)

@Composable
fun SoftwareCorrelationCard(
    analysis: SoftwareCorrelationAnalysis,
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
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Firmware & Version Correlation",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${analysis.firmwareSummaries.size} builds",
                style = MaterialTheme.typography.labelSmall,
                color = SubtitleColor,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(14.dp))

        if (analysis.firmwareSummaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2634), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            ) {
                Text(
                    "No completed benchmark tests with software snapshots in this comparison group.",
                    color = SubtitleColor,
                    fontSize = 12.sp,
                )
            }
        } else {
            // Firmware Builds List
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                analysis.firmwareSummaries.forEach { summary ->
                    FirmwareSummaryItem(summary)
                }
            }

            // Cross-Build Comparisons
            if (analysis.buildComparisons.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Observed Differences Across Builds",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    analysis.buildComparisons.forEach { comparison ->
                        BuildComparisonItem(comparison)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Neutral Methodology Disclaimer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0F14), RoundedCornerShape(8.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = SubtitleColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Firmware updates may alter OEM charging tables or thermal throttling thresholds. Observed changes reflect recorded session differences under identical test configurations without claiming causation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtitleColor,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun FirmwareSummaryItem(summary: FirmwareBuildBenchmarkSummary) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
        .withZone(ZoneId.systemDefault())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E2634), RoundedCornerShape(8.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary.firmwareDisplayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (summary.isLowEvidence) {
                Surface(
                    color = AmberAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "Low Evidence (${summary.sessionCount} test${if (summary.sessionCount > 1) "s" else ""})",
                            color = AmberAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                Surface(
                    color = CyanAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "${summary.sessionCount} tests",
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Active: ${dateFormatter.format(summary.firstSeenAt)} – ${dateFormatter.format(summary.lastSeenAt)}",
            color = SubtitleColor,
            fontSize = 10.sp,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val durText = summary.medianBenchmarkDurationMs?.let { formatDuration(it) } ?: "—"
            val pwrText = summary.meanBenchmarkAveragePowerUw?.let { "%.1f W".format(it / 1_000_000.0) } ?: "—"
            val tempText = summary.maxBenchmarkTempDeciC?.let { "%.1f °C".format(it / 10.0) } ?: "—"

            MiniMetricBox("Median Duration", durText, AmberAccent, Modifier.weight(1f))
            MiniMetricBox("Mean Avg Power", pwrText, Color(0xFF29B6F6), Modifier.weight(1f))
            MiniMetricBox("Max Temp", tempText, Color(0xFFEF5350), Modifier.weight(1f))
        }
    }
}

@Composable
private fun BuildComparisonItem(comparison: FirmwareBuildComparison) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0F14), RoundedCornerShape(8.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(
            text = "${comparison.priorBuild.firmwareDisplayLabel} → ${comparison.currentBuild.firmwareDisplayLabel}",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFE2E8F0),
            fontWeight = FontWeight.SemiBold,
        )

        if (comparison.isLowEvidence) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "⚠️ Low evidence comparison (<3 tests on one or both builds)",
                color = AmberAccent,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val durShift = comparison.durationShiftMs
            val durPct = comparison.durationShiftPercent
            val durSign = if ((durShift ?: 0L) > 0) "+" else ""
            val durColor = if ((durShift ?: 0L) > 0) RedWarning else GreenColor
            val durText = if (durShift != null && durPct != null) {
                "%s%.1f min (%s%.1f%%)".format(durSign, durShift / 60000.0, durSign, durPct)
            } else "—"

            val pwrShift = comparison.powerShiftUw
            val pwrPct = comparison.powerShiftPercent
            val pwrSign = if ((pwrShift ?: 0L) > 0) "+" else ""
            val pwrColor = if ((pwrShift ?: 0L) < 0) RedWarning else GreenColor
            val pwrText = if (pwrShift != null && pwrPct != null) {
                "%s%.1f W (%s%.1f%%)".format(pwrSign, pwrShift / 1_000_000.0, pwrSign, pwrPct)
            } else "—"

            MiniMetricBox("Observed Duration Change", durText, durColor, Modifier.weight(1f))
            MiniMetricBox("Observed Power Shift", pwrText, pwrColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniMetricBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF161B24), RoundedCornerShape(6.dp))
            .padding(6.dp),
    ) {
        Text(label, color = SubtitleColor, fontSize = 9.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
