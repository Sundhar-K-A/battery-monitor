package com.example.chargetrack.domain.session

import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.time.DefaultTimeSource
import com.example.chargetrack.domain.time.TimeSource
import java.util.UUID

/**
 * Pure, deterministic Kotlin state machine managing charging session lifecycle transitions.
 *
 * Adheres to the following invariants:
 * 1. Session starts ONLY on confirmed charging ([ChargingConditionEvaluator.isConfirmedCharging]).
 * 2. Monotonic duration is calculated strictly from [TimeSource.elapsedRealtime] differences.
 * 3. Transient unplug events transition to [SessionState.UnpluggedPending] and debounce for [SessionConfig.unplugDebounceMs].
 * 4. Resuming charging within the debounce window cleanly returns to [SessionState.Active].
 * 5. Every completed session has an explicit [SessionEndReason].
 * 6. CHARGING_STOPPED does NOT imply 100% battery level.
 * 7. Inactivity exceeding [SessionConfig.measurementGapTimeoutMs] transitions to [SessionEndReason.MEASUREMENT_LOST].
 * 8. Device reboots transition to [SessionEndReason.DEVICE_RESTARTED] and never carry monotonic timestamps across sessions.
 * 9. Duplicate start events while Active are ignored.
 */
class SessionStateMachine(
    private val config: SessionConfig = SessionConfig(),
    private val timeSource: TimeSource = DefaultTimeSource(),
) {

    /**
     * Pure reducer that computes the next [SessionState] given the current state and incoming [SessionEvent].
     */
    fun transition(currentState: SessionState, event: SessionEvent): SessionState {
        return when (currentState) {
            is SessionState.Idle -> handleIdle(currentState, event)
            is SessionState.Active -> handleActive(currentState, event)
            is SessionState.UnpluggedPending -> handleUnpluggedPending(currentState, event)
            is SessionState.Completed -> handleCompleted(currentState, event)
        }
    }

    // ── Idle ─────────────────────────────────────────────────────────────────

    private fun handleIdle(state: SessionState.Idle, event: SessionEvent): SessionState {
        return when (event) {
            is SessionEvent.StartSession -> {
                if (ChargingConditionEvaluator.isConfirmedCharging(event.snapshot)) {
                    val now = timeSource.now()
                    val nowRealtime = timeSource.elapsedRealtime()
                    val session = ChargingSession(
                        id = UUID.randomUUID().toString(),
                        startedAt = now,
                        startPercent = event.snapshot.percent ?: 0,
                        chargingSetupId = event.setup.id,
                        softwareSnapshotId = event.softwareSnapshot.id,
                        testType = event.testType,
                        userNotes = event.userNotes,
                    )
                    SessionState.Active(
                        session = session,
                        setupSnapshot = event.setup.copy(isFrozen = true),
                        softwareSnapshot = event.softwareSnapshot,
                        startRealtimeMs = nowRealtime,
                        lastSampleRealtimeMs = nowRealtime,
                        lastObservedPercent = event.snapshot.percent,
                        sampleCount = 1,
                    )
                } else {
                    state
                }
            }
            else -> state
        }
    }

    // ── Active ───────────────────────────────────────────────────────────────

    private fun handleActive(state: SessionState.Active, event: SessionEvent): SessionState {
        return when (event) {
            is SessionEvent.StartSession -> {
                // Ignore duplicate start requests while already active
                state
            }

            is SessionEvent.BatteryTick -> {
                val snapshot = event.snapshot
                val nowRealtime = timeSource.elapsedRealtime()
                val now = timeSource.now()

                // Check 1: Did we unplug?
                if (!ChargingConditionEvaluator.isPluggedIn(snapshot)) {
                    return SessionState.UnpluggedPending(
                        activeState = state.copy(
                            lastObservedPercent = snapshot.percent ?: state.lastObservedPercent
                        ),
                        unpluggedAtRealtimeMs = nowRealtime,
                        lastSnapshot = snapshot,
                    )
                }

                // Check 2: Did charging genuinely stop while plugged?
                if (ChargingConditionEvaluator.isChargingStoppedWhilePlugged(snapshot)) {
                    val durationMs = (nowRealtime - state.startRealtimeMs).coerceAtLeast(0L)
                    val finalSession = state.session.copy(
                        endedAt = now,
                        endPercent = snapshot.percent ?: state.lastObservedPercent,
                        endReason = SessionEndReason.CHARGING_STOPPED,
                    )
                    return SessionState.Completed(
                        session = finalSession,
                        durationMs = durationMs,
                        sampleCount = state.sampleCount + 1,
                    )
                }

                // Check 3: Check measurement gap timeout
                if ((nowRealtime - state.lastSampleRealtimeMs) > config.measurementGapTimeoutMs) {
                    val durationMs = (state.lastSampleRealtimeMs - state.startRealtimeMs).coerceAtLeast(0L)
                    val finalSession = state.session.copy(
                        endedAt = now,
                        endPercent = state.lastObservedPercent,
                        endReason = SessionEndReason.MEASUREMENT_LOST,
                    )
                    return SessionState.Completed(
                        session = finalSession,
                        durationMs = durationMs,
                        sampleCount = state.sampleCount,
                    )
                }

                // Normal tick (charging continues, missing fields like voltage/current/percent tolerated)
                state.copy(
                    lastSampleRealtimeMs = nowRealtime,
                    lastObservedPercent = snapshot.percent ?: state.lastObservedPercent,
                    sampleCount = state.sampleCount + 1,
                )
            }

            is SessionEvent.TimeTick -> {
                // Check gap timeout
                if ((event.elapsedRealtimeMs - state.lastSampleRealtimeMs) > config.measurementGapTimeoutMs) {
                    val durationMs = (state.lastSampleRealtimeMs - state.startRealtimeMs).coerceAtLeast(0L)
                    val finalSession = state.session.copy(
                        endedAt = event.currentTime,
                        endPercent = state.lastObservedPercent,
                        endReason = SessionEndReason.MEASUREMENT_LOST,
                    )
                    SessionState.Completed(
                        session = finalSession,
                        durationMs = durationMs,
                        sampleCount = state.sampleCount,
                    )
                } else {
                    state
                }
            }

            is SessionEvent.UserStop -> {
                val now = event.currentTime ?: timeSource.now()
                val nowRealtime = event.elapsedRealtimeMs ?: timeSource.elapsedRealtime()
                val durationMs = (nowRealtime - state.startRealtimeMs).coerceAtLeast(0L)
                val finalSession = state.session.copy(
                    endedAt = now,
                    endPercent = state.lastObservedPercent,
                    endReason = SessionEndReason.USER_STOPPED,
                )
                SessionState.Completed(
                    session = finalSession,
                    durationMs = durationMs,
                    sampleCount = state.sampleCount,
                )
            }

            is SessionEvent.DeviceRebootDetected -> {
                val now = event.currentTime ?: timeSource.now()
                val finalSession = state.session.copy(
                    endedAt = now,
                    endPercent = state.lastObservedPercent,
                    endReason = SessionEndReason.DEVICE_RESTARTED,
                )
                SessionState.Completed(
                    session = finalSession,
                    durationMs = 0L, // Monotonic time cannot cross reboot
                    sampleCount = state.sampleCount,
                )
            }

            is SessionEvent.Reset -> state
        }
    }

    // ── UnpluggedPending ─────────────────────────────────────────────────────

    private fun handleUnpluggedPending(
        state: SessionState.UnpluggedPending,
        event: SessionEvent,
    ): SessionState {
        return when (event) {
            is SessionEvent.BatteryTick -> {
                val snapshot = event.snapshot
                val nowRealtime = timeSource.elapsedRealtime()
                val now = timeSource.now()

                if (ChargingConditionEvaluator.isConfirmedCharging(snapshot)) {
                    // Charging resumed within debounce interval!
                    if ((nowRealtime - state.unpluggedAtRealtimeMs) < config.unplugDebounceMs) {
                        state.activeState.copy(
                            lastSampleRealtimeMs = nowRealtime,
                            lastObservedPercent = snapshot.percent ?: state.activeState.lastObservedPercent,
                            sampleCount = state.activeState.sampleCount + 1,
                        )
                    } else {
                        // Debounce expired before resume was observed
                        finalizeUnplugged(state, now, state.unpluggedAtRealtimeMs)
                    }
                } else {
                    // Still unplugged -> check if debounce window has elapsed
                    if ((nowRealtime - state.unpluggedAtRealtimeMs) >= config.unplugDebounceMs) {
                        finalizeUnplugged(state, now, state.unpluggedAtRealtimeMs)
                    } else {
                        state.copy(lastSnapshot = snapshot)
                    }
                }
            }

            is SessionEvent.TimeTick -> {
                if ((event.elapsedRealtimeMs - state.unpluggedAtRealtimeMs) >= config.unplugDebounceMs) {
                    finalizeUnplugged(state, event.currentTime, state.unpluggedAtRealtimeMs)
                } else {
                    state
                }
            }

            is SessionEvent.UserStop -> {
                val now = event.currentTime ?: timeSource.now()
                val nowRealtime = event.elapsedRealtimeMs ?: timeSource.elapsedRealtime()
                val durationMs = (nowRealtime - state.activeState.startRealtimeMs).coerceAtLeast(0L)
                val finalSession = state.activeState.session.copy(
                    endedAt = now,
                    endPercent = state.activeState.lastObservedPercent,
                    endReason = SessionEndReason.USER_STOPPED,
                )
                SessionState.Completed(
                    session = finalSession,
                    durationMs = durationMs,
                    sampleCount = state.activeState.sampleCount,
                )
            }

            is SessionEvent.DeviceRebootDetected -> {
                val now = event.currentTime ?: timeSource.now()
                val finalSession = state.activeState.session.copy(
                    endedAt = now,
                    endPercent = state.activeState.lastObservedPercent,
                    endReason = SessionEndReason.DEVICE_RESTARTED,
                )
                SessionState.Completed(
                    session = finalSession,
                    durationMs = 0L,
                    sampleCount = state.activeState.sampleCount,
                )
            }

            is SessionEvent.StartSession -> state
            is SessionEvent.Reset -> state
        }
    }

    private fun finalizeUnplugged(
        state: SessionState.UnpluggedPending,
        endedAt: java.time.Instant,
        unpluggedRealtimeMs: Long,
    ): SessionState.Completed {
        val durationMs = (unpluggedRealtimeMs - state.activeState.startRealtimeMs).coerceAtLeast(0L)
        val finalSession = state.activeState.session.copy(
            endedAt = endedAt,
            endPercent = state.lastSnapshot.percent ?: state.activeState.lastObservedPercent,
            endReason = SessionEndReason.UNPLUGGED,
        )
        return SessionState.Completed(
            session = finalSession,
            durationMs = durationMs,
            sampleCount = state.activeState.sampleCount,
        )
    }

    // ── Completed ────────────────────────────────────────────────────────────

    private fun handleCompleted(state: SessionState.Completed, event: SessionEvent): SessionState {
        return when (event) {
            is SessionEvent.Reset -> SessionState.Idle
            is SessionEvent.StartSession -> handleIdle(SessionState.Idle, event)
            else -> state
        }
    }
}
