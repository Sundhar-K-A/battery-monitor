package com.example.chargetrack.ui.standardtest

import android.content.Context
import android.os.BatteryManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.session.ChargingSessionRepository
import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTestConstants
import com.example.chargetrack.domain.model.StandardTestPreset
import com.example.chargetrack.domain.time.DefaultBootInfoProvider
import com.example.chargetrack.domain.time.DefaultTimeSource
import com.example.chargetrack.service.MeasurementServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StandardTestViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var sessionRepository: ChargingSessionRepository
    private lateinit var fakeBatteryDataSource: FakeBatteryDataSource
    private lateinit var fakeServiceController: FakeMeasurementServiceController

    private lateinit var viewModel: StandardTestViewModel

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

        sessionRepository = ChargingSessionRepository(
            database = database,
            bootInfoProvider = DefaultBootInfoProvider(),
            context = context,
            timeSource = DefaultTimeSource(),
        )

        fakeBatteryDataSource = FakeBatteryDataSource()
        fakeServiceController = FakeMeasurementServiceController()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): StandardTestViewModel {
        return StandardTestViewModel(
            sessionRepository = sessionRepository,
            batteryDataSource = fakeBatteryDataSource,
            database = database,
            measurementServiceController = fakeServiceController,
        )
    }

    @Test
    fun `01 - default state selects canonical 20-80 preset`() = testScope.runTest {
        viewModel = createViewModel()
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertEquals(StandardTestPreset.STANDARD_20_80, state.selectedPreset)
        assertEquals(20, state.startPercent)
        assertEquals(80, state.targetPercent)
        assertTrue(state.selectedPreset.isCanonical)
        assertNotNull(state.selectedSetup)
    }

    @Test
    fun `02 - selecting preset updates start and target boundaries`() = testScope.runTest {
        viewModel = createViewModel()
        testScheduler.runCurrent()

        viewModel.selectPreset(StandardTestPreset.FULL_10_100)
        val state = viewModel.uiState.value

        assertEquals(StandardTestPreset.FULL_10_100, state.selectedPreset)
        assertEquals(10, state.startPercent)
        assertEquals(100, state.targetPercent)
        assertTrue(state.comparisonGroupKey.contains("standard_10_100"))
    }

    @Test
    fun `03 - custom range enforces minimum span`() = testScope.runTest {
        viewModel = createViewModel()
        testScheduler.runCurrent()

        viewModel.setCustomRange(startPercent = 50, targetPercent = 52) // invalid gap of 2%
        val state = viewModel.uiState.value

        assertEquals(StandardTestPreset.CUSTOM, state.selectedPreset)
        assertEquals(50, state.startPercent)
        // Automatically coerced to start + MIN_STANDARD_TEST_PERCENT_SPAN (55)
        assertEquals(50 + StandardTestConstants.MIN_STANDARD_TEST_PERCENT_SPAN, state.targetPercent)
    }

    @Test
    fun `04 - battery readiness evaluates arming vs warning`() = testScope.runTest {
        viewModel = createViewModel()
        testScheduler.runCurrent()

        // 1. Below target (18% < 20%) -> Ready to arm
        fakeBatteryDataSource.emitSnapshot(
            BatterySnapshot(
                timestamp = Instant.now(),
                percent = 18,
                voltageMv = 4000,
                currentNowUa = 10_000_000,
                temperatureDeciC = 290,
                batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
                pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            )
        )
        viewModel.refreshBatteryStatus()
        testScheduler.runCurrent()

        val stateArmed = viewModel.uiState.value
        assertTrue(stateArmed.isBatteryReady)
        assertTrue(stateArmed.batteryReadinessMessage.contains("Ready to arm"))

        // 2. Above target (25% > 20%) -> Warning
        fakeBatteryDataSource.emitSnapshot(
            BatterySnapshot(
                timestamp = Instant.now(),
                percent = 25,
                voltageMv = 4100,
                currentNowUa = 10_000_000,
                temperatureDeciC = 300,
                batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
                pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            )
        )
        viewModel.refreshBatteryStatus()
        testScheduler.runCurrent()

        val stateWarning = viewModel.uiState.value
        assertFalse(stateWarning.isBatteryReady)
        assertTrue(stateWarning.batteryReadinessMessage.contains("Discharge battery"))
    }

    @Test
    fun `05 - startStandardTest launches session and foreground service`() = testScope.runTest {
        viewModel = createViewModel()
        testScheduler.runCurrent()

        fakeBatteryDataSource.emitSnapshot(
            BatterySnapshot(
                timestamp = Instant.now(),
                percent = 20,
                voltageMv = 4000,
                currentNowUa = 10_000_000,
                temperatureDeciC = 290,
                batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
                pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            )
        )
        viewModel.refreshBatteryStatus()
        testScheduler.runCurrent()

        var navigated = false
        viewModel.startStandardTest(onSuccess = { navigated = true })
        testScheduler.runCurrent()

        assertTrue("Should navigate to live session on success", navigated)
        assertTrue("Foreground service should be started", fakeServiceController.isServiceRunning)
    }

    private class FakeBatteryDataSource : BatteryDataSource {
        var currentSnapshot: BatterySnapshot = BatterySnapshot(
            timestamp = Instant.now(),
            percent = 20,
            voltageMv = 4000,
            currentNowUa = 15_000_000,
            batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
        )

        override suspend fun readSnapshot(): BatterySnapshot = currentSnapshot

        fun emitSnapshot(snapshot: BatterySnapshot) {
            currentSnapshot = snapshot
        }
    }

    private class FakeMeasurementServiceController : MeasurementServiceController {
        var isServiceRunning = false
        override fun startService(sessionId: String, startRealtimeMs: Long): Boolean {
            isServiceRunning = true
            return true
        }
        override fun stopService() {
            isServiceRunning = false
        }
    }
}
