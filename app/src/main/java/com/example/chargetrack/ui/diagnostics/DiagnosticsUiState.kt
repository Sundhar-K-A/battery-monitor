package com.example.chargetrack.ui.diagnostics

import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.device.BuildInfo
import com.example.chargetrack.domain.device.DeviceIdentifier

/** UI state for the Diagnostics screen. */
sealed interface DiagnosticsUiState {

    data object Loading : DiagnosticsUiState

    /**
     * A snapshot has been read successfully.
     *
     * @param snapshot      Latest battery reading.
     * @param buildInfo     Raw Build.* fields from this device.
     * @param knownDevice   Result of [DeviceIdentifier.identify] — never makes assumptions.
     * @param originOsLabel Best-effort OriginOS label; null if not detected.
     * @param appVersion    Human-readable app version string.
     */
    data class Ready(
        val snapshot: BatterySnapshot,
        val buildInfo: BuildInfo,
        val knownDevice: DeviceIdentifier.KnownDevice,
        val originOsLabel: String?,
        val appVersion: String,
    ) : DiagnosticsUiState

    data class Error(val message: String) : DiagnosticsUiState
}
