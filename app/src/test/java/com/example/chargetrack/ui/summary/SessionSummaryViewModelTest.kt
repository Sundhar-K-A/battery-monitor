package com.example.chargetrack.ui.summary

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
import com.example.chargetrack.data.db.entity.StandardTestEntity
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
class SessionSummaryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var sessionSummaryRepository: SessionSummaryRepository
    private lateinit var viewModel: SessionSummaryViewModel

    private val testSessionId = "session-summary-test-123"
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

        sessionSummaryRepository = SessionSummaryRepository(database)
        val exportImportRepository = com.example.chargetrack.data.export.ExportImportRepository(database)
        viewModel = SessionSummaryViewModel(database, sessionSummaryRepository, exportImportRepository, testDispatcher)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun seedTestData() {
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
        database.chargingSessionDao().insert(
            ChargingSessionEntity(
                id = testSessionId,
                startedAt = now.minusSeconds(60),
                endedAt = now,
                startPercent = 20,
                endPercent = 80,
                chargingSetupId = "setup-1",
                softwareSnapshotId = "snap-1",
                testType = TestType.STANDARD,
                endReason = SessionEndReason.USER_STOPPED,
            )
        )
        database.standardTestDao().insert(
            StandardTestEntity(
                id = "std-1",
                sessionId = testSessionId,
                targetStartPercent = 20,
                targetEndPercent = 80,
                comparisonGroupKey = "standard_20_80_wired_official_iqoo_100w_flash_charge",
            )
        )

        val sampleEntities = (0..5).map { i ->
            BatterySampleEntity(
                id = "sample-$i",
                sessionId = testSessionId,
                timestamp = now.minusSeconds(60).plusMillis(i * 5_000L),
                elapsedMs = i * 5_000L,
                percent = 20 + i * 10,
                voltageMv = 4000 + i * 50,
                currentNowUa = 10_000_000,
                temperatureDeciC = 300 + i * 5,
                derivedPowerUw = 40_000_000L + i * 1_000_000L,
                qualityFlags = emptySet(),
            )
        }
        database.batterySampleDao().insertSamples(sampleEntities)

        val transitionEntities = listOf(
            ChargeTransitionEntity(
                id = "trans-1",
                sessionId = testSessionId,
                fromPercent = 20,
                toPercent = 21,
                startedAt = now.minusSeconds(60),
                endedAt = now.minusSeconds(40),
                durationMs = 20_000L,
                quality = DataQuality.GOOD,
                averagePowerUw = 42_000_000L,
                sampleCount = 4,
            ),
            ChargeTransitionEntity(
                id = "trans-2",
                sessionId = testSessionId,
                fromPercent = 21,
                toPercent = 24, // Gap
                startedAt = now.minusSeconds(40),
                endedAt = now.minusSeconds(10),
                durationMs = 30_000L,
                quality = DataQuality.INSUFFICIENT,
                averagePowerUw = 44_000_000L,
                sampleCount = 6,
            ),
        )
        database.chargeTransitionDao().insertAll(transitionEntities)
    }

    @Test
    fun `01 - loadSessionSummary loads authoritative summary, setup, test, and transitions`() = testScope.runTest {
        seedTestData()

        viewModel.loadSessionSummary(testSessionId)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("Expected Success state, was: $state", state is SessionSummaryUiState.Success)
        val success = state as SessionSummaryUiState.Success

        assertEquals(testSessionId, success.session.id)
        assertNotNull(success.summary)
        assertEquals(20, success.summary.startPercent)
        assertEquals(80, success.summary.endPercent)
        assertEquals(60, success.summary.percentGained)
        assertEquals("iQOO", success.setup?.chargerBrand)
        assertEquals("standard_20_80_wired_official_iqoo_100w_flash_charge", success.standardTest?.comparisonGroupKey)
        assertEquals(2, success.transitions.size)
        assertEquals(DataQuality.GOOD, success.transitions[0].quality)
        assertEquals(DataQuality.INSUFFICIENT, success.transitions[1].quality)
    }

    @Test
    fun `02 - loadSessionSummary returns Error state when session does not exist`() = testScope.runTest {
        viewModel.loadSessionSummary("non-existent-session-id")
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("Expected Error state, was: $state", state is SessionSummaryUiState.Error)
    }
}
