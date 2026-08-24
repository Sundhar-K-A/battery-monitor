package com.example.chargetrack.ui.settings

data class SettingsUiState(
    val sessionCount: Int = 0,
    val appVersion: String = "1.0",
    val appBuildCode: Int = 1,
    val isResetDialogOpen: Boolean = false,
    val statusMessage: String? = null,
)
