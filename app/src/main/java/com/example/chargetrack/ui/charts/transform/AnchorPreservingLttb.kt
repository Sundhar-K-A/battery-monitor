package com.example.chargetrack.ui.charts.transform

import com.example.chargetrack.ui.charts.model.ChartDataPoint
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * High-performance, anchor-preserving downsampler using the Largest-Triangle-Three-Buckets (LTTB) algorithm.
 *
 * ## Principles:
 * - **Threshold $N > 500$**: If sample count $N \le 500$, returns original points completely untouched.
 * - **Mandatory Anchor Preservation**: Points marked with `isAnchor = true` (first, last, analytical peak power,
 *   taper start, benchmark start/end, transition arrivals) are strictly preserved and never downsampled away.
 * - **Transient In-Memory Only**: Used exclusively for UI Canvas rendering. Never persisted to Room database.
 */
object AnchorPreservingLttb {

    const val DOWNSAMPLE_THRESHOLD = 500
    const val TARGET_TOTAL_POINTS = 300

    /**
     * Downsamples a list of [ChartDataPoint]s if count exceeds [DOWNSAMPLE_THRESHOLD],
     * strictly preserving all anchor points.
     */
    fun downsample(points: List<ChartDataPoint>, targetCount: Int = TARGET_TOTAL_POINTS): List<ChartDataPoint> {
        if (points.size <= DOWNSAMPLE_THRESHOLD || targetCount >= points.size) {
            return points
        }

        // 1. Identify all mandatory anchor indices
        val anchorIndices = mutableSetOf<Int>()
        anchorIndices.add(0)
        anchorIndices.add(points.size - 1)

        for (i in points.indices) {
            if (points[i].isAnchor) {
                anchorIndices.add(i)
            }
        }

        val sortedAnchors = anchorIndices.sorted()
        val result = mutableListOf<ChartDataPoint>()

        // 2. Apply LTTB piecewise between each pair of adjacent anchors
        for (i in 0 until sortedAnchors.size - 1) {
            val startIdx = sortedAnchors[i]
            val endIdx = sortedAnchors[i + 1]
            val subList = points.subList(startIdx, endIdx + 1)
            val subLen = subList.size

            if (subLen <= 3) {
                // Keep all points in small sub-intervals
                for (j in 0 until subLen - 1) {
                    result.add(subList[j])
                }
            } else {
                val subTarget = (targetCount.toDouble() * subLen / points.size).roundToInt().coerceIn(3, subLen)
                val downsampledSub = runStandardLttb(subList, subTarget)
                for (j in 0 until downsampledSub.size - 1) {
                    result.add(downsampledSub[j])
                }
            }
        }

        // Add the final point
        result.add(points.last())
        return result
    }

    /**
     * Standard Largest-Triangle-Three-Buckets (LTTB) algorithm on a contiguous slice of points.
     */
    private fun runStandardLttb(data: List<ChartDataPoint>, threshold: Int): List<ChartDataPoint> {
        val dataSize = data.size
        if (threshold >= dataSize || threshold <= 2) {
            return data
        }

        val sampled = ArrayList<ChartDataPoint>(threshold)
        sampled.add(data[0]) // Always add the first point

        val every = (dataSize - 2).toDouble() / (threshold - 2)
        var a = 0

        for (i in 0 until threshold - 2) {
            var avgX = 0.0
            var avgY = 0.0
            val avgRangeStart = ((i + 1) * every).toInt() + 1
            val avgRangeEnd = (((i + 2) * every).toInt() + 1).coerceAtMost(dataSize)
            val avgRangeLength = (avgRangeEnd - avgRangeStart).coerceAtLeast(1)

            for (j in avgRangeStart until avgRangeEnd) {
                avgX += data[j].x
                avgY += data[j].y
            }
            avgX /= avgRangeLength
            avgY /= avgRangeLength

            val rangeOffs = (i * every).toInt() + 1
            val rangeTo = (((i + 1) * every).toInt() + 1).coerceAtMost(dataSize)

            val pointAX = data[a].x.toDouble()
            val pointAY = data[a].y.toDouble()

            var maxArea = -1.0
            var nextA = rangeOffs

            for (j in rangeOffs until rangeTo) {
                val area = abs(
                    (pointAX - avgX) * (data[j].y - pointAY) -
                        (pointAX - data[j].x) * (avgY - pointAY)
                ) * 0.5

                if (area > maxArea) {
                    maxArea = area
                    nextA = j
                }
            }

            sampled.add(data[nextA])
            a = nextA
        }

        sampled.add(data[dataSize - 1]) // Always add the last point
        return sampled
    }
}
