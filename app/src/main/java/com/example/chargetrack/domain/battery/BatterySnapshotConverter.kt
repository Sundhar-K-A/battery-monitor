package com.example.chargetrack.domain.battery

import com.example.chargetrack.domain.enums.QualityFlag
import java.time.Instant

/**
 * Pure conversion logic from raw Android battery API integers/longs
 * into the [BatterySnapshot] domain model.
 *
 * This object has **no Android framework dependencies** — all inputs are primitives.
 * The Android layer ([BatteryManagerDataSource]) reads from [android.os.BatteryManager]
 * and the sticky [android.content.Intent.ACTION_BATTERY_CHANGED] intent, then forwards
 * the raw values here.
 *
 * ## Sentinel conventions
 * | Source | Unavailability sentinel |
 * |--------|------------------------|
 * | BatteryManager int property | [INT_PROPERTY_UNAVAILABLE] = [Integer.MIN_VALUE] |
 * | BatteryManager long property | [LONG_PROPERTY_UNAVAILABLE] = [Long.MIN_VALUE] |
 * | Intent extras (not present) | caller passes -1 as the `defaultValue` for getIntExtra |
 *
 * Note: cycle count is exposed via [android.os.BatteryManager.EXTRA_CYCLE_COUNT]
 * (a sticky-intent extra added in API 34), not as a BatteryManager int property.
 * It therefore uses -1 as its absent sentinel, like other intent extras.
 */
object BatterySnapshotConverter {

    /** BatteryManager returns this for unavailable int properties. */
    const val INT_PROPERTY_UNAVAILABLE: Int = Int.MIN_VALUE

    /** BatteryManager returns this for unavailable long properties. */
    const val LONG_PROPERTY_UNAVAILABLE: Long = Long.MIN_VALUE

    // ── Primitive converters ──────────────────────────────────────────────

    /**
     * Converts a raw BatteryManager int property value to nullable.
     * Returns null if [raw] == [INT_PROPERTY_UNAVAILABLE].
     * Returns 0 for a genuine zero reading.
     * Returns negative values for negative readings (e.g. net discharge current).
     */
    fun intPropertyToNullable(raw: Int): Int? =
        if (raw == INT_PROPERTY_UNAVAILABLE) null else raw

    /**
     * Converts a raw BatteryManager long property value to nullable.
     * Returns null if [raw] == [LONG_PROPERTY_UNAVAILABLE].
     */
    fun longPropertyToNullable(raw: Long): Long? =
        if (raw == LONG_PROPERTY_UNAVAILABLE) null else raw

    /**
     * Calculates battery percentage from the raw EXTRA_LEVEL and EXTRA_SCALE intent extras.
     *
     * Returns null if:
     * - [level] < 0 (EXTRA_LEVEL absent — getIntExtra returned -1)
     * - [scale] <= 0 (EXTRA_SCALE absent or malformed)
     * - result would be outside 0..100
     */
    fun percentFromLevelScale(level: Int, scale: Int): Int? {
        if (level < 0 || scale <= 0) return null
        val percent = (level * 100) / scale
        return if (percent in 0..100) percent else null
    }

    /**
     * Converts a raw intent EXTRA_VOLTAGE value to a nullable millivolt value.
     *
     * The Android intent delivers voltage already in millivolts.
     * Returns null if [rawMv] <= 0 (absent or physically impossible).
     */
    fun voltageToNullable(rawMv: Int): Int? = if (rawMv > 0) rawMv else null

    /**
     * Converts a raw intent EXTRA_TEMPERATURE value to nullable.
     *
     * Unit: tenths of a degree Celsius (e.g. 295 = 29.5 °C).
     * Returns null if [raw] < 0 (extra absent; 0 deciC = 0 °C is technically valid).
     */
    fun temperatureToNullable(raw: Int): Int? = if (raw < 0) null else raw

    /**
     * Converts a raw intent extra int constant (STATUS, PLUGGED, HEALTH) to nullable.
     *
     * Returns null if [raw] < 0 (extra absent — caller passed -1 as getIntExtra default).
     * Returns 0 if the device returned 0 (e.g. pluggedType = 0 = not plugged).
     */
    fun intentConstantToNullable(raw: Int): Int? = if (raw < 0) null else raw

    // ── Full snapshot builder ─────────────────────────────────────────────

    /**
     * Constructs a [BatterySnapshot] from raw values extracted by the Android layer.
     *
     * All parameters are primitives; no Android framework types are referenced.
     * The caller must pass -1 for any intent extra that was absent (using the
     * `getIntExtra(key, -1)` pattern).
     *
     * @param timestamp        When this reading was taken.
     * @param levelRaw         EXTRA_LEVEL; -1 if absent.
     * @param scaleRaw         EXTRA_SCALE; -1 if absent.
     * @param voltageRaw       EXTRA_VOLTAGE in mV; -1 if absent.
     * @param temperatureRaw   EXTRA_TEMPERATURE in deciC; -1 if absent.
     * @param statusRaw        EXTRA_STATUS constant; -1 if absent.
     * @param pluggedRaw       EXTRA_PLUGGED constant; -1 if absent.
     * @param healthRaw        EXTRA_HEALTH constant; -1 if absent.
     * @param currentNowRaw    BATTERY_PROPERTY_CURRENT_NOW in µA; [INT_PROPERTY_UNAVAILABLE] if absent.
     * @param currentAvgRaw    BATTERY_PROPERTY_CURRENT_AVERAGE in µA; [INT_PROPERTY_UNAVAILABLE] if absent.
     * @param chargeCounterRaw BATTERY_PROPERTY_CHARGE_COUNTER in µAh; [INT_PROPERTY_UNAVAILABLE] if absent.
     * @param energyCounterRaw BATTERY_PROPERTY_ENERGY_COUNTER in nWh; [LONG_PROPERTY_UNAVAILABLE] if absent.
     * @param cycleCountRaw    EXTRA_CYCLE_COUNT from sticky intent (API 34+); -1 if absent.
     */
    fun build(
        timestamp: Instant,
        levelRaw: Int,
        scaleRaw: Int,
        voltageRaw: Int,
        temperatureRaw: Int,
        statusRaw: Int,
        pluggedRaw: Int,
        healthRaw: Int,
        currentNowRaw: Int,
        currentAvgRaw: Int,
        chargeCounterRaw: Int,
        energyCounterRaw: Long,
        cycleCountRaw: Int,
    ): BatterySnapshot {
        val percent = percentFromLevelScale(levelRaw, scaleRaw)
        val currentNowUa = intPropertyToNullable(currentNowRaw)

        val flags = mutableSetOf<QualityFlag>()
        if (percent == null) flags += QualityFlag.MISSING_REQUIRED_VALUE
        if (currentNowUa != null && currentNowUa < 0) flags += QualityFlag.OUTLIER

        return BatterySnapshot(
            timestamp         = timestamp,
            percent           = percent,
            voltageMv         = voltageToNullable(voltageRaw),
            temperatureDeciC  = temperatureToNullable(temperatureRaw),
            batteryStatus     = intentConstantToNullable(statusRaw),
            pluggedType       = intentConstantToNullable(pluggedRaw),
            health            = intentConstantToNullable(healthRaw),
            currentNowUa      = currentNowUa,
            currentAverageUa  = intPropertyToNullable(currentAvgRaw),
            chargeCounterUah  = intPropertyToNullable(chargeCounterRaw),
            energyCounterNwh  = longPropertyToNullable(energyCounterRaw),
            cycleCount        = intentConstantToNullable(cycleCountRaw),
            qualityFlags      = flags
        )
    }
}
