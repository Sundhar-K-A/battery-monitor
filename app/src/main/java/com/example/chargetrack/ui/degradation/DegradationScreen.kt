package com.example.chargetrack.ui.degradation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chargetrack.domain.degradation.BenchmarkTrendPoint
import com.example.chargetrack.domain.degradation.GroupTrendAnalysis
import com.example.chargetrack.ui.degradation.components.CapacityDegradationCard
import com.example.chargetrack.ui.degradation.components.LongitudinalTrendChart
import com.example.chargetrack.ui.degradation.components.PerformanceTrendCard
import com.example.chargetrack.ui.degradation.components.TrendDataPoint
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ScreenBackground = Color(0xFF000000)
private val CardBackground = Color(0xFF161B24)
private val CardBorder = Color(0xFF2A3241)
private val AmberAccent = Color(0xFFFFB300)
private val BlueAccent = Color(0xFF29B6F6)
private val GreenColor = Color(0xFF4CAF50)
private val SubtitleColor = Color(0xFF8C9BAE)
private val DialogBackground = Color(0xFF161B24)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DegradationScreen(
    viewModel: DegradationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showBaselineDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Longitudinal Analysis",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScreenBackground),
            )
        },
        containerColor = ScreenBackground,
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        when (val state = uiState) {
            is DegradationUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AmberAccent)
                }
            }
            is DegradationUiState.Empty -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = SubtitleColor, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No Longitudinal Data Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SubtitleColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            is DegradationUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(state.message, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodyMedium)
                }
            }
            is DegradationUiState.Ready -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Primary Tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = ScreenBackground,
                        contentColor = AmberAccent,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = AmberAccent,
                            )
                        },
                        edgePadding = 16.dp,
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = {
                                Text(
                                    "Charging Performance",
                                    color = if (selectedTabIndex == 0) AmberAccent else SubtitleColor,
                                    fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = {
                                Text(
                                    "Battery Capacity",
                                    color = if (selectedTabIndex == 1) GreenColor else SubtitleColor,
                                    fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = {
                                Text(
                                    "Firmware Correlation",
                                    color = if (selectedTabIndex == 2) Color(0xFF00E5FF) else SubtitleColor,
                                    fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }

                    when (selectedTabIndex) {
                        0 -> PerformanceTabContent(
                            state = state,
                            onSelectGroup = { viewModel.selectGroup(it) },
                            onOpenBaselineDialog = { showBaselineDialog = true },
                        )
                        1 -> CapacityTabContent(state = state)
                        2 -> SoftwareCorrelationTabContent(
                            state = state,
                            onSelectGroup = { viewModel.selectGroup(it) },
                        )
                    }
                }

                // Baseline Selection Dialog
                if (showBaselineDialog && state.performanceTrend != null) {
                    BaselineSelectionDialog(
                        analysis = state.performanceTrend,
                        onDismiss = { showBaselineDialog = false },
                        onSelectBaseline = { testId ->
                            state.selectedGroupKey?.let { groupKey ->
                                viewModel.setBaselineTest(testId, groupKey)
                            }
                            showBaselineDialog = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformanceTabContent(
    state: DegradationUiState.Ready,
    onSelectGroup: (String) -> Unit,
    onOpenBaselineDialog: () -> Unit,
) {
    val analysis = state.performanceTrend

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Comparison Group Selector
        if (state.availableGroups.size > 1) {
            item {
                Column {
                    Text("Comparison Group", style = MaterialTheme.typography.labelSmall, color = SubtitleColor)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.availableGroups.forEach { groupKey ->
                            val isSelected = groupKey == state.selectedGroupKey
                            Surface(
                                color = if (isSelected) AmberAccent.copy(alpha = 0.2f) else CardBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) AmberAccent else CardBorder
                                ),
                                modifier = Modifier.clickable { onSelectGroup(groupKey) },
                            ) {
                                Text(
                                    text = formatGroupKey(groupKey),
                                    color = if (isSelected) AmberAccent else SubtitleColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (analysis != null) {
            item {
                PerformanceTrendCard(
                    analysis = analysis,
                    onOpenBaselineDialog = onOpenBaselineDialog,
                )
            }

            // Duration Chart
            item {
                val durationPoints = analysis.points.map {
                    TrendDataPoint(it.timestamp, it.benchmarkDurationMs / 60000.0, it.isBaseline)
                }
                LongitudinalTrendChart(
                    title = "Benchmark Duration Trend",
                    unit = "minutes",
                    points = durationPoints,
                    seriesColor = AmberAccent,
                    firmwareTransitionTimestamps = state.firmwareTransitionTimestamps,
                )
            }

            // Average Power Chart
            item {
                val powerPoints = analysis.points.map {
                    TrendDataPoint(it.timestamp, it.benchmarkAveragePowerUw / 1_000_000.0, it.isBaseline)
                }
                LongitudinalTrendChart(
                    title = "Average Charging Power Trend",
                    unit = "Watts",
                    points = powerPoints,
                    seriesColor = BlueAccent,
                    firmwareTransitionTimestamps = state.firmwareTransitionTimestamps,
                )
            }

            // Max Temperature Chart
            item {
                val tempPoints = analysis.points.mapNotNull { p ->
                    p.benchmarkMaxTempDeciC?.let {
                        TrendDataPoint(p.timestamp, it / 10.0, p.isBaseline)
                    }
                }
                LongitudinalTrendChart(
                    title = "Max Benchmark Temperature",
                    unit = "°C",
                    points = tempPoints,
                    seriesColor = Color(0xFFEF5350),
                    firmwareTransitionTimestamps = state.firmwareTransitionTimestamps,
                )
            }

            // History Log
            item {
                Text(
                    "Standard Tests in Group (${analysis.points.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(analysis.points) { point ->
                BenchmarkPointRow(point)
            }
        }
    }
}

@Composable
private fun CapacityTabContent(state: DegradationUiState.Ready) {
    val analysis = state.capacityTrend

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CapacityDegradationCard(analysis = analysis)
        }

        if (analysis.points.isNotEmpty()) {
            item {
                val capacityPoints = analysis.points.map {
                    TrendDataPoint(it.timestamp, it.observedCapacityMah.toDouble(), false)
                }
                LongitudinalTrendChart(
                    title = "Observed Full-Charge Capacity",
                    unit = "mAh",
                    points = capacityPoints,
                    seriesColor = GreenColor,
                    referenceLineValue = analysis.referenceCapacityMah.toDouble(),
                    referenceLineLabel = "${analysis.referenceCapacityMah} mAh Ref",
                    firmwareTransitionTimestamps = state.firmwareTransitionTimestamps,
                )
            }

            item {
                Text(
                    "Full-Charge Observations (${analysis.points.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(analysis.points) { point ->
                CapacityPointRow(point, analysis.referenceCapacityMah)
            }
        }
    }
}

@Composable
private fun SoftwareCorrelationTabContent(
    state: DegradationUiState.Ready,
    onSelectGroup: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Comparison Group Selector
        if (state.availableGroups.size > 1) {
            item {
                Column {
                    Text("Comparison Group", style = MaterialTheme.typography.labelSmall, color = SubtitleColor)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.availableGroups.forEach { groupKey ->
                            val isSelected = groupKey == state.selectedGroupKey
                            Surface(
                                color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else CardBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF00E5FF) else CardBorder
                                ),
                                modifier = Modifier.clickable { onSelectGroup(groupKey) },
                            ) {
                                Text(
                                    text = formatGroupKey(groupKey),
                                    color = if (isSelected) Color(0xFF00E5FF) else SubtitleColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        val correlation = state.softwareCorrelation
        if (correlation != null) {
            item {
                com.example.chargetrack.ui.degradation.components.SoftwareCorrelationCard(
                    analysis = correlation,
                )
            }
        }
    }
}

@Composable
private fun BenchmarkPointRow(point: BenchmarkTrendPoint) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(8.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dateFormatter.format(point.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                if (point.isBaseline) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = AmberAccent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "BASELINE",
                            color = AmberAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Peak: %.1fW • Max: %.1f°C".format(
                    point.benchmarkPeakPowerUw / 1_000_000.0,
                    (point.benchmarkMaxTempDeciC ?: 0) / 10.0
                ),
                style = MaterialTheme.typography.bodySmall,
                color = SubtitleColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatDuration(point.benchmarkDurationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = AmberAccent,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "%.1f W avg".format(point.benchmarkAveragePowerUw / 1_000_000.0),
                style = MaterialTheme.typography.bodySmall,
                color = SubtitleColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CapacityPointRow(point: com.example.chargetrack.domain.degradation.CapacityTrendPoint, refCap: Int) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground, RoundedCornerShape(8.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                dateFormatter.format(point.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Session: ${point.sessionId.take(8)}...",
                style = MaterialTheme.typography.bodySmall,
                color = SubtitleColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${point.observedCapacityMah} mAh",
                style = MaterialTheme.typography.bodyMedium,
                color = GreenColor,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "%.1f%% of ref".format(point.retentionPercent),
                style = MaterialTheme.typography.bodySmall,
                color = SubtitleColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BaselineSelectionDialog(
    analysis: GroupTrendAnalysis,
    onDismiss: () -> Unit,
    onSelectBaseline: (String) -> Unit,
) {
    var selectedTestId by remember { mutableStateOf(analysis.baselinePoint?.testId ?: analysis.points.firstOrNull()?.testId) }
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm", Locale.US)
        .withZone(ZoneId.systemDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Group Baseline", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose which completed test serves as the reference baseline for duration and power deltas in this group.",
                    color = SubtitleColor,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(4.dp))
                analysis.points.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTestId = p.testId }
                            .background(
                                if (selectedTestId == p.testId) AmberAccent.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedTestId == p.testId,
                            onClick = { selectedTestId = p.testId },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                dateFormatter.format(p.timestamp),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${formatDuration(p.benchmarkDurationMs)} • ${"%.1f".format(p.benchmarkAveragePowerUw / 1_000_000.0)}W",
                                color = SubtitleColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedTestId?.let { onSelectBaseline(it) } },
            ) {
                Text("Set Baseline", color = AmberAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SubtitleColor)
            }
        },
        containerColor = DialogBackground,
    )
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

private fun formatGroupKey(key: String): String {
    return key.replace("_", " ").uppercase()
}
