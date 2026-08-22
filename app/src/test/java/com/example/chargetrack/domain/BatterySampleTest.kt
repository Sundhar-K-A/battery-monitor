package com.example.chargetrack.domain

import com.example.chargetrack.domain.model.BatterySample
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class BatterySampleTest {

    private fun sample(
        percent: Int = 50,
        voltageMv: Int? = null,
        currentNowUa: Int? = null,
        elapsedMs: Long = 0L,
    ) = BatterySample(
        sessionId = "session-1",
        timestamp = Instant.parse("2026-01-01T08:00:00Z"),
        elapsedMs = elapsedMs,
        percent = percent,
        voltageMv = voltageMv,
        currentNowUa = currentNowUa
    )

    @Test
    fun `all optional hardware fields default to null, not zero`() {
        val s = sample()
        assertNull("voltageMv", s.voltageMv)
        assertNull("currentNowUa", s.currentNowUa)
        assertNull("currentAverageUa", s.currentAverageUa)
        assertNull("chargeCounterUah", s.chargeCounterUah)
        assertNull("energyCounterNwh", s.energyCounterNwh)
        assertNull("temperatureDeciC", s.temperatureDeciC)
        assertNull("batteryStatus", s.batteryStatus)
        assertNull("pluggedType", s.pluggedType)
        assertNull("cycleCount", s.cycleCount)
        assertNull("derivedPowerUw", s.derivedPowerUw)
    }

    @Test
    fun `qualityFlags defaults to empty set`() {
        assertTrue(sample().qualityFlags.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `percent below 0 throws`() { sample(percent = -1) }

    @Test(expected = IllegalArgumentException::class)
    fun `percent above 100 throws`() { sample(percent = 101) }

    @Test
    fun `percent boundary values 0 and 100 are valid`() {
        assertNoThrow { sample(percent = 0) }
        assertNoThrow { sample(percent = 100) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative elapsedMs throws`() { sample(elapsedMs = -1L) }

    @Test
    fun `zero elapsedMs is valid`() {
        assertNoThrow { sample(elapsedMs = 0L) }
    }

    private fun assertNoThrow(block: () -> Unit) {
        try { block() } catch (e: Exception) { fail("Unexpected exception: ${e.message}") }
    }
}
