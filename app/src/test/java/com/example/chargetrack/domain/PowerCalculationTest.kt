package com.example.chargetrack.domain

import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.util.PowerCalculation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerCalculationTest {

    // ── derivedPowerUw ────────────────────────────────────────────────────

    @Test
    fun `positive voltage and positive current returns positive power`() {
        // 4000 mV × 5000 µA / 1000 = 20_000 µW = 20 mW
        val result = PowerCalculation.derivedPowerUw(voltageMv = 4000, currentNowUa = 5000)
        assertEquals(20_000L, result)
    }

    @Test
    fun `zero current returns zero power, not null`() {
        val result = PowerCalculation.derivedPowerUw(voltageMv = 4000, currentNowUa = 0)
        assertNotNull("Zero current must produce 0 µW, not null", result)
        assertEquals(0L, result)
    }

    @Test
    fun `negative current returns negative power`() {
        // 4000 mV × -2000 µA / 1000 = -8000 µW (net discharge while plugged in)
        val result = PowerCalculation.derivedPowerUw(voltageMv = 4000, currentNowUa = -2000)
        assertNotNull(result)
        assertEquals(-8_000L, result)
        assertTrue("Negative current must produce negative power", result!! < 0)
    }

    @Test
    fun `null voltage returns null`() {
        assertNull(PowerCalculation.derivedPowerUw(voltageMv = null, currentNowUa = 5000))
    }

    @Test
    fun `null current returns null`() {
        assertNull(PowerCalculation.derivedPowerUw(voltageMv = 4000, currentNowUa = null))
    }

    @Test
    fun `both inputs null returns null`() {
        assertNull(PowerCalculation.derivedPowerUw(voltageMv = null, currentNowUa = null))
    }

    @Test
    fun `high current scales correctly without overflow`() {
        // 4350 mV × 20_000_000 µA (20 A) / 1000 = 87_000_000 µW = 87 W
        val result = PowerCalculation.derivedPowerUw(voltageMv = 4350, currentNowUa = 20_000_000)
        assertEquals(87_000_000L, result)
    }

    @Test
    fun `minimum non-zero current returns non-zero power`() {
        // 4000 mV × 1 µA / 1000 = 4 µW (integer division)
        val result = PowerCalculation.derivedPowerUw(voltageMv = 4000, currentNowUa = 1)
        assertEquals(4L, result)
    }

    // ── qualityFlagsForPower ──────────────────────────────────────────────

    @Test
    fun `positive voltage and current produces no flags`() {
        val flags = PowerCalculation.qualityFlagsForPower(voltageMv = 4000, currentNowUa = 5000)
        assertTrue("No quality flags expected for valid positive inputs", flags.isEmpty())
    }

    @Test
    fun `zero current produces no flags`() {
        val flags = PowerCalculation.qualityFlagsForPower(voltageMv = 4000, currentNowUa = 0)
        assertTrue("Zero current is a valid state and should produce no flags", flags.isEmpty())
    }

    @Test
    fun `null voltage adds MISSING_REQUIRED_VALUE`() {
        val flags = PowerCalculation.qualityFlagsForPower(voltageMv = null, currentNowUa = 5000)
        assertTrue(QualityFlag.MISSING_REQUIRED_VALUE in flags)
        assertFalse(QualityFlag.OUTLIER in flags)
    }

    @Test
    fun `null current adds MISSING_REQUIRED_VALUE`() {
        val flags = PowerCalculation.qualityFlagsForPower(voltageMv = 4000, currentNowUa = null)
        assertTrue(QualityFlag.MISSING_REQUIRED_VALUE in flags)
        assertFalse(QualityFlag.OUTLIER in flags)
    }

    @Test
    fun `both inputs null adds MISSING_REQUIRED_VALUE`() {
        val flags = PowerCalculation.qualityFlagsForPower(voltageMv = null, currentNowUa = null)
        assertTrue(QualityFlag.MISSING_REQUIRED_VALUE in flags)
    }

    @Test
    fun `negative current adds OUTLIER flag`() {
        val flags = PowerCalculation.qualityFlagsForPower(voltageMv = 4000, currentNowUa = -1000)
        assertTrue(QualityFlag.OUTLIER in flags)
        assertFalse("MISSING_REQUIRED_VALUE should not be set when inputs are available",
            QualityFlag.MISSING_REQUIRED_VALUE in flags)
    }

    @Test
    fun `null voltage with negative current adds both MISSING and OUTLIER`() {
        // Voltage unavailable but current is known-negative: both flags apply.
        val flags = PowerCalculation.qualityFlagsForPower(voltageMv = null, currentNowUa = -500)
        assertTrue(QualityFlag.MISSING_REQUIRED_VALUE in flags)
        assertTrue(QualityFlag.OUTLIER in flags)
    }
}
