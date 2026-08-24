package com.example.chargetrack.ui.comparison.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.domain.comparison.ComparisonCondition

private val GreenMatch = Color(0xFF4CAF50)
private val AmberMismatch = Color(0xFFFFB300)
private val RedMismatch = Color(0xFFEF5350)
private val CardBackground = Color(0xFF161B24)

@Composable
fun ComparisonConditionBanner(
    conditions: ComparisonCondition,
    modifier: Modifier = Modifier,
) {
    val isIdeal = conditions.isIdealComparison
    val accentColor = if (isIdeal) GreenMatch else AmberMismatch

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isIdeal) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isIdeal) "Ideal Comparison Conditions" else "Condition Differences Detected",
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontSize = 14.sp,
                )
            }

            if (!isIdeal && conditions.mismatchWarnings.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column {
                    conditions.mismatchWarnings.forEach { warning ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text("• ", color = AmberMismatch, fontSize = 12.sp)
                            Text(warning, color = Color(0xFFE0E6ED), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
