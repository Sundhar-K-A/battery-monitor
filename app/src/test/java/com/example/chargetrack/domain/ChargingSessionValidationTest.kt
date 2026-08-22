package com.example.chargetrack.domain

import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargingSession
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class ChargingSessionValidationTest {

    private val t0 = Instant.parse("2026-01-01T08:00:00Z")
    private val t1 = Instant.parse("2026-01-01T08:30:00Z")

    private fun session(
        startedAt: Instant = t0,
        endedAt: Instant? = t1,
        startPercent: Int = 20,
        endPercent: Int? = 80,
    ) = ChargingSession(
        startedAt = startedAt,
        endedAt = endedAt,
        startPercent = startPercent,
        endPercent = endPercent,
        chargingSetupId = "setup-1",
        softwareSnapshotId = "snap-1",
        testType = TestType.STANDARD
    )

    @Test
    fun `valid session creates without exception`() {
        assertNoThrow { session() }
    }

    @Test
    fun `active session with null endedAt and null endPercent is valid`() {
        assertNoThrow { session(endedAt = null, endPercent = null) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `endedAt before startedAt throws`() {
        session(startedAt = t1, endedAt = t0)
    }

    @Test
    fun `endedAt equal to startedAt is valid (zero-duration edge case)`() {
        assertNoThrow { session(startedAt = t0, endedAt = t0, endPercent = 20) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startPercent below 0 throws`() { session(startPercent = -1) }

    @Test(expected = IllegalArgumentException::class)
    fun `startPercent above 100 throws`() { session(startPercent = 101) }

    @Test(expected = IllegalArgumentException::class)
    fun `endPercent below 0 throws`() { session(endPercent = -1) }

    @Test(expected = IllegalArgumentException::class)
    fun `endPercent above 100 throws`() { session(endPercent = 101) }

    @Test
    fun `startPercent 0 and endPercent 100 are valid`() {
        assertNoThrow { session(startPercent = 0, endPercent = 100) }
    }

    private fun assertNoThrow(block: () -> Unit) {
        try { block() } catch (e: Exception) { fail("Unexpected exception: ${e.message}") }
    }
}
