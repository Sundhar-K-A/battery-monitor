package com.example.chargetrack.domain

import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargingSession
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class ChargingSessionValidationTest {

    private val t0 = Instant.parse("2026-01-01T08:00:00Z")
    private val t1 = Instant.parse("2026-01-01T08:30:00Z")

    /** Completed session helper — both endedAt and endReason set. */
    private fun completedSession(
        startedAt: Instant = t0,
        endedAt: Instant? = t1,
        endReason: SessionEndReason? = SessionEndReason.USER_STOPPED,
        startPercent: Int = 20,
        endPercent: Int? = 80,
    ) = ChargingSession(
        startedAt = startedAt,
        endedAt = endedAt,
        endReason = endReason,
        startPercent = startPercent,
        endPercent = endPercent,
        chargingSetupId = "setup-1",
        softwareSnapshotId = "snap-1",
        testType = TestType.STANDARD
    )

    /** Active session helper — both endedAt and endReason null. */
    private fun activeSession(startPercent: Int = 20) = ChargingSession(
        startedAt = t0,
        endedAt = null,
        endReason = null,
        startPercent = startPercent,
        endPercent = null,
        chargingSetupId = "setup-1",
        softwareSnapshotId = "snap-1"
    )

    // ── Valid states ──────────────────────────────────────────────────────

    @Test
    fun `completed session with endedAt and endReason is valid`() {
        assertNoThrow { completedSession() }
    }

    @Test
    fun `active session with null endedAt and null endReason is valid`() {
        assertNoThrow { activeSession() }
    }

    @Test
    fun `completed session may have null endPercent (interrupted before last sample)`() {
        assertNoThrow { completedSession(endPercent = null) }
    }

    @Test
    fun `endedAt equal to startedAt is valid (zero-duration edge case)`() {
        assertNoThrow { completedSession(startedAt = t0, endedAt = t0, endPercent = 20) }
    }

    @Test
    fun `startPercent 0 and endPercent 100 are valid`() {
        assertNoThrow { completedSession(startPercent = 0, endPercent = 100) }
    }

    // ── endedAt / endReason co-presence invariant ─────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `endedAt set but endReason null throws`() {
        completedSession(endedAt = t1, endReason = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `endReason set but endedAt null throws`() {
        completedSession(endedAt = null, endReason = SessionEndReason.USER_STOPPED)
    }

    // ── Time ordering ─────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `endedAt before startedAt throws`() {
        completedSession(startedAt = t1, endedAt = t0)
    }

    // ── Percent range ─────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `startPercent below 0 throws`() { completedSession(startPercent = -1) }

    @Test(expected = IllegalArgumentException::class)
    fun `startPercent above 100 throws`() { completedSession(startPercent = 101) }

    @Test(expected = IllegalArgumentException::class)
    fun `endPercent below 0 throws`() { completedSession(endPercent = -1) }

    @Test(expected = IllegalArgumentException::class)
    fun `endPercent above 100 throws`() { completedSession(endPercent = 101) }

    private fun assertNoThrow(block: () -> Unit) {
        try { block() } catch (e: Exception) { fail("Unexpected exception: ${e.message}") }
    }
}
