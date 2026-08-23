package com.example.chargetrack.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.example.chargetrack.ui.charts.components.ChartCard
import com.example.chargetrack.ui.charts.components.InteractiveLineChart
import com.example.chargetrack.ui.charts.components.TimePerPercentBarChart
import java.util.Locale

private val ScreenBackground = Color(0xFF0D0F14)
private val TabSelectedColor = Color(0xFFFFB300)
private val TabUnselectedColor = Color(0xFF8C9BAE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionChartsScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    viewModel: SessionChartsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadSessionCharts(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Session Charts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            is SessionChartsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = TabSelectedColor)
                }
            }

            is SessionChartsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        color = Color(0xFFEF5350),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            is SessionChartsUiState.Success -> {
                ChartsContent(
                    state = state,
                    onSelectTab = { viewModel.selectTab(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }
        }
    }
}

@Composable
private fun ChartsContent(
    state: SessionChartsUiState.Success,
    onSelectTab: (ChartTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            containerColor = ScreenBackground,
            contentColor = TabSelectedColor,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab.ordinal]),
                    color = TabSelectedColor,
                    height = 2.5.dp,
                )
            },
            divider = {},
        ) {
            ChartTab.entries.forEach { tab ->
                val selected = state.selectedTab == tab
                Tab(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            color = if (selected) TabSelectedColor else TabUnselectedColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.selectedTab) {
                ChartTab.PERCENT_VS_TIME -> {
                    ChartCard(
                        title = "Battery Level vs Time",
                        subtitle = "Charging progress over monotonic elapsed time",
                        badgeText = "${state.sampleCount} samples",
                        badgeColor = Color(0xFF29B6F6),
                    ) {
                        InteractiveLineChart(series = state.batteryPercentVsTime)
                    }
                }

                ChartTab.POWER_VS_PERCENT -> {
                    val peakW = state.summary?.peakPowerUw?.let { String.format(Locale.US, "Peak: %.1f W", it / 1_000_000.0) }
                    ChartCard(
                        title = "Estimated Battery-Side Power vs Battery %",
                        subtitle = "Power delivery across charge level",
                        badgeText = peakW ?: "Power vs %",
                        badgeColor = Color(0xFFFFB300),
                    ) {
                        InteractiveLineChart(series = state.powerVsBatteryPercent)
                    }
                }

                ChartTab.POWER_VS_TIME -> {
                    val avgW = state.summary?.averagePowerUw?.let { String.format(Locale.US, "Avg: %.1f W", it / 1_000_000.0) }
                    ChartCard(
                        title = "Power vs Monotonic Elapsed Time",
                        subtitle = "Power delivery timeline and taper profile",
                        badgeText = avgW ?: "Power Profile",
                        badgeColor = Color(0xFFFFB300),
                    ) {
                        InteractiveLineChart(series = state.powerVsTime)
                    }
                }

                ChartTab.TEMP_VS_PERCENT -> {
                    val peakTemp = state.summary?.peakTemperatureDeciC?.let { String.format(Locale.US, "Peak: %.1f °C", it / 10.0) }
                    ChartCard(
                        title = "Temperature vs Battery %",
                        subtitle = "Battery thermal response across charge level",
                        badgeText = peakTemp ?: "Thermal",
                        badgeColor = Color(0xFFFF7043),
                    ) {
                        InteractiveLineChart(series = state.temperatureVsBatteryPercent)
                    }
                }

                ChartTab.CURRENT_VS_PERCENT -> {
                    val maxA = state.summary?.maxCurrentUa?.let { String.format(Locale.US, "Max: %.2f A", it / 1_000_000.0) }
                    ChartCard(
                        title = "Charging Current vs Battery %",
                        subtitle = "Battery current profile across charge level",
                        badgeText = maxA ?: "Current",
                        badgeColor = Color(0xFFAB47BC),
                    ) {
                        InteractiveLineChart(series = state.currentVsBatteryPercent)
                    }
                }

                ChartTab.TIME_PER_PERCENT -> {
                    val avgPace = state.summary?.averageTimePerOnePercentMs?.let { String.format(Locale.US, "Avg: %.1fs", it / 1000.0) }
                    ChartCard(
                        title = "Time Per 1% Transition",
                        subtitle = "Duration per 1 percentage step (with quality flags)",
                        badgeText = avgPace ?: "${state.timePerPercentBars.size} steps",
                        badgeColor = Color(0xFF4CAF50),
                    ) {
                        TimePerPercentBarChart(bars = state.timePerPercentBars)
                    }
                }
            }
        }
    }
}
