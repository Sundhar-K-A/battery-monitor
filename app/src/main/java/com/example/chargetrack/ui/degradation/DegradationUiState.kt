package com.example.chargetrack.ui.degradation

import com.example.chargetrack.domain.degradation.CapacityDegradationAnalysis
import com.example.chargetrack.domain.degradation.GroupTrendAnalysis

/**
 * UI State for the Longitudinal Degradation Analysis screen.
 */
sealed interface DegradationUiState {

    data object Loading : DegradationUiState

    data class Ready(
        val availableGroups: List<String>,
        val selectedGroupKey: String?,
        val performanceTrend: GroupTrendAnalysis?,
        val capacityTrend: CapacityDegradationAnalysis,
        val isSettingBaseline: Boolean = false,
    ) : DegradationUiState

    data class Empty(
        val message: String = "No completed Standard Tests or full-charge sessions found yet. Run standard benchmark tests to see longitudinal trends over time."
    ) : DegradationUiState

    data class Error(val message: String) : DegradationUiState
}
