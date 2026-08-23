package com.example.chargetrack.domain.power

/**
 * Pure analytical/presentation strategy for smoothing estimated battery-side power series (e.g. for charts).
 *
 * ## Important Invariants
 * - This smoothing is applied strictly for visualization/chart rendering and analytics.
 * - It **never** modifies the raw persisted [BatterySample.derivedPowerUw] in the database.
 * - It is decoupled from the hot 5-second sampling loop to ensure zero overhead on measurement ingestion.
 */
sealed interface PowerSmoothingStrategy {

    /**
     * Transforms a series of raw power measurements in microwatts (µW) into smoothed power values in Watts (W).
     *
     * @param powerValuesUw List of raw power values in microwatts (µW), where null indicates unavailable data.
     * @return List of smoothed power values in Watts (W), maintaining 1-to-1 index correspondence with the input.
     */
    fun smooth(powerValuesUw: List<Long?>): List<Double?>

    /**
     * Raw strategy: Returns un-smoothed power values in Watts.
     */
    data object Raw : PowerSmoothingStrategy {
        override fun smooth(powerValuesUw: List<Long?>): List<Double?> {
            return powerValuesUw.map { BatteryPowerEstimator.toWatts(it) }
        }
    }

    /**
     * Simple Moving Average (SMA) over a trailing sliding window.
     *
     * @property windowSize The number of consecutive samples to include in the average (must be >= 1).
     */
    data class MovingAverage(val windowSize: Int = 3) : PowerSmoothingStrategy {
        init {
            require(windowSize >= 1) { "windowSize must be >= 1, was $windowSize" }
        }

        override fun smooth(powerValuesUw: List<Long?>): List<Double?> {
            if (powerValuesUw.isEmpty()) return emptyList()
            if (windowSize == 1) return Raw.smooth(powerValuesUw)

            val wattsList = powerValuesUw.map { BatteryPowerEstimator.toWatts(it) }
            val result = ArrayList<Double?>(wattsList.size)

            for (i in wattsList.indices) {
                val current = wattsList[i]
                if (current == null) {
                    result.add(null)
                    continue
                }

                val windowStart = (i - windowSize + 1).coerceAtLeast(0)
                val window = wattsList.subList(windowStart, i + 1).filterNotNull()

                if (window.isEmpty()) {
                    result.add(null)
                } else {
                    result.add(window.sum() / window.size)
                }
            }

            return result
        }
    }

    /**
     * Exponential Moving Average (EMA) smoothing: `EMA_t = alpha * P_t + (1 - alpha) * EMA_{t-1}`.
     *
     * @property alpha Smoothing factor between 0.0 (exclusive) and 1.0 (inclusive).
     *                 Higher alpha discounts older observations faster.
     */
    data class ExponentialMovingAverage(val alpha: Double = 0.3) : PowerSmoothingStrategy {
        init {
            require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0.0, 1.0], was $alpha" }
        }

        override fun smooth(powerValuesUw: List<Long?>): List<Double?> {
            if (powerValuesUw.isEmpty()) return emptyList()

            val wattsList = powerValuesUw.map { BatteryPowerEstimator.toWatts(it) }
            val result = ArrayList<Double?>(wattsList.size)
            var currentEma: Double? = null

            for (watts in wattsList) {
                if (watts == null) {
                    result.add(null)
                    // Reset EMA chain across data gaps
                    currentEma = null
                } else {
                    val nextEma = if (currentEma == null) {
                        watts
                    } else {
                        (alpha * watts) + ((1.0 - alpha) * currentEma)
                    }
                    currentEma = nextEma
                    result.add(nextEma)
                }
            }

            return result
        }
    }

    /**
     * Median Filter over a sliding window: robust against transient spikes/outliers.
     *
     * @property windowSize The number of consecutive samples in the median window (must be >= 1).
     */
    data class MedianFilter(val windowSize: Int = 3) : PowerSmoothingStrategy {
        init {
            require(windowSize >= 1) { "windowSize must be >= 1, was $windowSize" }
        }

        override fun smooth(powerValuesUw: List<Long?>): List<Double?> {
            if (powerValuesUw.isEmpty()) return emptyList()
            if (windowSize == 1) return Raw.smooth(powerValuesUw)

            val wattsList = powerValuesUw.map { BatteryPowerEstimator.toWatts(it) }
            val result = ArrayList<Double?>(wattsList.size)

            for (i in wattsList.indices) {
                val current = wattsList[i]
                if (current == null) {
                    result.add(null)
                    continue
                }

                val windowStart = (i - windowSize + 1).coerceAtLeast(0)
                val window = wattsList.subList(windowStart, i + 1).filterNotNull().sorted()

                if (window.isEmpty()) {
                    result.add(null)
                } else {
                    val mid = window.size / 2
                    val median = if (window.size % 2 == 1) {
                        window[mid]
                    } else {
                        (window[mid - 1] + window[mid]) / 2.0
                    }
                    result.add(median)
                }
            }

            return result
        }
    }
}
