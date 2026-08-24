package com.example.chargetrack.data.degradation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.data.health.BatteryHealthRepository
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
class LongitudinalRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: LongitudinalRepository
    private lateinit var healthRepository: BatteryHealthRepository

    private val now = Instant.now()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        healthRepository = BatteryHealthRepository(database)
        repository = LongitudinalRepository(database, healthRepository)

        runTest {
            database.deviceProfileDao().insertOrUpdate(
                DeviceProfileEntity(
                    id = "profile-1",
                    manufacturer = "vivo",
                    brand = "iQOO",
                    model = "iQOO 15",
                    device = "I2501",
                    product = "I2501i",
                    androidVersion = "16",
                    sdkInt = 36,
                    buildFingerprint = "fingerprint",
                    buildDisplay = "PD2505",
                    buildIncremental = "inc",
                    typicalCapacityMah = 7000,
                    ratedCapacityMah = 6830,
                    createdAt = now,
                    updatedAt = now,
                )
            )
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
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedStandardTest(
        testId: String,
        sessionId: String,
        groupKey: String,
        isCompleted: Boolean = true,
        isBaseline: Boolean = false,
    ) {
        database.chargingSessionDao().insert(
            ChargingSessionEntity(
                id = sessionId,
                startedAt = now,
                endedAt = now.plusSeconds(1800),
                startPercent = 20,
                endPercent = 80,
                chargingSetupId = "setup-1",
                softwareSnapshotId = "snap-1",
                testType = TestType.STANDARD,
                endReason = SessionEndReason.CHARGING_STOPPED,
            )
        )
        database.standardTestDao().insert(
            StandardTestEntity(
                id = testId,
                sessionId = sessionId,
                comparisonGroupKey = groupKey,
                targetStartPercent = 20,
                targetEndPercent = 80,
                isBaseline = isBaseline,
                baselineSetAt = if (isBaseline) now else null,
                benchmarkStartedElapsedMs = if (isCompleted) 0L else null,
                benchmarkEndedElapsedMs = if (isCompleted) 1_800_000L else null,
            )
        )
        if (isCompleted) {
            database.batterySampleDao().insertSample(
                com.example.chargetrack.data.db.entity.BatterySampleEntity(
                    id = "$sessionId-sample",
                    sessionId = sessionId,
                    timestamp = now,
                    elapsedMs = 1000L,
                    percent = 50,
                    voltageMv = 4000,
                    currentNowUa = 10_000_000,
                )
            )
        }
    }

    @Test
    fun `01 - atomic baseline replacement replaces previous baseline in group`() = runTest {
        seedStandardTest("t1", "s1", "group_A", isBaseline = true)
        seedStandardTest("t2", "s2", "group_A", isBaseline = false)

        // Baseline is initially t1
        val b1 = database.standardTestDao().getBaselineForGroup("group_A")
        assertEquals("t1", b1?.id)

        // Replace baseline with t2
        val success = repository.setGroupBaseline("t2", "group_A")
        assertTrue(success)

        val b2 = database.standardTestDao().getBaselineForGroup("group_A")
        assertEquals("t2", b2?.id)

        // Verify t1 is no longer baseline
        val t1 = database.standardTestDao().getTestsForGroup("group_A").find { it.id == "t1" }
        assertFalse(t1?.isBaseline ?: true)
    }

    @Test
    fun `02 - setting baseline in Group A does not affect Group B`() = runTest {
        seedStandardTest("t_a1", "s_a1", "group_A", isBaseline = true)
        seedStandardTest("t_b1", "s_b1", "group_B", isBaseline = true)

        val success = repository.setGroupBaseline("t_a1", "group_A")
        assertTrue(success)

        // Group B baseline remains intact
        val bB = database.standardTestDao().getBaselineForGroup("group_B")
        assertEquals("t_b1", bB?.id)
    }

    @Test
    fun `03 - setting baseline on incomplete test returns false`() = runTest {
        seedStandardTest("t_inc", "s_inc", "group_A", isCompleted = false)

        val success = repository.setGroupBaseline("t_inc", "group_A")
        assertFalse("Incomplete test cannot be designated as baseline", success)
    }

    @Test
    fun `04 - getGroupTrendAnalysis loads completed tests and returns analysis`() = runTest {
        seedStandardTest("t1", "s1", "group_A", isBaseline = true)
        seedStandardTest("t2", "s2", "group_A", isBaseline = false)

        val groups = repository.getAvailableComparisonGroups()
        assertTrue(groups.contains("group_A"))

        val analysis = repository.getGroupTrendAnalysis("group_A")
        assertEquals("group_A", analysis.comparisonGroupKey)
        assertEquals(2, analysis.points.size)
        assertNotNull(analysis.baselinePoint)
    }
}
