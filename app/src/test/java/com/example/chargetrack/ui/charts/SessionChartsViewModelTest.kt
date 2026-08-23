package com.example.chargetrack.ui.charts

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.analytics.SessionSummaryRepository
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.BatterySampleEntity
import com.example.chargetrack.data.db.entity.ChargeTransitionEntity
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionChartsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var sessionSummaryRepository: SessionSummaryRepository
    private lateinit var viewModel: SessionChartsViewModel

    private val testSessionId = "session-chart-test-123"

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

        sessionSummaryRepository = SessionSummaryRepository(database)
        viewModel = SessionChartsViewModel(
            database = database,
            sessionSummaryRepository = sessionSummaryRepository,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun seedTestData() {
        val now = Instant.now()

        // 1. Foreign keys
        database.chargingSetupDao().insert(
            ChargingSetupEntity(
                id = "setup-1",
                chargerBrand = "iQOO",
                chargerModel = "100W",
                advertisedWattageW = 100,
                isOfficialCharger = true,
                cableBrand = "iQOO",
                cableModel = "Stock",
                isOfficialCable = true,
                chargingType = ChargingType.WIRED,
                chargingMode = ChargingMode.FLASH_CHARGE,
                createdAt = now,
            )
        )
        database.softwareSnapshotDao().insert(
            SoftwareSnapshotEntity(
                id = "snap-1",
                capturedAt = now,
                androidVersion = "16",
                sdkInt = 36,
                originOsVersion = "OriginOS 6",
                buildFingerprint = "fingerprint",
                appVersionName = "1.0",
                appVersionCode = 1,
            )
        )

        // 2. Session
        database.chargingSessionDao().insert(
            ChargingSessionEntity(
                id = testSessionId,
                startedAt = now.minusSeconds(60),
                endedAt = now,
                startPercent = 20,
                endPercent = 30,
                chargingSetupId = "setup-1",
                softwareSnapshotId = "snap-1",
                testType = TestType.STANDARD,
                endReason = SessionEndReason.USER_STOPPED,
            )
        )

        // 3. Samples
        val sampleEntities = (0..10).map { i ->
            BatterySampleEntity(
                id = "sample-$i",
                sessionId = testSessionId,
                timestamp = now.minusSeconds(60).plusMillis(i * 5_000L),
                elapsedMs = i * 5_000L,
                percent = 20 + i,
                voltageMv = 4000 + i * 20,
                currentNowUa = 10_000_000,
                temperatureDeciC = 290 + i * 2,
                derivedPowerUw = 40_000_000L + i * 500_000L,
                qualityFlags = emptySet(),
            )
        }
        database.batterySampleDao().insertSamples(sampleEntities)

        // 4. Transitions
        val transitionEntities = (20..29).map { p ->
            ChargeTransitionEntity(
                id = "trans-$p",
                sessionId = testSessionId,
                fromPercent = p,
                toPercent = p + 1,
                startedAt = now.minusSeconds(60).plusMillis(((p - 20) * 5_000L)),
                endedAt = now.minusSeconds(60).plusMillis(((p - 20 + 1) * 5_000L)),
                durationMs = 5_000L,
                quality = DataQuality.GOOD,
                averagePowerUw = 42_000_000L,
                sampleCount = 1,
            )
        }
        database.chargeTransitionDao().insertAll(transitionEntities)
    }

    @Test
    fun `01 - loadSessionCharts transforms all 6 chart models into Success state`() = testScope.runTest {
        seedTestData()

        viewModel.loadSessionCharts(testSessionId)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("State should be Success, was: $state", state is SessionChartsUiState.Success)
        val success = state as SessionChartsUiState.Success

        assertEquals(testSessionId, success.sessionId)
        assertEquals(11, success.sampleCount)
        assertFalse(success.batteryPercentVsTime.isEmpty)
        assertFalse(success.powerVsBatteryPercent.isEmpty)
        assertFalse(success.powerVsTime.isEmpty)
        assertFalse(success.temperatureVsBatteryPercent.isEmpty)
        assertFalse(success.currentVsBatteryPercent.isEmpty)
        assertEquals(10, success.timePerPercentBars.size)
        assertNotNull(success.summary)
    }

    @Test
    fun `02 - selectTab updates selectedTab in state`() = testScope.runTest {
        seedTestData()

        viewModel.loadSessionCharts(testSessionId)
        testScheduler.runCurrent()

        viewModel.selectTab(ChartTab.POWER_VS_PERCENT)
        val state = viewModel.uiState.value as SessionChartsUiState.Success
        assertEquals(ChartTab.POWER_VS_PERCENT, state.selectedTab)

        viewModel.selectTab(ChartTab.TIME_PER_PERCENT)
        val state2 = viewModel.uiState.value as SessionChartsUiState.Success
        assertEquals(ChartTab.TIME_PER_PERCENT, state2.selectedTab)
    }

    private fun assertFalse(condition: Boolean) {
        assertTrue(!condition)
    }
}
