package com.example.chargetrack.ui.summary

import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest

sealed interface SessionSummaryUiState {
    data object Loading : SessionSummaryUiState

    data class Success(
        val session: ChargingSession,
        val summary: SessionSummary,
        val setup: ChargingSetup?,
        val standardTest: StandardTest?,
        val software: SoftwareSnapshot?,
        val transitions: List<ChargeTransition>,
    ) : SessionSummaryUiState

    data class Error(val message: String) : SessionSummaryUiState
}
