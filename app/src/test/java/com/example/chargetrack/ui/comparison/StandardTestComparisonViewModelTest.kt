package com.example.chargetrack.ui.comparison

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.analytics.SessionSummaryRepository
import com.example.chargetrack.data.comparison.StandardTestComparisonRepository
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.data.history.HistoryRepository
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class StandardTestComparisonViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var summaryRepository: SessionSummaryRepository
    private lateinit var comparisonRepository: StandardTestComparisonRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var viewModel: StandardTestComparisonViewModel

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

        summaryRepository = SessionSummaryRepository(database)
        comparisonRepository = StandardTestComparisonRepository(database, summaryRepository)
        historyRepository = HistoryRepository(database)

        viewModel = StandardTestComparisonViewModel(
            comparisonRepository = comparisonRepository,
            historyRepository = historyRepository,
            ioDispatcher = testDispatcher,
        )
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

        // Seed 6 Standard Tests (s1 to s6)
        (1..6).forEach { i ->
            database.chargingSessionDao().insert(
                ChargingSessionEntity(
                    id = "s$i",
                    startedAt = now.minusSeconds((10 - i) * 100L),
                    endedAt = now.minusSeconds((10 - i) * 100L).plusSeconds(1200),
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
                    id = "std-$i",
                    sessionId = "s$i",
                    targetStartPercent = 20,
                    targetEndPercent = 80,
                    comparisonGroupKey = "standard_20_80_wired_official_iqoo_100w_flash_charge",
                )
            )
        }
    }

    @Test
    fun `01 - initialize selects primary and candidate tests`() = testScope.runTest {
        seedTestData()

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.initialize(initialPrimaryId = "s1", initialCandidateId = "s2")
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("Expected Success state, was: $state", state is StandardTestComparisonUiState.Success)
        val success = state as StandardTestComparisonUiState.Success

        assertEquals("s1", success.primarySessionId)
        assertTrue(success.selectedCandidateSessionIds.contains("s2"))
        assertEquals(1, success.pairwiseResults.size)
        assertEquals(2, success.alignedPowerSeries.size)

        collectJob.cancel()
    }

    @Test
    fun `02 - enforces max 5 active curves limit`() = testScope.runTest {
        seedTestData()

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.initialize(initialPrimaryId = "s1", initialCandidateId = "s2")
        testScheduler.runCurrent()

        // Add s3, s4, s5 -> Total: 1 primary + 4 candidates = 5 curves (the limit!)
        viewModel.toggleCandidate("s3")
        viewModel.toggleCandidate("s4")
        viewModel.toggleCandidate("s5")
        testScheduler.runCurrent()

        val success5 = viewModel.uiState.value as StandardTestComparisonUiState.Success
        assertEquals(5, success5.totalActiveCurves)
        assertEquals(4, success5.selectedCandidateSessionIds.size)

        // Attempting to add a 6th test (s6) is prevented
        viewModel.toggleCandidate("s6")
        testScheduler.runCurrent()

        val successLimit = viewModel.uiState.value as StandardTestComparisonUiState.Success
        assertEquals("Total curves must be capped at 5", 5, successLimit.totalActiveCurves)
        assertEquals(4, successLimit.selectedCandidateSessionIds.size)
        assertTrue(!successLimit.selectedCandidateSessionIds.contains("s6"))

        collectJob.cancel()
    }

    @Test
    fun `03 - explicit baseline update updates baseline in database`() = testScope.runTest {
        seedTestData()

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.initialize(initialPrimaryId = "s1", initialCandidateId = "s2")
        testScheduler.runCurrent()

        viewModel.openSetBaselineDialog()
        testScheduler.runCurrent()
        assertTrue((viewModel.uiState.value as StandardTestComparisonUiState.Success).isSetBaselineDialogOpen)

        viewModel.confirmSetBaseline()
        testScheduler.runCurrent()

        val baseline = comparisonRepository.getBaselineForGroup("standard_20_80_wired_official_iqoo_100w_flash_charge")
        assertNotNull(baseline)
        assertEquals("std-1", baseline?.id)

        collectJob.cancel()
    }
}
