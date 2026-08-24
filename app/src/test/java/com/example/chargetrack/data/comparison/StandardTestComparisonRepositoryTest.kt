package com.example.chargetrack.data.comparison

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.analytics.SessionSummaryRepository
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class StandardTestComparisonRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var summaryRepository: SessionSummaryRepository
    private lateinit var repository: StandardTestComparisonRepository

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

        summaryRepository = SessionSummaryRepository(database)
        repository = StandardTestComparisonRepository(database, summaryRepository)
    }

    @After
    fun tearDown() {
        database.close()
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
    }

    @Test
    fun `01 - setting baseline for group A updates only group A without modifying group B`() = runTest {
        seedTestData()

        val groupA = "group_A"
        val groupB = "group_B"

        // Insert sessions & tests in group A
        database.chargingSessionDao().insert(ChargingSessionEntity(id = "s-a1", startedAt = now, startPercent = 20, endPercent = 80, chargingSetupId = "setup-1", softwareSnapshotId = "snap-1", testType = TestType.STANDARD))
        database.standardTestDao().insert(StandardTestEntity(id = "test-a1", sessionId = "s-a1", targetStartPercent = 20, targetEndPercent = 80, comparisonGroupKey = groupA, isBaseline = true))

        database.chargingSessionDao().insert(ChargingSessionEntity(id = "s-a2", startedAt = now, startPercent = 20, endPercent = 80, chargingSetupId = "setup-1", softwareSnapshotId = "snap-1", testType = TestType.STANDARD))
        database.standardTestDao().insert(StandardTestEntity(id = "test-a2", sessionId = "s-a2", targetStartPercent = 20, targetEndPercent = 80, comparisonGroupKey = groupA, isBaseline = false))

        // Insert baseline in group B
        database.chargingSessionDao().insert(ChargingSessionEntity(id = "s-b1", startedAt = now, startPercent = 20, endPercent = 80, chargingSetupId = "setup-1", softwareSnapshotId = "snap-1", testType = TestType.STANDARD))
        database.standardTestDao().insert(StandardTestEntity(id = "test-b1", sessionId = "s-b1", targetStartPercent = 20, targetEndPercent = 80, comparisonGroupKey = groupB, isBaseline = true))

        // Update baseline in group A to test-a2
        repository.setBaselineForGroup(testId = "test-a2", comparisonGroupKey = groupA)

        // Verifications
        val baselineA = repository.getBaselineForGroup(groupA)
        assertNotNull(baselineA)
        assertEquals("test-a2", baselineA?.id)

        val oldBaselineA = database.standardTestDao().getForSession("s-a1")
        assertEquals(false, oldBaselineA?.isBaseline)

        val baselineB = repository.getBaselineForGroup(groupB)
        assertNotNull(baselineB)
        assertEquals("Group B baseline must remain unchanged", "test-b1", baselineB?.id)
        assertTrue(baselineB?.isBaseline == true)
    }

    @Test
    fun `02 - getStandardTestDataBundle retrieves complete session data`() = runTest {
        seedTestData()

        database.chargingSessionDao().insert(
            ChargingSessionEntity(
                id = "s-bundle",
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
                id = "std-bundle",
                sessionId = "s-bundle",
                targetStartPercent = 20,
                targetEndPercent = 80,
                comparisonGroupKey = "std_key",
            )
        )

        val bundle = repository.getStandardTestDataBundle("s-bundle")

        assertNotNull(bundle)
        assertEquals("s-bundle", bundle?.session?.id)
        assertEquals(20, bundle?.summary?.startPercent)
        assertEquals(80, bundle?.summary?.endPercent)
        assertEquals("iQOO", bundle?.setup?.chargerBrand)
    }
}
