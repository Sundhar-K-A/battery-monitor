package com.example.chargetrack.ui.charts.transform

import com.example.chargetrack.ui.charts.model.ChartDataPoint
import com.example.chargetrack.ui.charts.model.TooltipData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorPreservingLttbTest {

    private fun createPoint(x: Float, y: Float, isAnchor: Boolean = false): ChartDataPoint {
        return ChartDataPoint(
            x = x,
            y = y,
            tooltip = TooltipData(elapsedMs = (x * 1000).toLong()),
            isAnchor = isAnchor,
        )
    }

    @Test
    fun `01 - N equal or below 500 samples are not downsampled`() {
        val points = (0 until 500).map { i -> createPoint(i.toFloat(), (i % 50).toFloat()) }
        val result = AnchorPreservingLttb.downsample(points)

        assertEquals(500, result.size)
        // Ensure exact same items
        for (i in points.indices) {
            assertEquals(points[i].x, result[i].x, 0.001f)
            assertEquals(points[i].y, result[i].y, 0.001f)
        }
    }

    @Test
    fun `02 - N greater than 500 samples are downsampled to target size`() {
        val points = (0 until 1000).map { i -> createPoint(i.toFloat(), (i % 80).toFloat()) }
        val result = AnchorPreservingLttb.downsample(points, targetCount = 300)

        assertTrue("Result size should be <= 350, actual: ${result.size}", result.size <= 350)
        assertTrue("Result size should be significantly downsampled from 1000", result.size < 1000)
    }

    @Test
    fun `03 - first and last points are strictly preserved`() {
        val points = (0 until 800).map { i -> createPoint(i.toFloat(), i * 0.1f) }
        val result = AnchorPreservingLttb.downsample(points, targetCount = 200)

        assertEquals(0f, result.first().x, 0.001f)
        assertEquals(799f, result.last().x, 0.001f)
    }

    @Test
    fun `04 - mandatory peak power anchor is strictly preserved`() {
        val points = (0 until 800).map { i ->
            val isPeak = (i == 342)
            val y = if (isPeak) 95.0f else (i % 40).toFloat()
            createPoint(i.toFloat(), y, isAnchor = isPeak)
        }

        val result = AnchorPreservingLttb.downsample(points, targetCount = 200)
        val peakFound = result.any { it.x == 342f && it.y == 95.0f && it.isAnchor }

        assertTrue("Mandatory peak anchor at index 342 must be preserved after downsampling", peakFound)
    }

    @Test
    fun `05 - mandatory taper start and benchmark boundaries are strictly preserved`() {
        val points = (0 until 900).map { i ->
            val isBenchmarkStart = (i == 120)
            val isTaper = (i == 560)
            val isBenchmarkEnd = (i == 780)
            val isAnchor = isBenchmarkStart || isTaper || isBenchmarkEnd
            createPoint(i.toFloat(), (i % 50).toFloat(), isAnchor = isAnchor)
        }

        val result = AnchorPreservingLttb.downsample(points, targetCount = 250)

        assertTrue("Benchmark start at 120 must be preserved", result.any { it.x == 120f && it.isAnchor })
        assertTrue("Taper start at 560 must be preserved", result.any { it.x == 560f && it.isAnchor })
        assertTrue("Benchmark end at 780 must be preserved", result.any { it.x == 780f && it.isAnchor })
    }

    @Test
    fun `06 - downsampling is transient in memory and does not mutate input list`() {
        val points = (0 until 600).map { i -> createPoint(i.toFloat(), i * 0.5f) }
        val originalSize = points.size

        val result = AnchorPreservingLttb.downsample(points)

        assertEquals(600, originalSize)
        assertEquals(600, points.size)
        assertTrue(result.size < originalSize)
    }
}
