package com.example.chargetrack.ui.device

import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.DeviceProfile

data class DeviceUiState(
    val profile: DeviceProfile? = null,
    val savedSetups: List<ChargingSetup> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val editNickname: String = "",
    val editRamStorage: String = "",
    val editNotes: String = "",
    val statusMessage: String? = null,
)
