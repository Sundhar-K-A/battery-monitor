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
import com.example.chargetrack.domain.time.TimeSource
import kotlinx.coroutines.flow.first
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

    private lateinit var database: AppDatabase
    private lateinit var timeSource: TestTimeSource
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        timeSource = TestTimeSource()
        repository = ChargingSessionRepository(
            database = database,
            config = SessionConfig(unplugDebounceMs = 5_000L),
            timeSource = timeSource,
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

    // ── Reboot recovery test ─────────────────────────────────────────────────
    @Test
    fun `reboot recovery finalizes abandoned in-flight session with DEVICE_RESTARTED`() = runTest {
        // Start a session
        val startResult = repository.startSession(
            snapshot = createChargingSnapshot(percent = 20),
            setup = templateSetup,
            softwareSnapshot = softwareSnapshot,
        )
        val session = startResult.getOrThrow()

        // Simulate app restart / device reboot by creating new repository instance over same DB
        val rebootedTimeSource = TestTimeSource(
            currentInstant = Instant.parse("2026-08-23T11:00:00Z"),
            currentRealtimeMs = 10_000L, // New monotonic timeline after reboot
        )
        val newRepository = ChargingSessionRepository(
            database = database,
            config = SessionConfig(),
            timeSource = rebootedTimeSource,
        )

        // Recover abandoned session
        val recovered = newRepository.recoverOrFinalizeRebootedSession()
        assertNotNull(recovered)
        assertEquals(session.id, recovered?.id)
        assertEquals(SessionEndReason.DEVICE_RESTARTED, recovered?.endReason)

        // Verify database is cleaned up (no active sessions left)
        val activeInDb = database.chargingSessionDao().getActiveSession()
        assertNull(activeInDb)
    }
}
