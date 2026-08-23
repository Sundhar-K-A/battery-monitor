package com.example.chargetrack.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.converter.RoomTypeConverters
import com.example.chargetrack.data.db.dao.BatterySampleDao
import com.example.chargetrack.data.db.dao.ChargeTransitionDao
import com.example.chargetrack.data.db.dao.ChargingSessionDao
import com.example.chargetrack.data.db.dao.ChargingSetupDao
import com.example.chargetrack.data.db.dao.DeviceProfileDao
import com.example.chargetrack.data.db.dao.SoftwareSnapshotDao
import com.example.chargetrack.data.db.dao.StandardTestDao
import com.example.chargetrack.data.db.entity.BatterySampleEntity
import com.example.chargetrack.data.db.entity.ChargeTransitionEntity
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.enums.TestValidity
import kotlinx.coroutines.flow.first
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RoomDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var deviceProfileDao: DeviceProfileDao
    private lateinit var softwareSnapshotDao: SoftwareSnapshotDao
    private lateinit var chargingSetupDao: ChargingSetupDao
    private lateinit var chargingSessionDao: ChargingSessionDao
    private lateinit var batterySampleDao: BatterySampleDao
    private lateinit var chargeTransitionDao: ChargeTransitionDao
    private lateinit var standardTestDao: StandardTestDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        deviceProfileDao = database.deviceProfileDao()
        softwareSnapshotDao = database.softwareSnapshotDao()
        chargingSetupDao = database.chargingSetupDao()
        chargingSessionDao = database.chargingSessionDao()
        batterySampleDao = database.batterySampleDao()
        chargeTransitionDao = database.chargeTransitionDao()
        standardTestDao = database.standardTestDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── Helper methods for seeding prerequisites ─────────────────────────

    private suspend fun seedSetup(id: String = "setup-1", isTemplate: Boolean = false): ChargingSetupEntity {
        val setup = ChargingSetupEntity(
            id = id,
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
            isTemplate = isTemplate,
        )
        chargingSetupDao.insert(setup)
        return setup
    }

    private suspend fun seedSnapshot(id: String = "snapshot-1"): SoftwareSnapshotEntity {
        val snapshot = SoftwareSnapshotEntity(
            id = id,
            capturedAt = Instant.parse("2026-08-23T10:00:00Z"),
            androidVersion = "16",
            sdkInt = 36,
            originOsVersion = "OriginOS 6",
            buildFingerprint = "vivo/iQOO15/iQOO15:16/...",
            appVersionName = "1.0",
            appVersionCode = 1,
        )
        softwareSnapshotDao.insert(snapshot)
        return snapshot
    }

    private suspend fun seedSession(
        id: String = "session-1",
        setupId: String = "setup-1",
        snapshotId: String = "snapshot-1",
        startedAt: Instant = Instant.parse("2026-08-23T10:00:00Z"),
        startPercent: Int = 20,
    ): ChargingSessionEntity {
        val session = ChargingSessionEntity(
            id = id,
            startedAt = startedAt,
            startPercent = startPercent,
            chargingSetupId = setupId,
            softwareSnapshotId = snapshotId,
            testType = TestType.STANDARD,
        )
        chargingSessionDao.insert(session)
        return session
    }

    // ── DeviceProfileDao Tests ───────────────────────────────────────────

    @Test
    fun `insert and retrieve device profile`() = runTest {
        val profile = DeviceProfileEntity(
            id = "profile-1",
            manufacturer = "vivo",
            brand = "iQOO",
            model = "iQOO 15",
            device = "iQOO15",
            product = "iQOO15",
            androidVersion = "16",
            sdkInt = 36,
            buildFingerprint = "fingerprint-123",
            buildDisplay = "OriginOS 6.0",
            buildIncremental = "inc-123",
            originOsBuildLabel = "OriginOS 6.0",
            matchedDeviceName = "iQOO 15",
            typicalCapacityMah = 7000,
            ratedCapacityMah = 6830,
            typicalEnergyWh = 26.25,
            ratedEnergyWh = 25.62,
            wiredReferenceW = 100,
            wirelessReferenceW = 40,
            nickname = "My Daily Driver",
            purchaseDate = LocalDate.of(2026, 1, 15),
            firstUseDate = LocalDate.of(2026, 1, 16),
            ramStorageVariant = "16GB/512GB",
            notes = "Test device",
            createdAt = Instant.parse("2026-08-23T10:00:00Z"),
            updatedAt = Instant.parse("2026-08-23T10:00:00Z"),
            isOnboardingComplete = true,
        )

        deviceProfileDao.insertOrUpdate(profile)

        val retrieved = deviceProfileDao.getProfile()
        assertNotNull(retrieved)
        assertEquals("iQOO 15", retrieved?.model)
        assertEquals(7000, retrieved?.typicalCapacityMah)
        assertEquals(100, retrieved?.wiredReferenceW)
        assertEquals(LocalDate.of(2026, 1, 15), retrieved?.purchaseDate)
        assertTrue(retrieved?.isOnboardingComplete == true)

        val flowResult = deviceProfileDao.getProfileFlow().first()
        assertEquals(profile.id, flowResult?.id)
    }

    // ── SoftwareSnapshotDao Tests ────────────────────────────────────────

    @Test
    fun `insert and retrieve software snapshot`() = runTest {
        seedSnapshot("snap-1")
        val retrieved = softwareSnapshotDao.getById("snap-1")
        assertNotNull(retrieved)
        assertEquals("16", retrieved?.androidVersion)
        assertEquals(36, retrieved?.sdkInt)
        assertEquals("OriginOS 6", retrieved?.originOsVersion)
    }

    // ── ChargingSetupDao Tests ───────────────────────────────────────────

    @Test
    fun `insert and query template setups`() = runTest {
        seedSetup("setup-1", isTemplate = false)
        seedSetup("setup-template-1", isTemplate = true)

        val all = chargingSetupDao.getAllSetupsFlow().first()
        assertEquals(2, all.size)

        val templates = chargingSetupDao.getAllTemplatesFlow().first()
        assertEquals(1, templates.size)
        assertEquals("setup-template-1", templates[0].id)
    }

    @Test
    fun `setupSnapshotImmutableForSession verifies template edits do not mutate session snapshot and deletion is restricted`() = runTest {
        // 1. User creates a reusable template
        val template = ChargingSetupEntity(
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
            isTemplate = true,
        )
        chargingSetupDao.insert(template)

        // 2. Session starts from template -> creates immutable session snapshot
        val sessionSnapshot = template.copy(
            id = "snapshot-for-session-100",
            isTemplate = false,
            createdAt = Instant.parse("2026-08-23T10:05:00Z")
        )
        chargingSetupDao.insert(sessionSnapshot)
        seedSnapshot("snap-1")
        seedSession("session-100", setupId = sessionSnapshot.id, snapshotId = "snap-1")

        // 3. User later edits the template in settings
        val editedTemplate = template.copy(
            chargerBrand = "Anker",
            chargerModel = "GaNPrime 65W",
            advertisedWattageW = 65,
            protocol = "PD 3.0",
            isOfficialCharger = false,
            cableBrand = "Anker",
            cableModel = "PowerLine III",
            isOfficialCable = false,
            chargingMode = ChargingMode.NORMAL,
        )
        chargingSetupDao.update(editedTemplate)

        // 4. Verify template in DB has the updated values
        val retrievedTemplate = chargingSetupDao.getById("template-official-100w")
        assertNotNull(retrievedTemplate)
        assertEquals("Anker", retrievedTemplate?.chargerBrand)
        assertEquals(65, retrievedTemplate?.advertisedWattageW)
        assertEquals(ChargingMode.NORMAL, retrievedTemplate?.chargingMode)
        assertFalse(retrievedTemplate?.isOfficialCharger == true)

        // 5. Verify session snapshot in DB is completely UNCHANGED
        val retrievedSessionSnapshot = chargingSetupDao.getById("snapshot-for-session-100")
        assertNotNull(retrievedSessionSnapshot)
        assertEquals("iQOO", retrievedSessionSnapshot?.chargerBrand)
        assertEquals("FlashCharge 100W", retrievedSessionSnapshot?.chargerModel)
        assertEquals(100, retrievedSessionSnapshot?.advertisedWattageW)
        assertEquals(ChargingMode.FLASH_CHARGE, retrievedSessionSnapshot?.chargingMode)
        assertTrue(retrievedSessionSnapshot?.isOfficialCharger == true)
        assertFalse(retrievedSessionSnapshot?.isTemplate == true)

        // 6. Verify session still references the unchanged snapshot
        val session = chargingSessionDao.getById("session-100")
        assertEquals("snapshot-for-session-100", session?.chargingSetupId)

        // 7. Verify session snapshot cannot be deleted due to RESTRICT foreign key
        try {
            chargingSetupDao.delete("snapshot-for-session-100")
            org.junit.Assert.fail("Expected SQLiteConstraintException when deleting referenced setup snapshot")
        } catch (e: SQLiteConstraintException) {
            // Expected RESTRICT behavior
        }
    }

    // ── ChargingSessionDao Tests ─────────────────────────────────────────

    @Test
    fun `insert, observe, and finalize charging session`() = runTest {
        seedSetup("setup-1")
        seedSnapshot("snap-1")
        seedSession("session-1", "setup-1", "snap-1", startPercent = 20)

        val active = chargingSessionDao.getActiveSessionFlow().first()
        assertNotNull(active)
        assertEquals("session-1", active?.id)
        assertNull(active?.endedAt)

        val endInstant = Instant.parse("2026-08-23T10:25:00Z")
        chargingSessionDao.finalizeSession("session-1", endInstant, 80, SessionEndReason.CHARGING_STOPPED)

        val updated = chargingSessionDao.getById("session-1")
        assertNotNull(updated)
        assertEquals(endInstant, updated?.endedAt)
        assertEquals(80, updated?.endPercent)
        assertEquals(SessionEndReason.CHARGING_STOPPED, updated?.endReason)

        val activeAfterFinalize = chargingSessionDao.getActiveSessionFlow().first()
        assertNull(activeAfterFinalize)
    }

    // ── BatterySampleDao Tests ───────────────────────────────────────────

    @Test
    fun `insert samples, verify monotonic order and null preservation`() = runTest {
        seedSetup("setup-1")
        seedSnapshot("snap-1")
        seedSession("session-1", "setup-1", "snap-1")

        val sample1 = BatterySampleEntity(
            id = "sample-1",
            sessionId = "session-1",
            timestamp = Instant.parse("2026-08-23T10:00:00Z"),
            elapsedMs = 0L,
            percent = 20,
            voltageMv = 4050,
            currentNowUa = 15000000,
            currentAverageUa = null,      // Unavailable stays null
            chargeCounterUah = null,      // Unavailable stays null
            energyCounterNwh = null,      // Unavailable stays null
            temperatureDeciC = 295,
            derivedPowerUw = 60750000L,
            qualityFlags = setOf(QualityFlag.GAP_DETECTED),
        )

        val sample2 = BatterySampleEntity(
            id = "sample-2",
            sessionId = "session-1",
            timestamp = Instant.parse("2026-08-23T10:00:05Z"),
            elapsedMs = 5000L,
            percent = 21,
            voltageMv = 4080,
            currentNowUa = 14500000,
            currentAverageUa = null,
            temperatureDeciC = 300,
            derivedPowerUw = 59160000L,
            qualityFlags = emptySet(),
        )

        val row1 = batterySampleDao.insertSample(sample1)
        val row2 = batterySampleDao.insertSample(sample2)
        assertTrue(row1 > 0)
        assertTrue(row2 > 0)

        val samples = batterySampleDao.getSamplesForSessionOrdered("session-1")
        assertEquals(2, samples.size)
        assertEquals(0L, samples[0].elapsedMs)
        assertEquals(5000L, samples[1].elapsedMs)
        assertNull(samples[0].chargeCounterUah)
        assertNull(samples[0].energyCounterNwh)
        assertEquals(setOf(QualityFlag.GAP_DETECTED), samples[0].qualityFlags)
    }

    @Test
    fun `duplicate sample with same sessionId and elapsedMs is ignored`() = runTest {
        seedSetup("setup-1")
        seedSnapshot("snap-1")
        seedSession("session-1", "setup-1", "snap-1")

        val sample1 = BatterySampleEntity(
            id = "sample-1",
            sessionId = "session-1",
            timestamp = Instant.parse("2026-08-23T10:00:00Z"),
            elapsedMs = 5000L,
            percent = 20,
            voltageMv = 4000,
            currentNowUa = 10000000,
        )

        val sampleDuplicate = BatterySampleEntity(
            id = "sample-2-different-id-same-elapsed",
            sessionId = "session-1",
            timestamp = Instant.parse("2026-08-23T10:00:00Z"),
            elapsedMs = 5000L, // Same session + elapsedMs
            percent = 20,
            voltageMv = 4000,
            currentNowUa = 10000000,
        )

        val row1 = batterySampleDao.insertSample(sample1)
        val row2 = batterySampleDao.insertSample(sampleDuplicate)

        assertTrue(row1 > 0)
        assertEquals(-1L, row2) // IGNORE returns -1 for ignored row

        val count = batterySampleDao.getSampleCountForSession("session-1")
        assertEquals(1, count)
    }

    // ── ChargeTransitionDao Tests ────────────────────────────────────────

    @Test
    fun `insert and retrieve charge transitions`() = runTest {
        seedSetup("setup-1")
        seedSnapshot("snap-1")
        seedSession("session-1", "setup-1", "snap-1")

        val transition = ChargeTransitionEntity(
            id = "trans-1",
            sessionId = "session-1",
            fromPercent = 20,
            toPercent = 21,
            startedAt = Instant.parse("2026-08-23T10:00:00Z"),
            endedAt = Instant.parse("2026-08-23T10:00:35Z"),
            durationMs = 35000L,
            averagePowerUw = 65000000L,
            medianPowerUw = 65000000L,
            peakPowerUw = 70000000L,
            averageTemperatureDeciC = 305,
            maxTemperatureDeciC = 310,
            sampleCount = 7,
            quality = DataQuality.GOOD,
        )

        chargeTransitionDao.insert(transition)

        val transitions = chargeTransitionDao.getTransitionsForSession("session-1")
        assertEquals(1, transitions.size)
        assertEquals(20, transitions[0].fromPercent)
        assertEquals(21, transitions[0].toPercent)
        assertEquals(35000L, transitions[0].durationMs)
        assertEquals(DataQuality.GOOD, transitions[0].quality)
    }

    // ── StandardTestDao Tests ────────────────────────────────────────────

    @Test
    fun `insert standard test and set baseline scoped to comparison group`() = runTest {
        seedSetup("setup-1")
        seedSnapshot("snap-1")
        seedSession("session-1", "setup-1", "snap-1")
        seedSession("session-2", "setup-1", "snap-1")

        val test1 = StandardTestEntity(
            id = "st-1",
            sessionId = "session-1",
            targetStartPercent = 20,
            targetEndPercent = 80,
            comparisonGroupKey = "standard_20_80_wired_official",
            isBaseline = false,
            validity = TestValidity.VALID,
        )

        val test2 = StandardTestEntity(
            id = "st-2",
            sessionId = "session-2",
            targetStartPercent = 20,
            targetEndPercent = 80,
            comparisonGroupKey = "standard_20_80_wired_official",
            isBaseline = false,
            validity = TestValidity.VALID,
        )

        standardTestDao.insert(test1)
        standardTestDao.insert(test2)

        // Set test1 as baseline
        val now1 = Instant.parse("2026-08-23T11:00:00Z")
        standardTestDao.setBaselineForGroup("st-1", "standard_20_80_wired_official", now1)

        var baseline = standardTestDao.getBaselineForGroup("standard_20_80_wired_official")
        assertEquals("st-1", baseline?.id)
        assertTrue(baseline?.isBaseline == true)
        assertEquals(now1, baseline?.baselineSetAt)

        // Set test2 as new baseline -> unsets test1
        val now2 = Instant.parse("2026-08-23T12:00:00Z")
        standardTestDao.setBaselineForGroup("st-2", "standard_20_80_wired_official", now2)

        baseline = standardTestDao.getBaselineForGroup("standard_20_80_wired_official")
        assertEquals("st-2", baseline?.id)
        assertTrue(baseline?.isBaseline == true)

        val oldTest1 = standardTestDao.getForSession("session-1")
        assertFalse(oldTest1?.isBaseline == true)
        assertNull(oldTest1?.baselineSetAt)
    }

    // ── Cascade Deletion and Referential Integrity Tests ──────────────────

    @Test
    fun `deleting a session cascades and deletes samples, transitions, and standard tests`() = runTest {
        seedSetup("setup-1")
        seedSnapshot("snap-1")
        seedSession("session-1", "setup-1", "snap-1")

        batterySampleDao.insertSample(
            BatterySampleEntity(id = "s-1", sessionId = "session-1", timestamp = Instant.now(), elapsedMs = 0L)
        )
        chargeTransitionDao.insert(
            ChargeTransitionEntity(
                id = "t-1",
                sessionId = "session-1",
                fromPercent = 20,
                toPercent = 21,
                startedAt = Instant.now(),
                endedAt = Instant.now(),
                durationMs = 30000L,
                sampleCount = 6,
            )
        )
        standardTestDao.insert(
            StandardTestEntity(id = "st-1", sessionId = "session-1", comparisonGroupKey = "standard_20_80_wired_official")
        )

        assertEquals(1, batterySampleDao.getSampleCountForSession("session-1"))
        assertEquals(1, chargeTransitionDao.getTransitionsForSession("session-1").size)
        assertNotNull(standardTestDao.getForSession("session-1"))

        // Delete session
        chargingSessionDao.deleteSession("session-1")

        assertEquals(0, batterySampleDao.getSampleCountForSession("session-1"))
        assertEquals(0, chargeTransitionDao.getTransitionsForSession("session-1").size)
        assertNull(standardTestDao.getForSession("session-1"))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `deleting a referenced charging setup throws constraint exception`() = runTest {
        seedSetup("setup-1")
        seedSnapshot("snap-1")
        seedSession("session-1", "setup-1", "snap-1")

        // setup-1 is referenced by session-1 with RESTRICT
        chargingSetupDao.delete("setup-1")
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `deleting a referenced software snapshot throws constraint exception`() = runTest {
        seedSetup("setup-1")
        seedSnapshot("snap-1")
        seedSession("session-1", "setup-1", "snap-1")

        // snap-1 is referenced by session-1 with RESTRICT
        softwareSnapshotDao.delete("snap-1")
    }

    // ── TypeConverters Tests ──────────────────────────────────────────────

    @Test
    fun `type converters convert correctly and safely handle unrecognized tokens`() {
        val converters = RoomTypeConverters()

        // Instant
        val now = Instant.now()
        val epochMs = converters.instantToEpochMs(now)
        assertEquals(now.toEpochMilli(), converters.epochMsToInstant(epochMs)?.toEpochMilli())

        // LocalDate
        val today = LocalDate.of(2026, 8, 23)
        val epochDay = converters.localDateToEpochDay(today)
        assertEquals(today, converters.epochDayToLocalDate(epochDay))

        // Set<QualityFlag>
        val flags = setOf(QualityFlag.GAP_DETECTED, QualityFlag.OUTLIER)
        val flagsString = converters.qualityFlagsToString(flags)
        assertEquals(flags, converters.stringToQualityFlags(flagsString))
        assertEquals(emptySet<QualityFlag>(), converters.stringToQualityFlags(""))

        // Token fallbacks for unexpected values
        assertEquals(ChargingType.UNKNOWN, converters.stringToChargingType("non_existent_type"))
        assertEquals(ChargingMode.UNKNOWN, converters.stringToChargingMode("non_existent_mode"))
        assertEquals(SessionEndReason.UNKNOWN, converters.stringToSessionEndReason("unknown_future_reason"))
        assertEquals(TestValidity.INVALID, converters.stringToTestValidity("unrecognized_validity"))
        assertEquals(DataQuality.INSUFFICIENT, converters.stringToDataQuality("unrecognized_quality"))
    }
}
