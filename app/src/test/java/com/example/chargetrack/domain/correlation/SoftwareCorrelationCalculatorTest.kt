package com.example.chargetrack.domain.correlation

import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SoftwareCorrelationCalculatorTest {

    private val now = Instant.now()
    private val groupKey = "standard_20_80_wired_official"

    private fun createSnapshot(
        androidVersion: String = "16",
        sdkInt: Int = 36,
        originOsVersion: String? = "PD2505_A_16.0.4.1",
        buildFingerprint: String = "vivo/PD2505/PD2505:16/...",
        appVersionName: String = "1.0.0",
        appVersionCode: Int = 1,
    ) = SoftwareSnapshot(
        capturedAt = now,
        androidVersion = androidVersion,
        sdkInt = sdkInt,
        originOsVersion = originOsVersion,
        buildFingerprint = buildFingerprint,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
    )

    private fun createTestInput(
        testId: String,
        sessionId: String,
        startedAt: Instant,
        snapshot: SoftwareSnapshot,
        durationMs: Long = 1_800_000L, // 30 min
        avgPowerUw: Long = 40_000_000L, // 40W
        startElapsedMs: Long = 10_000L,
        endElapsedMs: Long = startElapsedMs + durationMs,
    ): StandardTestWithSnapshotInput {
        val test = StandardTest(
            id = testId,
            sessionId = sessionId,
            comparisonGroupKey = groupKey,
            targetStartPercent = 20,
            targetEndPercent = 80,
            benchmarkStartedElapsedMs = startElapsedMs,
            benchmarkEndedElapsedMs = endElapsedMs,
        )
        val samples = listOf(
            // Pre-benchmark sample (10W)
            BatterySample(id = "pre-$testId", sessionId = sessionId, timestamp = startedAt, elapsedMs = startElapsedMs - 5_000L, percent = 18, derivedPowerUw = 10_000_000L),
            // In-benchmark sample (avgPowerUw)
            BatterySample(id = "in-$testId", sessionId = sessionId, timestamp = startedAt, elapsedMs = startElapsedMs + 50_000L, percent = 50, derivedPowerUw = avgPowerUw, temperatureDeciC = 370),
            // Post-benchmark sample (5W)
            BatterySample(id = "post-$testId", sessionId = sessionId, timestamp = startedAt, elapsedMs = endElapsedMs + 10_000L, percent = 82, derivedPowerUw = 5_000_000L),
        )
        return StandardTestWithSnapshotInput(test, startedAt, snapshot, samples)
    }

    @Test
    fun `01 - robustness on N=0 qualifying benchmark sessions returns clean empty analysis`() {
        val emptyAnalysis = SoftwareCorrelationCalculator.calculateCorrelationAnalysis(groupKey, emptyList())

        assertEquals(groupKey, emptyAnalysis.comparisonGroupKey)
        assertTrue("Firmware summaries must be empty for N=0", emptyAnalysis.firmwareSummaries.isEmpty())
        assertTrue("Firmware transitions must be empty for N=0", emptyAnalysis.firmwareTransitions.isEmpty())
        assertTrue("Build comparisons must be empty for N=0", emptyAnalysis.buildComparisons.isEmpty())

        // Incomplete test (benchmarkEndedElapsedMs = null) should also result in N=0
        val incompleteTest = StandardTest(
            id = "t_inc",
            sessionId = "s_inc",
            comparisonGroupKey = groupKey,
            targetStartPercent = 20,
            targetEndPercent = 80,
            benchmarkStartedElapsedMs = 1000L,
            benchmarkEndedElapsedMs = null,
        )
        val incompleteInput = StandardTestWithSnapshotInput(incompleteTest, now, createSnapshot(), emptyList())
        val analysisFromIncomplete = SoftwareCorrelationCalculator.calculateCorrelationAnalysis(groupKey, listOf(incompleteInput))
        assertTrue(analysisFromIncomplete.firmwareSummaries.isEmpty())
    }

    @Test
    fun `02 - separates firmware identity from app version identity in transitions`() {
        val snapFw1App1 = createSnapshot(originOsVersion = "PD2505_Build_1", appVersionName = "1.0")
        val snapFw1App2 = createSnapshot(originOsVersion = "PD2505_Build_1", appVersionName = "1.1") // App updated only
        val snapFw2App2 = createSnapshot(originOsVersion = "PD2505_Build_2", appVersionName = "1.1") // Firmware updated only
        val snapFw3App3 = createSnapshot(originOsVersion = "PD2505_Build_3", appVersionName = "1.2") // Both updated

        val input1 = createTestInput("t1", "s1", now.minusSeconds(4000), snapFw1App1)
        val input2 = createTestInput("t2", "s2", now.minusSeconds(3000), snapFw1App2)
        val input3 = createTestInput("t3", "s3", now.minusSeconds(2000), snapFw2App2)
        val input4 = createTestInput("t4", "s4", now.minusSeconds(1000), snapFw3App3)

        val analysis = SoftwareCorrelationCalculator.calculateCorrelationAnalysis(groupKey, listOf(input1, input2, input3, input4))

        assertEquals(3, analysis.firmwareTransitions.size)

        // Transition 1: App updated only
        val tr1 = analysis.firmwareTransitions[0]
        assertFalse("Firmware did not change in transition 1", tr1.isFirmwareChanged)
        assertTrue("App changed in transition 1", tr1.isAppVersionChanged)

        // Transition 2: Firmware updated only
        val tr2 = analysis.firmwareTransitions[1]
        assertTrue("Firmware changed in transition 2", tr2.isFirmwareChanged)
        assertFalse("App did not change in transition 2", tr2.isAppVersionChanged)

        // Transition 3: Both updated
        val tr3 = analysis.firmwareTransitions[2]
        assertTrue("Firmware changed in transition 3", tr3.isFirmwareChanged)
        assertTrue("App changed in transition 3", tr3.isAppVersionChanged)
    }

    @Test
    fun `03 - metrics are calculated strictly from benchmark-only window`() {
        val snap = createSnapshot()
        val input = createTestInput("t1", "s1", now, snap, durationMs = 1_800_000L, avgPowerUw = 42_000_000L)

        val analysis = SoftwareCorrelationCalculator.calculateCorrelationAnalysis(groupKey, listOf(input))
        assertEquals(1, analysis.firmwareSummaries.size)

        val summary = analysis.firmwareSummaries.first()
        assertEquals(1_800_000L, summary.medianBenchmarkDurationMs)
        assertEquals(42_000_000L, summary.meanBenchmarkAveragePowerUw)
        assertEquals(370, summary.maxBenchmarkTempDeciC)
    }

    @Test
    fun `04 - low evidence flagging when build has fewer than 3 sessions`() {
        val snap1 = createSnapshot(originOsVersion = "Build_1")
        val snap2 = createSnapshot(originOsVersion = "Build_2")

        // Build 1 has 2 tests (Low evidence)
        val b1t1 = createTestInput("b1t1", "s1", now.minusSeconds(5000), snap1)
        val b1t2 = createTestInput("b1t2", "s2", now.minusSeconds(4000), snap1)

        // Build 2 has 3 tests (High evidence)
        val b2t1 = createTestInput("b2t1", "s3", now.minusSeconds(3000), snap2)
        val b2t2 = createTestInput("b2t2", "s4", now.minusSeconds(2000), snap2)
        val b2t3 = createTestInput("b2t3", "s5", now.minusSeconds(1000), snap2)

        val analysis = SoftwareCorrelationCalculator.calculateCorrelationAnalysis(groupKey, listOf(b1t1, b1t2, b2t1, b2t2, b2t3))

        assertEquals(2, analysis.firmwareSummaries.size)
        val summary1 = analysis.firmwareSummaries[0]
        val summary2 = analysis.firmwareSummaries[1]

        assertEquals(2, summary1.sessionCount)
        assertTrue("Build 1 with 2 sessions must be flagged as low evidence", summary1.isLowEvidence)

        assertEquals(3, summary2.sessionCount)
        assertFalse("Build 2 with 3 sessions must NOT be flagged as low evidence", summary2.isLowEvidence)

        // Comparison between Build 1 and Build 2 must be flagged as low evidence because Build 1 < 3
        assertEquals(1, analysis.buildComparisons.size)
        assertTrue("Comparison involving a low-evidence build must be flagged as low evidence", analysis.buildComparisons[0].isLowEvidence)
    }

    @Test
    fun `05 - computes neutral observed difference deltas between firmware builds`() {
        val snap1 = createSnapshot(originOsVersion = "Build_1")
        val snap2 = createSnapshot(originOsVersion = "Build_2")

        // Build 1: 30 min (1,800,000 ms), 40W (40,000,000 uW)
        val b1Tests = (1..3).map { i ->
            createTestInput("b1-$i", "s1-$i", now.minusSeconds(10000 - i.toLong() * 100), snap1, durationMs = 1_800_000L, avgPowerUw = 40_000_000L)
        }

        // Build 2: 33 min (1,980,000 ms, +10%), 36W (36,000,000 uW, -10%)
        val b2Tests = (1..3).map { i ->
            createTestInput("b2-$i", "s2-$i", now.minusSeconds(5000 - i.toLong() * 100), snap2, durationMs = 1_980_000L, avgPowerUw = 36_000_000L)
        }

        val analysis = SoftwareCorrelationCalculator.calculateCorrelationAnalysis(groupKey, b1Tests + b2Tests)

        assertEquals(1, analysis.buildComparisons.size)
        val comp = analysis.buildComparisons.first()

        assertFalse(comp.isLowEvidence)
        assertEquals(180_000L, comp.durationShiftMs) // +3 min
        assertEquals(10.0, comp.durationShiftPercent ?: 0.0, 0.01)
        assertEquals(-4_000_000L, comp.powerShiftUw) // -4 W
        assertEquals(-10.0, comp.powerShiftPercent ?: 0.0, 0.01)
    }

    @Test
    fun `06 - handles out-of-order session timestamps and sorts chronologically`() {
        val snap1 = createSnapshot(originOsVersion = "Build_1")
        val snap2 = createSnapshot(originOsVersion = "Build_2")

        val t1 = createTestInput("t1", "s1", now.minusSeconds(2000), snap1)
        val t2 = createTestInput("t2", "s2", now.minusSeconds(1000), snap2)

        // Pass in reverse order
        val analysis = SoftwareCorrelationCalculator.calculateCorrelationAnalysis(groupKey, listOf(t2, t1))

        assertEquals(2, analysis.firmwareSummaries.size)
        assertEquals("Build_1", analysis.firmwareSummaries[0].originOsVersion)
        assertEquals("Build_2", analysis.firmwareSummaries[1].originOsVersion)

        assertEquals(1, analysis.firmwareTransitions.size)
        assertEquals("s2", analysis.firmwareTransitions[0].sessionId)
    }
}
