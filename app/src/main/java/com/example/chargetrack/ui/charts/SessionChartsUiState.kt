package com.example.chargetrack.ui.charts

import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.model.StandardTest
import com.example.chargetrack.ui.charts.model.ChartSeries
import com.example.chargetrack.ui.charts.model.TimePerPercentBar

enum class ChartTab(val title: String) {
    PERCENT_VS_TIME("Level vs Time"),
    POWER_VS_PERCENT("Power vs %"),
    POWER_VS_TIME("Power vs Time"),
    TEMP_VS_PERCENT("Temp vs %"),
    CURRENT_VS_PERCENT("Current vs %"),
    TIME_PER_PERCENT("Time per 1%"),
}

sealed interface SessionChartsUiState {
    data object Loading : SessionChartsUiState

    data class Success(
        val sessionId: String,
        val selectedTab: ChartTab = ChartTab.PERCENT_VS_TIME,
        val batteryPercentVsTime: ChartSeries,
        val powerVsBatteryPercent: ChartSeries,
        val powerVsTime: ChartSeries,
        val temperatureVsBatteryPercent: ChartSeries,
        val currentVsBatteryPercent: ChartSeries,
        val timePerPercentBars: List<TimePerPercentBar>,
        val summary: SessionSummary? = null,
        val standardTest: StandardTest? = null,
        val sampleCount: Int = 0,
    ) : SessionChartsUiState

    data class Error(val message: String) : SessionChartsUiState
}
