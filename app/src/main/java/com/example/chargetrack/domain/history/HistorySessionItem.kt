package com.example.chargetrack.domain.history

import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.StandardTest
import java.time.Instant

/**
 * Lightweight, high-performance representation of a historical session for list rendering.
 *
 * Does not require loading thousands of raw [com.example.chargetrack.domain.model.BatterySample] records.
 */
data class HistorySessionItem(
    val sessionId: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val durationMs: Long?,
    val startPercent: Int,
    val endPercent: Int?,
    val testType: TestType,
    val chargingSetup: ChargingSetup?,
    val endReason: SessionEndReason?,
    val standardTest: StandardTest?,
    val isStandardTestComplete: Boolean?,
) {
    val percentGained: Int?
        get() = if (endPercent != null) endPercent - startPercent else null

    val isCanonical2080: Boolean
        get() = testType == TestType.STANDARD &&
            standardTest != null &&
            standardTest.targetStartPercent == 20 &&
            standardTest.targetEndPercent == 80

    val displayTitle: String
        get() {
            return if (testType == TestType.STANDARD && standardTest != null) {
                "${standardTest.targetStartPercent}% → ${standardTest.targetEndPercent}% Standard Test"
            } else {
                "Free-Form Charging"
            }
        }

    val displaySetup: String
        get() {
            if (chargingSetup == null) return "Unknown Setup"
            val brandAndModel = listOfNotNull(chargingSetup.chargerBrand, chargingSetup.chargerModel)
                .joinToString(" ")
                .ifBlank { "Charger" }
            val mode = chargingSetup.chargingMode.name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }
            val type = chargingSetup.chargingType.name.lowercase()
                .replaceFirstChar { it.uppercase() }
            return "$brandAndModel · $mode ($type)"
        }
}
