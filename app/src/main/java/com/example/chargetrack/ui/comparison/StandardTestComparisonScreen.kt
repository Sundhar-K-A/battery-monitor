package com.example.chargetrack.ui.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.chargetrack.domain.comparison.PercentTransitionDelta
import com.example.chargetrack.ui.comparison.components.AlignedMultiLineChart
import com.example.chargetrack.ui.comparison.components.ComparisonConditionBanner
import com.example.chargetrack.ui.comparison.components.ComparisonMetricsCard
import com.example.chargetrack.ui.comparison.components.ComparisonTestSelector
import com.example.chargetrack.ui.comparison.components.CurveColorPalette
import com.example.chargetrack.ui.comparison.components.PercentDeltaBarChart
import java.util.Locale

private val ScreenBackground = Color(0xFF000000)
private val CardBackground = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)
private val AmberAccent = Color(0xFFFFB300)
private val SubtitleColor = Color(0xFF8C9BAE)
private val GreenFaster = Color(0xFF4CAF50)
private val DialogBackground = Color(0xFF161B24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardTestComparisonScreen(
    primarySessionId: String?,
    candidateSessionId: String?,
    onNavigateBack: () -> Unit,
    viewModel: StandardTestComparisonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(primarySessionId, candidateSessionId) {
        viewModel.initialize(primarySessionId, candidateSessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Test Comparison",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val success = uiState as? StandardTestComparisonUiState.Success
                    val isBaseline = success?.activePrimaryBundle?.standardTest?.isBaseline ?: false

                    IconButton(
                        onClick = { viewModel.openSetBaselineDialog() },
                        enabled = success != null,
                    ) {
                        Icon(
                            imageVector = if (isBaseline) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Set as baseline",
                            tint = if (isBaseline) AmberAccent else SubtitleColor,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScreenBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = ScreenBackground,
    ) { paddingValues ->
        when (val state = uiState) {
            is StandardTestComparisonUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AmberAccent)
                }
            }

            is StandardTestComparisonUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is StandardTestComparisonUiState.Success -> {
                ComparisonContent(
                    state = state,
                    onSelectPrimary = { viewModel.selectPrimary(it) },
                    onToggleCandidate = { viewModel.toggleCandidate(it) },
                    onSelectTab = { viewModel.selectTab(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )

                // Baseline Confirmation Dialog
                if (state.isSetBaselineDialogOpen) {
                    val std = state.activePrimaryBundle.standardTest
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissSetBaselineDialog() },
                        title = {
                            Text("Set as Reference Baseline?", fontWeight = FontWeight.Bold, color = Color.White)
                        },
                        text = {
                            Text(
                                "This will designate this session as the reference benchmark baseline for comparison group: ${std?.comparisonGroupKey ?: ""}. Any previously designated baseline for this group will be updated.",
                                color = SubtitleColor,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.confirmSetBaseline() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberAccent,
                                    contentColor = Color.Black,
                                ),
                            ) {
                                Text("Set Baseline", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { viewModel.dismissSetBaselineDialog() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) {
                                Text("Cancel")
                            }
                        },
                        containerColor = DialogBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonContent(
    state: StandardTestComparisonUiState.Success,
    onSelectPrimary: (String) -> Unit,
    onToggleCandidate: (String) -> Unit,
    onSelectTab: (ComparisonChartTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Candidate Test Selector
        item {
            ComparisonTestSelector(
                allTests = state.allStandardTests,
                primarySessionId = state.primarySessionId,
                selectedCandidateIds = state.selectedCandidateSessionIds,
                onSelectPrimary = onSelectPrimary,
                onToggleCandidate = onToggleCandidate,
            )
        }

        // 2. Condition Mismatch Banner (for first pairwise result)
        state.primaryResult?.let { result ->
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ComparisonConditionBanner(conditions = result.conditions)
                }
            }

            // 3. Key Deltas Summary Card
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    val pName = state.alignedPowerSeries.firstOrNull()?.name ?: "Reference"
                    val cName = state.alignedPowerSeries.getOrNull(1)?.name ?: "Candidate"
                    ComparisonMetricsCard(
                        result = result,
                        primaryLabel = pName,
                        comparedLabel = cName,
                    )
                }
            }
        }

        // 4. Tab Switcher
        item {
            PrimaryTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = ScreenBackground,
                contentColor = Color.White,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(state.selectedTab.ordinal),
                        color = AmberAccent,
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                ComparisonChartTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = {
                            Text(
                                text = tab.label,
                                fontWeight = if (state.selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                color = if (state.selectedTab == tab) AmberAccent else SubtitleColor,
                                fontSize = 12.sp,
                            )
                        },
                    )
                }
            }
        }

        // 5. Chart Visualization
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (state.selectedTab) {
                        ComparisonChartTab.POWER_VS_PERCENT -> {
                            AlignedMultiLineChart(
                                seriesList = state.alignedPowerSeries,
                                colors = CurveColorPalette,
                            )
                        }

                        ComparisonChartTab.TEMP_VS_PERCENT -> {
                            AlignedMultiLineChart(
                                seriesList = state.alignedTempSeries,
                                colors = CurveColorPalette,
                            )
                        }

                        ComparisonChartTab.PACE_DELTAS -> {
                            val deltas = state.primaryResult?.perPercentDeltas ?: emptyList()
                            PercentDeltaBarChart(deltas = deltas)
                        }
                    }
                }
            }
        }

        // 6. Transition Pace Breakdown Table
        state.primaryResult?.let { result ->
            if (result.perPercentDeltas.isNotEmpty()) {
                item {
                    Text(
                        text = "1% Transition Pace Comparison",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                items(result.perPercentDeltas, key = { it.percent }) { deltaItem ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TransitionDeltaRow(deltaItem = deltaItem)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TransitionDeltaRow(deltaItem: PercentTransitionDelta) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${deltaItem.percent}% → ${deltaItem.percent + 1}%",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                val pSec = deltaItem.primaryDurationMs?.let { String.format(Locale.US, "%.1fs", it / 1000.0) } ?: "—"
                val cSec = deltaItem.comparedDurationMs?.let { String.format(Locale.US, "%.1fs", it / 1000.0) } ?: "—"

                Text(
                    text = "$pSec vs $cSec",
                    color = SubtitleColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )

                Spacer(Modifier.width(12.dp))

                val deltaSec = deltaItem.deltaMs?.let { it / 1000.0 }
                val deltaText = if (deltaSec != null) {
                    val sign = if (deltaSec > 0) "+" else ""
                    val fasterSlower = if (deltaSec < 0) "faster" else if (deltaSec > 0) "slower" else "same"
                    String.format(Locale.US, "%s%.1fs (%s)", sign, deltaSec, fasterSlower)
                } else "Gap"

                val deltaColor = if (deltaSec != null) {
                    if (deltaSec < 0) GreenFaster else if (deltaSec > 0) AmberAccent else Color.White
                } else Color(0xFFEF5350)

                Text(
                    text = deltaText,
                    color = deltaColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
