package com.example.chargetrack.domain.session

import android.os.BatteryManager
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.time.TimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SessionStateMachineTest {

    private class TestTimeSource(
        var currentInstant: Instant = Instant.parse("2026-08-23T10:00:00Z"),
        var currentRealtimeMs: Long = 100_000L,
    ) : TimeSource {
        override fun now(): Instant = currentInstant
        override fun elapsedRealtime(): Long = currentRealtimeMs

        fun advanceTime(seconds: Long) {
            currentInstant = currentInstant.plusSeconds(seconds)
            currentRealtimeMs += (seconds * 1000L)
        }

        fun advanceMillis(millis: Long) {
            currentInstant = currentInstant.plusMillis(millis)
            currentRealtimeMs += millis
        }
    }

    private lateinit var timeSource: TestTimeSource
    private lateinit var config: SessionConfig
    private lateinit var stateMachine: SessionStateMachine

    private val defaultSetup = ChargingSetup(
        id = "setup-100w",
        chargerBrand = "iQOO",
        chargerModel = "FlashCharge 100W",
        advertisedWattageW = 100,
        protocol = "FlashCharge",
        isOfficialCharger = true,
        cableBrand = "iQOO",
        cableModel = "Stock Type-C",
        isOfficialCable = true,
        chargingType = ChargingType.WIRED,
        chargingMode = ChargingMode.FLASH_CHARGE,
        createdAt = Instant.parse("2026-08-23T10:00:00Z"),
    )

    private val defaultSoftwareSnapshot = SoftwareSnapshot(
        id = "software-1",
        capturedAt = Instant.parse("2026-08-23T10:00:00Z"),
        androidVersion = "16",
        sdkInt = 36,
        originOsVersion = "OriginOS 6",
        buildFingerprint = "vivo/iQOO15/iQOO15:16/...",
        appVersionName = "1.0",
        appVersionCode = 1,
    )

    private fun createSnapshot(
        percent: Int? = 20,
        pluggedType: Int? = BatteryManager.BATTERY_PLUGGED_AC,
        batteryStatus: Int? = BatteryManager.BATTERY_STATUS_CHARGING,
        voltageMv: Int? = 4050,
        currentNowUa: Int? = 15_000_000,
        temperatureDeciC: Int? = 300,
    ): BatterySnapshot = BatterySnapshot(
        timestamp = timeSource.now(),
        percent = percent,
        voltageMv = voltageMv,
        currentNowUa = currentNowUa,
        currentAverageUa = null,
        chargeCounterUah = null,
        energyCounterNwh = null,
        temperatureDeciC = temperatureDeciC,
        batteryStatus = batteryStatus,
        pluggedType = pluggedType,
        cycleCount = null,
        qualityFlags = emptySet(),
    )

    @Before
    fun setUp() {
        timeSource = TestTimeSource()
        config = SessionConfig(
            expectedSampleIntervalMs = 5_000L,
            measurementGapTimeoutMs = 30_000L,
            unplugDebounceMs = 5_000L,
        )
        stateMachine = SessionStateMachine(config, timeSource)
    }

    // ── Test 1: Idle -> Active on confirmed charging ─────────────────────────
    @Test
    fun `1 - Idle to Active on confirmed charging`() {
        val initialSnapshot = createSnapshot(percent = 20)
        val event = SessionEvent.StartSession(
            snapshot = initialSnapshot,
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
            testType = TestType.STANDARD,
        )

        val nextState = stateMachine.transition(SessionState.Idle, event)
        assertTrue(nextState is SessionState.Active)
        val active = nextState as SessionState.Active
        assertEquals(20, active.session.startPercent)
        assertEquals(20, active.lastObservedPercent)
        assertEquals(100_000L, active.startRealtimeMs)
        assertEquals(1, active.sampleCount)
        assertTrue(active.setupSnapshot.isFrozen)
    }

    // ── Test 2: Active -> Active on normal BatteryTick ───────────────────────
    @Test
    fun `2 - Active to Active on normal BatteryTick`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        timeSource.advanceTime(5)
        val tickSnapshot = createSnapshot(percent = 21)
        state = stateMachine.transition(state, SessionEvent.BatteryTick(tickSnapshot))

        assertTrue(state is SessionState.Active)
        val active = state as SessionState.Active
        assertEquals(21, active.lastObservedPercent)
        assertEquals(2, active.sampleCount)
        assertEquals(105_000L, active.lastSampleRealtimeMs)
    }

    // ── Test 3: Active -> UnpluggedPending on transient unplug ───────────────
    @Test
    fun `3 - Active to UnpluggedPending on transient unplug`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        timeSource.advanceTime(5)
        val unpluggedSnapshot = createSnapshot(percent = 21, pluggedType = 0, batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING)
        state = stateMachine.transition(state, SessionEvent.BatteryTick(unpluggedSnapshot))

        assertTrue(state is SessionState.UnpluggedPending)
        val pending = state as SessionState.UnpluggedPending
        assertEquals(105_000L, pending.unpluggedAtRealtimeMs)
    }

    // ── Test 4: UnpluggedPending -> Active when charging resumes within debounce ──
    @Test
    fun `4 - UnpluggedPending to Active when charging resumes within debounce`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        // Unplug at +5s
        timeSource.advanceTime(5)
        val unpluggedSnapshot = createSnapshot(percent = 21, pluggedType = 0, batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING)
        state = stateMachine.transition(state, SessionEvent.BatteryTick(unpluggedSnapshot))
        assertTrue(state is SessionState.UnpluggedPending)

        // Resume charging at +2s later (total 2s after unplug < 5s debounce)
        timeSource.advanceTime(2)
        val resumeSnapshot = createSnapshot(percent = 21, pluggedType = BatteryManager.BATTERY_PLUGGED_AC, batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING)
        state = stateMachine.transition(state, SessionEvent.BatteryTick(resumeSnapshot))

        assertTrue(state is SessionState.Active)
        val active = state as SessionState.Active
        assertEquals(107_000L, active.lastSampleRealtimeMs)
        assertEquals(2, active.sampleCount) // Resumed existing session
    }

    // ── Test 5: UnpluggedPending -> Completed(UNPLUGGED) after debounce timeout ──
    @Test
    fun `5 - UnpluggedPending to Completed(UNPLUGGED) after debounce timeout`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        // Unplug at +5s
        timeSource.advanceTime(5)
        val unpluggedSnapshot = createSnapshot(percent = 21, pluggedType = 0, batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING)
        state = stateMachine.transition(state, SessionEvent.BatteryTick(unpluggedSnapshot))
        assertTrue(state is SessionState.UnpluggedPending)

        // Advance 5s (debounce expires)
        timeSource.advanceTime(5)
        state = stateMachine.transition(state, SessionEvent.TimeTick(timeSource.now(), timeSource.elapsedRealtime()))

        assertTrue(state is SessionState.Completed)
        val completed = state as SessionState.Completed
        assertEquals(SessionEndReason.UNPLUGGED, completed.session.endReason)
        assertEquals(5_000L, completed.durationMs)
        assertEquals(21, completed.session.endPercent)
    }

    // ── Test 6: Active -> Completed(USER_STOPPED) ────────────────────────────
    @Test
    fun `6 - Active to Completed(USER_STOPPED)`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        timeSource.advanceTime(10)
        state = stateMachine.transition(state, SessionEvent.UserStop(timeSource.now(), timeSource.elapsedRealtime()))

        assertTrue(state is SessionState.Completed)
        val completed = state as SessionState.Completed
        assertEquals(SessionEndReason.USER_STOPPED, completed.session.endReason)
        assertEquals(10_000L, completed.durationMs)
    }

    // ── Test 7: Active -> Completed(CHARGING_STOPPED) when charging genuinely stops ──
    @Test
    fun `7 - Active to Completed(CHARGING_STOPPED) when charging genuinely stops`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        timeSource.advanceTime(15)
        val stoppedSnapshot = createSnapshot(
            percent = 85,
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
        )
        state = stateMachine.transition(state, SessionEvent.BatteryTick(stoppedSnapshot))

        assertTrue(state is SessionState.Completed)
        val completed = state as SessionState.Completed
        assertEquals(SessionEndReason.CHARGING_STOPPED, completed.session.endReason)
        assertEquals(85, completed.session.endPercent)
        assertEquals(15_000L, completed.durationMs)
    }

    // ── Test 8: ChargingStopped must NOT imply endPercent == 100 ─────────────
    @Test
    fun `8 - ChargingStopped must NOT imply endPercent == 100`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 40),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        timeSource.advanceTime(10)
        val stoppedAt65 = createSnapshot(
            percent = 65, // Charging stopped at 65% (not 100%)
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
        )
        state = stateMachine.transition(state, SessionEvent.BatteryTick(stoppedAt65))

        assertTrue(state is SessionState.Completed)
        val completed = state as SessionState.Completed
        assertEquals(SessionEndReason.CHARGING_STOPPED, completed.session.endReason)
        assertEquals(65, completed.session.endPercent) // Preserves actual 65% observation
    }

    // ── Test 9: Active -> Completed(MEASUREMENT_LOST) after configured gap ────
    @Test
    fun `9 - Active to Completed(MEASUREMENT_LOST) after configured measurement gap`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        // Advance 35 seconds (> 30s measurementGapTimeoutMs) without any samples
        timeSource.advanceTime(35)
        state = stateMachine.transition(state, SessionEvent.TimeTick(timeSource.now(), timeSource.elapsedRealtime()))

        assertTrue(state is SessionState.Completed)
        val completed = state as SessionState.Completed
        assertEquals(SessionEndReason.MEASUREMENT_LOST, completed.session.endReason)
    }

    // ── Test 10: Active -> Completed(DEVICE_RESTARTED) ───────────────────────
    @Test
    fun `10 - Active to Completed(DEVICE_RESTARTED)`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        state = stateMachine.transition(state, SessionEvent.DeviceRebootDetected(timeSource.now()))

        assertTrue(state is SessionState.Completed)
        val completed = state as SessionState.Completed
        assertEquals(SessionEndReason.DEVICE_RESTARTED, completed.session.endReason)
        assertEquals(0L, completed.durationMs) // 0L because elapsedRealtime cannot cross reboot
    }

    // ── Test 11: Missing battery fields do not unnecessarily terminate a session ──
    @Test
    fun `11 - Missing battery fields do not unnecessarily terminate a session`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        // Tick with null voltage, current, and percent
        timeSource.advanceTime(5)
        val sparseSnapshot = createSnapshot(
            percent = null,
            voltageMv = null,
            currentNowUa = null,
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
        )
        state = stateMachine.transition(state, SessionEvent.BatteryTick(sparseSnapshot))

        // Still active!
        assertTrue(state is SessionState.Active)
        val active = state as SessionState.Active
        assertEquals(20, active.lastObservedPercent) // Retains previous observed percent
        assertEquals(2, active.sampleCount)
    }

    // ── Test 12: Duplicate lifecycle events do not create multiple sessions ──
    @Test
    fun `12 - Duplicate lifecycle events do not create multiple sessions`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        val state1 = stateMachine.transition(SessionState.Idle, startEvent)
        val originalSessionId = (state1 as SessionState.Active).session.id

        // Attempting to send StartSession while already active is ignored
        val state2 = stateMachine.transition(state1, startEvent)
        assertTrue(state2 is SessionState.Active)
        assertEquals(originalSessionId, (state2 as SessionState.Active).session.id)
    }

    // ── Test 15: Monotonic elapsed timing remains correct ───────────────────
    @Test
    fun `15 - Monotonic elapsed timing remains correct`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        timeSource.advanceTime(123)
        state = stateMachine.transition(state, SessionEvent.UserStop(timeSource.now(), timeSource.elapsedRealtime()))

        assertTrue(state is SessionState.Completed)
        val completed = state as SessionState.Completed
        assertEquals(123_000L, completed.durationMs)
    }

    // ── Test 16: Reboot never carries elapsedRealtime across sessions ───────
    @Test
    fun `16 - Reboot never carries elapsedRealtime across sessions`() {
        val startEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 20),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        var state = stateMachine.transition(SessionState.Idle, startEvent)

        // Session ended via reboot
        state = stateMachine.transition(state, SessionEvent.DeviceRebootDetected())
        assertTrue(state is SessionState.Completed)
        assertEquals(SessionEndReason.DEVICE_RESTARTED, (state as SessionState.Completed).session.endReason)

        // Reset to Idle
        state = stateMachine.transition(state, SessionEvent.Reset)
        assertTrue(state is SessionState.Idle)

        // Device rebooted -> new monotonic baseline starting at 5,000ms
        timeSource.currentRealtimeMs = 5_000L
        val newSessionEvent = SessionEvent.StartSession(
            snapshot = createSnapshot(percent = 30),
            setup = defaultSetup,
            softwareSnapshot = defaultSoftwareSnapshot,
        )
        val newState = stateMachine.transition(state, newSessionEvent)
        assertTrue(newState is SessionState.Active)
        val newActive = newState as SessionState.Active
        assertEquals(5_000L, newActive.startRealtimeMs)
    }
}
