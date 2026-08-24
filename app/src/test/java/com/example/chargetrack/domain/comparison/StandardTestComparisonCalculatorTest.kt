package com.example.chargetrack.domain.comparison

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
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StandardTestComparisonCalculatorTest {

    private val now = Instant.now()

    private fun createBundle(
        sessionId: String,
        targetStart: Int = 20,
        targetEnd: Int = 80,
        durationMs: Long = 1200_000L,
        avgPowerUw: Long = 50_000_000L,
        peakPowerUw: Long = 80_000_000L,
        startTempDeciC: Int? = 300,
        maxTempDeciC: Int? = 380,
        chargerWattage: Int = 100,
        chargingMode: ChargingMode = ChargingMode.FLASH_CHARGE,
        softwareSnapshot: SoftwareSnapshot? = SoftwareSnapshot("snap-1", now, "16", 36, "OriginOS", "build1", "1.0", 1),
        transitions: List<ChargeTransition> = emptyList(),
        samples: List<BatterySample> = emptyList(),
    ): StandardTestDataBundle {
        val session = ChargingSession(
            id = sessionId,
            startedAt = now,
            endedAt = now.plusMillis(durationMs),
            startPercent = targetStart,
            endPercent = targetEnd,
            chargingSetupId = "setup-$sessionId",
            softwareSnapshotId = "snap-1",
            testType = TestType.STANDARD,
            endReason = SessionEndReason.USER_STOPPED,
        )
        val summary = SessionSummary(
            sessionId = sessionId,
            testType = TestType.STANDARD,
            startedAt = now,
            endedAt = now.plusMillis(durationMs),
            endReason = SessionEndReason.USER_STOPPED,
            durationMs = durationMs,
            startPercent = targetStart,
            endPercent = targetEnd,
            percentGained = targetEnd - targetStart,
            isCompleteStandardTest = true,
            totalSampleCount = 100,
            validPowerSampleCount = 100,
            missingValueSampleCount = 0,
            gapSampleCount = 0,
            jitterSampleCount = 0,
            outlierSampleCount = 0,
            totalTransitionCount = 60,
            contiguousOnePercentTransitionCount = 60,
            degradedTransitionCount = 0,
            insufficientTransitionCount = 0,
            averagePowerUw = avgPowerUw,
            medianPowerUw = avgPowerUw,
            peakPowerUw = peakPowerUw,
            minCurrentUa = null,
            maxCurrentUa = null,
            averageCurrentUa = null,
            averageVoltageMv = null,
            startTemperatureDeciC = startTempDeciC,
            endTemperatureDeciC = null,
            averageTemperatureDeciC = null,
            peakTemperatureDeciC = maxTempDeciC,
            averageTimePerOnePercentMs = null,
            medianTimePerOnePercentMs = null,
            chargingTaperStartPercent = null,
            overallQuality = DataQuality.GOOD,
            qualityFlags = emptySet(),
        )
        val setup = ChargingSetup(
            id = "setup-$sessionId",
            chargerBrand = "iQOO",
            chargerModel = "${chargerWattage}W",
            advertisedWattageW = chargerWattage,
            isOfficialCharger = true,
            cableBrand = "iQOO",
            cableModel = "Stock",
            isOfficialCable = true,
            chargingType = ChargingType.WIRED,
            chargingMode = chargingMode,
            createdAt = now,
        )
        val standardTest = StandardTest(
            id = "std-$sessionId",
            sessionId = sessionId,
            targetStartPercent = targetStart,
            targetEndPercent = targetEnd,
            comparisonGroupKey = "standard_${targetStart}_${targetEnd}_wired_official_iqoo_${chargerWattage}w_${chargingMode.name.lowercase()}",
        )

        return StandardTestDataBundle(
            session = session,
            summary = summary,
            standardTest = standardTest,
            setup = setup,
            software = softwareSnapshot,
            transitions = transitions,
            samples = samples,
        )
    }

    @Test
    fun `01 - preserves chronological sample sequence when building multi-curve series with repeated percentages`() {
        val samples = listOf(
            BatterySample(id = "1", sessionId = "s1", timestamp = now, elapsedMs = 0L, percent = 50, derivedPowerUw = 80_000_000L),
            BatterySample(id = "2", sessionId = "s1", timestamp = now, elapsedMs = 5_000L, percent = 50, derivedPowerUw = 78_000_000L),
            BatterySample(id = "3", sessionId = "s1", timestamp = now, elapsedMs = 10_000L, percent = 50, derivedPowerUw = 76_000_000L),
            BatterySample(id = "4", sessionId = "s1", timestamp = now, elapsedMs = 15_000L, percent = 51, derivedPowerUw = 75_000_000L),
        )
        val bundle = createBundle(sessionId = "s1", samples = samples)

        val series = StandardTestComparisonCalculator.buildAlignedPowerSeries(bundle, "Test Series")

        assertEquals(1, series.segments.size)
        val points = series.segments[0].points
        assertEquals("Must NOT collapse 3 repeated 50% points into 1", 4, points.size)

        // Verifies chronological sequence is preserved
        assertEquals(0L, points[0].tooltip.elapsedMs)
        assertEquals(80.0f, points[0].y, 0.001f)

        assertEquals(5_000L, points[1].tooltip.elapsedMs)
        assertEquals(78.0f, points[1].y, 0.001f)

        assertEquals(10_000L, points[2].tooltip.elapsedMs)
        assertEquals(76.0f, points[2].y, 0.001f)
    }

    @Test
    fun `02 - calculates accurate duration, power, and temperature deltas`() {
        val primary = createBundle(
            sessionId = "p",
            durationMs = 1200_000L, // 20m
            avgPowerUw = 50_000_000L, // 50W
            peakPowerUw = 85_000_000L,
            startTempDeciC = 300, // 30.0 °C
            maxTempDeciC = 380, // 38.0 °C
        )
        val compared = createBundle(
            sessionId = "c",
            durationMs = 1260_000L, // 21m (+60s, +5%)
            avgPowerUw = 46_000_000L, // 46W (-4W, -8%)
            peakPowerUw = 80_000_000L, // -5W
            startTempDeciC = 320, // 32.0 °C (+2.0 °C)
            maxTempDeciC = 395, // 39.5 °C (+1.5 °C)
        )

        val result = StandardTestComparisonCalculator.calculatePairwiseComparison(primary, compared)

        assertEquals(60_000L, result.durationDeltaMs)
        assertEquals(5.0, result.durationDeltaPercent!!, 0.01)

        assertEquals(-4_000_000L, result.averagePowerDeltaUw)
        assertEquals(-8.0, result.averagePowerDeltaPercent!!, 0.01)

        assertEquals(-5_000_000L, result.peakPowerDeltaUw)
        assertEquals(15, result.maxTempDeltaDeciC) // +1.5 °C
        assertEquals(20, result.startTempDeltaDeciC) // +2.0 °C
        assertTrue(result.conditions.isIdealComparison)
    }

    @Test
    fun `03 - null start temperature does not become zero`() {
        val primary = createBundle(sessionId = "p", startTempDeciC = null)
        val compared = createBundle(sessionId = "c", startTempDeciC = 320)

        val result = StandardTestComparisonCalculator.calculatePairwiseComparison(primary, compared)

        assertNull("Null start temperature must yield null delta, never a fabricated number", result.startTempDeltaDeciC)
        assertEquals(ConditionMatchStatus.UNKNOWN, result.conditions.temperatureMatch)
    }

    @Test
    fun `04 - missing software snapshot is UNKNOWN not MISMATCH`() {
        val primary = createBundle(sessionId = "p", softwareSnapshot = null)
        val compared = createBundle(sessionId = "c", softwareSnapshot = SoftwareSnapshot("snap-2", now, "16", 36, "OriginOS", "build2", "1.0", 1))

        val result = StandardTestComparisonCalculator.calculatePairwiseComparison(primary, compared)

        assertEquals(ConditionMatchStatus.UNKNOWN, result.conditions.softwareMatch)
        assertFalse(result.conditions.mismatchWarnings.any { it.contains("Software build differs") })
    }

    @Test
    fun `05 - condition matcher flags charger wattage and mode mismatches`() {
        val primary = createBundle(sessionId = "p", chargerWattage = 100, chargingMode = ChargingMode.FLASH_CHARGE)
        val compared = createBundle(sessionId = "c", chargerWattage = 65, chargingMode = ChargingMode.NORMAL)

        val result = StandardTestComparisonCalculator.calculatePairwiseComparison(primary, compared)

        assertFalse(result.conditions.isIdealComparison)
        assertEquals(ConditionMatchStatus.MISMATCH, result.conditions.chargerMatch)
        assertEquals(ConditionMatchStatus.MISMATCH, result.conditions.modeMatch)
        assertEquals(2, result.conditions.mismatchWarnings.size)
    }

    @Test
    fun `06 - per-1% transition matching rules`() {
        val pTransitions = listOf(
            ChargeTransition(id = "1", sessionId = "p", fromPercent = 20, toPercent = 21, startedAt = now, endedAt = now, durationMs = 15_000L, sampleCount = 3, quality = DataQuality.GOOD),
            ChargeTransition(id = "2", sessionId = "p", fromPercent = 21, toPercent = 22, startedAt = now, endedAt = now, durationMs = 16_000L, sampleCount = 3, quality = DataQuality.DEGRADED),
            ChargeTransition(id = "3", sessionId = "p", fromPercent = 22, toPercent = 23, startedAt = now, endedAt = now, durationMs = 17_000L, sampleCount = 3, quality = DataQuality.INSUFFICIENT),
            ChargeTransition(id = "4", sessionId = "p", fromPercent = 23, toPercent = 24, startedAt = now, endedAt = now, durationMs = 18_000L, sampleCount = 3, quality = DataQuality.GOOD),
        )
        val cTransitions = listOf(
            ChargeTransition(id = "10", sessionId = "c", fromPercent = 20, toPercent = 21, startedAt = now, endedAt = now, durationMs = 14_000L, sampleCount = 3, quality = DataQuality.GOOD), // -1s
            ChargeTransition(id = "20", sessionId = "c", fromPercent = 21, toPercent = 22, startedAt = now, endedAt = now, durationMs = 18_000L, sampleCount = 3, quality = DataQuality.GOOD), // +2s
            ChargeTransition(id = "30", sessionId = "c", fromPercent = 22, toPercent = 23, startedAt = now, endedAt = now, durationMs = 17_000L, sampleCount = 3, quality = DataQuality.GOOD), // P is insufficient
            // 23->24 is missing in compared!
        )

        val deltas = StandardTestComparisonCalculator.calculatePerPercentDeltas(pTransitions, cTransitions)

        // 20->21: GOOD + GOOD -> comparable, -1000ms
        val d20 = deltas.first { it.percent == 20 }
        assertTrue(d20.isComparable)
        assertEquals(-1000L, d20.deltaMs)

        // 21->22: DEGRADED + GOOD -> comparable, +2000ms
        val d21 = deltas.first { it.percent == 21 }
        assertTrue(d21.isComparable)
        assertEquals(2000L, d21.deltaMs)

        // 22->23: INSUFFICIENT + GOOD -> non-comparable
        val d22 = deltas.first { it.percent == 22 }
        assertFalse("INSUFFICIENT transition on either side must produce no normal delta", d22.isComparable)
        assertNull(d22.deltaMs)

        // 23->24: Missing in compared -> non-comparable
        val d23 = deltas.first { it.percent == 23 }
        assertFalse(d23.isComparable)
        assertNull(d23.deltaMs)
    }

    @Test
    fun `07 - signed power delta preserves negative power without abs`() {
        val primary = createBundle(sessionId = "p", avgPowerUw = -2_000_000L) // -2W
        val compared = createBundle(sessionId = "c", avgPowerUw = 1_000_000L) // +1W

        val result = StandardTestComparisonCalculator.calculatePairwiseComparison(primary, compared)

        // delta = 1 - (-2) = +3W = +3,000,000 uW
        assertEquals(3_000_000L, result.averagePowerDeltaUw)
    }
}
