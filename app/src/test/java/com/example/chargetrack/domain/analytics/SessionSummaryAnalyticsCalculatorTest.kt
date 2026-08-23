package com.example.chargetrack.domain.analytics

import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.ChargingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionSummaryAnalyticsCalculatorTest {

    private fun createBaseSession(
        sessionId: String = "session-1",
        startPercent: Int = 20,
        endPercent: Int? = 80,
        testType: TestType = TestType.STANDARD,
        startedAt: Instant = Instant.parse("2026-08-23T10:00:00Z"),
        endedAt: Instant? = Instant.parse("2026-08-23T10:45:00Z"),
        endReason: SessionEndReason? = SessionEndReason.USER_STOPPED,
    ) = ChargingSession(
        id = sessionId,
        startedAt = startedAt,
        endedAt = endedAt,
        startPercent = startPercent,
        endPercent = endPercent,
        chargingSetupId = "setup-1",
        softwareSnapshotId = "sw-1",
        testType = testType,
        endReason = endReason,
    )

    private fun createSample(
        elapsedMs: Long,
        percent: Int? = 20,
        voltageMv: Int? = 4200,
        currentNowUa: Int? = 15_000_000,
        temperatureDeciC: Int? = 300,
        derivedPowerUw: Long? = 63_000_000L,
        qualityFlags: Set<QualityFlag> = emptySet(),
    ) = BatterySample(
        id = "s-$elapsedMs",
        sessionId = "session-1",
        timestamp = Instant.parse("2026-08-23T10:00:00Z").plusMillis(elapsedMs),
        elapsedMs = elapsedMs,
        percent = percent,
        voltageMv = voltageMv,
        currentNowUa = currentNowUa,
        temperatureDeciC = temperatureDeciC,
        derivedPowerUw = derivedPowerUw,
        qualityFlags = qualityFlags,
    )

    @Test
    fun `01 - Monotonic duration isolated from wall-clock changes`() {
        val sessionWithShiftedWallClock = createBaseSession(
            startedAt = Instant.parse("2026-08-23T08:00:00Z"), // Shifted by 2 hours
            endedAt = Instant.parse("2026-08-23T12:00:00Z"),
        )
        val samples = listOf(
            createSample(elapsedMs = 0L),
            createSample(elapsedMs = 15_000L),
            createSample(elapsedMs = 30_000L),
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = sessionWithShiftedWallClock,
            samples = samples,
            transitions = emptyList(),
            explicitDurationMs = 30_000L,
        )

        assertEquals("Duration must be strictly monotonic 30s, not 4 hours from wall clock", 30_000L, summary.durationMs)
    }

    @Test
    fun `02 - 32-bit Integer overflow prevention during aggregation`() {
        // 500 samples with currentNowUa = 20_000_000 uA
        // Direct Int sum: 500 * 20_000_000 = 10_000_000_000 (overflows Int.MAX_VALUE 2,147,483,647)
        val largeCurrentSamples = (0 until 500).map { i ->
            createSample(
                elapsedMs = i * 5000L,
                currentNowUa = 20_000_000,
                voltageMv = 4400,
                temperatureDeciC = 350,
            )
        }

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(),
            samples = largeCurrentSamples,
            transitions = emptyList(),
        )

        assertEquals(20_000_000, summary.averageCurrentUa)
        assertEquals(4400, summary.averageVoltageMv)
        assertEquals(350, summary.averageTemperatureDeciC)
    }

    @Test
    fun `03 - Arbitrary Standard Test target ranges evaluated accurately`() {
        val session = createBaseSession(startPercent = 10, endPercent = 85, testType = TestType.STANDARD)
        val standardTest10To80 = StandardTestEntity(
            sessionId = session.id,
            targetStartPercent = 10,
            targetEndPercent = 80,
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = session,
            standardTest = standardTest10To80,
            samples = listOf(createSample(0L), createSample(5000L), createSample(10000L)),
            transitions = emptyList(),
        )

        assertEquals(true, summary.isCompleteStandardTest)
    }

    @Test
    fun `04 - Incomplete standard test returns false`() {
        val session = createBaseSession(startPercent = 20, endPercent = 67, testType = TestType.STANDARD)
        val standardTest20To80 = StandardTestEntity(
            sessionId = session.id,
            targetStartPercent = 20,
            targetEndPercent = 80,
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = session,
            standardTest = standardTest20To80,
            samples = listOf(createSample(0L), createSample(5000L), createSample(10000L)),
            transitions = emptyList(),
        )

        assertEquals(false, summary.isCompleteStandardTest)
        assertEquals(47, summary.percentGained)
    }

    @Test
    fun `05 - Free-form session returns null for isCompleteStandardTest`() {
        val session = createBaseSession(startPercent = 20, endPercent = 80, testType = TestType.FREE_FORM)

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = session,
            standardTest = null,
            samples = listOf(createSample(0L), createSample(5000L), createSample(10000L)),
            transitions = emptyList(),
        )

        assertNull(summary.isCompleteStandardTest)
    }

    @Test
    fun `06 - Missing energy counter does NOT degrade session`() {
        val samples = (0 until 10).map { i ->
            createSample(elapsedMs = i * 5000L).copy(
                energyCounterNwh = null,
                chargeCounterUah = null,
                cycleCount = null,
            )
        }

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(),
            samples = samples,
            transitions = emptyList(),
        )

        assertEquals(0, summary.missingValueSampleCount)
        assertEquals(DataQuality.GOOD, summary.overallQuality)
    }

    @Test
    fun `07 - Missing voltage or current DOES increment missing count and degrade session`() {
        val samples = listOf(
            createSample(elapsedMs = 0L, voltageMv = null), // missing voltage
            createSample(elapsedMs = 5000L, currentNowUa = null), // missing current
            createSample(elapsedMs = 10000L),
            createSample(elapsedMs = 15000L),
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(),
            samples = samples,
            transitions = emptyList(),
        )

        assertEquals(2, summary.missingValueSampleCount)
        assertEquals(DataQuality.DEGRADED, summary.overallQuality)
    }

    @Test
    fun `08 - Negative percentGained is preserved without coercion`() {
        val session = createBaseSession(startPercent = 80, endPercent = 75)
        val samples = listOf(createSample(0L, percent = 80), createSample(5000L, percent = 78), createSample(10000L, percent = 75))

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = session,
            samples = samples,
            transitions = emptyList(),
        )

        assertEquals(-5, summary.percentGained)
    }

    @Test
    fun `09 - Negative net battery-side power contributes algebraically`() {
        val samples = listOf(
            createSample(0L, derivedPowerUw = -10_000_000L), // -10W
            createSample(5000L, derivedPowerUw = -20_000_000L), // -20W
            createSample(10000L, derivedPowerUw = 60_000_000L), // +60W
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(),
            samples = samples,
            transitions = emptyList(),
        )

        // (-10 + -20 + 60) / 3 = 30 / 3 = 10_000_000 uW = +10.0W
        assertEquals(10_000_000L, summary.averagePowerUw)
        assertEquals(-10_000_000L, summary.medianPowerUw)
        assertEquals(60_000_000L, summary.peakPowerUw)
    }

    @Test
    fun `10 - Negative or zero peak power produces null taper`() {
        val dischargingSamples = listOf(
            createSample(0L, percent = 50, derivedPowerUw = -5_000_000L),
            createSample(5000L, percent = 49, derivedPowerUw = -10_000_000L),
            createSample(10000L, percent = 48, derivedPowerUw = -15_000_000L),
            createSample(15000L, percent = 47, derivedPowerUw = -20_000_000L),
            createSample(20000L, percent = 46, derivedPowerUw = -25_000_000L),
            createSample(25000L, percent = 45, derivedPowerUw = -30_000_000L),
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(startPercent = 50, endPercent = 45),
            samples = dischargingSamples,
            transitions = emptyList(),
        )

        assertNull("Negative peak power must not trigger taper detection", summary.chargingTaperStartPercent)
    }

    @Test
    fun `11 - Exactly K qualifying consecutive samples triggers taper`() {
        // Peak power 100W (100_000_000 uW) at 60%
        // Threshold 80% = 80W
        // Samples after peak:
        // sample 1: 65% @ 70W (qualifying 1)
        // sample 2: 66% @ 65W (qualifying 2)
        // sample 3: 67% @ 60W (qualifying 3)
        // sample 4: 68% @ 50W (qualifying 4)
        // sample 5: 69% @ 40W (qualifying 5)
        val samples = listOf(
            createSample(0L, percent = 60, derivedPowerUw = 100_000_000L), // Peak
            createSample(5000L, percent = 65, derivedPowerUw = 70_000_000L), // j
            createSample(10000L, percent = 66, derivedPowerUw = 65_000_000L), // j+1
            createSample(15000L, percent = 67, derivedPowerUw = 60_000_000L), // j+2
            createSample(20000L, percent = 68, derivedPowerUw = 50_000_000L), // j+3
            createSample(25000L, percent = 69, derivedPowerUw = 40_000_000L), // j+4
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(startPercent = 60, endPercent = 69),
            samples = samples,
            transitions = emptyList(),
            taperConsecutiveSamples = 5,
        )

        assertEquals(65, summary.chargingTaperStartPercent)
    }

    @Test
    fun `12 - K minus 1 qualifying samples does NOT trigger taper`() {
        // Only 4 consecutive samples below 80W threshold
        val samples = listOf(
            createSample(0L, percent = 60, derivedPowerUw = 100_000_000L), // Peak
            createSample(5000L, percent = 65, derivedPowerUw = 70_000_000L),
            createSample(10000L, percent = 66, derivedPowerUw = 65_000_000L),
            createSample(15000L, percent = 67, derivedPowerUw = 60_000_000L),
            createSample(20000L, percent = 68, derivedPowerUw = 50_000_000L),
            // Only 4 samples, session ended
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(startPercent = 60, endPercent = 68),
            samples = samples,
            transitions = emptyList(),
            taperConsecutiveSamples = 5,
        )

        assertNull("K-1 samples must not trigger taper", summary.chargingTaperStartPercent)
    }

    @Test
    fun `13 - OUTLIER power spike excluded from analytical peak and taper`() {
        val samples = listOf(
            createSample(0L, percent = 20, derivedPowerUw = 80_000_000L), // Real peak 80W
            createSample(5000L, percent = 21, derivedPowerUw = 150_000_000L, qualityFlags = setOf(QualityFlag.OUTLIER)), // Outlier spike
            createSample(10000L, percent = 22, derivedPowerUw = 80_000_000L),
            createSample(15000L, percent = 23, derivedPowerUw = 75_000_000L),
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(),
            samples = samples,
            transitions = emptyList(),
        )

        assertEquals("Analytical peak power must exclude OUTLIER samples", 80_000_000L, summary.peakPowerUw)
        assertEquals(1, summary.outlierSampleCount)
        assertEquals(DataQuality.DEGRADED, summary.overallQuality)
    }

    @Test
    fun `14 - Unique sample corruption counting prevents double counting`() {
        // Sample carrying BOTH MISSING_REQUIRED_VALUE and GAP_DETECTED
        val corruptedSample = createSample(
            elapsedMs = 0L,
            voltageMv = null,
            qualityFlags = setOf(QualityFlag.MISSING_REQUIRED_VALUE, QualityFlag.GAP_DETECTED),
        )
        val valid1 = createSample(elapsedMs = 5000L)
        val valid2 = createSample(elapsedMs = 10000L)

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(),
            samples = listOf(corruptedSample, valid1, valid2),
            transitions = emptyList(),
        )

        assertEquals(1, summary.missingValueSampleCount)
        assertEquals(1, summary.gapSampleCount)
        // 1 corrupted out of 3 = 33.3% <= 50% -> DEGRADED (not INSUFFICIENT)
        assertEquals(DataQuality.DEGRADED, summary.overallQuality)
    }

    @Test
    fun `15 - Gap transitions excluded from per-one-percent pace calculation`() {
        val transitions = listOf(
            ChargeTransition(
                sessionId = "session-1",
                fromPercent = 50,
                toPercent = 51,
                startedAt = Instant.parse("2026-08-23T10:00:00Z"),
                endedAt = Instant.parse("2026-08-23T10:01:00Z"),
                durationMs = 60_000L,
                sampleCount = 12,
                quality = DataQuality.GOOD,
            ),
            ChargeTransition(
                sessionId = "session-1",
                fromPercent = 51,
                toPercent = 53, // GAP transition
                startedAt = Instant.parse("2026-08-23T10:01:00Z"),
                endedAt = Instant.parse("2026-08-23T10:03:00Z"),
                durationMs = 120_000L,
                sampleCount = 24,
                quality = DataQuality.INSUFFICIENT,
            ),
            ChargeTransition(
                sessionId = "session-1",
                fromPercent = 53,
                toPercent = 54,
                startedAt = Instant.parse("2026-08-23T10:03:00Z"),
                endedAt = Instant.parse("2026-08-23T10:04:02Z"),
                durationMs = 62_000L,
                sampleCount = 12,
                quality = DataQuality.GOOD,
            ),
        )

        val summary = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(),
            samples = listOf(createSample(0L), createSample(5000L), createSample(10000L)),
            transitions = transitions,
        )

        assertEquals(3, summary.totalTransitionCount)
        assertEquals(2, summary.contiguousOnePercentTransitionCount)
        assertEquals(1, summary.insufficientTransitionCount)
        // Average of 60_000 and 62_000 = 61_000L (gap 120_000L is excluded)
        assertEquals(61_000L, summary.averageTimePerOnePercentMs)
        assertEquals(61_000L, summary.medianTimePerOnePercentMs)
    }

    @Test
    fun `16 - Quality scoring marks session INSUFFICIENT when fewer than 3 samples or over 50 percent corrupted`() {
        val twoSamples = listOf(createSample(0L), createSample(5000L))
        val summaryFew = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(),
            samples = twoSamples,
            transitions = emptyList(),
        )
        assertEquals(DataQuality.INSUFFICIENT, summaryFew.overallQuality)

        val fourSamplesWithThreeCorrupted = listOf(
            createSample(0L, voltageMv = null, qualityFlags = setOf(QualityFlag.MISSING_REQUIRED_VALUE)),
            createSample(5000L, currentNowUa = null, qualityFlags = setOf(QualityFlag.MISSING_REQUIRED_VALUE)),
            createSample(10000L, qualityFlags = setOf(QualityFlag.GAP_DETECTED)),
            createSample(15000L),
        )
        val summaryCorrupted = SessionSummaryAnalyticsCalculator.calculateSummary(
            session = createBaseSession(),
            samples = fourSamplesWithThreeCorrupted,
            transitions = emptyList(),
        )
        // 3 out of 4 corrupted = 75% > 50% -> INSUFFICIENT
        assertEquals(DataQuality.INSUFFICIENT, summaryCorrupted.overallQuality)
    }
}
