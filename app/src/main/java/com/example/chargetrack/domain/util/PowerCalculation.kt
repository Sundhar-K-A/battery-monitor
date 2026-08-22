package com.example.chargetrack.domain.util

import com.example.chargetrack.domain.enums.QualityFlag

/**
 * Pure utility for battery-side power estimation and associated quality flags.
 *
 * Formula: P(µW) = voltageMv × currentNowUa / 1_000
 *
 * UI labelling requirement: always display the result as
 * "Estimated battery-side power" — NEVER as "charger power" or "wall power".
 * Battery-side power is an estimate from Android-reported readings, not wall input.
 */
object PowerCalculation {

    /**
     * Derives estimated battery-side power in microwatts (µW).
     *
     * Rules:
     * - Returns **null** if either [voltageMv] or [currentNowUa] is null (data unavailable).
     * - Returns **0** if [currentNowUa] is 0 (genuine zero current — do not coerce to null).
     * - Returns a **negative** value if [currentNowUa] is negative.
     *   Negative current indicates net discharge while plugged in, which is a valid Android
     *   BatteryManager state (e.g. heavy foreground load exceeds charging input).
     *
     * @param voltageMv   Battery voltage in millivolts; null if unavailable.
     * @param currentNowUa Battery current in microamperes; null if unavailable.
     *                     Positive = current entering battery; 0 = no net flow;
     *                     negative = net discharge even while plugged in.
     * @return Estimated power in microwatts, or null if either input is unavailable.
     */
    fun derivedPowerUw(voltageMv: Int?, currentNowUa: Int?): Long? {
        if (voltageMv == null || currentNowUa == null) return null
        return (voltageMv.toLong() * currentNowUa.toLong()) / 1_000L
    }

    /**
     * Returns quality flags appropriate for the given voltage/current pair.
     *
     * - [QualityFlag.MISSING_REQUIRED_VALUE] is added when either input is null.
     * - [QualityFlag.OUTLIER] is added when current is negative
     *   (net discharge while plugged in — notable but not an error).
     *
     * These flags should be merged with any other operational flags
     * (e.g. [QualityFlag.GAP_DETECTED]) before storing a [BatterySample].
     */
    fun qualityFlagsForPower(voltageMv: Int?, currentNowUa: Int?): Set<QualityFlag> {
        val flags = mutableSetOf<QualityFlag>()
        if (voltageMv == null || currentNowUa == null) {
            flags += QualityFlag.MISSING_REQUIRED_VALUE
        }
        if (currentNowUa != null && currentNowUa < 0) {
            flags += QualityFlag.OUTLIER
        }
        return flags
    }
}
