package com.example.chargetrack.domain.sampling

import android.os.BatteryManager
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.QualityFlag
import com.example.chargetrack.domain.model.BatterySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SampleQualityEvaluatorTest {

    private fun createSnapshot(
        percent: Int? = 20,
        voltageMv: Int? = 4050,
        currentNowUa: Int? = 15_000_000,
        temperatureDeciC: Int? = 300,
        batteryStatus: Int? = BatteryManager.BATTERY_STATUS_CHARGING,
        pluggedType: Int? = BatteryManager.BATTERY_PLUGGED_AC,
    ): BatterySnapshot = BatterySnapshot(
        timestamp = Instant.now(),
        percent = percent,
        voltageMv = voltageMv,
        currentNowUa = currentNowUa,
        currentAverageUa = null,
        chargeCounterUah = null,
        energyCounterNwh = null,
        temperatureDeciC = temperatureDeciC,
        batteryStatus = batteryStatus,
        pluggedType = pluggedType,
        cycleCount = null,
        qualityFlags = emptySet(),
    )

    private fun createPreviousSample(percent: Int = 20): BatterySample = BatterySample(
        id = "sample-prev",
        sessionId = "session-1",
        timestamp = Instant.now(),
        elapsedMs = 0L,
        percent = percent,
        voltageMv = 4050,
        currentNowUa = 15_000_000,
    )

    @Test
    fun `nominal sample has no quality flags`() {
        val snapshot = createSnapshot(percent = 20, voltageMv = 4050, currentNowUa = 15_000_000)
        val flags = SampleQualityEvaluator.evaluate(
            currentSnapshot = snapshot,
            previousSample = createPreviousSample(20),
            elapsedIntervalMs = 5000L,
            expectedIntervalMs = 5000L,
        )
        assertTrue(flags.isEmpty())
    }

    @Test
    fun `gap detected when interval exceeds 1_5x expected interval`() {
        val snapshot = createSnapshot()
        // 8000ms > 7500ms (1.5 * 5000ms)
        val flags = SampleQualityEvaluator.evaluate(
            currentSnapshot = snapshot,
            previousSample = createPreviousSample(20),
            elapsedIntervalMs = 8000L,
            expectedIntervalMs = 5000L,
        )
        assertTrue(flags.contains(QualityFlag.GAP_DETECTED))
    }

    @Test
    fun `gap not detected when interval is within 1_5x expected interval`() {
        val snapshot = createSnapshot()
        // 6000ms <= 7500ms
        val flags = SampleQualityEvaluator.evaluate(
            currentSnapshot = snapshot,
            previousSample = createPreviousSample(20),
            elapsedIntervalMs = 6000L,
            expectedIntervalMs = 5000L,
        )
        assertFalse(flags.contains(QualityFlag.GAP_DETECTED))
    }

    @Test
    fun `missing required value flagged when voltage or current is null`() {
        val noVoltage = createSnapshot(voltageMv = null, currentNowUa = 15_000_000)
        val flags1 = SampleQualityEvaluator.evaluate(
            currentSnapshot = noVoltage,
            previousSample = null,
            elapsedIntervalMs = null,
        )
        assertTrue(flags1.contains(QualityFlag.MISSING_REQUIRED_VALUE))

        val noCurrent = createSnapshot(voltageMv = 4050, currentNowUa = null)
        val flags2 = SampleQualityEvaluator.evaluate(
            currentSnapshot = noCurrent,
            previousSample = null,
            elapsedIntervalMs = null,
        )
        assertTrue(flags2.contains(QualityFlag.MISSING_REQUIRED_VALUE))
    }

    @Test
    fun `missing percent alone does not set missing required value`() {
        val noPercent = createSnapshot(percent = null, voltageMv = 4050, currentNowUa = 15_000_000)
        val flags = SampleQualityEvaluator.evaluate(
            currentSnapshot = noPercent,
            previousSample = null,
            elapsedIntervalMs = null,
        )
        assertFalse(flags.contains(QualityFlag.MISSING_REQUIRED_VALUE))
    }

    @Test
    fun `percentage jitter flagged when percentage decreases`() {
        val current = createSnapshot(percent = 21) // 21% after 22%
        val flags = SampleQualityEvaluator.evaluate(
            currentSnapshot = current,
            previousSample = createPreviousSample(percent = 22),
            elapsedIntervalMs = 5000L,
        )
        assertTrue(flags.contains(QualityFlag.PERCENTAGE_JITTER))
    }

    @Test
    fun `outlier flagged when values exceed physical bounds`() {
        // High voltage outlier (> 5000 mV)
        val highVoltage = createSnapshot(voltageMv = 5500)
        val flags1 = SampleQualityEvaluator.evaluate(highVoltage, null, null)
        assertTrue(flags1.contains(QualityFlag.OUTLIER))

        // Extreme temperature outlier (> 80°C = 800 deci-C)
        val highTemp = createSnapshot(temperatureDeciC = 850)
        val flags2 = SampleQualityEvaluator.evaluate(highTemp, null, null)
        assertTrue(flags2.contains(QualityFlag.OUTLIER))

        // Extreme current outlier (> 30A)
        val highCurrent = createSnapshot(currentNowUa = 35_000_000)
        val flags3 = SampleQualityEvaluator.evaluate(highCurrent, null, null)
        assertTrue(flags3.contains(QualityFlag.OUTLIER))
    }

    @Test
    fun `configurable outlier thresholds are respected`() {
        val customThresholds = OutlierThresholds(
            minVoltageMv = 3000,
            maxVoltageMv = 4500,
        )
        val snapshot = createSnapshot(voltageMv = 4600) // Within standard (5000), but exceeds custom (4500)
        val flags = SampleQualityEvaluator.evaluate(
            currentSnapshot = snapshot,
            previousSample = null,
            elapsedIntervalMs = null,
            thresholds = customThresholds,
        )
        assertTrue(flags.contains(QualityFlag.OUTLIER))
    }
}
