package com.example.chargetrack.data.sampling

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.sampling.BatterySampler
import com.example.chargetrack.domain.sampling.OutlierThresholds
import com.example.chargetrack.domain.session.SessionConfig
import com.example.chargetrack.domain.time.TimeSource
import com.example.chargetrack.testutil.FakeBatteryDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SamplingRepositoryTest {

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

    private lateinit var database: AppDatabase
    private lateinit var timeSource: TestTimeSource
    private lateinit var fakeBatteryDataSource: FakeBatteryDataSource
    private lateinit var batterySampler: BatterySampler
    private lateinit var repository: SamplingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        timeSource = TestTimeSource()
        fakeBatteryDataSource = FakeBatteryDataSource()

        val config = SessionConfig(
            expectedSampleIntervalMs = 5_000L,
            measurementGapTimeoutMs = 30_000L,
            unplugDebounceMs = 5_000L,
        )

        batterySampler = BatterySampler(
            batteryDataSource = fakeBatteryDataSource,
            timeSource = timeSource,
            config = config,
            outlierThresholds = OutlierThresholds(),
        )

        kotlinx.coroutines.runBlocking {
            // Seed prerequisite database entities
            val setup = ChargingSetupEntity(
                id = "setup-1",
                chargingType = ChargingType.WIRED,
                chargingMode = ChargingMode.FLASH_CHARGE,
                createdAt = Instant.now(),
            )
            database.chargingSetupDao().insert(setup)

            val snapshot = SoftwareSnapshotEntity(
                id = "snapshot-1",
                capturedAt = Instant.now(),
                androidVersion = "16",
                sdkInt = 36,
                buildFingerprint = "fp",
                appVersionName = "1.0",
                appVersionCode = 1,
            )
            database.softwareSnapshotDao().insert(snapshot)

            val session = ChargingSessionEntity(
                id = "session-1",
                startedAt = Instant.now(),
                startPercent = 20,
                chargingSetupId = "setup-1",
                softwareSnapshotId = "snapshot-1",
            )
            database.chargingSessionDao().insert(session)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `startSampling captures and persists samples to Room on cadence`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        repository = SamplingRepository(
            batterySampler = batterySampler,
            batterySampleDao = database.batterySampleDao(),
            ioDispatcher = testDispatcher,
        )

        repository.startSampling("session-1", startRealtimeMs = 100_000L, scope = this)
        testScheduler.runCurrent()

        // First sample persisted immediately
        var samples = database.batterySampleDao().getSamplesForSessionOrdered("session-1")
        assertEquals(1, samples.size)
        assertEquals("session-1", samples[0].sessionId)
        assertEquals(0L, samples[0].elapsedMs)
        assertEquals(20, samples[0].percent)
        assertEquals(4050, samples[0].voltageMv)
        assertEquals(60_750_000L, samples[0].derivedPowerUw) // 4050 mV * 15,000,000 uA / 1000 = 60,750,000 uW persisted to Room

        // Advance 5 seconds
        timeSource.advanceSeconds(5)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        samples = database.batterySampleDao().getSamplesForSessionOrdered("session-1")
        assertEquals(2, samples.size)
        assertEquals(5_000L, samples[1].elapsedMs)

        // Advance another 5 seconds
        timeSource.advanceSeconds(5)
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        samples = database.batterySampleDao().getSamplesForSessionOrdered("session-1")
        assertEquals(3, samples.size)
        assertEquals(10_000L, samples[2].elapsedMs)

        // Stop sampling
        repository.stopSampling()
        testScheduler.runCurrent()

        // Time advancement after stop does not write new rows
        timeSource.advanceSeconds(10)
        advanceTimeBy(10_000L)
        testScheduler.runCurrent()

        val count = repository.getSampleCount("session-1")
        assertEquals(3, count)
    }
}
