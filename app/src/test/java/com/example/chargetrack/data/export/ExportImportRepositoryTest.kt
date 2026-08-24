package com.example.chargetrack.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.BatterySampleEntity
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.export.DuplicateStrategy
import com.example.chargetrack.domain.export.ExportFormat
import com.example.chargetrack.domain.export.ImportResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ExportImportRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: ExportImportRepository

    private val now = Instant.now()
    private val sessionId = "sess-repo-test-1"

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        repository = ExportImportRepository(database)

        // Seed initial session in database
        database.deviceProfileDao().insertOrUpdate(
            DeviceProfileEntity(
                id = "prof-1",
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
                protocol = "FlashCharge",
                isOfficialCharger = true,
                cableBrand = "iQOO",
                cableModel = "Stock",
                isOfficialCable = true,
                chargingType = ChargingType.WIRED,
                chargingMode = ChargingMode.FLASH_CHARGE,
                isTemplate = false,
                notes = null,
                createdAt = now,
            )
        )
        database.softwareSnapshotDao().insert(
            SoftwareSnapshotEntity("snap-1", now, "16", 36, "PD2505", "fingerprint", "1.0", 1)
        )
        database.chargingSessionDao().insert(
            ChargingSessionEntity(
                id = sessionId,
                startedAt = now.minusSeconds(1200),
                endedAt = now,
                startPercent = 20,
                endPercent = 80,
                chargingSetupId = "setup-1",
                softwareSnapshotId = "snap-1",
                testType = TestType.STANDARD,
                userNotes = "Initial session",
                endReason = SessionEndReason.USER_STOPPED,
            )
        )
        database.standardTestDao().insert(
            StandardTestEntity(
                id = "test-1",
                sessionId = sessionId,
                comparisonGroupKey = "standard_20_80_wired_official",
                targetStartPercent = 20,
                targetEndPercent = 80,
                benchmarkStartedElapsedMs = 1000L,
                benchmarkEndedElapsedMs = 1100000L,
            )
        )
        database.batterySampleDao().insertSamples(
            listOf(
                BatterySampleEntity(
                    id = "samp-1",
                    sessionId = sessionId,
                    timestamp = now.minusSeconds(1200),
                    elapsedMs = 0L,
                    percent = 20,
                    voltageMv = 7600,
                    currentNowUa = 5000000,
                    derivedPowerUw = 38000000L,
                    temperatureDeciC = 280,
                    chargeCounterUah = 1400000,
                ),
                BatterySampleEntity(
                    id = "samp-2",
                    sessionId = sessionId,
                    timestamp = now,
                    elapsedMs = 1200000L,
                    percent = 80,
                    voltageMv = 8400,
                    currentNowUa = 2000000,
                    derivedPowerUw = 16800000L,
                    temperatureDeciC = 370,
                    chargeCounterUah = 5600000,
                )
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `01 - export and import round-trip preserves session structure and sample data`() = runTest {
        // Export to JSON
        val outputStream = ByteArrayOutputStream()
        repository.exportSession(sessionId, ExportFormat.JSON, outputStream)
        val exportedJson = outputStream.toByteArray()

        // Clear session from database
        database.chargingSessionDao().deleteSession(sessionId)
        assertEquals(0, database.batterySampleDao().getSampleCountForSession(sessionId))

        // Import session back
        val inputStream = ByteArrayInputStream(exportedJson)
        val result = repository.importSession(inputStream, DuplicateStrategy.REJECT)

        assertTrue("Import should succeed", result is ImportResult.Success)
        val success = result as ImportResult.Success
        assertEquals(sessionId, success.sessionId)
        assertEquals(2, success.sampleCount)

        // Verify database state
        val reloadedSession = database.chargingSessionDao().getById(sessionId)
        assertNotNull(reloadedSession)
        assertEquals(20, reloadedSession!!.startPercent)
        assertEquals(80, reloadedSession.endPercent)

        val reloadedSamples = database.batterySampleDao().getSamplesForSessionOrdered(sessionId)
        assertEquals(2, reloadedSamples.size)
        assertEquals(38000000L, reloadedSamples[0].derivedPowerUw)
    }

    @Test
    fun `02 - duplicate detection returns Duplicate when strategy is REJECT`() = runTest {
        val outputStream = ByteArrayOutputStream()
        repository.exportSession(sessionId, ExportFormat.JSON, outputStream)
        val exportedJson = outputStream.toByteArray()

        // Attempt import without deleting original session
        val inputStream = ByteArrayInputStream(exportedJson)
        val result = repository.importSession(inputStream, DuplicateStrategy.REJECT)

        assertTrue("Should detect duplicate", result is ImportResult.Duplicate)
        assertEquals(sessionId, (result as ImportResult.Duplicate).existingSessionId)
    }

    @Test
    fun `03 - ASSIGN_NEW_ID creates an independent duplicate session alongside original`() = runTest {
        val outputStream = ByteArrayOutputStream()
        repository.exportSession(sessionId, ExportFormat.JSON, outputStream)
        val exportedJson = outputStream.toByteArray()

        val inputStream = ByteArrayInputStream(exportedJson)
        val result = repository.importSession(inputStream, DuplicateStrategy.ASSIGN_NEW_ID)

        assertTrue("Should succeed with ASSIGN_NEW_ID", result is ImportResult.Success)
        val newSessionId = (result as ImportResult.Success).sessionId
        assertTrue(newSessionId != sessionId)

        // Both original and new sessions exist in database
        assertNotNull(database.chargingSessionDao().getById(sessionId))
        assertNotNull(database.chargingSessionDao().getById(newSessionId))

        assertEquals(2, database.batterySampleDao().getSampleCountForSession(sessionId))
        assertEquals(2, database.batterySampleDao().getSampleCountForSession(newSessionId))
    }

    @Test
    fun `04 - OVERWRITE atomically replaces existing session record and samples`() = runTest {
        val outputStream = ByteArrayOutputStream()
        repository.exportSession(sessionId, ExportFormat.JSON, outputStream)
        val exportedJson = outputStream.toByteArray()

        val inputStream = ByteArrayInputStream(exportedJson)
        val result = repository.importSession(inputStream, DuplicateStrategy.OVERWRITE)

        assertTrue("Should succeed with OVERWRITE", result is ImportResult.Success)
        assertEquals(sessionId, (result as ImportResult.Success).sessionId)
        assertEquals(2, database.batterySampleDao().getSampleCountForSession(sessionId))
    }
}
