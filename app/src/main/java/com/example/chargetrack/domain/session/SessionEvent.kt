package com.example.chargetrack.domain.session

import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.SoftwareSnapshot
import java.time.Instant

/**
 * Events processed by the [SessionStateMachine].
 */
sealed class SessionEvent {

    /**
     * Explicit or auto request to start a charging session.
     */
    data class StartSession(
        val snapshot: BatterySnapshot,
        val setup: ChargingSetup,
        val softwareSnapshot: SoftwareSnapshot,
        val testType: TestType = TestType.FREE_FORM,
        val userNotes: String? = null,
    ) : SessionEvent()

    /**
     * Inflow measurement observation from the battery subsystem.
     */
    data class BatteryTick(
        val snapshot: BatterySnapshot,
    ) : SessionEvent()

    /**
     * Clock/timer tick for gap timeout or debounce expiry evaluation.
     */
    data class TimeTick(
        val currentTime: Instant,
        val elapsedRealtimeMs: Long,
    ) : SessionEvent()

    /**
     * User requested manual termination of the active charging session.
     */
    data class UserStop(
        val currentTime: Instant? = null,
        val elapsedRealtimeMs: Long? = null,
    ) : SessionEvent()

    /**
     * System detected an in-flight session after device boot.
     */
    data class DeviceRebootDetected(
        val currentTime: Instant? = null,
    ) : SessionEvent()

    /**
     * Resets a completed session back to [SessionState.Idle].
     */
    data object Reset : SessionEvent()
}
