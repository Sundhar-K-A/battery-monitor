package com.example.chargetrack.domain.power

import java.util.Locale

/**
 * Pure mathematical utility for deriving estimated battery-side electrical power.
 *
 * ## Domain Invariants
 * - Power is derived as: `P = V * I`
 * - Internal storage unit is microwatts (µW) in a 64-bit signed [Long].
 * - Android provides [voltageMv] (millivolts) and [currentNowUa] (microamperes) as 32-bit [Int]s.
 * - To prevent 32-bit integer overflow when computing high-wattage FlashCharge metrics
 *   (e.g., 4400 mV × 22,700,000 µA = 99,880,000,000 > Int.MAX_VALUE), both operands
 *   **must be converted to [Long] before multiplication**.
 * - Net discharge (negative current) produces negative power and is strictly preserved.
 * - UI labelling requirement: Always display as [LABEL_BATTERY_SIDE_POWER] ("Estimated battery-side power")
 *   and **never** as "charger power", "wall power", or "adapter wattage".
 */
object BatteryPowerEstimator {

    /**
     * The official, mandatory user-facing label for battery-side power metrics.
     */
    const val LABEL_BATTERY_SIDE_POWER = "Estimated battery-side power"

    /**
     * Derives estimated battery-side power in microwatts (µW).
     *
     * Rules:
     * - Returns `null` if either [voltageMv] or [currentNowUa] is null (unavailable).
     * - Returns `0L` if [currentNowUa] is 0 (genuine zero net current).
     * - Returns a negative value if [currentNowUa] is negative (discharging under load).
     * - Operands are converted to [Long] prior to multiplication to avoid 32-bit integer overflow.
     *
     * @param voltageMv Battery voltage in millivolts (mV).
     * @param currentNowUa Instantaneous battery current in microamperes (µA).
     * @return Estimated battery-side power in microwatts (µW), or null if either input is unavailable.
     */
    fun calculatePowerUw(voltageMv: Int?, currentNowUa: Int?): Long? {
        if (voltageMv == null || currentNowUa == null) return null
        return (voltageMv.toLong() * currentNowUa.toLong()) / 1_000L
    }

    /**
     * Converts power in microwatts (µW) to Watts (W).
     *
     * @param powerUw Power in microwatts (µW).
     * @return Power in Watts (W), or null if input is null.
     */
    fun toWatts(powerUw: Long?): Double? {
        return powerUw?.let { it.toDouble() / 1_000_000.0 }
    }

    /**
     * Formats power in Watts with the specified number of decimal places (using US locale).
     *
     * @param powerUw Power in microwatts (µW).
     * @param decimals Number of decimal digits (default 2).
     * @return Formatted string (e.g. "60.00" or "-2.50"), or null if powerUw is null.
     */
    fun formatWatts(powerUw: Long?, decimals: Int = 2): String? {
        val watts = toWatts(powerUw) ?: return null
        return String.format(Locale.US, "%.${decimals}f", watts)
    }

    /**
     * Formats power in Watts with a "W" unit suffix (e.g. "60.00 W" or "-2.00 W").
     *
     * @param powerUw Power in microwatts (µW).
     * @param decimals Number of decimal digits (default 2).
     * @return Formatted string with unit suffix, or null if powerUw is null.
     */
    fun formatWattsWithUnit(powerUw: Long?, decimals: Int = 2): String? {
        val formatted = formatWatts(powerUw, decimals) ?: return null
        return "$formatted W"
    }
}
