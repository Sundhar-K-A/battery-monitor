package com.example.chargetrack.domain.model

import com.example.chargetrack.domain.enums.QualityFlag
import java.time.Instant
import java.util.UUID

/**
 * A single raw measurement sample captured during a charging session.
 *
 * ## Null semantics
 * Null means "this value was not available from the Android BatteryManager on this device".
 * **Zero is never used as a sentinel for unavailability.**
 *
 * ## Current semantics (per Android BatteryManager documentation)
 * - [currentNowUa] > 0 → current entering the battery (charging)
 * - [currentNowUa] == 0 → no net current flow
 * - [currentNowUa] < 0 → net discharge (even while plugged in — valid under heavy load)
 * - [currentNowUa] == null → unavailable on this device
 *
 * ## Derived power
 * [derivedPowerUw] is computed separately using [PowerCalculation.derivedPowerUw]
 * and stored alongside the raw inputs. It must be labelled "Estimated battery-side power"
 * in the UI — never as "charger power" or "wall power".
 *
 * ## Quality flags
 * [qualityFlags] should include power-related flags from [PowerCalculation.qualityFlagsForPower]
 * merged with any operational flags (e.g. [QualityFlag.GAP_DETECTED]) known at sample time.
 */
data class BatterySample(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val timestamp: Instant,
    /** Monotonic elapsed milliseconds from session start. Must be >= 0. */
    val elapsedMs: Long,
    /** Battery percentage: 0..100. */
    val percent: Int,
    /** Battery voltage in millivolts. Null if unavailable. */
    val voltageMv: Int? = null,
    /** Instantaneous battery current in microamperes. Null if unavailable. See class KDoc. */
    val currentNowUa: Int? = null,
    /** Average battery current in microamperes. Null if unavailable. */
    val currentAverageUa: Int? = null,
    /** Charge counter in microampere-hours. Null if unavailable. */
    val chargeCounterUah: Int? = null,
    /** Energy counter in nanowatt-hours. Null if unavailable. */
    val energyCounterNwh: Long? = null,
    /** Temperature in tenths of a degree Celsius (e.g. 295 = 29.5 °C). Null if unavailable. */
    val temperatureDeciC: Int? = null,
    /** Raw BatteryManager STATUS_* constant. Null if unavailable. */
    val batteryStatus: Int? = null,
    /** Raw BatteryManager BATTERY_PLUGGED_* constant. Null if unavailable. */
    val pluggedType: Int? = null,
    /** Cycle count (BatteryManager.BATTERY_PROPERTY_CYCLE_COUNT, API 34+). Null if unavailable. */
    val cycleCount: Int? = null,
    /**
     * Estimated battery-side power in microwatts.
     * Populated by the sampling engine using [PowerCalculation.derivedPowerUw].
     * Null means either the required inputs were unavailable or power has not been computed.
     */
    val derivedPowerUw: Long? = null,
    /** See class KDoc for merging guidance. */
    val qualityFlags: Set<QualityFlag> = emptySet()
) {
    init {
        require(percent in 0..100) { "percent must be in 0..100, was $percent" }
        require(elapsedMs >= 0) { "elapsedMs must be non-negative, was $elapsedMs" }
    }
}
