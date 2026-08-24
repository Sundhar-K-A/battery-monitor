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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Info
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
import com.example.chargetrack.domain.degradation.CapacityDegradationAnalysis
import com.example.chargetrack.domain.degradation.DegradationConfidence

private val CardBackground = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)
private val AmberAccent = Color(0xFFFFB300)
private val BlueAccent = Color(0xFF29B6F6)
private val SubtitleColor = Color(0xFF8C9BAE)
private val GreenColor = Color(0xFF4CAF50)
private val RedWarning = Color(0xFFEF5350)

@Composable
fun CapacityDegradationCard(
    analysis: CapacityDegradationAnalysis,
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
            Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = GreenColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Observed Capacity Trend",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            ConfidenceBadge(analysis.confidence, analysis.observationCount)
        }

        Spacer(Modifier.height(14.dp))

        // Main Estimated Capacity Metric
        if (analysis.estimatedCapacityMah != null && analysis.estimatedHealthPercent != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Estimated Battery Health",
                        style = MaterialTheme.typography.labelSmall,
                        color = SubtitleColor,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${analysis.estimatedHealthPercent}%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = GreenColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Estimated Capacity",
                        style = MaterialTheme.typography.labelSmall,
                        color = SubtitleColor,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${analysis.estimatedCapacityMah} mAh",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "Ref: ${analysis.referenceCapacityMah} mAh typical",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtitleColor,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Consistency Badge & Change vs Reference
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val consistencyLabel = if (analysis.isConsistent) {
                    "Consistent observations (CV %.1f%%)".format(analysis.coefficientOfVariation?.times(100) ?: 0.0)
                } else {
                    "Variable observations (higher session variance)"
                }
                val consistencyColor = if (analysis.isConsistent) GreenColor else AmberAccent

                Surface(
                    color = consistencyColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = consistencyLabel,
                        color = consistencyColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                if (analysis.changeFromReferenceMah != null && analysis.changeFromReferencePercent != null) {
                    val sign = if (analysis.changeFromReferenceMah >= 0) "+" else ""
                    Surface(
                        color = Color(0xFF1E2634),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = "%s%d mAh (%s%.1f%%) vs ref".format(
                                sign,
                                analysis.changeFromReferenceMah,
                                sign,
                                analysis.changeFromReferencePercent,
                            ),
                            color = SubtitleColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        } else {
            // Insufficient Data state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2634), RoundedCornerShape(8.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        "Not enough full-charge data",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ChargeTrack requires at least 3 qualifying full-charge sessions (100%) to calculate estimated capacity. Currently recorded: ${analysis.observationCount} observations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtitleColor,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Methodology & Experimental Disclosure Banner
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
                    "Experimental estimate derived from fuel-gauge readings during 100% full-charge windows. Not an official manufacturer battery health reading.",
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
private fun ConfidenceBadge(confidence: DegradationConfidence, count: Int) {
    val (label, color) = when (confidence) {
        DegradationConfidence.HIGH -> "High Confidence ($count obs)" to GreenColor
        DegradationConfidence.PRELIMINARY -> "Preliminary ($count obs)" to AmberAccent
        DegradationConfidence.INSUFFICIENT -> "Insufficient Data" to SubtitleColor
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
