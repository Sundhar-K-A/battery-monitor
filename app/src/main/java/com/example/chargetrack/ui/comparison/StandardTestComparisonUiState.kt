package com.example.chargetrack.ui.comparison

import com.example.chargetrack.domain.comparison.StandardTestDataBundle
import com.example.chargetrack.domain.comparison.TestComparisonResult
import com.example.chargetrack.domain.history.HistorySessionItem
import com.example.chargetrack.ui.charts.model.ChartSeries

enum class ComparisonChartTab(val label: String) {
    POWER_VS_PERCENT("Aligned Power"),
    TEMP_VS_PERCENT("Aligned Temp"),
    PACE_DELTAS("1% Pace Deltas"),
}

sealed interface StandardTestComparisonUiState {
    data object Loading : StandardTestComparisonUiState

    data class Success(
        val allStandardTests: List<HistorySessionItem>,
        val primarySessionId: String,
        val selectedCandidateSessionIds: Set<String>,
        val activePrimaryBundle: StandardTestDataBundle,
        val activeCandidateBundles: List<StandardTestDataBundle>,
        val pairwiseResults: List<TestComparisonResult>,
        val alignedPowerSeries: List<ChartSeries>,
        val alignedTempSeries: List<ChartSeries>,
        val selectedTab: ComparisonChartTab = ComparisonChartTab.POWER_VS_PERCENT,
        val isSetBaselineDialogOpen: Boolean = false,
    ) : StandardTestComparisonUiState {
        val totalActiveCurves: Int
            get() = 1 + activeCandidateBundles.size

        val primaryResult: TestComparisonResult?
            get() = pairwiseResults.firstOrNull()
    }

    data class Error(val message: String) : StandardTestComparisonUiState
}
