package com.example.chargetrack.domain

import com.example.chargetrack.domain.enums.TestValidity
import com.example.chargetrack.domain.model.StandardTest
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class StandardTestValidationTest {

    private fun test(
        targetStart: Int = 20,
        targetEnd: Int = 80,
        isBaseline: Boolean = false,
        baselineSetAt: Instant? = null,
        validity: TestValidity = TestValidity.VALID,
        invalidationReason: String? = null,
    ) = StandardTest(
        sessionId = "session-1",
        targetStartPercent = targetStart,
        targetEndPercent = targetEnd,
        isBaseline = isBaseline,
        baselineSetAt = baselineSetAt,
        validity = validity,
        invalidationReason = invalidationReason
    )

    @Test
    fun `default 20 to 80 test is valid`() {
        assertNoThrow { test() }
    }

    @Test
    fun `full range 0 to 100 is valid`() {
        assertNoThrow { test(targetStart = 0, targetEnd = 100) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `targetStartPercent below 0 throws`() { test(targetStart = -1) }

    @Test(expected = IllegalArgumentException::class)
    fun `targetEndPercent above 100 throws`() { test(targetEnd = 101) }

    @Test(expected = IllegalArgumentException::class)
    fun `targetEnd equal to targetStart throws`() { test(targetStart = 50, targetEnd = 50) }

    @Test(expected = IllegalArgumentException::class)
    fun `targetEnd less than targetStart throws`() { test(targetStart = 80, targetEnd = 20) }

    @Test(expected = IllegalArgumentException::class)
    fun `isBaseline true without baselineSetAt throws`() {
        test(isBaseline = true, baselineSetAt = null)
    }

    @Test
    fun `isBaseline true with baselineSetAt is valid`() {
        assertNoThrow { test(isBaseline = true, baselineSetAt = Instant.now()) }
    }

    @Test
    fun `isBaseline false without baselineSetAt is valid`() {
        assertNoThrow { test(isBaseline = false, baselineSetAt = null) }
    }

    @Test
    fun `QUESTIONABLE validity with reason is valid`() {
        assertNoThrow {
            test(validity = TestValidity.QUESTIONABLE, invalidationReason = "Temperature elevated")
        }
    }

    @Test
    fun `INVALID validity with reason is valid`() {
        assertNoThrow {
            test(validity = TestValidity.INVALID, invalidationReason = "Charger disconnected mid-run")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `VALID validity with invalidationReason throws`() {
        test(validity = TestValidity.VALID, invalidationReason = "Should not be here")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank comparisonGroupKey throws`() {
        StandardTest(sessionId = "session-1", comparisonGroupKey = "  ")
    }

    @Test
    fun `QUESTIONABLE without reason is allowed (reason is optional)`() {
        assertNoThrow { test(validity = TestValidity.QUESTIONABLE, invalidationReason = null) }
    }

    private fun assertNoThrow(block: () -> Unit) {
        try { block() } catch (e: Exception) { fail("Unexpected exception: ${e.message}") }
    }
}
