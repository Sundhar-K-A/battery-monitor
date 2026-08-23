package com.example.chargetrack.domain.session

import android.os.BatteryManager
import com.example.chargetrack.domain.battery.BatterySnapshot

/**
 * Pure evaluation of battery observations to determine charging state and transitions.
 *
 * Enforces the strict 4-tier charging confirmation contract:
 * 1. CONFIRMED CHARGING: pluggedType != NONE AND batteryStatus == STATUS_CHARGING
 * 2. CONFIRMED FULL/CONNECTED: pluggedType != NONE AND batteryStatus == STATUS_FULL
 *    (indication that device remains connected to power; does NOT start a session on its own without charging)
 * 3. FALLBACK: If batteryStatus is unavailable, allow: pluggedType != NONE AND currentNowUa > 0
 * 4. NOT CONFIRMED: pluggedType != NONE alone with unavailable/NOT_CHARGING status and
 *    unavailable/non-positive current.
 *
 * A valid pluggedType alone NEVER starts a charging session without confirmed charging current or status.
 */
object ChargingConditionEvaluator {

    /**
     * Determines whether the snapshot provides confirmed evidence that battery charging is active.
     */
    fun isConfirmedCharging(snapshot: BatterySnapshot): Boolean {
        val plugged = snapshot.pluggedType
        val isPlugged = plugged != null && plugged != 0
        if (!isPlugged) return false

        val status = snapshot.batteryStatus

        // Priority 1: Confirmed charging status
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            return true
        }

        // Priority 3: Fallback when batteryStatus is unavailable
        if (status == null) {
            val current = snapshot.currentNowUa
            if (current != null && current > 0) {
                return true
            }
        }

        // Priority 2 & 4: STATUS_FULL, STATUS_NOT_CHARGING, STATUS_DISCHARGING, or unknown without positive current
        return false
    }

    /**
     * Determines whether the device is physically connected to an external power source.
     */
    fun isPluggedIn(snapshot: BatterySnapshot): Boolean {
        val plugged = snapshot.pluggedType
        return plugged != null && plugged != 0
    }

    /**
     * Determines whether charging has genuinely stopped while the device remains connected to power.
     * Note: [isChargingStopped] does NOT imply battery full (100%).
     */
    fun isChargingStoppedWhilePlugged(snapshot: BatterySnapshot): Boolean {
        val isPlugged = isPluggedIn(snapshot)
        if (!isPlugged) return false

        val status = snapshot.batteryStatus ?: return false
        return status == BatteryManager.BATTERY_STATUS_NOT_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_DISCHARGING
    }
}
