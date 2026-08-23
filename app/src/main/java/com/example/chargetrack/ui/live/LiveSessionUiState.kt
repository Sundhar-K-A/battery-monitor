package com.example.chargetrack.ui.live

import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.transition.PartialTransitionInfo

/**
 * Immutable UI state hierarchy for the Live Charging Session screen.
 */
sealed interface LiveSessionUiState {

    /**
     * No charging session is currently in progress.
     */
    data object NoSession : LiveSessionUiState

    /**
     * An active charging session is underway (or temporarily debouncing a transient unplug).
     *
     * @property sessionId The unique ID of the active charging session.
     * @property startPercent Battery percentage when the session started.
     * @property elapsedMs Monotonic elapsed milliseconds from session start (updated ~1x/sec).
     * @property isDebouncing True if currently in [com.example.chargetrack.domain.session.SessionState.UnpluggedPending].
     * @property currentPercent Latest battery percentage observation, or null if unavailable.
     * @property voltageMv Latest battery voltage in millivolts, or null if unavailable.
     * @property currentNowUa Latest battery current in microamperes, or null if unavailable.
     * @property temperatureDeciC Latest battery temperature in tenths of a °C, or null if unavailable.
     * @property derivedPowerUw Latest estimated battery-side power in microwatts, or null if unavailable.
     * @property qualityFlags Latest measurement quality flags.
     * @property sampleCount Total number of actual raw measurement samples collected in this session.
     * @property completedTransitions List of integer percentage transitions completed so far (newest first).
     */
    data class Active(
        val sessionId: String,
        val startPercent: Int?,
        val elapsedMs: Long,
        val isDebouncing: Boolean = false,
        val currentPercent: Int? = null,
        val voltageMv: Int? = null,
        val currentNowUa: Int? = null,
        val temperatureDeciC: Int? = null,
        val derivedPowerUw: Long? = null,
        val qualityFlags: Set<QualityFlag> = emptySet(),
        val sampleCount: Int = 1,
        val completedTransitions: List<ChargeTransition> = emptyList(),
    ) : LiveSessionUiState

    /**
     * The charging session has ended and finalized.
     *
     * @property session The finalized charging session domain model.
     * @property durationMs Total monotonic duration of the session in milliseconds.
     * @property sampleCount Total number of raw measurement samples captured during the session.
     * @property endReason The explicit reason why the session ended.
     * @property completedTransitions All completed transitions recorded during the session.
     * @property partialTransitionInfo Partial/incomplete transition state at the moment session ended, if any.
     */
    data class SessionEnded(
        val session: ChargingSession,
        val durationMs: Long,
        val sampleCount: Int,
        val endReason: SessionEndReason,
        val completedTransitions: List<ChargeTransition> = emptyList(),
        val partialTransitionInfo: PartialTransitionInfo? = null,
    ) : LiveSessionUiState
}
