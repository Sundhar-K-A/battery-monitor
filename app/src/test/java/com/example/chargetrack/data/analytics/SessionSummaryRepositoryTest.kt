package com.example.chargetrack.data.analytics

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SessionSummaryRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: SessionSummaryRepository

    private val sessionId = "session-test-100"
    private val setupId = "setup-test-100"
    private val snapshotId = "snapshot-test-100"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        repository = SessionSummaryRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getSessionSummary loads session, samples, and transitions from Room and computes summary`() = runTest {
        // 1. Insert SoftwareSnapshot
        database.softwareSnapshotDao().insert(
            SoftwareSnapshotEntity(
                id = snapshotId,
                capturedAt = Instant.parse("2026-08-23T10:00:00Z"),
                androidVersion = "16",
                sdkInt = 36,
                originOsVersion = "OriginOS 6",
                buildFingerprint = "vivo/iQOO15/...",
                appVersionName = "1.0",
                appVersionCode = 1,
            )
        )

        // 2. Insert ChargingSetup
        database.chargingSetupDao().insert(
            ChargingSetupEntity(
                id = setupId,
                chargerBrand = "iQOO",
                chargerModel = "100W FlashCharge",
                advertisedWattageW = 100,
                protocol = "FlashCharge",
                isOfficialCharger = true,
                cableBrand = "iQOO",
                cableModel = "Stock Type-C",
                isOfficialCable = true,
                chargingType = ChargingType.WIRED,
                chargingMode = ChargingMode.FLASH_CHARGE,
                isTemplate = false,
                createdAt = Instant.parse("2026-08-23T10:00:00Z"),
            )
        )

        // 3. Insert ChargingSession
        database.chargingSessionDao().insert(
            ChargingSessionEntity(
                id = sessionId,
                startedAt = Instant.parse("2026-08-23T10:00:00Z"),
                endedAt = Instant.parse("2026-08-23T10:30:00Z"),
                startPercent = 20,
                endPercent = 80,
                chargingSetupId = setupId,
                softwareSnapshotId = snapshotId,
                testType = TestType.STANDARD,
                endReason = SessionEndReason.CHARGING_STOPPED,
            )
        )

        // 4. Insert StandardTest
        database.standardTestDao().insert(
            StandardTestEntity(
                sessionId = sessionId,
                targetStartPercent = 20,
                targetEndPercent = 80,
            )
        )

        // 5. Insert BatterySamples
        val sampleEntities = (0 until 10).map { i ->
            BatterySampleEntity(
                id = "sample-$i",
                sessionId = sessionId,
                timestamp = Instant.parse("2026-08-23T10:00:00Z").plusSeconds(i * 5L),
                elapsedMs = i * 5000L,
                percent = 20 + (i * 6),
                voltageMv = 4000 + (i * 20),
                currentNowUa = 15_000_000,
                currentAverageUa = null,
                chargeCounterUah = null,
                energyCounterNwh = null,
                temperatureDeciC = 300 + (i * 5),
                batteryStatus = 2,
                pluggedType = 1,
                cycleCount = null,
                derivedPowerUw = 60_000_000L + (i * 500_000L),
                qualityFlags = emptySet(),
            )
        }
        database.batterySampleDao().insertSamples(sampleEntities)

        // 6. Insert ChargeTransitions
        val transitionEntities = listOf(
            ChargeTransitionEntity(
                id = "t-1",
                sessionId = sessionId,
                fromPercent = 20,
                toPercent = 21,
                startedAt = Instant.parse("2026-08-23T10:00:00Z"),
                endedAt = Instant.parse("2026-08-23T10:01:00Z"),
                durationMs = 60_000L,
                sampleCount = 12,
                quality = DataQuality.GOOD,
            ),
            ChargeTransitionEntity(
                id = "t-2",
                sessionId = sessionId,
                fromPercent = 21,
                toPercent = 22,
                startedAt = Instant.parse("2026-08-23T10:01:00Z"),
                endedAt = Instant.parse("2026-08-23T10:02:00Z"),
                durationMs = 60_000L,
                sampleCount = 12,
                quality = DataQuality.GOOD,
            ),
        )
        database.chargeTransitionDao().insertAll(transitionEntities)

        // 7. Compute summary via repository
        val summary = repository.getSessionSummary(sessionId)

        assertNotNull(summary)
        assertEquals(sessionId, summary?.sessionId)
        assertEquals(20, summary?.startPercent)
        assertEquals(80, summary?.endPercent)
        assertEquals(60, summary?.percentGained)
        assertEquals(true, summary?.isCompleteStandardTest)
        assertEquals(10, summary?.totalSampleCount)
        assertEquals(2, summary?.totalTransitionCount)
        assertEquals(2, summary?.contiguousOnePercentTransitionCount)
        assertEquals(60_000L, summary?.averageTimePerOnePercentMs)
        assertEquals(DataQuality.GOOD, summary?.overallQuality)

        // 8. Test Flow
        val flowSummary = repository.getSessionSummaryFlow(sessionId).first()
        assertNotNull(flowSummary)
        assertEquals(sessionId, flowSummary?.sessionId)
    }
}
