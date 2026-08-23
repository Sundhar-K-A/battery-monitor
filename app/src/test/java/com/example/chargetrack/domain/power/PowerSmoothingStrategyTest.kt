package com.example.chargetrack.domain.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSmoothingStrategyTest {

    @Test
    fun `Raw strategy converts micro-watts to Watts directly`() {
        val input = listOf(60_000_000L, null, 99_880_000L, -2_000_000L)
        val result = PowerSmoothingStrategy.Raw.smooth(input)

        assertEquals(4, result.size)
        assertEquals(60.0, result[0]!!, 0.001)
        assertNull(result[1])
        assertEquals(99.88, result[2]!!, 0.001)
        assertEquals(-2.0, result[3]!!, 0.001)
    }

    @Test
    fun `MovingAverage computes trailing sliding window average`() {
        val strategy = PowerSmoothingStrategy.MovingAverage(windowSize = 3)
        val input = listOf(60_000_000L, 63_000_000L, 66_000_000L, 60_000_000L)
        val result = strategy.smooth(input)

        assertEquals(4, result.size)
        // t=0: [60.0] -> 60.0
        assertEquals(60.0, result[0]!!, 0.001)
        // t=1: [60.0, 63.0] -> 61.5
        assertEquals(61.5, result[1]!!, 0.001)
        // t=2: [60.0, 63.0, 66.0] -> 63.0
        assertEquals(63.0, result[2]!!, 0.001)
        // t=3: [63.0, 66.0, 60.0] -> 63.0
        assertEquals(63.0, result[3]!!, 0.001)
    }

    @Test
    fun `MovingAverage handles interspersed nulls gracefully`() {
        val strategy = PowerSmoothingStrategy.MovingAverage(windowSize = 3)
        val input = listOf(60_000_000L, null, 70_000_000L, 80_000_000L)
        val result = strategy.smooth(input)

        assertEquals(4, result.size)
        assertEquals(60.0, result[0]!!, 0.001)
        assertNull(result[1])
        // t=2: window includes [60.0, null, 70.0] -> (60.0 + 70.0) / 2 = 65.0
        assertEquals(65.0, result[2]!!, 0.001)
        // t=3: window includes [null, 70.0, 80.0] -> (70.0 + 80.0) / 2 = 75.0
        assertEquals(75.0, result[3]!!, 0.001)
    }

    @Test
    fun `ExponentialMovingAverage computes recursive exponential weighting`() {
        val strategy = PowerSmoothingStrategy.ExponentialMovingAverage(alpha = 0.5)
        val input = listOf(60_000_000L, 80_000_000L, 100_000_000L)
        val result = strategy.smooth(input)

        assertEquals(3, result.size)
        // t=0: 60.0
        assertEquals(60.0, result[0]!!, 0.001)
        // t=1: 0.5 * 80.0 + 0.5 * 60.0 = 70.0
        assertEquals(70.0, result[1]!!, 0.001)
        // t=2: 0.5 * 100.0 + 0.5 * 70.0 = 85.0
        assertEquals(85.0, result[2]!!, 0.001)
    }

    @Test
    fun `MedianFilter rejects transient power spikes`() {
        val strategy = PowerSmoothingStrategy.MedianFilter(windowSize = 3)
        // Spike of 100W between two 60W samples:
        val input = listOf(60_000_000L, 100_000_000L, 62_000_000L)
        val result = strategy.smooth(input)

        assertEquals(3, result.size)
        // t=0: median([60.0]) = 60.0
        assertEquals(60.0, result[0]!!, 0.001)
        // t=1: median([60.0, 100.0]) = 80.0
        assertEquals(80.0, result[1]!!, 0.001)
        // t=2: median([60.0, 62.0, 100.0]) = 62.0 (100.0 spike rejected)
        assertEquals(62.0, result[2]!!, 0.001)
    }

    @Test
    fun `empty and all-null lists handle safely across all strategies`() {
        val strategies = listOf(
            PowerSmoothingStrategy.Raw,
            PowerSmoothingStrategy.MovingAverage(3),
            PowerSmoothingStrategy.ExponentialMovingAverage(0.3),
            PowerSmoothingStrategy.MedianFilter(3),
        )

        for (strategy in strategies) {
            assertTrue(strategy.smooth(emptyList()).isEmpty())

            val allNulls = listOf<Long?>(null, null, null)
            val result = strategy.smooth(allNulls)
            assertEquals(3, result.size)
            assertTrue(result.all { it == null })
        }
    }
}
