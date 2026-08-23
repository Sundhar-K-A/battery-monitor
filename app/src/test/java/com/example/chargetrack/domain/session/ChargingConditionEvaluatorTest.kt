package com.example.chargetrack.domain.session

import android.os.BatteryManager
import com.example.chargetrack.domain.battery.BatterySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChargingConditionEvaluatorTest {

    private fun sampleSnapshot(
        pluggedType: Int? = BatteryManager.BATTERY_PLUGGED_AC,
        batteryStatus: Int? = BatteryManager.BATTERY_STATUS_CHARGING,
        currentNowUa: Int? = 15_000_000,
        voltageMv: Int? = 4050,
        percent: Int? = 25,
    ): BatterySnapshot = BatterySnapshot(
        timestamp = Instant.now(),
        percent = percent,
        voltageMv = voltageMv,
        currentNowUa = currentNowUa,
        currentAverageUa = null,
        chargeCounterUah = null,
        energyCounterNwh = null,
        temperatureDeciC = 300,
        batteryStatus = batteryStatus,
        pluggedType = pluggedType,
        cycleCount = null,
        qualityFlags = emptySet(),
    )

    @Test
    fun `priority 1 - confirmed charging with plugged and charging status`() {
        val snapshot = sampleSnapshot(
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
        )
        assertTrue(ChargingConditionEvaluator.isConfirmedCharging(snapshot))
    }

    @Test
    fun `priority 2 - confirmed full with plugged is not treated as active charging start`() {
        val snapshot = sampleSnapshot(
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = BatteryManager.BATTERY_STATUS_FULL,
        )
        assertFalse(ChargingConditionEvaluator.isConfirmedCharging(snapshot))
        assertTrue(ChargingConditionEvaluator.isPluggedIn(snapshot))
    }

    @Test
    fun `priority 3 - fallback when status unavailable but plugged and positive current`() {
        val snapshot = sampleSnapshot(
            pluggedType = BatteryManager.BATTERY_PLUGGED_USB,
            batteryStatus = null, // Unavailable status
            currentNowUa = 2_000_000, // Positive charging current
        )
        assertTrue(ChargingConditionEvaluator.isConfirmedCharging(snapshot))
    }

    @Test
    fun `priority 4 - plugged alone with unavailable status and null current is not confirmed`() {
        val snapshot = sampleSnapshot(
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = null,
            currentNowUa = null, // Current unavailable
        )
        assertFalse(ChargingConditionEvaluator.isConfirmedCharging(snapshot))
    }

    @Test
    fun `priority 4 - plugged alone with NOT_CHARGING status and zero current is not confirmed`() {
        val snapshot = sampleSnapshot(
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            currentNowUa = 0,
        )
        assertFalse(ChargingConditionEvaluator.isConfirmedCharging(snapshot))
    }

    @Test
    fun `unplugged snapshot is never confirmed charging`() {
        val snapshot = sampleSnapshot(
            pluggedType = 0, // Unplugged
            batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING,
        )
        assertFalse(ChargingConditionEvaluator.isConfirmedCharging(snapshot))
        assertFalse(ChargingConditionEvaluator.isPluggedIn(snapshot))
    }

    @Test
    fun `charging stopped while plugged returns true for NOT_CHARGING and DISCHARGING`() {
        val notChargingSnapshot = sampleSnapshot(
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = BatteryManager.BATTERY_STATUS_NOT_CHARGING,
        )
        assertTrue(ChargingConditionEvaluator.isChargingStoppedWhilePlugged(notChargingSnapshot))

        val dischargingSnapshot = sampleSnapshot(
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING,
        )
        assertTrue(ChargingConditionEvaluator.isChargingStoppedWhilePlugged(dischargingSnapshot))

        val chargingSnapshot = sampleSnapshot(
            pluggedType = BatteryManager.BATTERY_PLUGGED_AC,
            batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
        )
        assertFalse(ChargingConditionEvaluator.isChargingStoppedWhilePlugged(chargingSnapshot))
    }
}
