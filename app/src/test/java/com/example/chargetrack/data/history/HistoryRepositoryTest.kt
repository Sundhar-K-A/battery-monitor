package com.example.chargetrack.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.history.DateFilterOption
import com.example.chargetrack.domain.history.HistoryFilter
import com.example.chargetrack.domain.history.HistorySortOption
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
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: HistoryRepository

    private val zoneId = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-08-24T12:00:00Z")

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertSetup(
        id: String = "setup-1",
        brand: String = "iQOO",
        model: String = "100W",
        wattage: Int = 100,
        type: ChargingType = ChargingType.WIRED,
        mode: ChargingMode = ChargingMode.FLASH_CHARGE,
    ) {
        database.chargingSetupDao().insert(
            ChargingSetupEntity(
                id = id,
                chargerBrand = brand,
                chargerModel = model,
                advertisedWattageW = wattage,
                isOfficialCharger = true,
                cableBrand = "iQOO",
                cableModel = "Stock",
                isOfficialCable = true,
                chargingType = type,
                chargingMode = mode,
                createdAt = now,
            )
        )
    }

    private suspend fun insertSoftware(id: String = "snap-1") {
        database.softwareSnapshotDao().insert(
            SoftwareSnapshotEntity(
                id = id,
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

    private suspend fun insertSession(
        id: String = UUID.randomUUID().toString(),
        startedAt: Instant,
        endedAt: Instant? = startedAt.plusSeconds(1200),
        startPercent: Int = 20,
        endPercent: Int? = 80,
        testType: TestType = TestType.FREE_FORM,
        setupId: String = "setup-1",
        softwareId: String = "snap-1",
        endReason: SessionEndReason = SessionEndReason.USER_STOPPED,
    ) {
        database.chargingSessionDao().insert(
            ChargingSessionEntity(
                id = id,
                startedAt = startedAt,
                endedAt = endedAt,
                startPercent = startPercent,
                endPercent = endPercent,
                chargingSetupId = setupId,
                softwareSnapshotId = softwareId,
                testType = testType,
                endReason = endReason,
            )
        )
    }

    private suspend fun insertStandardTest(
        sessionId: String,
        targetStart: Int = 20,
        targetEnd: Int = 80,
        groupKey: String = "standard_20_80_wired_official_iqoo_100w_flash_charge",
    ) {
        database.standardTestDao().insert(
            StandardTestEntity(
                id = "std-$sessionId",
                sessionId = sessionId,
                targetStartPercent = targetStart,
                targetEndPercent = targetEnd,
                comparisonGroupKey = groupKey,
            )
        )
    }

    @Test
    fun `01 - getFilteredSessionsFlow queries lightweight tables without requiring sample loading`() = runTest {
        insertSetup()
        insertSoftware()
        insertSession(id = "s1", startedAt = now, startPercent = 20, endPercent = 80)

        // No samples inserted in database; query succeeds immediately and returns metadata
        val items = repository.getFilteredSessionsFlow(HistoryFilter(), zoneId, now).first()

        assertEquals(1, items.size)
        assertEquals("s1", items[0].sessionId)
        assertEquals(20, items[0].startPercent)
        assertEquals(80, items[0].endPercent)
        assertEquals(60, items[0].percentGained)
        assertEquals("iQOO", items[0].chargingSetup?.chargerBrand)
    }

    @Test
    fun `02 - TODAY filter respects device-local timezone boundaries`() = runTest {
        insertSetup()
        insertSoftware()

        val todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
        val yesterday = todayStart.minusSeconds(3600) // 1 hour before local today
        val todayMidday = todayStart.plusSeconds(3600)

        insertSession(id = "s-yesterday", startedAt = yesterday)
        insertSession(id = "s-today", startedAt = todayMidday)

        val items = repository.getFilteredSessionsFlow(
            HistoryFilter(dateOption = DateFilterOption.TODAY),
            zoneId,
            now,
        ).first()

        assertEquals(1, items.size)
        assertEquals("s-today", items[0].sessionId)
    }

    @Test
    fun `03 - LAST_7_DAYS filter includes 7 local days and excludes older`() = runTest {
        insertSetup()
        insertSoftware()

        val todayStart = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
        val within7Days = todayStart.minus(java.time.Duration.ofDays(5)).plusSeconds(3600)
        val eightDaysAgo = todayStart.minus(java.time.Duration.ofDays(8))

        insertSession(id = "s-within", startedAt = within7Days)
        insertSession(id = "s-old", startedAt = eightDaysAgo)

        val items = repository.getFilteredSessionsFlow(
            HistoryFilter(dateOption = DateFilterOption.LAST_7_DAYS),
            zoneId,
            now,
        ).first()

        assertEquals(1, items.size)
        assertEquals("s-within", items[0].sessionId)
    }

    @Test
    fun `04 - canonical 20-to-80 filter selects only 20-to-80 StandardTest and excludes free-form 15-to-90`() = runTest {
        insertSetup()
        insertSoftware()

        // 1. Canonical 20->80 Standard Test
        insertSession(id = "s-std-20-80", startedAt = now.minusSeconds(100), startPercent = 20, endPercent = 80, testType = TestType.STANDARD)
        insertStandardTest("s-std-20-80", targetStart = 20, targetEnd = 80)

        // 2. Free-form 15->90 session (covers 20->80 range, but NOT a canonical standard test)
        insertSession(id = "s-free-15-90", startedAt = now.minusSeconds(200), startPercent = 15, endPercent = 90, testType = TestType.FREE_FORM)

        // 3. Different Standard Test (10->80)
        insertSession(id = "s-std-10-80", startedAt = now.minusSeconds(300), startPercent = 10, endPercent = 80, testType = TestType.STANDARD)
        insertStandardTest("s-std-10-80", targetStart = 10, targetEnd = 80)

        val items = repository.getFilteredSessionsFlow(
            HistoryFilter(canonical2080Only = true),
            zoneId,
            now,
        ).first()

        assertEquals(1, items.size)
        assertEquals("s-std-20-80", items[0].sessionId)
        assertTrue(items[0].isCanonical2080)
    }

    @Test
    fun `05 - incomplete standard test is correctly marked incomplete`() = runTest {
        insertSetup()
        insertSoftware()

        // Standard test configured for 20->80, but ended early at 67%
        insertSession(
            id = "s-incomplete",
            startedAt = now,
            startPercent = 20,
            endPercent = 67,
            testType = TestType.STANDARD,
            endReason = SessionEndReason.USER_STOPPED,
        )
        insertStandardTest("s-incomplete", targetStart = 20, targetEnd = 80)

        val items = repository.getFilteredSessionsFlow(HistoryFilter(), zoneId, now).first()

        assertEquals(1, items.size)
        assertEquals(false, items[0].isStandardTestComplete)
    }

    @Test
    fun `06 - deleteSession deletes session but protects reusable ChargingSetupEntity`() = runTest {
        insertSetup(id = "setup-protected")
        insertSoftware(id = "snap-1")
        insertSession(id = "s-to-delete", startedAt = now, setupId = "setup-protected")
        insertStandardTest("s-to-delete")

        // Confirm session exists
        assertNotNull(database.chargingSessionDao().getById("s-to-delete"))
        assertNotNull(database.chargingSetupDao().getById("setup-protected"))

        // Delete session
        repository.deleteSession("s-to-delete")

        // Session is deleted
        assertNull(database.chargingSessionDao().getById("s-to-delete"))
        assertNull(database.standardTestDao().getForSession("s-to-delete"))

        // Reusable Setup template is NOT deleted
        assertNotNull("ChargingSetupEntity must be preserved when session is deleted", database.chargingSetupDao().getById("setup-protected"))
    }

    @Test
    fun `07 - sorting by date and duration`() = runTest {
        insertSetup()
        insertSoftware()

        insertSession(id = "s1", startedAt = now.minusSeconds(100), endedAt = now.minusSeconds(100).plusSeconds(300)) // 300s duration
        insertSession(id = "s2", startedAt = now.minusSeconds(50), endedAt = now.minusSeconds(50).plusSeconds(900))   // 900s duration
        insertSession(id = "s3", startedAt = now, endedAt = now.plusSeconds(100))                                      // 100s duration

        // DATE_DESC (default)
        val dateDesc = repository.getFilteredSessionsFlow(HistoryFilter(sortBy = HistorySortOption.DATE_DESC), zoneId, now).first()
        assertEquals(listOf("s3", "s2", "s1"), dateDesc.map { it.sessionId })

        // DATE_ASC
        val dateAsc = repository.getFilteredSessionsFlow(HistoryFilter(sortBy = HistorySortOption.DATE_ASC), zoneId, now).first()
        assertEquals(listOf("s1", "s2", "s3"), dateAsc.map { it.sessionId })

        // DURATION_DESC
        val durDesc = repository.getFilteredSessionsFlow(HistoryFilter(sortBy = HistorySortOption.DURATION_DESC), zoneId, now).first()
        assertEquals(listOf("s2", "s1", "s3"), durDesc.map { it.sessionId })
    }
}
