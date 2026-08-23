package com.example.chargetrack.domain.battery

import com.example.chargetrack.domain.enums.QualityFlag
import java.time.Instant

/**
 * An immutable point-in-time reading from the Android battery hardware.
 *
 * This is a **transient** domain model — it represents one hardware read.
 * It is NOT the same as [BatterySample], which is a persisted record attached
 * to a charging session. The session engine (Prompt 07) converts snapshots into
 * samples by adding session context (sessionId, elapsedMs).
 *
 * ## Null semantics
 * Null means the field was genuinely unavailable from the device API.
 * Zero is never used as a sentinel for unavailability.
 *
 * ## Source categories
 * - **Sticky intent** ([android.content.Intent.ACTION_BATTERY_CHANGED]):
 *   percent, voltageMv, temperatureDeciC, batteryStatus, pluggedType, health, cycleCount
 * - **BatteryManager properties** ([android.os.BatteryManager.getIntProperty] /
 *   [android.os.BatteryManager.getLongProperty]):
 *   currentNowUa, currentAverageUa, chargeCounterUah, energyCounterNwh
 *
 * ## Current direction (per Android BatteryManager documentation)
 * - positive → current entering the battery (charging)
 * - zero     → no net current
 * - negative → net discharge (even while plugged in — valid under heavy load)
 * - null     → unavailable on this device
 */
data class BatterySnapshot(
    val timestamp: Instant,

    // ── Sticky intent fields ──────────────────────────────────────────────
    /** Battery level: 0..100. Null if EXTRA_LEVEL or EXTRA_SCALE is absent. */
    val percent: Int? = null,
    /** Battery voltage in millivolts. Null if EXTRA_VOLTAGE is absent or <= 0. */
    val voltageMv: Int? = null,
    /** Temperature in tenths of a degree Celsius (e.g. 295 = 29.5°C). Null if absent. */
    val temperatureDeciC: Int? = null,
    /** Raw BatteryManager.STATUS_* constant. Null if EXTRA_STATUS is absent. */
    val batteryStatus: Int? = null,
    /** Raw BatteryManager.BATTERY_PLUGGED_* constant. Null if EXTRA_PLUGGED is absent. */
    val pluggedType: Int? = null,
    /** Raw BatteryManager.HEALTH_* constant. Null if EXTRA_HEALTH is absent. */
    val health: Int? = null,

    // ── BatteryManager property fields ───────────────────────────────────
    /**
     * Instantaneous current in microamperes.
     * Null if BatteryManager returned [Integer.MIN_VALUE].
     * See class KDoc for current direction semantics.
     */
    val currentNowUa: Int? = null,
    /** Average current in microamperes. Null if unavailable. */
    val currentAverageUa: Int? = null,
    /** Charge counter in microampere-hours. Null if unavailable. */
    val chargeCounterUah: Int? = null,
    /** Energy counter in nanowatt-hours. Null if unavailable. */
    val energyCounterNwh: Long? = null,
    /** Battery cycle count from [android.os.BatteryManager.EXTRA_CYCLE_COUNT] (API 34+). Null if unavailable. */
    val cycleCount: Int? = null,

    // ── Quality ───────────────────────────────────────────────────────────
    /** Quality flags for this reading. */
    val qualityFlags: Set<QualityFlag> = emptySet()
)
