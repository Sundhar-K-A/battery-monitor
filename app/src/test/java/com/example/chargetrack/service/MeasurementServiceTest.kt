package com.example.chargetrack.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.sampling.SamplingRepository
import com.example.chargetrack.data.session.ChargingSessionRepository
import com.example.chargetrack.domain.sampling.BatterySampler
import com.example.chargetrack.domain.sampling.OutlierThresholds
import com.example.chargetrack.domain.session.SessionConfig
import com.example.chargetrack.domain.time.BootInfoProvider
import com.example.chargetrack.domain.time.TimeSource
import com.example.chargetrack.testutil.FakeBatteryDataSource
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeasurementServiceTest {

    private class TestTimeSource(
        var currentInstant: Instant = Instant.parse("2026-08-23T10:00:00Z"),
        var currentRealtimeMs: Long = 100_000L,
    ) : TimeSource {
        override fun now(): Instant = currentInstant
        override fun elapsedRealtime(): Long = currentRealtimeMs
    }

    private class FakeBootInfoProvider(var currentBootId: String = "boot-12345") : BootInfoProvider {
        override fun getBootId(): String = currentBootId
    }

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var timeSource: TestTimeSource
    private lateinit var bootInfoProvider: FakeBootInfoProvider
    private lateinit var fakeBatteryDataSource: FakeBatteryDataSource
    private lateinit var batterySampler: BatterySampler
    private lateinit var samplingRepository: SamplingRepository
    private lateinit var sessionRepository: ChargingSessionRepository
    private lateinit var notificationManager: MeasurementNotificationManager
    private lateinit var service: MeasurementService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        timeSource = TestTimeSource()
        bootInfoProvider = FakeBootInfoProvider()
        fakeBatteryDataSource = FakeBatteryDataSource()

        val config = SessionConfig(
            expectedSampleIntervalMs = 5_000L,
            unplugDebounceMs = 5_000L,
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
        )

        sessionRepository = ChargingSessionRepository(
            database = database,
            config = config,
            timeSource = timeSource,
            bootInfoProvider = bootInfoProvider,
            context = context,
        )

        notificationManager = MeasurementNotificationManager(context)

        service = MeasurementService().apply {
            this.sessionRepository = this@MeasurementServiceTest.sessionRepository
            this.samplingRepository = this@MeasurementServiceTest.samplingRepository
            this.notificationManager = this@MeasurementServiceTest.notificationManager
            this.timeSource = this@MeasurementServiceTest.timeSource
        }
    }

    @After
    fun tearDown() {
        service.onDestroy()
        database.close()
    }

    @Test
    fun `ACTION_START_SESSION starts sampling and handles commands without error`() {
        val startIntent = MeasurementService.createStartIntent(
            context = context,
            sessionId = "test-session-123",
            startRealtimeMs = 100_000L,
        )

        val result = service.onStartCommand(startIntent, 0, 1)
        assertTrue("onStartCommand must return START_NOT_STICKY", result == android.app.Service.START_NOT_STICKY)
    }

    @Test
    fun `MeasurementServiceController dispatches start and stop intents safely`() {
        val controller = DefaultMeasurementServiceController(context)
        val started = controller.startService("session-123", 100_000L)
        assertTrue("Controller must report start success", started)

        controller.stopService()
    }

    @Test
    fun `ACTION_STOP_SESSION intent can be created and dispatched`() {
        val stopIntent = MeasurementService.createStopIntent(context)
        assertNotNull(stopIntent)
        assertTrue(stopIntent.action == MeasurementService.ACTION_STOP_SESSION)

        val result = service.onStartCommand(stopIntent, 0, 2)
        assertTrue(result == android.app.Service.START_NOT_STICKY)
    }
}
