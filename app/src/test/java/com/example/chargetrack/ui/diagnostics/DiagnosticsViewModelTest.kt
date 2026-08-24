package com.example.chargetrack.ui.diagnostics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.health.BatteryHealthRepository
import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.health.BatteryHealthEstimate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DiagnosticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var fakeBatteryDataSource: FakeBatteryDataSource
    private lateinit var batteryHealthRepository: BatteryHealthRepository
    private lateinit var viewModel: DiagnosticsViewModel

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

        batteryHealthRepository = BatteryHealthRepository(database)
        fakeBatteryDataSource = FakeBatteryDataSource()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `01 - refresh populates Ready state with battery snapshot and health estimate`() = testScope.runTest {
        viewModel = DiagnosticsViewModel(
            batteryDataSource = fakeBatteryDataSource,
            batteryHealthRepository = batteryHealthRepository,
            context = context,
        )
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("State should be Ready", state is DiagnosticsUiState.Ready)
        val ready = state as DiagnosticsUiState.Ready
        assertEquals(70, ready.snapshot.percent)
        assertEquals(4_410_000, ready.snapshot.chargeCounterUah)
        // With empty database, health estimate should be InsufficientData or Unavailable
        assertTrue(ready.healthEstimate is BatteryHealthEstimate.InsufficientData || ready.healthEstimate is BatteryHealthEstimate.Unavailable)
    }

    private class FakeBatteryDataSource : BatteryDataSource {
        override suspend fun readSnapshot(): BatterySnapshot = BatterySnapshot(
            timestamp = Instant.now(),
            percent = 70,
            voltageMv = 4000,
            currentNowUa = 15_000_000,
            chargeCounterUah = 4_410_000, // 4410 mAh at 70%
        )
    }
}
