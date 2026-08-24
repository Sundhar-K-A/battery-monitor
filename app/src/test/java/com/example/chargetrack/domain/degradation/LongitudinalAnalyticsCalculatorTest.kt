package com.example.chargetrack.domain.degradation

import com.example.chargetrack.domain.health.FullChargeCapacityObservation
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.StandardTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LongitudinalAnalyticsCalculatorTest {

    private val now = Instant.now()
    private val refCap = 7000

    @Test
    fun `01 - benchmark metrics are calculated strictly within the benchmark window`() {
        val test = StandardTest(
            id = "t1",
            sessionId = "s1",
            comparisonGroupKey = "group_1",
            targetStartPercent = 20,
            targetEndPercent = 80,
            isBaseline = false,
            benchmarkStartedElapsedMs = 10_000L,
            benchmarkEndedElapsedMs = 70_000L,
        )

        val samples = listOf(
            // Pre-benchmark idle sample (10W)
            BatterySample(id = "1", sessionId = "s1", timestamp = now, elapsedMs = 5_000L, percent = 18, voltageMv = 3800, currentNowUa = 2_631_000, derivedPowerUw = 10_000_000L, temperatureDeciC = 250),
            // Benchmark window samples (30W, 45W, 35W) -> avg = 36.66W, max temp = 380 (38.0 C)
            BatterySample(id = "2", sessionId = "s1", timestamp = now, elapsedMs = 20_000L, percent = 30, voltageMv = 4000, currentNowUa = 7_500_000, derivedPowerUw = 30_000_000L, temperatureDeciC = 300),
            BatterySample(id = "3", sessionId = "s1", timestamp = now, elapsedMs = 40_000L, percent = 55, voltageMv = 4200, currentNowUa = 10_714_000, derivedPowerUw = 45_000_000L, temperatureDeciC = 380),
            BatterySample(id = "4", sessionId = "s1", timestamp = now, elapsedMs = 65_000L, percent = 79, voltageMv = 4300, currentNowUa = 8_139_000, derivedPowerUw = 35_000_000L, temperatureDeciC = 350),
            // Post-benchmark sample (5W)
            BatterySample(id = "5", sessionId = "s1", timestamp = now, elapsedMs = 80_000L, percent = 82, voltageMv = 4350, currentNowUa = 1_149_000, derivedPowerUw = 5_000_000L, temperatureDeciC = 320),
        )

        val input = StandardTestPerformanceInput(test, now, samples)
        val analysis = LongitudinalAnalyticsCalculator.calculatePerformanceTrend("group_1", listOf(input))

        assertEquals(1, analysis.points.size)
        val point = analysis.points.first()
        assertEquals(60_000L, point.benchmarkDurationMs)
        assertEquals("Benchmark avg power should only average samples in [10s..70s]", (30_000_000L + 45_000_000L + 35_000_000L) / 3, point.benchmarkAveragePowerUw)
        assertEquals(45_000_000L, point.benchmarkPeakPowerUw)
        assertEquals(380, point.benchmarkMaxTempDeciC)
    }

    @Test
    fun `02 - signed power preservation excludes net discharge session without mutating sample values`() {
        val test = StandardTest(
            id = "t_neg",
            sessionId = "s_neg",
            comparisonGroupKey = "group_1",
            targetStartPercent = 20,
            targetEndPercent = 80,
            isBaseline = false,
            benchmarkStartedElapsedMs = 0L,
            benchmarkEndedElapsedMs = 60_000L,
        )

        val samples = listOf(
            BatterySample(id = "1", sessionId = "s_neg", timestamp = now, elapsedMs = 10_000L, percent = 25, voltageMv = 3800, currentNowUa = -2_000_000, derivedPowerUw = -7_600_000L),
            BatterySample(id = "2", sessionId = "s_neg", timestamp = now, elapsedMs = 50_000L, percent = 24, voltageMv = 3800, currentNowUa = -2_000_000, derivedPowerUw = -7_600_000L),
        )

        // Raw sample derivedPowerUw must remain negative
        assertEquals(-7_600_000L, samples[0].derivedPowerUw)

        val input = StandardTestPerformanceInput(test, now, samples)
        val analysis = LongitudinalAnalyticsCalculator.calculatePerformanceTrend("group_1", listOf(input))

        // Net discharging test excluded from charging performance trend
        assertTrue("Net-discharging session must be excluded from charging trend", analysis.points.isEmpty())
        // Underlying sample power values were NOT mutated
        assertEquals(-7_600_000L, samples[0].derivedPowerUw)
    }

    @Test
    fun `03 - incomplete or corrupted standard tests are excluded`() {
        val incompleteTest = StandardTest(
            id = "t_inc",
            sessionId = "s_inc",
            comparisonGroupKey = "group_1",
            targetStartPercent = 20,
            targetEndPercent = 80,
            isBaseline = false,
            benchmarkStartedElapsedMs = 1000L,
            benchmarkEndedElapsedMs = null, // Incomplete
        )
        val input = StandardTestPerformanceInput(incompleteTest, now, emptyList())
        val analysis = LongitudinalAnalyticsCalculator.calculatePerformanceTrend("group_1", listOf(input))

        assertTrue(analysis.points.isEmpty())
    }

    @Test
    fun `04 - points are sorted chronologically and baseline deltas are computed accurately`() {
        val test1 = StandardTest(
            id = "t1", sessionId = "s1", comparisonGroupKey = "group_1",
            targetStartPercent = 20, targetEndPercent = 80,
            isBaseline = true, baselineSetAt = now.minusSeconds(86400),
            benchmarkStartedElapsedMs = 0L, benchmarkEndedElapsedMs = 1_800_000L, // 30.0 min
        )
        val samples1 = listOf(
            BatterySample(id = "1", sessionId = "s1", timestamp = now, elapsedMs = 1000L, percent = 50, derivedPowerUw = 40_000_000L)
        )

        val test2 = StandardTest(
            id = "t2", sessionId = "s2", comparisonGroupKey = "group_1",
            targetStartPercent = 20, targetEndPercent = 80,
            isBaseline = false,
            benchmarkStartedElapsedMs = 0L, benchmarkEndedElapsedMs = 1_980_000L, // 33.0 min (+3.0 min / +10%)
        )
        val samples2 = listOf(
            BatterySample(id = "2", sessionId = "s2", timestamp = now, elapsedMs = 1000L, percent = 50, derivedPowerUw = 36_000_000L) // -4W / -10%
        )

        val input1 = StandardTestPerformanceInput(test1, now.minusSeconds(86400), samples1)
        val input2 = StandardTestPerformanceInput(test2, now, samples2)

        // Pass in reverse order to test sorting
        val analysis = LongitudinalAnalyticsCalculator.calculatePerformanceTrend("group_1", listOf(input2, input1))

        assertEquals(2, analysis.points.size)
        assertEquals("t1", analysis.points[0].testId)
        assertEquals("t2", analysis.points[1].testId)

        assertNotNull(analysis.baselinePoint)
        assertEquals("t1", analysis.baselinePoint?.testId)

        // Deltas
        assertEquals(180_000L, analysis.latestDurationChangeFromBaselineMs) // +3 min
        assertEquals(10.0, analysis.latestDurationChangePercent ?: 0.0, 0.01)
        assertEquals(-4_000_000L, analysis.latestPowerChangeFromBaselineUw) // -4W
        assertEquals(-10.0, analysis.latestPowerChangePercent ?: 0.0, 0.01)
    }

    @Test
    fun `05 - missing baseline leaves deltas null without crashing`() {
        val test1 = StandardTest(
            id = "t1", sessionId = "s1", comparisonGroupKey = "group_1",
            targetStartPercent = 20, targetEndPercent = 80,
            isBaseline = false,
            benchmarkStartedElapsedMs = 0L, benchmarkEndedElapsedMs = 1_800_000L,
        )
        val samples1 = listOf(
            BatterySample(id = "1", sessionId = "s1", timestamp = now, elapsedMs = 1000L, percent = 50, derivedPowerUw = 40_000_000L)
        )
        val input1 = StandardTestPerformanceInput(test1, now, samples1)
        val analysis = LongitudinalAnalyticsCalculator.calculatePerformanceTrend("group_1", listOf(input1))

        assertNull(analysis.baselinePoint)
        assertNull(analysis.latestDurationChangeFromBaselineMs)
        assertNull(analysis.latestPowerChangeFromBaselineUw)
    }

    @Test
    fun `06 - latest observation vs robust median capacity are clearly distinguished`() {
        val obs1 = FullChargeCapacityObservation("s1", now.minusSeconds(3000), 6750, 6_750_000L, 5)
        val obs2 = FullChargeCapacityObservation("s2", now.minusSeconds(2000), 6700, 6_700_000L, 5)
        val obs3 = FullChargeCapacityObservation("s3", now.minusSeconds(1000), 6720, 6_720_000L, 5)
        val obsLatest = FullChargeCapacityObservation("s4", now, 6680, 6_680_000L, 5) // Latest single observation = 6680 mAh

        // Capacities: [6680, 6700, 6720, 6750] -> median = (6700 + 6720) / 2 = 6710 mAh
        val analysis = LongitudinalAnalyticsCalculator.calculateCapacityTrend(listOf(obs1, obs2, obs3, obsLatest), refCap)

        assertEquals("Latest observation must be 6680 mAh", 6680, analysis.latestObservation?.observedCapacityMah)
        assertEquals("Robust median must be 6710 mAh", 6710, analysis.estimatedCapacityMah)
        assertEquals(96, analysis.estimatedHealthPercent)
        assertEquals(-290, analysis.changeFromReferenceMah) // 6710 - 7000
    }

    @Test
    fun `07 - consistency evaluation both pass CV le 0_05 AND spread le 8 percent`() {
        // 7 consistent observations around 6700 mAh (spread = 60 mAh = 0.85% of 7000, CV < 1%)
        val obs = (1..7).map { i ->
            FullChargeCapacityObservation("s$i", now.plusSeconds(i.toLong() * 100), 6700 + (i % 3) * 20, 6_700_000L, 5)
        }
        val analysis = LongitudinalAnalyticsCalculator.calculateCapacityTrend(obs, refCap)

        assertTrue(analysis.isConsistent)
        assertEquals(DegradationConfidence.HIGH, analysis.confidence)
    }

    @Test
    fun `08 - consistency evaluation fails when spread gt 8 percent even if CV is low`() {
        // 7 observations: six at 6800 mAh, one outlier at 6150 mAh (spread = 650 mAh = 9.28% of 7000 > 8%)
        val obs = listOf(
            FullChargeCapacityObservation("s1", now, 6800, 6_800_000L, 5),
            FullChargeCapacityObservation("s2", now, 6800, 6_800_000L, 5),
            FullChargeCapacityObservation("s3", now, 6800, 6_800_000L, 5),
            FullChargeCapacityObservation("s4", now, 6800, 6_800_000L, 5),
            FullChargeCapacityObservation("s5", now, 6800, 6_800_000L, 5),
            FullChargeCapacityObservation("s6", now, 6800, 6_800_000L, 5),
            FullChargeCapacityObservation("s7", now, 6150, 6_150_000L, 5),
        )
        val analysis = LongitudinalAnalyticsCalculator.calculateCapacityTrend(obs, refCap)

        assertFalse("Spread > 8% must make isConsistent false", analysis.isConsistent)
        assertEquals("N=7 with high variance must downgrade to PRELIMINARY", DegradationConfidence.PRELIMINARY, analysis.confidence)
    }

    @Test
    fun `09 - consistency evaluation fails when CV gt 0_05 even if spread le 8 percent`() {
        // Six observations: three at 4500, three at 5040 (spread = 540 mAh = 7.71% <= 8%, CV = 6.20% > 5%)
        val obs = listOf(
            FullChargeCapacityObservation("s1", now, 4500, 4_500_000L, 5),
            FullChargeCapacityObservation("s2", now, 4500, 4_500_000L, 5),
            FullChargeCapacityObservation("s3", now, 4500, 4_500_000L, 5),
            FullChargeCapacityObservation("s4", now, 5040, 5_040_000L, 5),
            FullChargeCapacityObservation("s5", now, 5040, 5_040_000L, 5),
            FullChargeCapacityObservation("s6", now, 5040, 5_040_000L, 5),
        )
        val analysis = LongitudinalAnalyticsCalculator.calculateCapacityTrend(obs, refCap)

        assertFalse("CV > 0.05 must make isConsistent false", analysis.isConsistent)
        assertEquals(DegradationConfidence.PRELIMINARY, analysis.confidence)
    }

    @Test
    fun `10 - confidence tiers N=2 is INSUFFICIENT, N=4 is PRELIMINARY, N=7 is HIGH`() {
        val obs2 = (1..2).map { FullChargeCapacityObservation("s$it", now, 6700, 6_700_000L, 5) }
        val analysis2 = LongitudinalAnalyticsCalculator.calculateCapacityTrend(obs2, refCap)
        assertEquals(DegradationConfidence.INSUFFICIENT, analysis2.confidence)
        assertNull(analysis2.estimatedCapacityMah)

        val obs4 = (1..4).map { FullChargeCapacityObservation("s$it", now, 6700, 6_700_000L, 5) }
        val analysis4 = LongitudinalAnalyticsCalculator.calculateCapacityTrend(obs4, refCap)
        assertEquals(DegradationConfidence.PRELIMINARY, analysis4.confidence)
        assertNotNull(analysis4.estimatedCapacityMah)

        val obs7 = (1..7).map { FullChargeCapacityObservation("s$it", now, 6700, 6_700_000L, 5) }
        val analysis7 = LongitudinalAnalyticsCalculator.calculateCapacityTrend(obs7, refCap)
        assertEquals(DegradationConfidence.HIGH, analysis7.confidence)
    }
}
