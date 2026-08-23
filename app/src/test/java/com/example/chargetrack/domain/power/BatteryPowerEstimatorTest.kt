package com.example.chargetrack.domain.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryPowerEstimatorTest {

    @Test
    fun `standard 15A FlashCharge calculation produces 60W`() {
        // 4000 mV x 15,000,000 uA = 60,000,000 uW = 60.0 W
        val powerUw = BatteryPowerEstimator.calculatePowerUw(
            voltageMv = 4000,
            currentNowUa = 15_000_000,
        )
        assertNotNull(powerUw)
        assertEquals(60_000_000L, powerUw)
        assertEquals(60.0, BatteryPowerEstimator.toWatts(powerUw)!!, 0.001)
        assertEquals("60.00", BatteryPowerEstimator.formatWatts(powerUw))
        assertEquals("60.00 W", BatteryPowerEstimator.formatWattsWithUnit(powerUw))
    }

    @Test
    fun `peak 22_7A FlashCharge calculation produces 99_88W`() {
        // 4400 mV x 22,700,000 uA = 99,880,000 uW = 99.88 W
        val powerUw = BatteryPowerEstimator.calculatePowerUw(
            voltageMv = 4400,
            currentNowUa = 22_700_000,
        )
        assertNotNull(powerUw)
        assertEquals(99_880_000L, powerUw)
        assertEquals(99.88, BatteryPowerEstimator.toWatts(powerUw)!!, 0.001)
        assertEquals("99.88", BatteryPowerEstimator.formatWatts(powerUw))
        assertEquals("99.88 W", BatteryPowerEstimator.formatWattsWithUnit(powerUw))
    }

    @Test
    fun `pre-multiplication widening to Long prevents 32-bit integer overflow`() {
        val voltageMv = 4400
        val currentNowUa = 22_700_000

        // If calculated with 32-bit Int multiplication before widening:
        // (4400 * 22_700_000) overflows 32-bit signed Int to 1_095_752_192, yielding ~1.09 W:
        val incorrect32BitCalculation = (voltageMv * currentNowUa).toLong() / 1_000L
        assertEquals(1_095_752L, incorrect32BitCalculation)

        // BatteryPowerEstimator widens before multiplication, producing the correct 64-bit Long (99.88 W):
        val powerUw = BatteryPowerEstimator.calculatePowerUw(voltageMv, currentNowUa)
        assertNotNull(powerUw)
        assertEquals(99_880_000L, powerUw)
        assertTrue("Widened calculation must not equal overflowed 32-bit calculation", powerUw != incorrect32BitCalculation)
    }

    @Test
    fun `discharging negative current preserves negative sign for power`() {
        // 4000 mV x -500,000 uA = -2,000,000 uW = -2.0 W
        val dischargePowerUw = BatteryPowerEstimator.calculatePowerUw(
            voltageMv = 4000,
            currentNowUa = -500_000,
        )
        assertNotNull(dischargePowerUw)
        assertEquals(-2_000_000L, dischargePowerUw)
        assertEquals(-2.0, BatteryPowerEstimator.toWatts(dischargePowerUw)!!, 0.001)
        assertEquals("-2.00", BatteryPowerEstimator.formatWatts(dischargePowerUw))
        assertEquals("-2.00 W", BatteryPowerEstimator.formatWattsWithUnit(dischargePowerUw))

        // Heavy discharge: 3800 mV x -1,500,000 uA = -5,700,000 uW = -5.7 W
        val heavyDischargeUw = BatteryPowerEstimator.calculatePowerUw(
            voltageMv = 3800,
            currentNowUa = -1_500_000,
        )
        assertNotNull(heavyDischargeUw)
        assertEquals(-5_700_000L, heavyDischargeUw)
        assertEquals(-5.7, BatteryPowerEstimator.toWatts(heavyDischargeUw)!!, 0.001)
    }

    @Test
    fun `zero current produces zero power and does not coerce to null`() {
        val zeroPowerUw = BatteryPowerEstimator.calculatePowerUw(
            voltageMv = 4000,
            currentNowUa = 0,
        )
        assertNotNull(zeroPowerUw)
        assertEquals(0L, zeroPowerUw)
        assertEquals(0.0, BatteryPowerEstimator.toWatts(zeroPowerUw)!!, 0.001)
        assertEquals("0.00 W", BatteryPowerEstimator.formatWattsWithUnit(zeroPowerUw))
    }

    @Test
    fun `null voltage or null current returns null without zero coercion`() {
        assertNull(BatteryPowerEstimator.calculatePowerUw(voltageMv = null, currentNowUa = 15_000_000))
        assertNull(BatteryPowerEstimator.calculatePowerUw(voltageMv = 4000, currentNowUa = null))
        assertNull(BatteryPowerEstimator.calculatePowerUw(voltageMv = null, currentNowUa = null))
        assertNull(BatteryPowerEstimator.toWatts(null))
        assertNull(BatteryPowerEstimator.formatWatts(null))
        assertNull(BatteryPowerEstimator.formatWattsWithUnit(null))
    }

    @Test
    fun `maximum physical bound produces correct 150W power`() {
        // 5000 mV x 30,000,000 uA = 150,000,000 uW = 150.0 W
        val maxPowerUw = BatteryPowerEstimator.calculatePowerUw(
            voltageMv = 5000,
            currentNowUa = 30_000_000,
        )
        assertNotNull(maxPowerUw)
        assertEquals(150_000_000L, maxPowerUw)
        assertEquals(150.0, BatteryPowerEstimator.toWatts(maxPowerUw)!!, 0.001)
        assertEquals("150.00 W", BatteryPowerEstimator.formatWattsWithUnit(maxPowerUw))
    }

    @Test
    fun `official user-facing label matches exact specification`() {
        assertEquals("Estimated battery-side power", BatteryPowerEstimator.LABEL_BATTERY_SIDE_POWER)
    }
}
