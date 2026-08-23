package com.example.chargetrack.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.data.sampling.SamplingRepository
import com.example.chargetrack.data.session.ChargingSessionRepository
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
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
 * ViewModel driving the read-only Live Charging Session screen and Standard Test progress.
 *
 * ## Invariants
 * - Pure consumer of [ChargingSessionRepository] and [SamplingRepository].
 * - Delegates background measurement lifecycle to [MeasurementServiceController].
 * - Tracks benchmark arming vs active state and edge-triggered target arrival.
 * - Anchors benchmark boundaries to exact [BatterySample.elapsedMs] values.
 * - 1-second monotonic timer updates only elapsed duration during active sessions.
 * - Freezes elapsed duration immediately upon session completion.
 */
@HiltViewModel
class LiveSessionViewModel @Inject constructor(
    private val sessionRepository: ChargingSessionRepository,
    private val samplingRepository: SamplingRepository,
    private val database: AppDatabase,
    private val timeSource: TimeSource = DefaultTimeSource(),
    private val serviceController: MeasurementServiceController? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LiveSessionUiState>(LiveSessionUiState.NoSession)
    val uiState: StateFlow<LiveSessionUiState> = _uiState.asStateFlow()

    private var activeSessionId: String? = null
    private var detector: ChargeTransitionDetector? = null
    private val completedTransitions = mutableListOf<ChargeTransition>()

    private var activeStandardTest: StandardTestEntity? = null
    private var benchmarkStartedElapsedMs: Long? = null
    private var benchmarkEndedElapsedMs: Long? = null
    private var hasReachedTarget: Boolean = false
    private var targetReachedDialogDismissed: Boolean = false

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

    /**
     * User dismissed the target arrival dialog to continue recording up to 100%.
     */
    fun dismissTargetReachedDialog() {
        targetReachedDialogDismissed = true
        val current = _uiState.value
        if (current is LiveSessionUiState.Active) {
            _uiState.value = current.copy(showTargetReachedDialog = false)
        }
    }

    private fun handleSessionState(state: SessionState) {
        when (state) {
            is SessionState.Idle -> {
                stopJobs()
                detector = null
                activeSessionId = null
                activeStandardTest = null
                benchmarkStartedElapsedMs = null
                benchmarkEndedElapsedMs = null
                hasReachedTarget = false
                targetReachedDialogDismissed = false
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
                    isStandardTest = state.session.testType == TestType.STANDARD,
                )
            }

            is SessionState.UnpluggedPending -> {
                ensureSessionActive(
                    sessionId = state.activeState.session.id,
                    startPercent = state.activeState.session.startPercent,
                    startRealtimeMs = state.activeState.startRealtimeMs,
                    isDebouncing = true,
                    sampleCount = state.activeState.sampleCount,
                    isStandardTest = state.activeState.session.testType == TestType.STANDARD,
                )
            }

            is SessionState.Completed -> {
                stopJobs()
                val partialInfo = detector?.onSessionEnd()
                val finalTransitions = completedTransitions.toList()
                val stdInfo = buildStandardTestProgressInfo(state.session.startPercent)

                _uiState.value = LiveSessionUiState.SessionEnded(
                    session = state.session,
                    durationMs = state.durationMs,
                    sampleCount = state.sampleCount,
                    endReason = state.session.endReason ?: SessionEndReason.UNKNOWN,
                    completedTransitions = finalTransitions,
                    partialTransitionInfo = partialInfo,
                    standardTestInfo = stdInfo,
                )

                detector = null
                activeSessionId = null
                activeStandardTest = null
                benchmarkStartedElapsedMs = null
                benchmarkEndedElapsedMs = null
                hasReachedTarget = false
                targetReachedDialogDismissed = false
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
        isStandardTest: Boolean,
    ) {
        if (activeSessionId != sessionId) {
            stopJobs()
            completedTransitions.clear()
            detector = ChargeTransitionDetector(sessionId)
            activeSessionId = sessionId
            activeStandardTest = null
            benchmarkStartedElapsedMs = null
            benchmarkEndedElapsedMs = null
            hasReachedTarget = false
            targetReachedDialogDismissed = false

            // Ensure background service is launched for active session
            serviceController?.startService(sessionId, startRealtimeMs)

            val initialElapsed = (timeSource.elapsedRealtime() - startRealtimeMs).coerceAtLeast(0L)
            val latest = samplingRepository.latestSample.value

            // Load StandardTestEntity if applicable
            if (isStandardTest) {
                viewModelScope.launch {
                    activeStandardTest = database.standardTestDao().getForSession(sessionId)
                    activeStandardTest?.let { test ->
                        benchmarkStartedElapsedMs = test.benchmarkStartedElapsedMs
                        benchmarkEndedElapsedMs = test.benchmarkEndedElapsedMs
                        if (benchmarkEndedElapsedMs != null) {
                            hasReachedTarget = true
                        }
                    }
                }
            }

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
                standardTestInfo = buildStandardTestProgressInfo(latest?.percent ?: startPercent),
                showTargetReachedDialog = false,
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
                    processStandardTestBoundaries(sessionId, sample)
                    updateSampleFields(sample)
                }
        }
    }

    private fun processStandardTestBoundaries(sessionId: String, sample: BatterySample) {
        val test = activeStandardTest ?: return
        val currentPct = sample.percent ?: return

        // 1. Arming check -> Activate benchmark at targetStartPercent
        if (currentPct >= test.targetStartPercent && benchmarkStartedElapsedMs == null) {
            benchmarkStartedElapsedMs = sample.elapsedMs
            viewModelScope.launch {
                database.standardTestDao().updateBenchmarkStart(sessionId, sample.elapsedMs)
            }
        }

        // 2. Target check -> One-way edge trigger at targetEndPercent
        if (currentPct >= test.targetEndPercent && !hasReachedTarget) {
            hasReachedTarget = true
            benchmarkEndedElapsedMs = sample.elapsedMs
            viewModelScope.launch {
                database.standardTestDao().updateBenchmarkEnd(sessionId, sample.elapsedMs)
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
            val showDialog = hasReachedTarget && !targetReachedDialogDismissed
            val stdInfo = buildStandardTestProgressInfo(sample.percent)

            _uiState.value = current.copy(
                currentPercent = sample.percent,
                voltageMv = sample.voltageMv,
                currentNowUa = sample.currentNowUa,
                temperatureDeciC = sample.temperatureDeciC,
                derivedPowerUw = sample.derivedPowerUw,
                qualityFlags = sample.qualityFlags,
                completedTransitions = completedTransitions.toList(),
                standardTestInfo = stdInfo,
                showTargetReachedDialog = showDialog,
            )
        }
    }

    private fun buildStandardTestProgressInfo(currentPercent: Int?): StandardTestProgressInfo? {
        val test = activeStandardTest ?: return null
        val pct = currentPercent ?: 0
        val isArmed = pct < test.targetStartPercent && benchmarkStartedElapsedMs == null
        val isBenchmarkActive = !isArmed && !hasReachedTarget

        return StandardTestProgressInfo(
            targetStartPercent = test.targetStartPercent,
            targetEndPercent = test.targetEndPercent,
            isArmed = isArmed,
            isBenchmarkActive = isBenchmarkActive,
            isTargetReached = hasReachedTarget,
            benchmarkStartedElapsedMs = benchmarkStartedElapsedMs,
            benchmarkEndedElapsedMs = benchmarkEndedElapsedMs,
        )
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
