package com.example.chargetrack.ui.charts.transform

import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChartDataTransformerTest {

    private fun createSample(
        elapsedMs: Long,
        percent: Int? = 50,
        voltageMv: Int? = 4000,
        currentNowUa: Int? = 10_000_000,
        temperatureDeciC: Int? = 320,
        derivedPowerUw: Long? = 40_000_000L,
        qualityFlags: Set<QualityFlag> = emptySet(),
    ): BatterySample {
        return BatterySample(
            id = "sample-$elapsedMs",
            sessionId = "test-session-1",
            timestamp = Instant.ofEpochMilli(1700000000000L + elapsedMs),
            elapsedMs = elapsedMs,
            percent = percent,
            voltageMv = voltageMv,
            currentNowUa = currentNowUa,
            temperatureDeciC = temperatureDeciC,
            derivedPowerUw = derivedPowerUw,
            qualityFlags = qualityFlags,
        )
    }

    @Test
    fun `01 - buildBatteryPercentVsTime converts time to minutes and splits on null percent`() {
        val samples = listOf(
            createSample(elapsedMs = 0L, percent = 20),
            createSample(elapsedMs = 60_000L, percent = 22),
            createSample(elapsedMs = 120_000L, percent = null), // gap
            createSample(elapsedMs = 180_000L, percent = 25),
            createSample(elapsedMs = 240_000L, percent = 27),
        )

        val series = ChartDataTransformer.buildBatteryPercentVsTime(samples)

        assertEquals("Battery % vs Time", series.name)
        assertEquals("%", series.yUnit)
        assertEquals("min", series.xUnit)
        assertEquals(2, series.segments.size) // Split into 2 segments around null gap
        assertEquals(2, series.segments[0].points.size)
        assertEquals(2, series.segments[1].points.size)

        assertEquals(0f, series.segments[0].points[0].x, 0.001f) // 0 min
        assertEquals(1f, series.segments[0].points[1].x, 0.001f) // 1 min
        assertEquals(3f, series.segments[1].points[0].x, 0.001f) // 3 min
        assertEquals(4f, series.segments[1].points[1].x, 0.001f) // 4 min
    }

    @Test
    fun `02 - buildPowerVsBatteryPercent preserves repeated percentage points as distinct sequential observations`() {
        val samples = listOf(
            createSample(elapsedMs = 0L, percent = 50, derivedPowerUw = 80_000_000L),
            createSample(elapsedMs = 5_000L, percent = 50, derivedPowerUw = 79_000_000L),
            createSample(elapsedMs = 10_000L, percent = 50, derivedPowerUw = 78_000_000L),
            createSample(elapsedMs = 15_000L, percent = 51, derivedPowerUw = 77_000_000L),
        )

        val series = ChartDataTransformer.buildPowerVsBatteryPercent(samples)

        assertEquals(1, series.segments.size)
        val points = series.segments[0].points
        assertEquals("Must NOT collapse 3 repeated 50% samples into one", 4, points.size)

        assertEquals(50f, points[0].x, 0.001f)
        assertEquals(80.0f, points[0].y, 0.001f) // 80W
        assertEquals(0L, points[0].tooltip.elapsedMs)

        assertEquals(50f, points[1].x, 0.001f)
        assertEquals(79.0f, points[1].y, 0.001f) // 79W
        assertEquals(5_000L, points[1].tooltip.elapsedMs)

        assertEquals(50f, points[2].x, 0.001f)
        assertEquals(78.0f, points[2].y, 0.001f) // 78W
        assertEquals(10_000L, points[2].tooltip.elapsedMs)
    }

    @Test
    fun `03 - buildPowerVsTime converts uW to W, preserves negative values, and sets zero line`() {
        val samples = listOf(
            createSample(elapsedMs = 0L, derivedPowerUw = 50_000_000L),  // +50W
            createSample(elapsedMs = 60_000L, derivedPowerUw = -5_000_000L), // -5W (net discharge)
            createSample(elapsedMs = 120_000L, derivedPowerUw = 40_000_000L), // +40W
        )

        val series = ChartDataTransformer.buildPowerVsTime(samples)

        assertEquals(1, series.segments.size)
        val points = series.segments[0].points
        assertEquals(50.0f, points[0].y, 0.001f)
        assertEquals(-5.0f, points[1].y, 0.001f)
        assertEquals(40.0f, points[2].y, 0.001f)
        assertTrue("Must activate zero reference line when data spans across zero", series.hasZeroLine)
    }

    @Test
    fun `04 - buildTemperatureVsBatteryPercent converts deciC to Celsius`() {
        val samples = listOf(
            createSample(elapsedMs = 0L, percent = 20, temperatureDeciC = 285), // 28.5 °C
            createSample(elapsedMs = 30_000L, percent = 25, temperatureDeciC = 342), // 34.2 °C
        )

        val series = ChartDataTransformer.buildTemperatureVsBatteryPercent(samples)
        val points = series.segments[0].points

        assertEquals(28.5f, points[0].y, 0.001f)
        assertEquals(34.2f, points[1].y, 0.001f)
    }

    @Test
    fun `05 - buildCurrentVsBatteryPercent converts uA to Amperes without clamping negative current`() {
        val samples = listOf(
            createSample(elapsedMs = 0L, percent = 30, currentNowUa = 12_500_000), // +12.5 A
            createSample(elapsedMs = 10_000L, percent = 31, currentNowUa = -1_200_000), // -1.2 A
        )

        val series = ChartDataTransformer.buildCurrentVsBatteryPercent(samples)
        val points = series.segments[0].points

        assertEquals(12.5f, points[0].y, 0.001f)
        assertEquals(-1.2f, points[1].y, 0.001f)
        assertTrue(series.hasZeroLine)
    }

    @Test
    fun `06 - buildTimePerPercentBars distinguishes GOOD, DEGRADED, and INSUFFICIENT multi-percent gaps`() {
        val transitions = listOf(
            ChargeTransition(
                id = "trans-1",
                sessionId = "s1",
                fromPercent = 20,
                toPercent = 21,
                startedAt = Instant.now(),
                endedAt = Instant.now(),
                durationMs = 15_000L,
                sampleCount = 3,
                quality = DataQuality.GOOD,
            ),
            ChargeTransition(
                id = "trans-2",
                sessionId = "s1",
                fromPercent = 21,
                toPercent = 22,
                startedAt = Instant.now(),
                endedAt = Instant.now(),
                durationMs = 22_000L,
                sampleCount = 4,
                quality = DataQuality.DEGRADED,
            ),
            ChargeTransition(
                id = "trans-3",
                sessionId = "s1",
                fromPercent = 22,
                toPercent = 25, // 3% gap jump!
                startedAt = Instant.now(),
                endedAt = Instant.now(),
                durationMs = 50_000L,
                sampleCount = 8,
                quality = DataQuality.INSUFFICIENT,
            ),
        )

        val bars = ChartDataTransformer.buildTimePerPercentBars(transitions)

        assertEquals(3, bars.size)

        // 1. GOOD 1% bar
        assertEquals("20→21%", bars[0].label)
        assertEquals(15.0, bars[0].durationSeconds, 0.001)
        assertEquals(DataQuality.GOOD, bars[0].quality)
        assertFalse(bars[0].isGap)

        // 2. DEGRADED 1% bar
        assertEquals("21→22%", bars[1].label)
        assertEquals(22.0, bars[1].durationSeconds, 0.001)
        assertEquals(DataQuality.DEGRADED, bars[1].quality)
        assertFalse(bars[1].isGap)

        // 3. INSUFFICIENT multi-% gap bar
        assertTrue("Multi-% jump must be flagged as a gap", bars[2].isGap)
        assertTrue(bars[2].label.contains("GAP"))
        assertEquals(DataQuality.INSUFFICIENT, bars[2].quality)
    }

    @Test
    fun `07 - chart peak and taper anchors use authoritative Prompt 13 SessionSummary without redefining rules`() {
        val samples = listOf(
            createSample(elapsedMs = 0L, percent = 20, derivedPowerUw = 50_000_000L),
            createSample(elapsedMs = 5_000L, percent = 20, derivedPowerUw = 99_000_000L, qualityFlags = setOf(QualityFlag.OUTLIER)), // Outlier!
            createSample(elapsedMs = 10_000L, percent = 21, derivedPowerUw = 82_000_000L), // Authoritative analytical peak!
            createSample(elapsedMs = 15_000L, percent = 55, derivedPowerUw = 60_000_000L), // Taper start onset!
            createSample(elapsedMs = 20_000L, percent = 80, derivedPowerUw = 20_000_000L),
        )

        val summary = SessionSummary(
            sessionId = "s1",
            testType = TestType.STANDARD,
            startedAt = Instant.now(),
            endedAt = Instant.now(),
            endReason = SessionEndReason.USER_STOPPED,
            durationMs = 20_000L,
            startPercent = 20,
            endPercent = 80,
            percentGained = 60,
            isCompleteStandardTest = true,
            totalSampleCount = 5,
            validPowerSampleCount = 4,
            missingValueSampleCount = 0,
            gapSampleCount = 0,
            jitterSampleCount = 0,
            outlierSampleCount = 1,
            totalTransitionCount = 1,
            contiguousOnePercentTransitionCount = 1,
            degradedTransitionCount = 0,
            insufficientTransitionCount = 0,
            averagePowerUw = 60_000_000L,
            medianPowerUw = 60_000_000L,
            peakPowerUw = 82_000_000L, // Authoritative peak from Prompt 13
            minCurrentUa = null,
            maxCurrentUa = null,
            averageCurrentUa = null,
            averageVoltageMv = null,
            startTemperatureDeciC = null,
            endTemperatureDeciC = null,
            averageTemperatureDeciC = null,
            peakTemperatureDeciC = null,
            averageTimePerOnePercentMs = null,
            medianTimePerOnePercentMs = null,
            chargingTaperStartPercent = 55, // Authoritative taper from Prompt 13
            overallQuality = DataQuality.GOOD,
            qualityFlags = emptySet(),
        )

        val series = ChartDataTransformer.buildPowerVsTime(samples, summary = summary)

        // Peak point should match 82W (sample at index 2), NOT the 99W outlier (sample at index 1)
        assertNotNull(series.peakPoint)
        assertEquals(82.0f, series.peakPoint!!.y, 0.001f)
        assertTrue(series.peakPoint!!.tooltip.isPeak)
        assertFalse(series.peakPoint!!.tooltip.isOutlier)

        // Taper point should match 55% onset
        assertNotNull(series.taperPoint)
        assertTrue(series.taperPoint!!.tooltip.isTaperStart)

        // Outlier sample should be marked isOutlier = true
        val outlierPoint = series.segments[0].points.first { it.y == 99.0f }
        assertTrue(outlierPoint.isOutlier)
        assertFalse(outlierPoint.tooltip.isPeak)
    }

    @Test
    fun `08 - tooltip metadata contains exact telemetry and quality flags`() {
        val sample = createSample(
            elapsedMs = 45_000L,
            percent = 65,
            voltageMv = 4250,
            currentNowUa = 8_200_000,
            temperatureDeciC = 365,
            derivedPowerUw = 34_850_000L,
            qualityFlags = setOf(QualityFlag.GAP_DETECTED),
        )

        val series = ChartDataTransformer.buildPowerVsTime(listOf(sample))
        val tt = series.segments[0].points[0].tooltip

        assertEquals(45_000L, tt.elapsedMs)
        assertEquals(65, tt.percent)
        assertEquals(34.85, tt.powerW!!, 0.001)
        assertEquals(4.25, tt.voltageV!!, 0.001)
        assertEquals(8.20, tt.currentA!!, 0.001)
        assertEquals(36.5, tt.temperatureC!!, 0.001)
        assertTrue(tt.qualityFlags.contains(QualityFlag.GAP_DETECTED))
    }
}
