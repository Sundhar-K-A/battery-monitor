package com.example.chargetrack.domain.battery

import com.example.chargetrack.domain.enums.QualityFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BatterySnapshotConverterTest {

    // ─── intPropertyToNullable ────────────────────────────────────────────

    @Test
    fun `INT_MIN returns null (unavailable)`() {
        assertNull(BatterySnapshotConverter.intPropertyToNullable(Int.MIN_VALUE))
    }

    @Test
    fun `zero returns zero (genuine zero current)`() {
        assertEquals(0, BatterySnapshotConverter.intPropertyToNullable(0))
    }

    @Test
    fun `positive value is returned as-is`() {
        assertEquals(5000, BatterySnapshotConverter.intPropertyToNullable(5000))
    }

    @Test
    fun `negative value is returned as-is (net discharge current)`() {
        assertEquals(-2000, BatterySnapshotConverter.intPropertyToNullable(-2000))
    }

    // ─── longPropertyToNullable ───────────────────────────────────────────

    @Test
    fun `LONG_MIN returns null`() {
        assertNull(BatterySnapshotConverter.longPropertyToNullable(Long.MIN_VALUE))
    }

    @Test
    fun `long zero returns zero`() {
        assertEquals(0L, BatterySnapshotConverter.longPropertyToNullable(0L))
    }

    @Test
    fun `large positive long is returned as-is`() {
        assertEquals(123_456_789L, BatterySnapshotConverter.longPropertyToNullable(123_456_789L))
    }

    // ─── percentFromLevelScale ────────────────────────────────────────────

    @Test
    fun `50 of 100 scale gives 50 percent`() {
        assertEquals(50, BatterySnapshotConverter.percentFromLevelScale(50, 100))
    }

    @Test
    fun `full charge gives 100`() {
        assertEquals(100, BatterySnapshotConverter.percentFromLevelScale(100, 100))
    }

    @Test
    fun `empty gives 0`() {
        assertEquals(0, BatterySnapshotConverter.percentFromLevelScale(0, 100))
    }

    @Test
    fun `negative level returns null`() {
        assertNull(BatterySnapshotConverter.percentFromLevelScale(-1, 100))
    }

    @Test
    fun `zero scale returns null`() {
        assertNull(BatterySnapshotConverter.percentFromLevelScale(50, 0))
    }

    @Test
    fun `negative scale returns null`() {
        assertNull(BatterySnapshotConverter.percentFromLevelScale(50, -1))
    }

    @Test
    fun `non-100 scale is normalised correctly`() {
        // Some devices report scale = 200
        assertEquals(50, BatterySnapshotConverter.percentFromLevelScale(100, 200))
    }

    // ─── voltageToNullable ────────────────────────────────────────────────

    @Test
    fun `positive voltage is returned as-is`() {
        assertEquals(4200, BatterySnapshotConverter.voltageToNullable(4200))
    }

    @Test
    fun `zero voltage returns null (physically impossible)`() {
        assertNull(BatterySnapshotConverter.voltageToNullable(0))
    }

    @Test
    fun `negative voltage sentinel returns null`() {
        assertNull(BatterySnapshotConverter.voltageToNullable(-1))
    }

    // ─── temperatureToNullable ────────────────────────────────────────────

    @Test
    fun `temperature 295 (29_5 C) is returned`() {
        assertEquals(295, BatterySnapshotConverter.temperatureToNullable(295))
    }

    @Test
    fun `temperature 0 (0 C) is valid and returned`() {
        assertEquals(0, BatterySnapshotConverter.temperatureToNullable(0))
    }

    @Test
    fun `negative temperature sentinel minus1 returns null`() {
        assertNull(BatterySnapshotConverter.temperatureToNullable(-1))
    }

    // ─── intentConstantToNullable ─────────────────────────────────────────

    @Test
    fun `status constant 2 (CHARGING) is returned`() {
        assertEquals(2, BatterySnapshotConverter.intentConstantToNullable(2))
    }

    @Test
    fun `constant 0 is returned as 0 (e_g_ pluggedType not plugged)`() {
        assertEquals(0, BatterySnapshotConverter.intentConstantToNullable(0))
    }

    @Test
    fun `absent extra sentinel minus1 returns null`() {
        assertNull(BatterySnapshotConverter.intentConstantToNullable(-1))
    }

    // ─── build() — full snapshot assembly ────────────────────────────────

    private fun buildSnapshot(
        levelRaw: Int = 50,
        scaleRaw: Int = 100,
        voltageRaw: Int = 4200,
        temperatureRaw: Int = 280,
        statusRaw: Int = 2,
        pluggedRaw: Int = 1,
        healthRaw: Int = 2,
        currentNowRaw: Int = 15_000,
        currentAvgRaw: Int = 14_500,
        chargeCounterRaw: Int = 3_000_000,
        energyCounterRaw: Long = 10_000_000L,
        cycleCountRaw: Int = 42,
    ) = BatterySnapshotConverter.build(
        timestamp        = Instant.parse("2026-01-01T08:00:00Z"),
        levelRaw         = levelRaw,
        scaleRaw         = scaleRaw,
        voltageRaw       = voltageRaw,
        temperatureRaw   = temperatureRaw,
        statusRaw        = statusRaw,
        pluggedRaw       = pluggedRaw,
        healthRaw        = healthRaw,
        currentNowRaw    = currentNowRaw,
        currentAvgRaw    = currentAvgRaw,
        chargeCounterRaw = chargeCounterRaw,
        energyCounterRaw = energyCounterRaw,
        cycleCountRaw    = cycleCountRaw,
    )

    @Test
    fun `full valid snapshot has all fields populated`() {
        val s = buildSnapshot()
        assertEquals(50, s.percent)
        assertEquals(4200, s.voltageMv)
        assertEquals(280, s.temperatureDeciC)
        assertEquals(2, s.batteryStatus)
        assertEquals(1, s.pluggedType)
        assertEquals(2, s.health)
        assertEquals(15_000, s.currentNowUa)
        assertEquals(14_500, s.currentAverageUa)
        assertEquals(3_000_000, s.chargeCounterUah)
        assertEquals(10_000_000L, s.energyCounterNwh)
        assertEquals(42, s.cycleCount)
    }

    @Test
    fun `no quality flags for clean snapshot`() {
        assertTrue(buildSnapshot().qualityFlags.isEmpty())
    }

    @Test
    fun `unavailable percent adds MISSING_REQUIRED_VALUE flag`() {
        val s = buildSnapshot(levelRaw = -1)
        assertNull(s.percent)
        assertTrue(QualityFlag.MISSING_REQUIRED_VALUE in s.qualityFlags)
    }

    @Test
    fun `negative currentNow adds OUTLIER flag and preserves value`() {
        val s = buildSnapshot(currentNowRaw = -3000)
        assertEquals(-3000, s.currentNowUa)
        assertTrue(QualityFlag.OUTLIER in s.qualityFlags)
    }

    @Test
    fun `zero currentNow is preserved without OUTLIER flag`() {
        val s = buildSnapshot(currentNowRaw = 0)
        assertEquals(0, s.currentNowUa)
        assertFalse(QualityFlag.OUTLIER in s.qualityFlags)
    }

    @Test
    fun `INT_MIN currentNow produces null without OUTLIER flag`() {
        val s = buildSnapshot(currentNowRaw = Int.MIN_VALUE)
        assertNull(s.currentNowUa)
        assertFalse("Null current should not set OUTLIER", QualityFlag.OUTLIER in s.qualityFlags)
    }

    @Test
    fun `unavailable cycleCount returns null`() {
        assertNull(buildSnapshot(cycleCountRaw = -1).cycleCount)
    }

    @Test
    fun `unavailable energyCounter returns null`() {
        assertNull(buildSnapshot(energyCounterRaw = Long.MIN_VALUE).energyCounterNwh)
    }

    @Test
    fun `all BatteryManager properties unavailable produces snapshot with null fields`() {
        val s = buildSnapshot(
            currentNowRaw    = Int.MIN_VALUE,
            currentAvgRaw    = Int.MIN_VALUE,
            chargeCounterRaw = Int.MIN_VALUE,
            energyCounterRaw = Long.MIN_VALUE,
            cycleCountRaw    = Int.MIN_VALUE,
        )
        assertNull(s.currentNowUa)
        assertNull(s.currentAverageUa)
        assertNull(s.chargeCounterUah)
        assertNull(s.energyCounterNwh)
        assertNull(s.cycleCount)
    }

    @Test
    fun `absent intent extras produce null snapshot fields`() {
        val s = buildSnapshot(
            voltageRaw     = -1,
            temperatureRaw = -1,
            statusRaw      = -1,
            pluggedRaw     = -1,
            healthRaw      = -1,
        )
        assertNull(s.voltageMv)
        assertNull(s.temperatureDeciC)
        assertNull(s.batteryStatus)
        assertNull(s.pluggedType)
        assertNull(s.health)
    }

    @Test
    fun `timestamp is preserved exactly`() {
        val ts = Instant.parse("2026-06-15T12:30:00Z")
        val s = BatterySnapshotConverter.build(
            timestamp = ts,
            levelRaw = 80, scaleRaw = 100, voltageRaw = 4200, temperatureRaw = 300,
            statusRaw = 2, pluggedRaw = 1, healthRaw = 2,
            currentNowRaw = 10_000, currentAvgRaw = 9_500, chargeCounterRaw = 4_000_000,
            energyCounterRaw = 15_000_000L, cycleCountRaw = 10
        )
        assertEquals(ts, s.timestamp)
    }

    @Test
    fun `both unavailable percent and negative current accumulate flags`() {
        val s = buildSnapshot(levelRaw = -1, currentNowRaw = -5000)
        assertNotNull(s.currentNowUa)
        assertTrue(QualityFlag.MISSING_REQUIRED_VALUE in s.qualityFlags)
        assertTrue(QualityFlag.OUTLIER in s.qualityFlags)
    }
}
