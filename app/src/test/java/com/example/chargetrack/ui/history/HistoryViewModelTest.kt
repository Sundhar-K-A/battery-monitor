package com.example.chargetrack.ui.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import com.example.chargetrack.domain.history.DateFilterOption
import com.example.chargetrack.domain.history.HistorySortOption
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: HistoryRepository
    private lateinit var viewModel: HistoryViewModel

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

        repository = HistoryRepository(database)
        viewModel = HistoryViewModel(repository, testDispatcher)
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
                id = "s-1",
                startedAt = now.minusSeconds(100),
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
                sessionId = "s-1",
                targetStartPercent = 20,
                targetEndPercent = 80,
                comparisonGroupKey = "std-key",
            )
        )
    }

    @Test
    fun `01 - initial state loads sessions and available setups`() = testScope.runTest {
        seedTestData()

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(1, state.sessions.size)
        assertEquals("s-1", state.sessions[0].sessionId)
        assertEquals(1, state.availableSetups.size)
        assertFalse(state.isFiltered)

        collectJob.cancel()
    }

    @Test
    fun `02 - filter toggles update ui state`() = testScope.runTest {
        seedTestData()

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.runCurrent()

        viewModel.toggleCanonical2080()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.filter.canonical2080Only)
        assertTrue(viewModel.uiState.value.isFiltered)

        viewModel.setDateOption(DateFilterOption.TODAY)
        testScheduler.runCurrent()
        assertEquals(DateFilterOption.TODAY, viewModel.uiState.value.filter.dateOption)

        viewModel.setSortOption(HistorySortOption.DURATION_DESC)
        testScheduler.runCurrent()
        assertEquals(HistorySortOption.DURATION_DESC, viewModel.uiState.value.filter.sortBy)

        viewModel.resetFilters()
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.isFiltered)

        collectJob.cancel()
    }

    @Test
    fun `03 - delete confirmation flow deletes session from database`() = testScope.runTest {
        seedTestData()

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testScheduler.runCurrent()

        viewModel.requestDeleteSession("s-1")
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isDeleteConfirmDialogOpen)
        assertEquals("s-1", viewModel.uiState.value.pendingDeleteSessionId)

        viewModel.confirmDeleteSession()
        testScheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isDeleteConfirmDialogOpen)
        assertNull(viewModel.uiState.value.pendingDeleteSessionId)

        val remainingSessions = database.chargingSessionDao().getById("s-1")
        assertNull(remainingSessions)

        collectJob.cancel()
    }
}
