package com.example.chargetrack.ui.diagnostics

import android.os.BatteryManager
import com.example.chargetrack.domain.health.BatteryHealthEstimate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DiagnosticsFormatterTest {

    @Test
    fun `01 - Android health states map to correct human-readable strings`() {
        assertEquals("Good", DiagnosticsFormatter.formatHealth(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals("Overheat", DiagnosticsFormatter.formatHealth(BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals("Dead", DiagnosticsFormatter.formatHealth(BatteryManager.BATTERY_HEALTH_DEAD))
        assertEquals("Over voltage", DiagnosticsFormatter.formatHealth(BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE))
        assertEquals("Failure", DiagnosticsFormatter.formatHealth(BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE))
        assertEquals("Cold", DiagnosticsFormatter.formatHealth(BatteryManager.BATTERY_HEALTH_COLD))
        assertEquals("Unknown", DiagnosticsFormatter.formatHealth(BatteryManager.BATTERY_HEALTH_UNKNOWN))
        assertEquals("—", DiagnosticsFormatter.formatHealth(null))
        assertEquals("Unknown (999)", DiagnosticsFormatter.formatHealth(999))
    }

    @Test
    fun `02 - formatChargeCounter formats raw charge telemetry accurately without calculating health`() {
        // Current charge counter 4,410,000 uAh is formatted as raw telemetry
        assertEquals("4,410,000 µAh  (4410 mAh)", DiagnosticsFormatter.formatChargeCounter(4_410_000))
        assertEquals("—", DiagnosticsFormatter.formatChargeCounter(null))
    }

    @Test
    fun `03 - formatEstimatedHealth formats Calculated, InsufficientData, and Unavailable`() {
        val calculated = BatteryHealthEstimate.Calculated(
            displayedHealthPercentage = 96,
            rawHealthPercentage = 95.8,
            medianCapacityMah = 6710,
            referenceCapacityMah = 7000,
            observationCount = 4,
            lastObservationAt = Instant.now(),
        )
        assertEquals("96%", DiagnosticsFormatter.formatEstimatedHealth(calculated))

        val insufficient = BatteryHealthEstimate.InsufficientData(
            observationCount = 1,
            requiredCount = 3,
            referenceCapacityMah = 7000,
        )
        assertEquals("Not enough data", DiagnosticsFormatter.formatEstimatedHealth(insufficient))

        assertEquals("Unavailable", DiagnosticsFormatter.formatEstimatedHealth(BatteryHealthEstimate.Unavailable))
    }

    @Test
    fun `04 - formatCapacityReference labels typical capacity correctly`() {
        assertEquals("7000 mAh (typical reference)", DiagnosticsFormatter.formatCapacityReference(7000))
        assertEquals("—", DiagnosticsFormatter.formatCapacityReference(null))
    }
}
