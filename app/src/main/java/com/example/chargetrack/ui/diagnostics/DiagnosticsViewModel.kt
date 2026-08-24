package com.example.chargetrack.ui.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.BuildConfig
import com.example.chargetrack.data.device.BuildInfoReader
import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.device.DeviceIdentifier
import com.example.chargetrack.domain.device.DeviceProfileFactory
import com.example.chargetrack.data.health.BatteryHealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val batteryDataSource: BatteryDataSource,
    private val batteryHealthRepository: BatteryHealthRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.Loading)
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DiagnosticsUiState.Loading
            runCatching {
                val snapshot  = batteryDataSource.readSnapshot()
                val buildInfo = BuildInfoReader.read()
                val proposal  = DeviceProfileFactory.buildProposal(buildInfo)
                val health    = batteryHealthRepository.getEstimatedBatteryHealth()
                val refCap    = batteryHealthRepository.getReferenceCapacityMah()

                _uiState.value = DiagnosticsUiState.Ready(
                    snapshot             = snapshot,
                    buildInfo            = buildInfo,
                    knownDevice          = proposal.matchedDevice,
                    originOsLabel        = proposal.proposedProfile.originOsBuildLabel,
                    appVersion           = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    healthEstimate       = health,
                    referenceCapacityMah = refCap,
                )
            }.onFailure { e ->
                _uiState.value = DiagnosticsUiState.Error(
                    e.message ?: "Unknown error during diagnostics read"
                )
            }
        }
    }
}
