package com.example.chargetrack.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.battery.BatterySnapshotConverter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [BatteryDataSource] that reads from:
 * 1. The sticky [Intent.ACTION_BATTERY_CHANGED] broadcast — received without
 *    registering a receiver ([Context.registerReceiver] with null receiver returns
 *    the last known sticky value immediately without blocking).
 * 2. [BatteryManager.getIntProperty] / [BatteryManager.getLongProperty] for
 *    CURRENT_NOW, CURRENT_AVERAGE, CHARGE_COUNTER, ENERGY_COUNTER, CYCLE_COUNT.
 *
 * All raw values are forwarded to [BatterySnapshotConverter.build] which applies
 * the unavailability-sentinel logic. No conversion or interpretation occurs here.
 *
 * Thread safety: [readSnapshot] always executes on [Dispatchers.IO].
 */
@Singleton
class BatteryManagerDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : BatteryDataSource {

    override suspend fun readSnapshot(): BatterySnapshot = withContext(Dispatchers.IO) {
        val timestamp = Instant.now()
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        // ── Sticky intent extras ──────────────────────────────────────────
        // Use -1 as the "not present" sentinel (passed to BatterySnapshotConverter)
        val levelRaw       = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scaleRaw       = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val voltageRaw     = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val temperatureRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val statusRaw      = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pluggedRaw     = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val healthRaw      = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        // EXTRA_CYCLE_COUNT added in API 34; returns -1 (our absent sentinel) on older APIs.
        val cycleCountRaw  = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1

        // ── BatteryManager properties ─────────────────────────────────────
        // BatteryManager returns INT_MIN / LONG_MIN for unavailable properties.
        val currentNowRaw    = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?: BatterySnapshotConverter.INT_PROPERTY_UNAVAILABLE
        val currentAvgRaw    = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?: BatterySnapshotConverter.INT_PROPERTY_UNAVAILABLE
        val chargeCounterRaw = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?: BatterySnapshotConverter.INT_PROPERTY_UNAVAILABLE
        val energyCounterRaw = batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
            ?: BatterySnapshotConverter.LONG_PROPERTY_UNAVAILABLE

        BatterySnapshotConverter.build(
            timestamp        = timestamp,
            levelRaw         = levelRaw,
            scaleRaw         = scaleRaw,
            voltageRaw       = voltageRaw,
            temperatureRaw   = temperatureRaw,
            statusRaw        = statusRaw,
            pluggedRaw       = pluggedRaw,
            healthRaw        = healthRaw,
            cycleCountRaw    = cycleCountRaw,
            currentNowRaw    = currentNowRaw,
            currentAvgRaw    = currentAvgRaw,
            chargeCounterRaw = chargeCounterRaw,
            energyCounterRaw = energyCounterRaw,
        )
    }
}
