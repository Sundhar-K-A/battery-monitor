package com.example.chargetrack.domain.export

import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.DataQuality
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class SessionExportEngineTest {

    private val now = Instant.now()

    private fun createTestBundle(): FullSessionBundle {
        val session = ChargingSession(
            id = "sess-1234",
            startedAt = now.minusSeconds(1800),
            endedAt = now,
            startPercent = 20,
            endPercent = 80,
            chargingSetupId = "setup-1",
            softwareSnapshotId = "snap-1",
            testType = TestType.STANDARD,
            userNotes = "Prompt 20 export test",
            endReason = SessionEndReason.USER_STOPPED,
        )
        val setup = ChargingSetup(
            id = "setup-1",
            chargerBrand = "iQOO",
            chargerModel = "100W FlashCharge",
            advertisedWattageW = 100,
            protocol = "FlashCharge",
            isOfficialCharger = true,
            cableBrand = "iQOO",
            cableModel = "Stock 6A",
            isOfficialCable = true,
            chargingType = ChargingType.WIRED,
            chargingMode = ChargingMode.FLASH_CHARGE,
            notes = "OEM",
            createdAt = now.minusSeconds(10000),
        )
        val snapshot = SoftwareSnapshot(
            id = "snap-1",
            capturedAt = now.minusSeconds(1800),
            androidVersion = "16",
            sdkInt = 36,
            originOsVersion = "PD2505_A_16.0.4.1",
            buildFingerprint = "vivo/PD2505:16",
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
            id = "std-1",
            sessionId = "sess-1234",
            comparisonGroupKey = "standard_20_80_wired_official",
            targetStartPercent = 20,
            targetEndPercent = 80,
            benchmarkStartedElapsedMs = 10000L,
            benchmarkEndedElapsedMs = 1750000L,
        )
        val samples = listOf(
            // Charging sample (positive power and current)
            BatterySample(
                id = "samp-1",
                sessionId = "sess-1234",
                timestamp = now.minusSeconds(1800),
                elapsedMs = 0L,
                percent = 20,
                voltageMv = 7600,
                currentNowUa = 5_000_000,
                derivedPowerUw = 38_000_000L, // 38W
                temperatureDeciC = 285,
                chargeCounterUah = 1_400_000,
            ),
            // Discharging/net negative sample (negative power and current preserved)
            BatterySample(
                id = "samp-2",
                sessionId = "sess-1234",
                timestamp = now.minusSeconds(900),
                elapsedMs = 900_000L,
                percent = 50,
                voltageMv = 8200,
                currentNowUa = -500_000,
                derivedPowerUw = -4_100_000L, // -4.1W
                temperatureDeciC = null, // null preserved
                chargeCounterUah = null, // null preserved
            )
        )
        val transitions = listOf(
            ChargeTransition(
                id = "trans-1",
                sessionId = "sess-1234",
                fromPercent = 20,
                toPercent = 21,
                startedAt = now.minusSeconds(1800),
                endedAt = now.minusSeconds(1775),
                durationMs = 25000L,
                averagePowerUw = 42000000L,
                peakPowerUw = 45000000L,
                averageTemperatureDeciC = 290,
                sampleCount = 5,
            )
        )

        return FullSessionBundle(session, setup, snapshot, profile, standardTest, samples, transitions)
    }

    @Test
    fun `01 - CSV export contains required headers, power disclaimer, and preserves signed metrics`() {
        val bundle = createTestBundle()
        val csv = SessionExportEngine.generateCsv(bundle)

        assertTrue("CSV header must contain estimated battery-side power disclaimer", csv.contains("Estimated battery-side power"))
        assertTrue("CSV must not claim wall/charger power", !csv.contains("charger/wall input power (claimed)"))
        assertTrue("CSV header line must contain elapsed_ms", csv.contains("elapsed_ms,timestamp_iso,battery_percent"))

        val lines = csv.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(3, lines.size) // 1 header line + 2 sample lines

        // Line 1: Positive sample
        val row1 = lines[1]
        assertTrue("Row 1 must contain 38.0000", row1.contains("38.0000"))
        assertTrue("Row 1 must contain 28.5", row1.contains("28.5"))

        // Line 2: Negative signed sample and nulls
        val row2 = lines[2]
        assertTrue("Row 2 must contain negative power -4.1000", row2.contains("-4.1000"))
        assertTrue("Row 2 must preserve negative current -500000", row2.contains("-500000"))
    }

    @Test
    fun `02 - JSON export contains complete relational graph with schemaVersion 1`() {
        val bundle = createTestBundle()
        val analytics = SessionSummary(
            sessionId = bundle.session.id,
            testType = TestType.STANDARD,
            startedAt = bundle.session.startedAt,
            endedAt = bundle.session.endedAt,
            endReason = bundle.session.endReason,
            durationMs = 1800000L,
            startPercent = 20,
            endPercent = 80,
            percentGained = 60,
            isCompleteStandardTest = true,
            totalSampleCount = 2,
            validPowerSampleCount = 2,
            missingValueSampleCount = 0,
            gapSampleCount = 0,
            jitterSampleCount = 0,
            outlierSampleCount = 0,
            totalTransitionCount = 1,
            contiguousOnePercentTransitionCount = 1,
            degradedTransitionCount = 0,
            insufficientTransitionCount = 0,
            averagePowerUw = 37000000L,
            medianPowerUw = 37000000L,
            peakPowerUw = 45000000L,
            minCurrentUa = -500000,
            maxCurrentUa = 5000000,
            averageCurrentUa = 2250000,
            averageVoltageMv = 7900,
            startTemperatureDeciC = 285,
            endTemperatureDeciC = 375,
            averageTemperatureDeciC = 330,
            peakTemperatureDeciC = 375,
            averageTimePerOnePercentMs = 30000L,
            medianTimePerOnePercentMs = 30000L,
            chargingTaperStartPercent = 65,
            overallQuality = DataQuality.GOOD,
            qualityFlags = emptySet(),
        )

        val jsonString = SessionExportEngine.generateJson(bundle, analytics)
        val json = JSONObject(jsonString)

        assertEquals(1, json.getInt("schemaVersion"))
        assertNotNull(json.getString("exportedAt"))
        assertTrue(json.getJSONObject("session").getString("id") == "sess-1234")
        assertTrue(json.getJSONObject("chargingSetup").getString("chargerModel") == "100W FlashCharge")
        assertTrue(json.getJSONObject("softwareSnapshot").getString("originOsVersion") == "PD2505_A_16.0.4.1")
        assertEquals(2, json.getJSONArray("samples").length())
        assertEquals(1, json.getJSONArray("chargeTransitions").length())
        assertEquals(37000000L, json.getJSONObject("derivedAnalytics").getLong("averagePowerUw"))
    }
}
