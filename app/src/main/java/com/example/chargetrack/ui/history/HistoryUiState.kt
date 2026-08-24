package com.example.chargetrack.ui.history

import com.example.chargetrack.domain.history.HistoryFilter
import com.example.chargetrack.domain.history.HistorySessionItem
import com.example.chargetrack.domain.model.ChargingSetup

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter(),
    val sessions: List<HistorySessionItem> = emptyList(),
    val availableSetups: List<ChargingSetup> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleteConfirmDialogOpen: Boolean = false,
    val pendingDeleteSessionId: String? = null,
) {
    val totalCount: Int
        get() = sessions.size

    val isFiltered: Boolean
        get() = filter.canonical2080Only ||
            filter.standardTestOnly ||
            filter.chargingType != null ||
            filter.chargingSetupId != null ||
            filter.dateOption != com.example.chargetrack.domain.history.DateFilterOption.ALL ||
            filter.minStartPercent != null ||
            filter.maxEndPercent != null
}
