package com.example.chargetrack.domain.session

import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.SoftwareSnapshot

/**
 * Conceptual states of the charging session state machine.
 *
 * Transitions:
 * - [Idle] -> [Active]: On confirmed charging start.
 * - [Active] -> [Active]: On normal battery ticks while charging.
 * - [Active] -> [UnpluggedPending]: On transient unplug detection.
 * - [UnpluggedPending] -> [Active]: When charging resumes within debounce interval.
 * - [UnpluggedPending] -> [Completed]: When unplug persists beyond debounce interval ([com.example.chargetrack.domain.enums.SessionEndReason.UNPLUGGED]).
 * - [Active] -> [Completed]: On [com.example.chargetrack.domain.enums.SessionEndReason.CHARGING_STOPPED], [com.example.chargetrack.domain.enums.SessionEndReason.USER_STOPPED], [com.example.chargetrack.domain.enums.SessionEndReason.MEASUREMENT_LOST], or [com.example.chargetrack.domain.enums.SessionEndReason.DEVICE_RESTARTED].
 * - [Completed] -> [Idle]: When acknowledged/reset.
 */
sealed class SessionState {

    /** No active charging session is currently in progress. */
    data object Idle : SessionState()

    /**
     * An active charging session is underway.
     *
     * @property session The in-progress session domain model.
     * @property setupSnapshot Immutable charger/cable configuration captured at session start.
     * @property softwareSnapshot Immutable OS/app snapshot captured at session start.
     * @property startRealtimeMs Monotonic elapsedRealtime at session start.
     * @property lastSampleRealtimeMs Monotonic elapsedRealtime of the most recent valid sample.
     * @property lastObservedPercent Most recent battery percent observation.
     * @property sampleCount Total number of measurement samples collected so far.
     */
    data class Active(
        val session: ChargingSession,
        val setupSnapshot: ChargingSetup,
        val softwareSnapshot: SoftwareSnapshot,
        val startRealtimeMs: Long,
        val lastSampleRealtimeMs: Long,
        val lastObservedPercent: Int?,
        val sampleCount: Int = 1,
    ) : SessionState()

    /**
     * Transient disconnect state. The cable was unplugged, but the session is given a configurable
     * grace period ([SessionConfig.unplugDebounceMs]) to resume before being finalized.
     *
     * @property activeState The underlying active session state prior to the unplug event.
     * @property unpluggedAtRealtimeMs Monotonic elapsedRealtime when unplug was first detected.
     * @property lastSnapshot The snapshot that triggered the unplug state.
     */
    data class UnpluggedPending(
        val activeState: Active,
        val unpluggedAtRealtimeMs: Long,
        val lastSnapshot: BatterySnapshot,
    ) : SessionState()

    /**
     * The charging session has concluded and has been finalized with an explicit end reason.
     *
     * @property session The finalized session domain model.
     * @property durationMs Total session duration computed strictly from monotonic elapsedRealtime.
     * @property sampleCount Total number of measurement samples recorded during the session.
     */
    data class Completed(
        val session: ChargingSession,
        val durationMs: Long,
        val sampleCount: Int,
    ) : SessionState()
}
