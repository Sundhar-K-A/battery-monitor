package com.example.chargetrack.data.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.BatterySampleEntity
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.health.BatteryHealthEstimate
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
class BatteryHealthRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: BatteryHealthRepository

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

        repository = BatteryHealthRepository(database)
        runTest {
            database.deviceProfileDao().insertOrUpdate(
                com.example.chargetrack.data.db.entity.DeviceProfileEntity(
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
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedMetadata() {
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

    private suspend fun seedFullSession(sessionId: String, capacityUah: Int) {
        database.chargingSessionDao().insert(
            ChargingSessionEntity(
                id = sessionId,
                startedAt = now.minusSeconds(1000),
                endedAt = now,
                startPercent = 20,
                endPercent = 100,
                chargingSetupId = "setup-1",
                softwareSnapshotId = "snap-1",
                testType = TestType.STANDARD,
                endReason = SessionEndReason.USER_STOPPED,
            )
        )
        // Insert sample reaching 100%
        database.batterySampleDao().insertSample(
            BatterySampleEntity(
                id = "$sessionId-sample",
                sessionId = sessionId,
                timestamp = now,
                elapsedMs = 1000L,
                percent = 100,
                chargeCounterUah = capacityUah,
            )
        )
    }

    @Test
    fun `01 - empty database produces InsufficientData`() = runTest {
        val result = repository.getEstimatedBatteryHealth()
        assertTrue(result is BatteryHealthEstimate.InsufficientData)
        assertEquals(0, (result as BatteryHealthEstimate.InsufficientData).observationCount)
    }

    @Test
    fun `02 - three qualifying full sessions calculate estimated health`() = runTest {
        seedMetadata()
        seedFullSession("s1", 6_700_000) // 6700 mAh
        seedFullSession("s2", 6_720_000) // 6720 mAh
        seedFullSession("s3", 6_710_000) // 6710 mAh (median = 6710 mAh)

        val result = repository.getEstimatedBatteryHealth()
        assertTrue(result is BatteryHealthEstimate.Calculated)
        val calculated = result as BatteryHealthEstimate.Calculated

        assertEquals(6710, calculated.medianCapacityMah)
        assertEquals(3, calculated.observationCount)
        assertNotNull(calculated.lastObservationAt)
    }
}
