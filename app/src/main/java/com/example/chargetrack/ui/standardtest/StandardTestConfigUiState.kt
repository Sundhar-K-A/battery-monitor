package com.example.chargetrack.ui.standardtest

import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.StandardTestPreset

/**
 * Immutable UI state for Standard Test configuration and readiness checklist.
 */
data class StandardTestConfigUiState(
    val selectedPreset: StandardTestPreset = StandardTestPreset.STANDARD_20_80,
    val startPercent: Int = StandardTestPreset.STANDARD_20_80.startPercent,
    val targetPercent: Int = StandardTestPreset.STANDARD_20_80.targetPercent,
    val availableSetups: List<ChargingSetup> = emptyList(),
    val selectedSetup: ChargingSetup? = null,
    val selectedChargingMode: ChargingMode = ChargingMode.FLASH_CHARGE,
    val userNotes: String = "",
    val latestSnapshot: BatterySnapshot? = null,
    val isCharging: Boolean = false,
    val isBatteryReady: Boolean = false,
    val batteryReadinessMessage: String = "",
    val comparisonGroupKey: String = "",
    val isStarting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canStart: Boolean
        get() = isCharging && selectedSetup != null && isBatteryReady && !isStarting
}
