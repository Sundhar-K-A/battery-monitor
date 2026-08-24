package com.example.chargetrack.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.history.HistoryFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class HistorySoftwareTransitionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: HistoryRepository

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

        repository = HistoryRepository(database)

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
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `history sessions correctly receive firmware and app update flags across chronological sessions`() = runTest {
        // Snapshots:
        // snap1: FW 1, App 1.0
        // snap2: FW 1, App 1.1 (App updated)
        // snap3: FW 2, App 1.1 (Firmware updated)
        // snap4: FW 2, App 1.1 (Unchanged)
        val snap1 = SoftwareSnapshotEntity("snap-1", now.minusSeconds(4000), "16", 36, "PD2505_A", "fp1", "1.0", 1)
        val snap2 = SoftwareSnapshotEntity("snap-2", now.minusSeconds(3000), "16", 36, "PD2505_A", "fp1", "1.1", 2)
        val snap3 = SoftwareSnapshotEntity("snap-3", now.minusSeconds(2000), "16", 36, "PD2505_B", "fp2", "1.1", 2)
        val snap4 = SoftwareSnapshotEntity("snap-4", now.minusSeconds(1000), "16", 36, "PD2505_B", "fp2", "1.1", 2)

        database.softwareSnapshotDao().insert(snap1)
        database.softwareSnapshotDao().insert(snap2)
        database.softwareSnapshotDao().insert(snap3)
        database.softwareSnapshotDao().insert(snap4)

        database.chargingSessionDao().insert(
            ChargingSessionEntity("s1", now.minusSeconds(4000), now.minusSeconds(3500), 20, 80, "setup-1", "snap-1", TestType.FREE_FORM, null, SessionEndReason.CHARGING_STOPPED)
        )
        database.chargingSessionDao().insert(
            ChargingSessionEntity("s2", now.minusSeconds(3000), now.minusSeconds(2500), 20, 80, "setup-1", "snap-2", TestType.FREE_FORM, null, SessionEndReason.CHARGING_STOPPED)
        )
        database.chargingSessionDao().insert(
            ChargingSessionEntity("s3", now.minusSeconds(2000), now.minusSeconds(1500), 20, 80, "setup-1", "snap-3", TestType.FREE_FORM, null, SessionEndReason.CHARGING_STOPPED)
        )
        database.chargingSessionDao().insert(
            ChargingSessionEntity("s4", now.minusSeconds(1000), now.minusSeconds(500), 20, 80, "setup-1", "snap-4", TestType.FREE_FORM, null, SessionEndReason.CHARGING_STOPPED)
        )

        val items = repository.getFilteredSessionsFlow(HistoryFilter()).first()

        // History items default sort is newest first: [s4, s3, s2, s1]
        val s1Item = items.find { it.sessionId == "s1" }!!
        val s2Item = items.find { it.sessionId == "s2" }!!
        val s3Item = items.find { it.sessionId == "s3" }!!
        val s4Item = items.find { it.sessionId == "s4" }!!

        // s1: baseline first session
        assertFalse(s1Item.isFirmwareUpdateSession)
        assertFalse(s1Item.isAppUpdateSession)

        // s2: App updated only
        assertFalse(s2Item.isFirmwareUpdateSession)
        assertTrue("s2 should have app updated flag", s2Item.isAppUpdateSession)

        // s3: Firmware updated only
        assertTrue("s3 should have firmware updated flag", s3Item.isFirmwareUpdateSession)
        assertFalse(s3Item.isAppUpdateSession)

        // s4: Neither updated
        assertFalse(s4Item.isFirmwareUpdateSession)
        assertFalse(s4Item.isAppUpdateSession)
    }
}
