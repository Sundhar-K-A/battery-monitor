package com.example.chargetrack.domain

import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.model.ChargeTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class ChargeTransitionValidationTest {

    private val t0 = Instant.parse("2026-01-01T08:00:00Z")
    private val t1 = Instant.parse("2026-01-01T08:01:30Z") // 90 s later

    private fun transition(
        fromPercent: Int = 20,
        toPercent: Int = 21,
        startedAt: Instant = t0,
        endedAt: Instant = t1,
        durationMs: Long = 90_000L,
        sampleCount: Int = 18,
        quality: DataQuality = DataQuality.GOOD,
    ) = ChargeTransition(
        sessionId = "session-1",
        fromPercent = fromPercent,
        toPercent = toPercent,
        startedAt = startedAt,
        endedAt = endedAt,
        durationMs = durationMs,
        sampleCount = sampleCount,
        quality = quality
    )

    @Test
    fun `valid transition creates without exception`() {
        assertNoThrow { transition() }
    }

    @Test
    fun `duration is stored correctly`() {
        assertEquals(90_000L, transition().durationMs)
    }

    @Test
    fun `power and temperature fields default to null`() {
        val t = transition()
        assertNull(t.averagePowerUw)
        assertNull(t.medianPowerUw)
        assertNull(t.peakPowerUw)
        assertNull(t.averageTemperatureDeciC)
        assertNull(t.maxTemperatureDeciC)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toPercent equal to fromPercent throws`() { transition(fromPercent = 21, toPercent = 21) }

    @Test(expected = IllegalArgumentException::class)
    fun `toPercent less than fromPercent throws`() { transition(fromPercent = 21, toPercent = 20) }

    @Test(expected = IllegalArgumentException::class)
    fun `fromPercent of 100 throws`() { transition(fromPercent = 100, toPercent = 101) }

    @Test(expected = IllegalArgumentException::class)
    fun `toPercent of 0 throws`() { transition(fromPercent = -1, toPercent = 0) }

    @Test(expected = IllegalArgumentException::class)
    fun `negative durationMs throws`() { transition(durationMs = -1L) }

    @Test(expected = IllegalArgumentException::class)
    fun `endedAt before startedAt throws`() { transition(startedAt = t1, endedAt = t0) }

    @Test(expected = IllegalArgumentException::class)
    fun `negative sampleCount throws`() { transition(sampleCount = -1) }

    @Test
    fun `INSUFFICIENT quality is stored and returned`() {
        val t = transition(quality = DataQuality.INSUFFICIENT)
        assertEquals(DataQuality.INSUFFICIENT, t.quality)
    }

    @Test
    fun `full valid range 99 to 100 is allowed`() {
        assertNoThrow { transition(fromPercent = 99, toPercent = 100) }
    }

    @Test
    fun `full valid range 0 to 1 is allowed`() {
        assertNoThrow { transition(fromPercent = 0, toPercent = 1) }
    }

    private fun assertNoThrow(block: () -> Unit) {
        try { block() } catch (e: Exception) { fail("Unexpected exception: ${e.message}") }
    }
}
