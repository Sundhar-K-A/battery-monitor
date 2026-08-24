package com.example.chargetrack.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var fakeBatteryDataSource: FakeBatteryDataSource
    private lateinit var viewModel: HomeViewModel

    private val now = Instant.now()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        fakeBatteryDataSource = FakeBatteryDataSource(
            BatterySnapshot(
                timestamp = now,
                percent = 65,
                voltageMv = 8100,
                currentNowUa = 4_500_000,
                temperatureDeciC = 295,
            )
        )

        viewModel = HomeViewModel(fakeBatteryDataSource, database, testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `01 - UI state passively observes live battery telemetry and computes estimated power`() = runTest {
        advanceTimeBy(100)
        val state = viewModel.uiState.first { !it.isLoading }

        assertNotNull(state.batterySnapshot)
        assertEquals(65, state.batterySnapshot?.percent)
        assertEquals(8100, state.batterySnapshot?.voltageMv)
        assertEquals(4_500_000, state.batterySnapshot?.currentNowUa)
        assertTrue(state.isCharging)

        // Estimated power: 8100mV * 4500000uA / 1000 = 36_450_000 uW (36.45W)
        assertNotNull(state.estimatedPowerUw)
        assertEquals(36_450_000L, state.estimatedPowerUw)
    }

    @Test
    fun `02 - UI state detects active session in progress without owning its lifecycle`() = runTest {
        // Seed active session in database
        database.chargingSetupDao().insert(
            ChargingSetupEntity(
                id = "setup-1",
                chargerBrand = "iQOO",
                chargerModel = "100W",
                advertisedWattageW = 100,
                protocol = "FlashCharge",
                isOfficialCharger = true,
                cableBrand = "iQOO",
                cableModel = "Stock",
                isOfficialCable = true,
                chargingType = ChargingType.WIRED,
                chargingMode = ChargingMode.FLASH_CHARGE,
                isTemplate = false,
                notes = null,
                createdAt = now,
            )
        )
        database.softwareSnapshotDao().insert(
            SoftwareSnapshotEntity("snap-1", now, "16", 36, "PD2505", "fingerprint", "1.0", 1)
        )
        database.chargingSessionDao().insert(
            ChargingSessionEntity(
                id = "active-sess-1",
                startedAt = now.minusSeconds(300),
                endedAt = null, // Active
                startPercent = 20,
                endPercent = null,
                chargingSetupId = "setup-1",
                softwareSnapshotId = "snap-1",
                testType = TestType.STANDARD,
                userNotes = null,
                endReason = null,
            )
        )

        advanceTimeBy(100)
        val state = viewModel.uiState.first { it.hasActiveSession }

        assertTrue(state.hasActiveSession)
        assertEquals("active-sess-1", state.activeSessionId)
        assertEquals(TestType.STANDARD, state.activeSessionTestType)
        assertEquals(20, state.activeSessionStartPercent)
    }

    @Test
    fun `03 - UI state extracts recent benchmark snapshot duration when available`() = runTest {
        database.chargingSetupDao().insert(
            ChargingSetupEntity("setup-1", "iQOO", "100W", 100, "FlashCharge", true, "iQOO", "Stock", true, ChargingType.WIRED, ChargingMode.FLASH_CHARGE, null, now, false)
        )
        database.softwareSnapshotDao().insert(
            SoftwareSnapshotEntity("snap-1", now, "16", 36, "PD2505", "fingerprint", "1.0", 1)
        )
        database.chargingSessionDao().insert(
            ChargingSessionEntity("completed-sess-1", now.minusSeconds(2000), now, 20, 80, "setup-1", "snap-1", TestType.STANDARD, null, SessionEndReason.USER_STOPPED)
        )
        database.standardTestDao().insert(
            StandardTestEntity(
                id = "std-1",
                sessionId = "completed-sess-1",
                comparisonGroupKey = "standard_20_80_wired_official",
                targetStartPercent = 20,
                targetEndPercent = 80,
                benchmarkStartedElapsedMs = 10000L,
                benchmarkEndedElapsedMs = 1810000L, // 30 min duration
            )
        )

        advanceTimeBy(100)
        val state = viewModel.uiState.first { it.latestBenchmarkDurationMs != null }

        assertNotNull(state.latestBenchmarkDurationMs)
        assertEquals(1800000L, state.latestBenchmarkDurationMs)
        assertEquals("standard_20_80_wired_official", state.latestBenchmarkGroupKey)
    }

    private class FakeBatteryDataSource(private var snapshot: BatterySnapshot) : BatteryDataSource {
        fun setSnapshot(s: BatterySnapshot) { snapshot = s }
        override suspend fun readSnapshot(): BatterySnapshot = snapshot
    }
}
