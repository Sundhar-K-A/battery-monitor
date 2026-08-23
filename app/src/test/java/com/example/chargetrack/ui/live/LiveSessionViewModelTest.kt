package com.example.chargetrack.ui.live

import android.content.Context
import android.os.BatteryManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.sampling.SamplingRepository
import com.example.chargetrack.data.session.ChargingSessionRepository
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.sampling.BatterySampler
import com.example.chargetrack.domain.sampling.OutlierThresholds
import com.example.chargetrack.domain.session.SessionConfig
import com.example.chargetrack.domain.time.TimeSource
import com.example.chargetrack.testutil.FakeBatteryDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveSessionViewModelTest {

    private class TestTimeSource(
        var currentInstant: Instant = Instant.parse("2026-08-23T10:00:00Z"),
        var currentRealtimeMs: Long = 100_000L,
    ) : TimeSource {
        override fun now(): Instant = currentInstant
        override fun elapsedRealtime(): Long = currentRealtimeMs

        fun advanceSeconds(seconds: Long) {
            currentInstant = currentInstant.plusSeconds(seconds)
            currentRealtimeMs += (seconds * 1000L)
        }
    }

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var database: AppDatabase
    private lateinit var timeSource: TestTimeSource
    private lateinit var fakeBatteryDataSource: FakeBatteryDataSource
    private lateinit var batterySampler: BatterySampler
    private lateinit var samplingRepository: SamplingRepository
    private lateinit var sessionRepository: ChargingSessionRepository
    private lateinit var viewModel: LiveSessionViewModel

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

    private fun createSoftwareSnapshot(): SoftwareSnapshot = SoftwareSnapshot(
        id = UUID.randomUUID().toString(),
        capturedAt = timeSource.now(),
        androidVersion = "16",
        sdkInt = 36,
        originOsVersion = "OriginOS 6",
        buildFingerprint = "vivo/iQOO15/iQOO15:16/...",
        appVersionName = "1.0",
        appVersionCode = 1,
    )

    private fun createChargingSnapshot(
        percent: Int = 20,
        voltageMv: Int = 4050,
        currentNowUa: Int = 15_000_000,
        temperatureDeciC: Int = 300,
    ): BatterySnapshot = BatterySnapshot(
        timestamp = timeSource.now(),
        percent = percent,
        voltageMv = voltageMv,
        currentNowUa = currentNowUa,
        currentAverageUa = null,
        chargeCounterUah = null,
        energyCounterNwh = null,
        temperatureDeciC = temperatureDeciC,
        batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
        pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
        cycleCount = null,
        qualityFlags = emptySet(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        timeSource = TestTimeSource()
        fakeBatteryDataSource = FakeBatteryDataSource(
            currentSnapshot = createChargingSnapshot(),
        )

        val config = SessionConfig(
            expectedSampleIntervalMs = 5_000L,
            unplugDebounceMs = 10_000L,
        )

        batterySampler = BatterySampler(
            batteryDataSource = fakeBatteryDataSource,
            timeSource = timeSource,
            config = config,
            outlierThresholds = OutlierThresholds(),
        )

        samplingRepository = SamplingRepository(
            batterySampler = batterySampler,
            batterySampleDao = database.batterySampleDao(),
            ioDispatcher = testDispatcher,
        )

        sessionRepository = ChargingSessionRepository(
            database = database,
            config = config,
            timeSource = timeSource,
        )

        viewModel = LiveSessionViewModel(
            sessionRepository = sessionRepository,
            samplingRepository = samplingRepository,
            database = database,
            timeSource = timeSource,
        )
    }

    @After
    fun tearDown() {
        viewModel.onCleared()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `starts in NoSession state when repository is Idle`() = testScope.runTest {
        testScheduler.runCurrent()
        assertEquals(LiveSessionUiState.NoSession, viewModel.uiState.value)
    }

    @Test
    fun `transitions to Active state when session starts`() = testScope.runTest {
        testScheduler.runCurrent()

        val startSnapshot = createChargingSnapshot(percent = 20)

        val result = sessionRepository.startSession(
            snapshot = startSnapshot,
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
            testType = TestType.FREE_FORM,
        )
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()

        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("Expected Active state, got $state", state is LiveSessionUiState.Active)
        val active = state as LiveSessionUiState.Active
        assertEquals(session.id, active.sessionId)
        assertEquals(20, active.startPercent)
        assertEquals(0L, active.elapsedMs)
        assertFalse(active.isDebouncing)
        assertEquals(1, active.sampleCount)

        viewModel.stopSession()
        testScheduler.runCurrent()
    }

    @Test
    fun `null sample fields propagate as null without zero coercion`() = testScope.runTest {
        testScheduler.runCurrent()

        val nullsSnapshot = BatterySnapshot(
            timestamp = timeSource.now(),
            percent = null,
            voltageMv = null,
            currentNowUa = null,
            currentAverageUa = null,
            chargeCounterUah = null,
            energyCounterNwh = null,
            temperatureDeciC = null,
            batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            cycleCount = null,
        )
        fakeBatteryDataSource.currentSnapshot = nullsSnapshot

        val session = sessionRepository.startSession(
            snapshot = nullsSnapshot,
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()

        samplingRepository.startSampling(session.id, timeSource.elapsedRealtime(), this)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value as LiveSessionUiState.Active
        assertNull("currentPercent must be null", state.currentPercent)
        assertNull("voltageMv must be null", state.voltageMv)
        assertNull("currentNowUa must be null", state.currentNowUa)
        assertNull("temperatureDeciC must be null", state.temperatureDeciC)
        assertNull("derivedPowerUw must be null", state.derivedPowerUw)
        assertTrue("qualityFlags must contain MISSING_REQUIRED_VALUE", QualityFlag.MISSING_REQUIRED_VALUE in state.qualityFlags)

        samplingRepository.stopSampling()
        viewModel.stopSession()
        testScheduler.runCurrent()
    }

    @Test
    fun `sample values update voltage current temperature derived power and quality flags`() = testScope.runTest {
        testScheduler.runCurrent()

        val initialSnapshot = createChargingSnapshot(
            percent = 45,
            voltageMv = 4200,
            currentNowUa = 20_000_000,
            temperatureDeciC = 350,
        )
        fakeBatteryDataSource.currentSnapshot = initialSnapshot

        val session = sessionRepository.startSession(
            snapshot = initialSnapshot,
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()

        samplingRepository.startSampling(session.id, timeSource.elapsedRealtime(), this)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value as LiveSessionUiState.Active
        assertEquals(45, state.currentPercent)
        assertEquals(4200, state.voltageMv)
        assertEquals(20_000_000, state.currentNowUa)
        assertEquals(350, state.temperatureDeciC)
        // 4200 * 20_000_000 / 1000 = 84_000_000 uW = 84.0 W
        assertEquals(84_000_000L, state.derivedPowerUw)
        assertTrue(state.qualityFlags.isEmpty())

        samplingRepository.stopSampling()
        viewModel.stopSession()
        testScheduler.runCurrent()
    }

    @Test
    fun `completed transition appears in state when samples cross percentage boundary`() = testScope.runTest {
        testScheduler.runCurrent()

        val startSnapshot = createChargingSnapshot(
            percent = 50,
            voltageMv = 4000,
            currentNowUa = 15_000_000,
        )
        fakeBatteryDataSource.currentSnapshot = startSnapshot

        val session = sessionRepository.startSession(
            snapshot = startSnapshot,
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()

        samplingRepository.startSampling(session.id, timeSource.elapsedRealtime(), this)
        testScheduler.runCurrent()

        // Advance 5 seconds and stay at 50%
        timeSource.advanceSeconds(5)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        // Advance 5 seconds and step up to 51% (completes transition 50 -> 51)
        timeSource.advanceSeconds(5)
        fakeBatteryDataSource.currentSnapshot = createChargingSnapshot(
            percent = 51,
            voltageMv = 4050,
            currentNowUa = 15_000_000,
        )
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value as LiveSessionUiState.Active
        assertEquals(51, state.currentPercent)
        assertEquals(1, state.completedTransitions.size)
        val transition = state.completedTransitions[0]
        assertEquals(50, transition.fromPercent)
        assertEquals(51, transition.toPercent)
        assertEquals(session.id, transition.sessionId)

        samplingRepository.stopSampling()
        viewModel.stopSession()
        testScheduler.runCurrent()
    }

    @Test
    fun `1-second timer increments elapsedMs monotonically`() = testScope.runTest {
        testScheduler.runCurrent()

        sessionRepository.startSession(
            snapshot = createChargingSnapshot(),
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()

        testScheduler.runCurrent()
        var state = viewModel.uiState.value as LiveSessionUiState.Active
        assertEquals(0L, state.elapsedMs)

        // Advance time by 3 seconds
        timeSource.advanceSeconds(3)
        advanceTimeBy(3_000L)
        testScheduler.runCurrent()

        state = viewModel.uiState.value as LiveSessionUiState.Active
        assertEquals(3_000L, state.elapsedMs)

        viewModel.stopSession()
        testScheduler.runCurrent()
    }

    @Test
    fun `session completion freezes elapsed time and emits SessionEnded with correct reason`() = testScope.runTest {
        testScheduler.runCurrent()

        val session = sessionRepository.startSession(
            snapshot = createChargingSnapshot(),
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()

        testScheduler.runCurrent()

        // Advance time by 10 seconds
        timeSource.advanceSeconds(10)
        advanceTimeBy(10_000L)
        testScheduler.runCurrent()

        // User stops session
        viewModel.stopSession()
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertTrue("Expected SessionEnded state, got $state", state is LiveSessionUiState.SessionEnded)
        val ended = state as LiveSessionUiState.SessionEnded
        assertEquals(session.id, ended.session.id)
        assertEquals(SessionEndReason.USER_STOPPED, ended.endReason)
        assertEquals(10_000L, ended.durationMs)

        // Further time advances do NOT change elapsed duration
        timeSource.advanceSeconds(5)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        val stateAfter = viewModel.uiState.value as LiveSessionUiState.SessionEnded
        assertEquals(10_000L, stateAfter.durationMs)
    }

    @Test
    fun `SessionEnded captures PartialTransitionInfo when session concludes mid-transition`() = testScope.runTest {
        testScheduler.runCurrent()

        val snapshot60 = createChargingSnapshot(percent = 60)
        fakeBatteryDataSource.currentSnapshot = snapshot60

        val session = sessionRepository.startSession(
            snapshot = snapshot60,
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()

        samplingRepository.startSampling(session.id, timeSource.elapsedRealtime(), this)
        testScheduler.runCurrent()

        // Mid-transition at 60% with samples arriving
        timeSource.advanceSeconds(5)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        // Stop session mid-transition (still at 60%)
        viewModel.stopSession()
        testScheduler.runCurrent()

        val ended = viewModel.uiState.value as LiveSessionUiState.SessionEnded
        assertNotNull("Expected partial transition info", ended.partialTransitionInfo)
        val partial = ended.partialTransitionInfo!!
        assertEquals(60, partial.fromPercent)
        assertTrue(partial.samplesCollected >= 1)

        samplingRepository.stopSampling()
    }

    @Test
    fun `resetSession calls repository resetSession and returns to NoSession`() = testScope.runTest {
        testScheduler.runCurrent()

        sessionRepository.startSession(
            snapshot = createChargingSnapshot(),
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        )
        testScheduler.runCurrent()

        viewModel.stopSession()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value is LiveSessionUiState.SessionEnded)

        viewModel.resetSession()
        testScheduler.runCurrent()
        assertEquals(LiveSessionUiState.NoSession, viewModel.uiState.value)
    }

    @Test
    fun `new session after reset clears previous transitions and resets detector`() = testScope.runTest {
        testScheduler.runCurrent()

        // 1. Session 1 with 1 completed transition
        val session1 = sessionRepository.startSession(
            snapshot = createChargingSnapshot(percent = 20),
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()

        samplingRepository.startSampling(session1.id, timeSource.elapsedRealtime(), this)
        testScheduler.runCurrent()

        timeSource.advanceSeconds(5)
        fakeBatteryDataSource.currentSnapshot = createChargingSnapshot(percent = 21)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        var activeState = viewModel.uiState.value as LiveSessionUiState.Active
        assertEquals(1, activeState.completedTransitions.size)

        samplingRepository.stopSampling()
        viewModel.stopSession()
        testScheduler.runCurrent()
        viewModel.resetSession()
        testScheduler.runCurrent()
        assertEquals(LiveSessionUiState.NoSession, viewModel.uiState.value)

        // 2. Session 2 starts fresh
        timeSource.advanceSeconds(10)
        val session2 = sessionRepository.startSession(
            snapshot = createChargingSnapshot(percent = 30),
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()

        samplingRepository.startSampling(session2.id, timeSource.elapsedRealtime(), this)
        testScheduler.runCurrent()

        activeState = viewModel.uiState.value as LiveSessionUiState.Active
        assertEquals(session2.id, activeState.sessionId)
        assertTrue("Previous session transitions must be cleared", activeState.completedTransitions.isEmpty())

        samplingRepository.stopSampling()
        viewModel.stopSession()
        testScheduler.runCurrent()
    }

    @Test
    fun `UnpluggedPending sets isDebouncing to true and preserves sampleCount`() = testScope.runTest {
        testScheduler.runCurrent()

        val session = sessionRepository.startSession(
            snapshot = createChargingSnapshot(),
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()
        testScheduler.runCurrent()

        var activeState = viewModel.uiState.value as LiveSessionUiState.Active
        assertFalse(activeState.isDebouncing)

        // Unplug event (transient disconnect with pluggedType = NONE)
        val unpluggedSnapshot = createChargingSnapshot().copy(
            timestamp = timeSource.now(),
            pluggedType = 0, // BATTERY_PLUGGED_NONE
            batteryStatus = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            currentNowUa = -500_000,
        )
        sessionRepository.onBatteryTick(unpluggedSnapshot)
        testScheduler.runCurrent()

        activeState = viewModel.uiState.value as LiveSessionUiState.Active
        assertTrue("isDebouncing must be true during unplug debounce", activeState.isDebouncing)
        assertEquals(session.id, activeState.sessionId)

        viewModel.stopSession()
        testScheduler.runCurrent()
    }

    @Test
    fun `sampleCount represents actual raw sample observations and is independent of transitions count`() = testScope.runTest {
        testScheduler.runCurrent()

        sessionRepository.startSession(
            snapshot = createChargingSnapshot(percent = 50),
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
        ).getOrThrow()

        // 9 battery ticks while percent stays at 50%
        repeat(9) {
            timeSource.advanceSeconds(5)
            sessionRepository.onBatteryTick(createChargingSnapshot(percent = 50))
        }
        testScheduler.runCurrent()

        val activeState = viewModel.uiState.value as LiveSessionUiState.Active
        // 1 initial + 9 ticks = 10 raw samples
        assertEquals(10, activeState.sampleCount)
        // 0 completed transitions
        assertEquals(0, activeState.completedTransitions.size)

        viewModel.stopSession()
        testScheduler.runCurrent()
    }

    @Test
    fun `standard test arms below start target and records exact sample elapsedMs at start boundary`() = testScope.runTest {
        testScheduler.runCurrent()

        val initialSnapshot = createChargingSnapshot(percent = 18)
        fakeBatteryDataSource.currentSnapshot = initialSnapshot

        // Start session at 18% with target 20% -> 80%
        val session = sessionRepository.startSession(
            snapshot = initialSnapshot,
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
            testType = TestType.STANDARD,
            targetStartPercent = 20,
            targetEndPercent = 80,
            comparisonGroupKey = "standard_20_80_wired_official",
        ).getOrThrow()

        samplingRepository.startSampling(session.id, timeSource.elapsedRealtime(), this)
        testScheduler.runCurrent()

        var activeState = viewModel.uiState.value as LiveSessionUiState.Active
        assertNotNull(activeState.standardTestInfo)
        assertTrue(activeState.standardTestInfo!!.isArmed)
        assertFalse(activeState.standardTestInfo!!.isBenchmarkActive)
        assertNull(activeState.standardTestInfo!!.benchmarkStartedElapsedMs)

        // Advance 15 seconds and step up to 20% -> Activates benchmark
        timeSource.advanceSeconds(15)
        fakeBatteryDataSource.currentSnapshot = createChargingSnapshot(percent = 20)
        advanceTimeBy(15_000L)
        testScheduler.runCurrent()

        activeState = viewModel.uiState.value as LiveSessionUiState.Active
        assertFalse(activeState.standardTestInfo!!.isArmed)
        assertTrue(activeState.standardTestInfo!!.isBenchmarkActive)
        assertEquals(15_000L, activeState.standardTestInfo!!.benchmarkStartedElapsedMs)

        samplingRepository.stopSampling()
        viewModel.stopSession()
        testScheduler.runCurrent()
    }

    @Test
    fun `target reached triggers dialog once and percentage jitter does not retrigger dialog`() = testScope.runTest {
        testScheduler.runCurrent()

        val initialSnapshot = createChargingSnapshot(percent = 20)
        fakeBatteryDataSource.currentSnapshot = initialSnapshot

        val session = sessionRepository.startSession(
            snapshot = initialSnapshot,
            setup = templateSetup,
            softwareSnapshot = createSoftwareSnapshot(),
            testType = TestType.STANDARD,
            targetStartPercent = 20,
            targetEndPercent = 80,
            comparisonGroupKey = "standard_20_80_wired_official",
        ).getOrThrow()

        samplingRepository.startSampling(session.id, timeSource.elapsedRealtime(), this)
        testScheduler.runCurrent()

        // Advance 60s and reach 80%
        timeSource.advanceSeconds(60)
        fakeBatteryDataSource.currentSnapshot = createChargingSnapshot(percent = 80)
        advanceTimeBy(60_000L)
        testScheduler.runCurrent()

        var activeState = viewModel.uiState.value as LiveSessionUiState.Active
        assertTrue("Dialog should be shown when target reached", activeState.showTargetReachedDialog)
        assertTrue(activeState.standardTestInfo!!.isTargetReached)
        assertEquals(60_000L, activeState.standardTestInfo!!.benchmarkEndedElapsedMs)

        // Dismiss dialog to continue recording
        viewModel.dismissTargetReachedDialog()
        testScheduler.runCurrent()

        activeState = viewModel.uiState.value as LiveSessionUiState.Active
        assertFalse("Dialog must be dismissed", activeState.showTargetReachedDialog)

        // Jitter: drops to 79% then back to 80%
        timeSource.advanceSeconds(5)
        fakeBatteryDataSource.currentSnapshot = createChargingSnapshot(percent = 79)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        timeSource.advanceSeconds(5)
        fakeBatteryDataSource.currentSnapshot = createChargingSnapshot(percent = 80)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        activeState = viewModel.uiState.value as LiveSessionUiState.Active
        assertFalse("Jitter back to 80% must NOT re-trigger the target dialog", activeState.showTargetReachedDialog)
        assertEquals("Original benchmarkEndedElapsedMs must remain frozen at 60_000L", 60_000L, activeState.standardTestInfo!!.benchmarkEndedElapsedMs)

        samplingRepository.stopSampling()
        viewModel.stopSession()
        testScheduler.runCurrent()
    }
}
