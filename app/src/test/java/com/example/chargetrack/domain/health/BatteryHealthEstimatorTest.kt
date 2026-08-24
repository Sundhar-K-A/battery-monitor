package com.example.chargetrack.domain.health

import android.os.BatteryManager
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.model.BatterySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BatteryHealthEstimatorTest {

    private val now = Instant.now()
    private val iqoo15TypicalCapacityMah = 7000

    @Test
    fun `01 - STATUS_FULL at 80 percent does not qualify as full capacity`() {
        val samples = listOf(
            BatterySample(
                id = "1",
                sessionId = "s1",
                timestamp = now,
                elapsedMs = 0L,
                percent = 80,
                batteryStatus = BatteryManager.BATTERY_STATUS_FULL,
                chargeCounterUah = 5_600_000, // 5600 mAh at 80% (charge limit protection)
            )
        )

        val observation = BatteryHealthEstimator.extractSessionObservation(
            sessionId = "s1",
            sessionTimestamp = now,
            samples = samples,
            chargingMode = ChargingMode.NORMAL,
            referenceCapacityMah = iqoo15TypicalCapacityMah,
        )

        assertNull("STATUS_FULL at 80% must NOT qualify as a full-capacity observation", observation)
    }

    @Test
    fun `02 - 100 percent without valid chargeCounter does not qualify`() {
        val samples = listOf(
            BatterySample(id = "1", sessionId = "s1", timestamp = now, elapsedMs = 0L, percent = 100, chargeCounterUah = null),
            BatterySample(id = "2", sessionId = "s1", timestamp = now, elapsedMs = 5000L, percent = 100, chargeCounterUah = 0),
            BatterySample(id = "3", sessionId = "s1", timestamp = now, elapsedMs = 10000L, percent = 100, chargeCounterUah = -100),
        )

        val observation = BatteryHealthEstimator.extractSessionObservation(
            sessionId = "s1",
            sessionTimestamp = now,
            samples = samples,
            chargingMode = ChargingMode.NORMAL,
            referenceCapacityMah = iqoo15TypicalCapacityMah,
        )

        assertNull("100% without positive chargeCounterUah must return null", observation)
    }

    @Test
    fun `03 - multiple 100 percent samples produce exactly one session observation using window median`() {
        val samples = listOf(
            // Pre-100% samples
            BatterySample(id = "1", sessionId = "s1", timestamp = now, elapsedMs = 0L, percent = 98, chargeCounterUah = 6_500_000),
            BatterySample(id = "2", sessionId = "s1", timestamp = now, elapsedMs = 5000L, percent = 99, chargeCounterUah = 6_600_000),
            // 100% window samples: 6700, 6720, 6710 mAh
            BatterySample(id = "3", sessionId = "s1", timestamp = now, elapsedMs = 10000L, percent = 100, chargeCounterUah = 6_700_000),
            BatterySample(id = "4", sessionId = "s1", timestamp = now, elapsedMs = 15000L, percent = 100, chargeCounterUah = 6_720_000),
            BatterySample(id = "5", sessionId = "s1", timestamp = now, elapsedMs = 20000L, percent = 100, chargeCounterUah = 6_710_000),
        )

        val observation = BatteryHealthEstimator.extractSessionObservation(
            sessionId = "s1",
            sessionTimestamp = now,
            samples = samples,
            chargingMode = ChargingMode.FLASH_CHARGE,
            referenceCapacityMah = iqoo15TypicalCapacityMah,
        )

        assertNotNull(observation)
        assertEquals("s1", observation?.sessionId)
        assertEquals("Median of [6700, 6710, 6720] is 6710 mAh", 6710, observation?.capacityMah)
        assertEquals(6_710_000L, observation?.rawMedianUah)
        assertEquals(3, observation?.sampleCountAtFull)
    }

    @Test
    fun `04 - bypass charging sessions are rejected`() {
        val samples = listOf(
            BatterySample(id = "1", sessionId = "s1", timestamp = now, elapsedMs = 0L, percent = 100, chargeCounterUah = 6_700_000)
        )

        val observation = BatteryHealthEstimator.extractSessionObservation(
            sessionId = "s1",
            sessionTimestamp = now,
            samples = samples,
            chargingMode = ChargingMode.BYPASS,
            referenceCapacityMah = iqoo15TypicalCapacityMah,
        )

        assertNull("Bypass charging must never qualify for full capacity estimation", observation)
    }

    @Test
    fun `05 - plausibility ratio constants reject extreme outliers`() {
        // Below MIN_CAPACITY_RATIO (0.40 * 7000 = 2800 mAh)
        val lowSamples = listOf(
            BatterySample(id = "1", sessionId = "s1", timestamp = now, elapsedMs = 0L, percent = 100, chargeCounterUah = 2_500_000) // 2500 mAh
        )
        val lowObservation = BatteryHealthEstimator.extractSessionObservation(
            sessionId = "s1",
            sessionTimestamp = now,
            samples = lowSamples,
            chargingMode = ChargingMode.NORMAL,
            referenceCapacityMah = iqoo15TypicalCapacityMah,
        )
        assertNull("Capacity below 40% of reference must be rejected as an outlier", lowObservation)

        // Above MAX_CAPACITY_RATIO (1.30 * 7000 = 9100 mAh)
        val highSamples = listOf(
            BatterySample(id = "1", sessionId = "s2", timestamp = now, elapsedMs = 0L, percent = 100, chargeCounterUah = 10_000_000) // 10,000 mAh
        )
        val highObservation = BatteryHealthEstimator.extractSessionObservation(
            sessionId = "s2",
            sessionTimestamp = now,
            samples = highSamples,
            chargingMode = ChargingMode.NORMAL,
            referenceCapacityMah = iqoo15TypicalCapacityMah,
        )
        assertNull("Capacity above 130% of reference must be rejected as an outlier", highObservation)
    }

    @Test
    fun `06 - single or two observations produce InsufficientData`() {
        val obs1 = FullChargeCapacityObservation("s1", now, 6720, 6_720_000L, 5)
        val obs2 = FullChargeCapacityObservation("s2", now, 6700, 6_700_000L, 5)

        // 1 observation
        val result1 = BatteryHealthEstimator.calculateHealth(listOf(obs1), iqoo15TypicalCapacityMah)
        assertTrue(result1 is BatteryHealthEstimate.InsufficientData)
        assertEquals(1, (result1 as BatteryHealthEstimate.InsufficientData).observationCount)
        assertEquals(3, result1.requiredCount)

        // 2 observations
        val result2 = BatteryHealthEstimator.calculateHealth(listOf(obs1, obs2), iqoo15TypicalCapacityMah)
        assertTrue(result2 is BatteryHealthEstimate.InsufficientData)
        assertEquals(2, (result2 as BatteryHealthEstimate.InsufficientData).observationCount)
    }

    @Test
    fun `07 - three or more observations calculate health using median`() {
        val observations = listOf(
            FullChargeCapacityObservation("s1", now, 6720, 6_720_000L, 5),
            FullChargeCapacityObservation("s2", now, 6680, 6_680_000L, 5),
            FullChargeCapacityObservation("s3", now, 6710, 6_710_000L, 5),
            FullChargeCapacityObservation("s4", now, 6650, 6_650_000L, 5),
        )
        // sorted capacities: [6650, 6680, 6710, 6720] -> median = (6680 + 6710) / 2 = 6695 mAh
        // 6695 / 7000 = 95.6428% -> displayed 96%

        val result = BatteryHealthEstimator.calculateHealth(observations, iqoo15TypicalCapacityMah)

        assertTrue(result is BatteryHealthEstimate.Calculated)
        val calculated = result as BatteryHealthEstimate.Calculated

        assertEquals(6695, calculated.medianCapacityMah)
        assertEquals(7000, calculated.referenceCapacityMah)
        assertEquals(96, calculated.displayedHealthPercentage)
        assertEquals(95.64, calculated.rawHealthPercentage, 0.01)
        assertEquals(4, calculated.observationCount)
    }

    @Test
    fun `08 - raw capacity is preserved even when calculated health exceeds 100 percent`() {
        val observations = listOf(
            FullChargeCapacityObservation("s1", now, 7100, 7_100_000L, 5),
            FullChargeCapacityObservation("s2", now, 7150, 7_150_000L, 5),
            FullChargeCapacityObservation("s3", now, 7120, 7_120_000L, 5),
        )
        // median = 7120 mAh; 7120 / 7000 = 101.71%

        val result = BatteryHealthEstimator.calculateHealth(observations, iqoo15TypicalCapacityMah)

        assertTrue(result is BatteryHealthEstimate.Calculated)
        val calculated = result as BatteryHealthEstimate.Calculated

        assertEquals("Raw median capacity must be preserved unclipped", 7120, calculated.medianCapacityMah)
        assertEquals(101.71, calculated.rawHealthPercentage, 0.01)
        assertEquals("Displayed estimated health must be capped at 100%", 100, calculated.displayedHealthPercentage)
    }

    @Test
    fun `09 - null or invalid reference capacity produces Unavailable`() {
        val observations = listOf(
            FullChargeCapacityObservation("s1", now, 6700, 6_700_000L, 5),
            FullChargeCapacityObservation("s2", now, 6700, 6_700_000L, 5),
            FullChargeCapacityObservation("s3", now, 6700, 6_700_000L, 5),
        )

        assertEquals(BatteryHealthEstimate.Unavailable, BatteryHealthEstimator.calculateHealth(observations, null))
        assertEquals(BatteryHealthEstimate.Unavailable, BatteryHealthEstimator.calculateHealth(observations, 0))
    }
}
