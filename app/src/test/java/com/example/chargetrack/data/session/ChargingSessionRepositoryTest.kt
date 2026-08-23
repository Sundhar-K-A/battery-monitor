package com.example.chargetrack.data.session

import android.content.Context
import android.os.BatteryManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.session.SessionConfig
import com.example.chargetrack.domain.session.SessionState
import com.example.chargetrack.domain.time.BootInfoProvider
import com.example.chargetrack.domain.time.TimeSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ChargingSessionRepositoryTest {

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
    }

    private class FakeBootInfoProvider(var currentBootId: String = "boot-1") : BootInfoProvider {
        override fun getBootId(): String = currentBootId
    }

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var timeSource: TestTimeSource
    private lateinit var bootInfoProvider: FakeBootInfoProvider
    private lateinit var repository: ChargingSessionRepository

    private val templateSetup = ChargingSetup(
        id = "template-official-100w",
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

    private val softwareSnapshot = SoftwareSnapshot(
        id = "snapshot-1",
        capturedAt = Instant.parse("2026-08-23T10:00:00Z"),
        androidVersion = "16",
        sdkInt = 36,
        originOsVersion = "OriginOS 6",
        buildFingerprint = "vivo/iQOO15/iQOO15:16/...",
        appVersionName = "1.0",
        appVersionCode = 1,
    )

    private fun createChargingSnapshot(percent: Int = 20): BatterySnapshot = BatterySnapshot(
        timestamp = timeSource.now(),
        percent = percent,
        voltageMv = 4050,
        currentNowUa = 15_000_000,
        currentAverageUa = null,
        chargeCounterUah = null,
        energyCounterNwh = null,
        temperatureDeciC = 300,
        batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
        pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
        cycleCount = null,
        qualityFlags = emptySet(),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        timeSource = TestTimeSource()
        bootInfoProvider = FakeBootInfoProvider("boot-session-1")
        repository = ChargingSessionRepository(
            database = database,
            config = SessionConfig(unplugDebounceMs = 5_000L),
            timeSource = timeSource,
            bootInfoProvider = bootInfoProvider,
            context = context,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Test 13: Session start is atomic ─────────────────────────────────────
    @Test
    fun `13 - Session start is atomic across software snapshot, setup snapshot, session, and standard test`() = runTest {
        val result = repository.startSession(
            snapshot = createChargingSnapshot(percent = 20),
            setup = templateSetup,
            softwareSnapshot = softwareSnapshot,
            testType = TestType.STANDARD,
            userNotes = "Bench test 100W",
            comparisonGroupKey = "standard_20_80_wired_official",
        )

        assertTrue(result.isSuccess)
        val session = result.getOrThrow()

        // 1. Verify SoftwareSnapshot in Room
        val dbSnapshot = database.softwareSnapshotDao().getById("snapshot-1")
        assertNotNull(dbSnapshot)
        assertEquals("16", dbSnapshot?.androidVersion)

        // 2. Verify immutable Setup Snapshot in Room
        val dbSetup = database.chargingSetupDao().getById(session.chargingSetupId)
        assertNotNull(dbSetup)
        assertEquals("iQOO", dbSetup?.chargerBrand)
        assertEquals(100, dbSetup?.advertisedWattageW)
        assertFalse(dbSetup?.isTemplate == true) // Snapshot is not a template

        // 3. Verify Session in Room
        val dbSession = database.chargingSessionDao().getById(session.id)
        assertNotNull(dbSession)
        assertEquals(20, dbSession?.startPercent)
        assertEquals(TestType.STANDARD, dbSession?.testType)
        assertEquals("Bench test 100W", dbSession?.userNotes)
        assertNull(dbSession?.endedAt)

        // 4. Verify StandardTest in Room
        val dbStandardTest = database.standardTestDao().getForSession(session.id)
        assertNotNull(dbStandardTest)
        assertEquals("standard_20_80_wired_official", dbStandardTest?.comparisonGroupKey)

        // 5. Verify Repository StateFlow
        val state = repository.sessionState.value
        assertTrue(state is SessionState.Active)
    }

    // ── Test 14: Session finalization is atomic ──────────────────────────────
    @Test
    fun `14 - Session finalization is atomic`() = runTest {
        val startResult = repository.startSession(
            snapshot = createChargingSnapshot(percent = 20),
            setup = templateSetup,
            softwareSnapshot = softwareSnapshot,
            testType = TestType.FREE_FORM,
        )
        val session = startResult.getOrThrow()

        timeSource.advanceTime(20)
        repository.stopSession()

        val finalState = repository.sessionState.value
        assertTrue(finalState is SessionState.Completed)
        val completed = finalState as SessionState.Completed
        assertEquals(SessionEndReason.USER_STOPPED, completed.session.endReason)

        // Verify Room database is finalized atomically
        val dbSession = database.chargingSessionDao().getById(session.id)
        assertNotNull(dbSession)
        assertEquals(SessionEndReason.USER_STOPPED, dbSession?.endReason)
        assertEquals(timeSource.now(), dbSession?.endedAt)
    }

    // ── Reboot vs Process Loss Recovery Tests ─────────────────────────────────

    @Test
    fun `evaluateOrphanedSessionEndReason returns DEVICE_RESTARTED when boot IDs mismatch`() {
        val reason = ChargingSessionRepository.evaluateOrphanedSessionEndReason(
            sessionStartBootId = "boot-old-123",
            sessionStartRealtimeMs = 50_000L,
            currentBootId = "boot-new-456",
            currentElapsedRealtimeMs = 20_000L,
        )
        assertEquals(SessionEndReason.DEVICE_RESTARTED, reason)
    }

    @Test
    fun `evaluateOrphanedSessionEndReason returns DEVICE_RESTARTED when monotonic clock is reset`() {
        val reason = ChargingSessionRepository.evaluateOrphanedSessionEndReason(
            sessionStartBootId = null, // boot ID unavailable
            sessionStartRealtimeMs = 100_000L,
            currentBootId = null,
            currentElapsedRealtimeMs = 15_000L, // clock reset to 15s after reboot
        )
        assertEquals(SessionEndReason.DEVICE_RESTARTED, reason)
    }

    @Test
    fun `evaluateOrphanedSessionEndReason returns MEASUREMENT_LOST when same boot and clock advanced`() {
        val reason = ChargingSessionRepository.evaluateOrphanedSessionEndReason(
            sessionStartBootId = "boot-same-123",
            sessionStartRealtimeMs = 50_000L,
            currentBootId = "boot-same-123",
            currentElapsedRealtimeMs = 80_000L, // same boot, clock advanced
        )
        assertEquals(SessionEndReason.MEASUREMENT_LOST, reason)
    }

    @Test
    fun `recoverOrFinalizeOrphanedSession assigns DEVICE_RESTARTED on actual reboot`() = runTest {
        // 1. Start a session on boot-1 with startRealtimeMs = 100_000L
        val startResult = repository.startSession(
            snapshot = createChargingSnapshot(percent = 20),
            setup = templateSetup,
            softwareSnapshot = softwareSnapshot,
        )
        val session = startResult.getOrThrow()

        // 2. Simulate reboot with new bootId and reset monotonic clock
        val rebootedTimeSource = TestTimeSource(
            currentInstant = Instant.parse("2026-08-23T11:00:00Z"),
            currentRealtimeMs = 15_000L, // Reset to 15s
        )
        val rebootedBootProvider = FakeBootInfoProvider("boot-2-after-reboot")
        val recoveryRepository = ChargingSessionRepository(
            database = database,
            config = SessionConfig(),
            timeSource = rebootedTimeSource,
            bootInfoProvider = rebootedBootProvider,
            context = context,
        )

        // 3. Recover session
        val recovered = recoveryRepository.recoverOrFinalizeOrphanedSession()
        assertNotNull(recovered)
        assertEquals(session.id, recovered?.id)
        assertEquals(SessionEndReason.DEVICE_RESTARTED, recovered?.endReason)

        // Database cleaned up
        assertNull(database.chargingSessionDao().getActiveSession())
    }

    @Test
    fun `recoverOrFinalizeOrphanedSession assigns MEASUREMENT_LOST on process death in same boot`() = runTest {
        // 1. Start a session on boot-1 with startRealtimeMs = 100_000L
        val startResult = repository.startSession(
            snapshot = createChargingSnapshot(percent = 20),
            setup = templateSetup,
            softwareSnapshot = softwareSnapshot,
        )
        val session = startResult.getOrThrow()

        // 2. Simulate process death in same boot (same bootId, clock advanced to 150_000L)
        val sameBootTimeSource = TestTimeSource(
            currentInstant = Instant.parse("2026-08-23T10:05:00Z"),
            currentRealtimeMs = 150_000L,
        )
        val sameBootProvider = FakeBootInfoProvider("boot-session-1")
        val recoveryRepository = ChargingSessionRepository(
            database = database,
            config = SessionConfig(),
            timeSource = sameBootTimeSource,
            bootInfoProvider = sameBootProvider,
            context = context,
        )

        // 3. Recover session
        val recovered = recoveryRepository.recoverOrFinalizeOrphanedSession()
        assertNotNull(recovered)
        assertEquals(session.id, recovered?.id)
        assertEquals(SessionEndReason.MEASUREMENT_LOST, recovered?.endReason)

        // Database cleaned up
        assertNull(database.chargingSessionDao().getActiveSession())
    }
}
