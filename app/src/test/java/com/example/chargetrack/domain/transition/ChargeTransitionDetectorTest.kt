package com.example.chargetrack.domain.transition

import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [ChargeTransitionDetector] — all 24 cases from the
 * Prompt 10 readiness review.
 *
 * Test data conventions
 * ---------------------
 * - Base wall-clock time: T0 = 2026-08-23T18:00:00Z.
 * - [BatterySample.elapsedMs] is advanced in 5 000 ms steps (nominal 5-second interval).
 * - [BatterySample.derivedPowerUw] is set explicitly where power stats are tested.
 * - [BatterySample.qualityFlags] is set explicitly where flag logic is tested.
 */
class ChargeTransitionDetectorTest {

    private val SESSION_ID = "session-test"
    private val T0 = Instant.parse("2026-08-23T18:00:00Z")

    /** Tick counter — advances elapsedMs and timestamp together for simplicity. */
    private var tick = 0

    private lateinit var detector: ChargeTransitionDetector

    @Before
    fun setUp() {
        tick = 0
        detector = ChargeTransitionDetector(SESSION_ID)
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    private fun sample(
        percent: Int?,
        powerUw: Long? = null,
        tempDeciC: Int? = null,
        flags: Set<QualityFlag> = emptySet(),
        elapsedOverride: Long? = null,
        timestampOverride: Instant? = null,
    ): BatterySample {
        val elapsed   = elapsedOverride ?: (tick * 5_000L)
        val timestamp = timestampOverride ?: T0.plusMillis(elapsed)
        tick++
        return BatterySample(
            sessionId        = SESSION_ID,
            timestamp        = timestamp,
            elapsedMs        = elapsed,
            percent          = percent,
            derivedPowerUw   = powerUw,
            temperatureDeciC = tempDeciC,
            qualityFlags     = flags,
        )
    }

    private fun feedSamples(vararg samples: BatterySample): List<ChargeTransition> =
        samples.mapNotNull { detector.onSample(it) }

    // ── Test 01 ───────────────────────────────────────────────────────────────

    @Test
    fun `01 single sample at 50 then session end emits no transition and returns partial info`() {
        detector.onSample(sample(50))

        val partial = detector.onSessionEnd()

        assertNotNull("partial info must not be null", partial)
        assertEquals(50, partial!!.fromPercent)
        assertEquals(1,  partial.samplesCollected)
    }

    // ── Test 02 ───────────────────────────────────────────────────────────────

    @Test
    fun `02 two samples at 50 then 51 emits one GOOD transition with sampleCount 2`() {
        feedSamples(sample(50), sample(50))
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertEquals(50,               result!!.fromPercent)
        assertEquals(51,               result.toPercent)
        assertEquals(2,                result.sampleCount)
        assertEquals(DataQuality.GOOD, result.quality)
    }

    // ── Test 03 ───────────────────────────────────────────────────────────────

    @Test
    fun `03 four samples at 50 then 51 emits GOOD transition with sampleCount 4`() {
        feedSamples(sample(50), sample(50), sample(50), sample(50))
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertEquals(4,                result!!.sampleCount)
        assertEquals(DataQuality.GOOD, result.quality)
    }

    // ── Test 04 ───────────────────────────────────────────────────────────────

    @Test
    fun `04 50 then 51 then 52 emits two DEGRADED single-sample transitions`() {
        val first  = detector.onSample(sample(50))
        val second = detector.onSample(sample(51))
        val third  = detector.onSample(sample(52))

        // 50% sample opens acc — no transition yet
        assertNull("opening sample must not emit", first)

        assertNotNull(second)
        assertEquals(50, second!!.fromPercent); assertEquals(51, second.toPercent)
        assertEquals(1,  second.sampleCount)
        assertEquals(DataQuality.DEGRADED, second.quality)   // single-sample

        assertNotNull(third)
        assertEquals(51, third!!.fromPercent); assertEquals(52, third.toPercent)
        assertEquals(1,  third.sampleCount)
        assertEquals(DataQuality.DEGRADED, third.quality)    // single-sample
    }

    // ── Test 05 ───────────────────────────────────────────────────────────────

    @Test
    fun `05 null-percent samples are accumulated and counted toward sampleCount`() {
        feedSamples(sample(50), sample(null), sample(null))
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertEquals(3,                result!!.sampleCount)   // 50% + null + null
        assertEquals(DataQuality.GOOD, result.quality)
    }

    // ── Test 06 ───────────────────────────────────────────────────────────────

    @Test
    fun `06 only null-percent samples then session end returns null partial info`() {
        feedSamples(sample(null), sample(null), sample(null))
        val partial = detector.onSessionEnd()
        assertNull("no valid percent observed — acc never opened", partial)
    }

    // ── Test 07 ───────────────────────────────────────────────────────────────

    @Test
    fun `07 jitter sample causes DEGRADED quality and is not reverse-transitioned`() {
        feedSamples(
            sample(50),
            sample(49, flags = setOf(QualityFlag.PERCENTAGE_JITTER)),
            sample(50),
        )
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertEquals(50,  result!!.fromPercent)
        assertEquals(51,  result.toPercent)
        assertEquals(3,   result.sampleCount)
        assertEquals(DataQuality.DEGRADED, result.quality)
    }

    // ── Test 08 ───────────────────────────────────────────────────────────────

    @Test
    fun `08 jitter only then session end returns partial info — no transition emitted`() {
        feedSamples(
            sample(50),
            sample(49, flags = setOf(QualityFlag.PERCENTAGE_JITTER)),
        )
        val partial = detector.onSessionEnd()

        assertNotNull(partial)
        assertEquals(50, partial!!.fromPercent)
        assertEquals(2,  partial.samplesCollected)
    }

    // ── Test 09 ───────────────────────────────────────────────────────────────

    @Test
    fun `09 skip of one step 50 to 52 emits single honest gap transition INSUFFICIENT`() {
        detector.onSample(sample(50))
        val result = detector.onSample(sample(52))

        assertNotNull(result)
        assertEquals(50,                      result!!.fromPercent)
        assertEquals(52,                      result.toPercent)    // gap record, not split
        assertEquals(DataQuality.INSUFFICIENT, result.quality)
        assertEquals(1,                        result.sampleCount) // only the 50% sample
    }

    // ── Test 10 ───────────────────────────────────────────────────────────────

    @Test
    fun `10 skip of four steps 50 to 55 emits single INSUFFICIENT gap transition`() {
        detector.onSample(sample(50))
        val result = detector.onSample(sample(55))

        assertNotNull(result)
        assertEquals(50,                      result!!.fromPercent)
        assertEquals(55,                      result.toPercent)
        assertEquals(DataQuality.INSUFFICIENT, result.quality)
    }

    // ── Test 11 ───────────────────────────────────────────────────────────────

    @Test
    fun `11 GAP_DETECTED-flagged sample in accumulator causes DEGRADED quality`() {
        feedSamples(
            sample(50),
            sample(50, flags = setOf(QualityFlag.GAP_DETECTED)),
        )
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertEquals(DataQuality.DEGRADED, result!!.quality)
    }

    // ── Test 12 ───────────────────────────────────────────────────────────────

    @Test
    fun `12 session starting at 35 percent produces no 0-34 transitions`() {
        feedSamples(sample(35), sample(35))
        val result = detector.onSample(sample(36))

        assertNotNull(result)
        assertEquals(35,               result!!.fromPercent)
        assertEquals(36,               result.toPercent)
        assertEquals(2,                result.sampleCount)
        assertEquals(DataQuality.GOOD, result.quality)
    }

    // ── Test 13 ───────────────────────────────────────────────────────────────

    @Test
    fun `13 clean 20 to 80 run produces 60 GOOD transitions`() {
        // Each percent level gets 2 clean samples before advancing.
        val transitions = mutableListOf<ChargeTransition>()
        for (pct in 20..79) {
            detector.onSample(sample(pct))     // opener
            detector.onSample(sample(pct))     // second sample at same level
            val t = detector.onSample(sample(pct + 1))
            if (t != null) transitions += t
        }

        assertEquals(60, transitions.size)
        transitions.forEachIndexed { i, t ->
            assertEquals("transition $i fromPercent", 20 + i, t.fromPercent)
            assertEquals("transition $i toPercent",   21 + i, t.toPercent)
            assertEquals("transition $i quality", DataQuality.GOOD, t.quality)
        }
    }

    // ── Test 14 ───────────────────────────────────────────────────────────────

    @Test
    fun `14 durationMs derives from elapsedMs not wall-clock`() {
        // elapsedMs: 0 ms, 6_000 ms, 13_500 ms (deliberately non-uniform)
        val s50a = sample(50, elapsedOverride = 0L,      timestampOverride = T0)
        val s50b = sample(50, elapsedOverride = 6_000L,  timestampOverride = T0.plusSeconds(6))
        val s51  = sample(51, elapsedOverride = 13_500L, timestampOverride = T0.plusSeconds(13))

        feedSamples(s50a, s50b)
        val result = detector.onSample(s51)

        assertNotNull(result)
        // durationMs = closingElapsedMs − startElapsedMs = 13_500 − 0 = 13_500
        assertEquals(13_500L, result!!.durationMs)
    }

    // ── Test 15 ───────────────────────────────────────────────────────────────

    @Test
    fun `15 single-sample transition is emitted with DEGRADED quality`() {
        detector.onSample(sample(50))           // opens acc — no emission
        val result = detector.onSample(sample(51))  // closes immediately with 1 sample

        assertNotNull(result)
        assertEquals(1,                    result!!.sampleCount)
        assertEquals(DataQuality.DEGRADED, result.quality)
    }

    // ── Test 16 ───────────────────────────────────────────────────────────────

    @Test
    fun `16 OUTLIER-flagged sample in accumulator causes DEGRADED quality`() {
        feedSamples(
            sample(50, flags = setOf(QualityFlag.OUTLIER)),
            sample(50),
        )
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertEquals(DataQuality.DEGRADED, result!!.quality)
    }

    // ── Test 17 ───────────────────────────────────────────────────────────────

    @Test
    fun `17 all null derivedPowerUw samples produce null power stats`() {
        feedSamples(sample(50, powerUw = null), sample(50, powerUw = null))
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertNull(result!!.averagePowerUw)
        assertNull(result.medianPowerUw)
        assertNull(result.peakPowerUw)
    }

    // ── Test 18 ───────────────────────────────────────────────────────────────

    @Test
    fun `18 mixed null and non-null power only non-null values contribute to stats`() {
        feedSamples(
            sample(50, powerUw = null),
            sample(50, powerUw = 3_000_000L),
            sample(50, powerUw = null),
            sample(50, powerUw = 5_000_000L),
        )
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertEquals(4_000_000L, result!!.averagePowerUw) // (3M + 5M) / 2
        assertEquals(4_000_000L, result.medianPowerUw)    // median of [3M, 5M] = 4M
        assertEquals(5_000_000L, result.peakPowerUw)
    }

    // ── Test 19 ───────────────────────────────────────────────────────────────

    @Test
    fun `19 boundary ownership closing sample power belongs to new window not closing transition`() {
        // 50% sample has power = 1_000_000 µW → this is in the 50→51 accumulator
        feedSamples(sample(50, powerUw = 1_000_000L))
        // 51% sample has power = 9_000_000 µW → closes 50→51 but NOT counted in its stats
        val closing51 = sample(51, powerUw = 9_000_000L)
        val result50to51 = detector.onSample(closing51)

        assertNotNull(result50to51)
        // Only the 50% sample's power should appear in 50→51 stats
        assertEquals(1_000_000L, result50to51!!.peakPowerUw)
        assertEquals(1_000_000L, result50to51.averagePowerUw)

        // Now advance to 52% with a clean sample — the 51% sample IS in the 51→52 window
        feedSamples(sample(51, powerUw = 2_000_000L))
        val result51to52 = detector.onSample(sample(52))
        assertNotNull(result51to52)
        // The closing51 sample (9M) was the first of acc(51). acc(51) also has a 2M sample.
        // peakPowerUw of 51→52 should be max(9M, 2M) = 9M
        assertEquals(9_000_000L, result51to52!!.peakPowerUw)
    }

    // ── Test 20 ───────────────────────────────────────────────────────────────

    @Test
    fun `20 boundary ownership durationMs uses closing sample elapsedMs`() {
        val open   = sample(50, elapsedOverride = 1_000L)
        val second = sample(50, elapsedOverride = 6_000L)
        val close  = sample(51, elapsedOverride = 11_000L)

        feedSamples(open, second)
        val result = detector.onSample(close)

        assertNotNull(result)
        // durationMs = close.elapsedMs − open.elapsedMs = 11_000 − 1_000 = 10_000
        assertEquals(10_000L, result!!.durationMs)
    }

    // ── Test 21 ───────────────────────────────────────────────────────────────

    @Test
    fun `21 session end with open accumulator returns PartialTransitionInfo not a transition`() {
        feedSamples(sample(50), sample(50))

        val partial = detector.onSessionEnd()

        assertNotNull(partial)
        assertEquals(50, partial!!.fromPercent)
        assertEquals(2,  partial.samplesCollected)

        // Detector is reset — next call opens a fresh accumulator
        val afterReset = detector.onSample(sample(60))
        assertNull("first sample after reset must not emit", afterReset)
    }

    // ── Test 22 ───────────────────────────────────────────────────────────────

    @Test
    fun `22 two consecutive sessions using same detector do not cross-pollute`() {
        // Session 1
        feedSamples(sample(50), sample(50))
        val session1Transition = detector.onSample(sample(51))
        detector.onSessionEnd()

        // Session 2 — detector is reset; tick continues but that's fine
        feedSamples(sample(70), sample(70))
        val session2Transition = detector.onSample(sample(71))

        assertNotNull(session1Transition)
        assertEquals(50, session1Transition!!.fromPercent)
        assertEquals(51, session1Transition.toPercent)

        assertNotNull(session2Transition)
        assertEquals(70, session2Transition!!.fromPercent)
        assertEquals(71, session2Transition.toPercent)
    }

    // ── Test 23 ───────────────────────────────────────────────────────────────

    @Test
    fun `23 median power is middle value for odd-count sample list`() {
        // 3 samples with power [100, 300, 200] µW → sorted [100, 200, 300] → median = 200
        feedSamples(
            sample(50, powerUw = 100L),
            sample(50, powerUw = 300L),
            sample(50, powerUw = 200L),
        )
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertEquals(200L, result!!.medianPowerUw)
        assertEquals(200L, result.averagePowerUw)   // (100+300+200)/3 = 200
        assertEquals(300L, result.peakPowerUw)
    }

    // ── Test 24 ───────────────────────────────────────────────────────────────

    @Test
    fun `24 median power is average of two middle values for even-count sample list`() {
        // 4 samples with power [100, 200, 300, 400] µW → sorted → median = (200+300)/2 = 250
        feedSamples(
            sample(50, powerUw = 100L),
            sample(50, powerUw = 400L),
            sample(50, powerUw = 200L),
            sample(50, powerUw = 300L),
        )
        val result = detector.onSample(sample(51))

        assertNotNull(result)
        assertEquals(250L, result!!.medianPowerUw)
        assertEquals(250L, result.averagePowerUw)   // (100+200+300+400)/4 = 250
        assertEquals(400L, result.peakPowerUw)
    }
}
