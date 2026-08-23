package com.example.chargetrack.ui.diagnostics

import android.os.BatteryManager

/**
 * Converts raw Android BatteryManager integer constants and raw measurement units
 * into human-readable strings for the Diagnostics screen.
 *
 * All functions accept nullable inputs — null returns a "—" placeholder.
 * No Android framework dependency except the BatteryManager constant values.
 */
object DiagnosticsFormatter {

    // ── Human-readable constants ──────────────────────────────────────────

    fun formatStatus(raw: Int?): String = when (raw) {
        BatteryManager.BATTERY_STATUS_CHARGING     -> "Charging"
        BatteryManager.BATTERY_STATUS_DISCHARGING  -> "Discharging"
        BatteryManager.BATTERY_STATUS_FULL         -> "Full"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
        BatteryManager.BATTERY_STATUS_UNKNOWN      -> "Unknown"
        null -> "—"
        else -> "Unknown ($raw)"
    }

    fun formatPlugged(raw: Int?): String = when (raw) {
        0                                -> "Not plugged"
        BatteryManager.BATTERY_PLUGGED_AC      -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB     -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        null -> "—"
        else -> "Other ($raw)"
    }

    fun formatHealth(raw: Int?): String = when (raw) {
        BatteryManager.BATTERY_HEALTH_GOOD               -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT           -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD               -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE       -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        BatteryManager.BATTERY_HEALTH_COLD               -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNKNOWN            -> "Unknown"
        null -> "—"
        else -> "Unknown ($raw)"
    }

    // ── Unit conversion display strings ───────────────────────────────────

    /** e.g. 4200 → "4200 mV  (4.200 V)" */
    fun formatVoltage(rawMv: Int?): String {
        rawMv ?: return "—"
        val volts = rawMv / 1000.0
        return "%d mV  (%.3f V)".format(rawMv, volts)
    }

    /** e.g. 295 → "29.5 °C" */
    fun formatTemperature(rawDeciC: Int?): String {
        rawDeciC ?: return "—"
        return "%.1f °C".format(rawDeciC / 10.0)
    }

    /**
     * e.g.  15000 → "+15.0 mA (charging)"
     *        -500 → "−0.5 mA (net discharge)"
     *           0 → "0.0 mA (no net flow)"
     */
    fun formatCurrentNow(rawUa: Int?): String {
        rawUa ?: return "—"
        val mA = rawUa / 1000.0
        val label = when {
            rawUa > 0  -> "charging"
            rawUa < 0  -> "net discharge"
            else       -> "no net flow"
        }
        val sign = if (rawUa > 0) "+" else ""
        return "%s%.1f mA  (%s)".format(sign, mA, label)
    }

    /** e.g. 14500 → "14.5 mA" */
    fun formatCurrentAvg(rawUa: Int?): String {
        rawUa ?: return "—"
        return "%.1f mA".format(rawUa / 1000.0)
    }

    /** e.g. 3000000 → "3,000,000 µAh  (3000 mAh)" */
    fun formatChargeCounter(rawUah: Int?): String {
        rawUah ?: return "—"
        return "%,d µAh  (%d mAh)".format(rawUah, rawUah / 1000)
    }

    /** e.g. 10_000_000 → "10,000,000 nWh  (10.0 mWh)" */
    fun formatEnergyCounter(rawNwh: Long?): String {
        rawNwh ?: return "—"
        val mwh = rawNwh / 1_000_000.0
        return "%,d nWh  (%.3f mWh)".format(rawNwh, mwh)
    }

    fun formatPercent(raw: Int?): String = raw?.let { "$it %" } ?: "—"

    fun formatCycleCount(raw: Int?): String = raw?.toString() ?: "—"
}
