package com.example.chargetrack.ui.comparison.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.chargetrack.domain.history.HistorySessionItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val CurveColorPalette = listOf(
    Color(0xFFFFB300), // Gold (Primary/Baseline)
    Color(0xFF29B6F6), // Cyan (Candidate 1)
    Color(0xFFBA68C8), // Purple (Candidate 2)
    Color(0xFF4CAF50), // Green (Candidate 3)
    Color(0xFFFF7043), // Coral (Candidate 4)
)

private val SubtitleColor = Color(0xFF8C9BAE)
private val ChipBorderColor = Color(0xFF2A3241)
private val ChipBgColor = Color(0xFF161B24)

@Composable
fun ComparisonTestSelector(
    allTests: List<HistorySessionItem>,
    primarySessionId: String?,
    selectedCandidateIds: Set<String>,
    onSelectPrimary: (String) -> Unit,
    onToggleCandidate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val formatter = DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.US).withZone(ZoneId.systemDefault())

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Compare Standard Tests", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            val totalSelected = (if (primarySessionId != null) 1 else 0) + selectedCandidateIds.size
            Text("$totalSelected / 5 curves selected", color = SubtitleColor, fontSize = 12.sp)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            allTests.forEach { test ->
                val isPrimary = test.sessionId == primarySessionId
                val isCandidate = selectedCandidateIds.contains(test.sessionId)
                val isSelected = isPrimary || isCandidate

                val assignedColor = if (isPrimary) {
                    CurveColorPalette[0]
                } else if (isCandidate) {
                    val candidateIndex = selectedCandidateIds.toList().indexOf(test.sessionId)
                    CurveColorPalette.getOrElse(candidateIndex + 1) { CurveColorPalette.last() }
                } else null

                val formattedDate = formatter.format(test.startedAt)
                val chargerName = test.chargingSetup?.chargerModel ?: "Charger"

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (primarySessionId == null) {
                            onSelectPrimary(test.sessionId)
                        } else if (isPrimary) {
                            // Can't unselect primary directly; switch if clicked elsewhere
                        } else {
                            onToggleCandidate(test.sessionId)
                        }
                    },
                    label = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (assignedColor != null) {
                                    Surface(
                                        color = assignedColor,
                                        shape = CircleShape,
                                        modifier = Modifier.size(8.dp),
                                    ) {}
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (isPrimary) "REF: $formattedDate" else formattedDate,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                )
                            }
                            Text(
                                text = "${test.displayTitle} · $chargerName",
                                color = SubtitleColor,
                                fontSize = 10.sp,
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = assignedColor?.copy(alpha = 0.2f) ?: ChipBgColor,
                        selectedLabelColor = Color.White,
                        containerColor = ChipBgColor,
                        labelColor = Color.White,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = ChipBorderColor,
                        selectedBorderColor = assignedColor ?: ChipBorderColor,
                        enabled = true,
                        selected = isSelected,
                    ),
                )
            }
        }
    }
}
