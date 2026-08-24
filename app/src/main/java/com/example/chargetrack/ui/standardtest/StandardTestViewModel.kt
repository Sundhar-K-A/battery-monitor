package com.example.chargetrack.ui.standardtest

import android.os.Build
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.BuildConfig
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.data.session.ChargingSessionRepository
import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.ComparisonGroupKeyGenerator
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTestConstants
import com.example.chargetrack.domain.model.StandardTestPreset
import com.example.chargetrack.service.MeasurementServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class StandardTestViewModel @Inject constructor(
    private val sessionRepository: ChargingSessionRepository,
    private val batteryDataSource: BatteryDataSource,
    private val database: AppDatabase,
    private val measurementServiceController: MeasurementServiceController,
    private val softwareSnapshotProvider: com.example.chargetrack.domain.system.SoftwareSnapshotProvider = com.example.chargetrack.data.system.DefaultSoftwareSnapshotProvider(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(StandardTestConfigUiState())
    val uiState: StateFlow<StandardTestConfigUiState> = _uiState.asStateFlow()

    init {
        loadSetupsAndRefresh()
    }

    private fun loadSetupsAndRefresh() {
        viewModelScope.launch {
            // Load setups from database or fallback to official default template
            val setupEntities = database.chargingSetupDao().getById("template-official-100w")
            val defaultSetup = setupEntities?.toDomain() ?: ChargingSetup(
                id = "template-official-100w",
                chargerBrand = "iQOO",
                chargerModel = "100W FlashCharge",
                advertisedWattageW = 100,
                protocol = "FlashCharge",
                isOfficialCharger = true,
                cableBrand = "iQOO",
                cableModel = "Stock Type-C",
                isOfficialCable = true,
                chargingType = ChargingType.WIRED,
                chargingMode = ChargingMode.FLASH_CHARGE,
                isFrozen = false,
                createdAt = Instant.now(),
            )

            _uiState.update { current ->
                current.copy(
                    availableSetups = listOf(defaultSetup),
                    selectedSetup = defaultSetup,
                    selectedChargingMode = defaultSetup.chargingMode,
                    comparisonGroupKey = computeGroupKey(
                        start = current.startPercent,
                        target = current.targetPercent,
                        setup = defaultSetup,
                        mode = defaultSetup.chargingMode,
                    ),
                )
            }

            refreshBatteryStatus()
        }
    }

    fun refreshBatteryStatus() {
        viewModelScope.launch {
            try {
                val snapshot = batteryDataSource.readSnapshot()
                val isCharging = snapshot.batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                    snapshot.batteryStatus == BatteryManager.BATTERY_STATUS_FULL ||
                    (snapshot.pluggedType != null && snapshot.pluggedType != 0)

                val (isReady, message) = evaluateBatteryReadiness(snapshot.percent, _uiState.value.startPercent)

                _uiState.update { current ->
                    current.copy(
                        latestSnapshot = snapshot,
                        isCharging = isCharging,
                        isBatteryReady = isReady,
                        batteryReadinessMessage = message,
                    )
                }
            } catch (e: Exception) {
                // Ignore snapshot read failures
            }
        }
    }

    fun selectPreset(preset: StandardTestPreset) {
        _uiState.update { current ->
            val start = preset.startPercent
            val target = preset.targetPercent
            val (isReady, message) = evaluateBatteryReadiness(current.latestSnapshot?.percent, start)
            val key = current.selectedSetup?.let { computeGroupKey(start, target, it, current.selectedChargingMode) } ?: ""

            current.copy(
                selectedPreset = preset,
                startPercent = start,
                targetPercent = target,
                isBatteryReady = isReady,
                batteryReadinessMessage = message,
                comparisonGroupKey = key,
            )
        }
    }

    fun setCustomRange(startPercent: Int, targetPercent: Int) {
        val safeStart = startPercent.coerceIn(0, 95)
        val safeTarget = targetPercent.coerceIn(safeStart + StandardTestConstants.MIN_STANDARD_TEST_PERCENT_SPAN, 100)

        _uiState.update { current ->
            val (isReady, message) = evaluateBatteryReadiness(current.latestSnapshot?.percent, safeStart)
            val key = current.selectedSetup?.let { computeGroupKey(safeStart, safeTarget, it, current.selectedChargingMode) } ?: ""

            current.copy(
                selectedPreset = StandardTestPreset.CUSTOM,
                startPercent = safeStart,
                targetPercent = safeTarget,
                isBatteryReady = isReady,
                batteryReadinessMessage = message,
                comparisonGroupKey = key,
            )
        }
    }

    fun selectSetup(setup: ChargingSetup) {
        _uiState.update { current ->
            val key = computeGroupKey(current.startPercent, current.targetPercent, setup, setup.chargingMode)
            current.copy(
                selectedSetup = setup,
                selectedChargingMode = setup.chargingMode,
                comparisonGroupKey = key,
            )
        }
    }

    fun setChargingMode(mode: ChargingMode) {
        _uiState.update { current ->
            val updatedSetup = current.selectedSetup?.copy(chargingMode = mode)
            val key = updatedSetup?.let { computeGroupKey(current.startPercent, current.targetPercent, it, mode) } ?: ""
            current.copy(
                selectedChargingMode = mode,
                selectedSetup = updatedSetup,
                comparisonGroupKey = key,
            )
        }
    }

    fun setUserNotes(notes: String) {
        _uiState.update { it.copy(userNotes = notes) }
    }

    fun startStandardTest(onSuccess: () -> Unit) {
        val state = _uiState.value
        val snapshot = state.latestSnapshot
        val setup = state.selectedSetup

        if (snapshot == null || setup == null || !state.isCharging) {
            _uiState.update { it.copy(errorMessage = "Cannot start test: phone must be connected to charger.") }
            return
        }

        _uiState.update { it.copy(isStarting = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val swSnapshot = softwareSnapshotProvider.captureCurrentSnapshot()
                val result = sessionRepository.startSession(
                    snapshot = snapshot,
                    setup = setup,
                    softwareSnapshot = swSnapshot,
                    testType = TestType.STANDARD,
                    userNotes = state.userNotes.ifBlank { null },
                    targetStartPercent = state.startPercent,
                    targetEndPercent = state.targetPercent,
                    comparisonGroupKey = state.comparisonGroupKey,
                )

                if (result.isSuccess) {
                    val session = result.getOrNull()
                    if (session != null) {
                        measurementServiceController.startService(session.id, android.os.SystemClock.elapsedRealtime())
                    }
                    _uiState.update { it.copy(isStarting = false) }
                    onSuccess()
                } else {
                    _uiState.update {
                        it.copy(
                            isStarting = false,
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to start session.",
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStarting = false,
                        errorMessage = "Error starting standard test: ${e.message}",
                    )
                }
            }
        }
    }

    private fun evaluateBatteryReadiness(currentPercent: Int?, targetStart: Int): Pair<Boolean, String> {
        if (currentPercent == null) return Pair(false, "Battery percentage unavailable.")
        return if (currentPercent <= targetStart) {
            Pair(
                true,
                if (currentPercent < targetStart) {
                    "Ready to arm (Current: $currentPercent%). Benchmark begins when battery reaches $targetStart%."
                } else {
                    "Ready to start benchmark immediately at $currentPercent%."
                }
            )
        } else {
            Pair(
                false,
                "Current battery is $currentPercent%, which is above the $targetStart% target start. Discharge battery before test or increase start percentage."
            )
        }
    }

    private fun computeGroupKey(
        start: Int,
        target: Int,
        setup: ChargingSetup,
        mode: ChargingMode,
    ): String = ComparisonGroupKeyGenerator.generateKey(
        targetStartPercent = start,
        targetEndPercent = target,
        chargingType = setup.chargingType,
        isOfficialCharger = setup.isOfficialCharger,
        isOfficialCable = setup.isOfficialCable,
        chargerBrand = setup.chargerBrand,
        advertisedWattageW = setup.advertisedWattageW,
        chargingMode = mode,
    )
}
