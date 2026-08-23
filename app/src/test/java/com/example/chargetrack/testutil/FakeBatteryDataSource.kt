package com.example.chargetrack.testutil

import android.os.BatteryManager
import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.battery.BatterySnapshot
import java.time.Instant

/**
 * Scriptable fake implementation of [BatteryDataSource] for deterministic testing.
 */
class FakeBatteryDataSource(
    var currentSnapshot: BatterySnapshot = defaultSnapshot(),
) : BatteryDataSource {

    private val snapshotQueue = mutableListOf<BatterySnapshot>()

    fun enqueueSnapshot(snapshot: BatterySnapshot) {
        snapshotQueue.add(snapshot)
    }

    override suspend fun readSnapshot(): BatterySnapshot {
        return if (snapshotQueue.isNotEmpty()) {
            val next = snapshotQueue.removeAt(0)
            currentSnapshot = next
            next
        } else {
            currentSnapshot
        }
    }

    companion object {
        fun defaultSnapshot(
            percent: Int? = 20,
            voltageMv: Int? = 4050,
            currentNowUa: Int? = 15_000_000,
            temperatureDeciC: Int? = 300,
            batteryStatus: Int? = BatteryManager.BATTERY_STATUS_CHARGING,
            pluggedType: Int? = BatteryManager.BATTERY_PLUGGED_AC,
        ): BatterySnapshot = BatterySnapshot(
            timestamp = Instant.parse("2026-08-23T10:00:00Z"),
            percent = percent,
            voltageMv = voltageMv,
            currentNowUa = currentNowUa,
            currentAverageUa = null,
            chargeCounterUah = null,
            energyCounterNwh = null,
            temperatureDeciC = temperatureDeciC,
            batteryStatus = batteryStatus,
            pluggedType = pluggedType,
            cycleCount = null,
            qualityFlags = emptySet(),
        )
    }
}
