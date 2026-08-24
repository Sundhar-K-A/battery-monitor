package com.example.chargetrack.domain.export

import com.example.chargetrack.domain.analytics.SessionSummaryAnalyticsCalculator
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.DeviceProfile
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SessionImportEngineTest {

    private val now = Instant.now()

    private fun createValidBundle(): FullSessionBundle {
        val session = ChargingSession(
            id = "orig-session-id",
            startedAt = now.minusSeconds(1200),
            endedAt = now,
            startPercent = 20,
            endPercent = 80,
            chargingSetupId = "orig-setup-id",
            softwareSnapshotId = "orig-snap-id",
            testType = TestType.STANDARD,
            userNotes = "Original notes",
            endReason = SessionEndReason.USER_STOPPED,
        )
        val setup = ChargingSetup(
            id = "orig-setup-id",
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
            createdAt = now.minusSeconds(5000),
        )
        val snapshot = SoftwareSnapshot(
            id = "orig-snap-id",
            capturedAt = now.minusSeconds(1200),
            androidVersion = "16",
            sdkInt = 36,
            originOsVersion = "PD2505_Build_1",
            buildFingerprint = "fingerprint",
            appVersionName = "1.0",
            appVersionCode = 1,
        )
        val profile = DeviceProfile(
            id = "profile-1",
            manufacturer = "vivo",
            brand = "iQOO",
            model = "iQOO 15",
            device = "I2501",
            product = "I2501i",
            androidVersion = "16",
            sdkInt = 36,
            buildFingerprint = "fingerprint",
            originOsBuildLabel = "PD2505",
            typicalCapacityMah = 7000,
            ratedCapacityMah = 6830,
            createdAt = now,
            updatedAt = now,
        )
        val standardTest = StandardTest(
            id = "orig-test-id",
            sessionId = "orig-session-id",
            comparisonGroupKey = "standard_20_80_wired_official",
            targetStartPercent = 20,
            targetEndPercent = 80,
            benchmarkStartedElapsedMs = 5000L,
            benchmarkEndedElapsedMs = 1150000L,
        )
        val samples = listOf(
            BatterySample(
                id = "orig-sample-1",
                sessionId = "orig-session-id",
                timestamp = now.minusSeconds(1200),
                elapsedMs = 0L,
                percent = 20,
                voltageMv = 7600,
                currentNowUa = 5_000_000,
                derivedPowerUw = 38_000_000L,
                temperatureDeciC = 280,
                chargeCounterUah = 1_400_000,
            ),
            BatterySample(
                id = "orig-sample-2",
                sessionId = "orig-session-id",
                timestamp = now,
                elapsedMs = 1200000L,
                percent = 80,
                voltageMv = 8400,
                currentNowUa = -200_000, // Negative signed current preserved
                derivedPowerUw = -1_680_000L, // Negative signed power preserved
                temperatureDeciC = null, // Null preserved
                chargeCounterUah = null, // Null preserved
            )
        )
        val transitions = listOf(
            ChargeTransition(
                id = "orig-trans-1",
                sessionId = "orig-session-id",
                fromPercent = 20,
                toPercent = 21,
                startedAt = now.minusSeconds(1200),
                endedAt = now.minusSeconds(1180),
                durationMs = 20000L,
                averagePowerUw = 40000000L,
                peakPowerUw = 42000000L,
                averageTemperatureDeciC = 285,
                sampleCount = 5,
            )
        )

        return FullSessionBundle(session, setup, snapshot, profile, standardTest, samples, transitions)
    }

    @Test
    fun `01 - structural validation succeeds on valid JSON and rejects corrupted or future versions`() {
        val bundle = createValidBundle()
        val validJson = SessionExportEngine.generateJson(bundle)

        val validResult = SessionImportEngine.validateAndParse(validJson)
        assertTrue("Valid JSON must produce ImportValidationResult.Valid", validResult is ImportValidationResult.Valid)

        // Invalid JSON syntax
        val malformedResult = SessionImportEngine.validateAndParse("{ not valid json }")
        assertTrue("Malformed JSON must produce Invalid", malformedResult is ImportValidationResult.Invalid)

        // Unsupported future schema version
        val futureJson = JSONObject(validJson).apply { put("schemaVersion", 2) }.toString()
        val futureResult = SessionImportEngine.validateAndParse(futureJson)
        assertTrue("Future schema version must produce UnsupportedVersion", futureResult is ImportValidationResult.UnsupportedVersion)

        // Missing required 'samples' block
        val missingSamplesJson = JSONObject(validJson).apply { remove("samples") }.toString()
        val missingResult = SessionImportEngine.validateAndParse(missingSamplesJson)
        assertTrue("Missing samples must produce Invalid", missingResult is ImportValidationResult.Invalid)
    }

    @Test
    fun `02 - tampered derivedAnalytics does not override raw telemetry as ground truth`() {
        val bundle = createValidBundle()
        val jsonObj = JSONObject(SessionExportEngine.generateJson(bundle))

        // Tamper with the derivedAnalytics block in the JSON (fabricate 9999W average power)
        val fakeAnalytics = JSONObject().apply {
            put("sessionId", bundle.session.id)
            put("durationMs", 999999L)
            put("averagePowerUw", 999_999_999L) // 999.9W fake power
            put("peakPowerUw", 999_999_999L)
            put("overallQuality", "CLEAN")
            put("outlierSampleCount", 0)
        }
        jsonObj.put("derivedAnalytics", fakeAnalytics)

        val validation = SessionImportEngine.validateAndParse(jsonObj.toString())
        assertTrue(validation is ImportValidationResult.Valid)
        val payload = (validation as ImportValidationResult.Valid).payload

        // Ground truth recalculation directly from raw imported samples
        val recalculated = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = payload.session,
            samples = payload.samples,
            transitions = payload.transitions,
        )

        assertNotNull(recalculated)
        // Recalculated power must reflect raw samples, not the fabricated 999_999_999L
        assertNotEquals(999_999_999L, recalculated!!.averagePowerUw)
        assertTrue("Recalculated power must reflect actual sample measurements", (recalculated.averagePowerUw ?: 0L) < 50_000_000L)
    }

    @Test
    fun `03 - ASSIGN_NEW_ID performs complete session and child foreign key remapping`() {
        val bundle = createValidBundle()
        val json = SessionExportEngine.generateJson(bundle)
        val payload = (SessionImportEngine.validateAndParse(json) as ImportValidationResult.Valid).payload

        val prepared = SessionImportEngine.prepareEntities(payload, DuplicateStrategy.ASSIGN_NEW_ID)

        val newSessionId = prepared.session.id
        assertNotEquals("orig-session-id", newSessionId)

        val newSetupId = prepared.setup.id
        assertNotEquals("orig-setup-id", newSetupId)
        assertEquals(newSetupId, prepared.session.chargingSetupId)

        val newSnapshotId = prepared.softwareSnapshot.id
        assertNotEquals("orig-snap-id", newSnapshotId)
        assertEquals(newSnapshotId, prepared.session.softwareSnapshotId)

        // Standard test remapped
        assertNotNull(prepared.standardTest)
        assertEquals(newSessionId, prepared.standardTest!!.sessionId)
        assertNotEquals("orig-test-id", prepared.standardTest!!.id)

        // Samples remapped
        assertEquals(2, prepared.samples.size)
        prepared.samples.forEach { sample ->
            assertEquals(newSessionId, sample.sessionId)
            assertNotEquals("orig-sample-1", sample.id)
            assertNotEquals("orig-sample-2", sample.id)
        }

        // Transitions remapped
        assertEquals(1, prepared.transitions.size)
        assertEquals(newSessionId, prepared.transitions.first().sessionId)
        assertNotEquals("orig-trans-1", prepared.transitions.first().id)

        // Notes prepended with [Imported]
        assertTrue(prepared.session.userNotes?.startsWith("[Imported]") == true)
    }

    @Test
    fun `04 - preserves nulls and signed values through serialization and deserialization roundtrip`() {
        val bundle = createValidBundle()
        val json = SessionExportEngine.generateJson(bundle)
        val payload = (SessionImportEngine.validateAndParse(json) as ImportValidationResult.Valid).payload

        val sample2 = payload.samples.find { it.percent == 80 }!!
        assertEquals(-200_000, sample2.currentNowUa)
        assertEquals(-1_680_000L, sample2.derivedPowerUw)
        assertNull(sample2.temperatureDeciC)
        assertNull(sample2.chargeCounterUah)
    }
}
