package com.example.chargetrack.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.sampling.SamplingRepository
import com.example.chargetrack.data.session.ChargingSessionRepository
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.session.SessionState
import com.example.chargetrack.domain.time.DefaultTimeSource
import com.example.chargetrack.domain.time.TimeSource
import com.example.chargetrack.domain.transition.ChargeTransitionDetector
import com.example.chargetrack.service.MeasurementServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel driving the read-only Live Charging Session screen.
 *
 * ## Invariants
 * - Pure consumer of [ChargingSessionRepository] and [SamplingRepository].
 * - Delegates background measurement lifecycle to [MeasurementServiceController].
 * - Never starts its own battery polling loop or duplicate sampling coroutines.
 * - Manages a single [ChargeTransitionDetector] per active session lifecycle.
 * - 1-second monotonic timer updates only elapsed duration during active sessions.
 * - Freezes elapsed duration immediately upon session completion.
 */
@HiltViewModel
class LiveSessionViewModel @Inject constructor(
    private val sessionRepository: ChargingSessionRepository,
    private val samplingRepository: SamplingRepository,
    private val timeSource: TimeSource = DefaultTimeSource(),
    private val serviceController: MeasurementServiceController? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LiveSessionUiState>(LiveSessionUiState.NoSession)
    val uiState: StateFlow<LiveSessionUiState> = _uiState.asStateFlow()

    private var activeSessionId: String? = null
    private var detector: ChargeTransitionDetector? = null
    private val completedTransitions = mutableListOf<ChargeTransition>()

    private var timerJob: Job? = null
    private var sampleCollectorJob: Job? = null

    init {
        viewModelScope.launch {
            sessionRepository.sessionState
                .collect { state -> handleSessionState(state) }
        }
    }

    /**
     * User requested manual termination of the active charging session.
     */
    fun stopSession() {
        serviceController?.stopService()
        viewModelScope.launch {
            sessionRepository.stopSession()
        }
    }

    /**
     * User acknowledged/reset a completed session back to [SessionState.Idle].
     */
    fun resetSession() {
        sessionRepository.resetSession()
    }

    private fun handleSessionState(state: SessionState) {
        when (state) {
            is SessionState.Idle -> {
                stopJobs()
                detector = null
                activeSessionId = null
                completedTransitions.clear()
                _uiState.value = LiveSessionUiState.NoSession
            }

            is SessionState.Active -> {
                ensureSessionActive(
                    sessionId = state.session.id,
                    startPercent = state.session.startPercent,
                    startRealtimeMs = state.startRealtimeMs,
                    isDebouncing = false,
                    sampleCount = state.sampleCount,
                )
            }

            is SessionState.UnpluggedPending -> {
                ensureSessionActive(
                    sessionId = state.activeState.session.id,
                    startPercent = state.activeState.session.startPercent,
                    startRealtimeMs = state.activeState.startRealtimeMs,
                    isDebouncing = true,
                    sampleCount = state.activeState.sampleCount,
                )
            }

            is SessionState.Completed -> {
                stopJobs()
                val partialInfo = detector?.onSessionEnd()
                val finalTransitions = completedTransitions.toList()

                _uiState.value = LiveSessionUiState.SessionEnded(
                    session = state.session,
                    durationMs = state.durationMs,
                    sampleCount = state.sampleCount,
                    endReason = state.session.endReason ?: SessionEndReason.UNKNOWN,
                    completedTransitions = finalTransitions,
                    partialTransitionInfo = partialInfo,
                )

                detector = null
                activeSessionId = null
                completedTransitions.clear()
            }
        }
    }

    private fun ensureSessionActive(
        sessionId: String,
        startPercent: Int?,
        startRealtimeMs: Long,
        isDebouncing: Boolean,
        sampleCount: Int,
    ) {
        if (activeSessionId != sessionId) {
            stopJobs()
            completedTransitions.clear()
            detector = ChargeTransitionDetector(sessionId)
            activeSessionId = sessionId

            // Ensure background service is launched for active session
            serviceController?.startService(sessionId, startRealtimeMs)

            val initialElapsed = (timeSource.elapsedRealtime() - startRealtimeMs).coerceAtLeast(0L)
            val latest = samplingRepository.latestSample.value

            _uiState.value = LiveSessionUiState.Active(
                sessionId = sessionId,
                startPercent = startPercent,
                elapsedMs = initialElapsed,
                isDebouncing = isDebouncing,
                currentPercent = latest?.percent,
                voltageMv = latest?.voltageMv,
                currentNowUa = latest?.currentNowUa,
                temperatureDeciC = latest?.temperatureDeciC,
                derivedPowerUw = latest?.derivedPowerUw,
                qualityFlags = latest?.qualityFlags ?: emptySet(),
                sampleCount = sampleCount,
                completedTransitions = emptyList(),
            )

            startTimerJob(startRealtimeMs)
            startSampleCollectorJob(sessionId)
        } else {
            val current = _uiState.value
            if (current is LiveSessionUiState.Active) {
                _uiState.value = current.copy(
                    isDebouncing = isDebouncing,
                    sampleCount = sampleCount,
                )
            }
        }
    }

    private fun startTimerJob(startRealtimeMs: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val currentElapsed = (timeSource.elapsedRealtime() - startRealtimeMs).coerceAtLeast(0L)
                updateElapsed(currentElapsed)
            }
        }
    }

    private fun startSampleCollectorJob(sessionId: String) {
        sampleCollectorJob?.cancel()
        sampleCollectorJob = viewModelScope.launch {
            samplingRepository.sampleStream
                .filter { it.sessionId == sessionId }
                .collect { sample ->
                    val transition = detector?.onSample(sample)
                    if (transition != null) {
                        completedTransitions.add(0, transition) // newest first
                    }
                    updateSampleFields(sample)
                }
        }
    }

    private fun updateElapsed(elapsedMs: Long) {
        val current = _uiState.value
        if (current is LiveSessionUiState.Active) {
            _uiState.value = current.copy(elapsedMs = elapsedMs)
        }
    }

    private fun updateSampleFields(sample: BatterySample) {
        val current = _uiState.value
        if (current is LiveSessionUiState.Active) {
            _uiState.value = current.copy(
                currentPercent = sample.percent,
                voltageMv = sample.voltageMv,
                currentNowUa = sample.currentNowUa,
                temperatureDeciC = sample.temperatureDeciC,
                derivedPowerUw = sample.derivedPowerUw,
                qualityFlags = sample.qualityFlags,
                completedTransitions = completedTransitions.toList(),
            )
        }
    }

    private fun stopJobs() {
        timerJob?.cancel()
        timerJob = null
        sampleCollectorJob?.cancel()
        sampleCollectorJob = null
    }

    public override fun onCleared() {
        super.onCleared()
        stopJobs()
    }
}
