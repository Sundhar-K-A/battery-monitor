package com.example.chargetrack.domain.comparison

import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest

enum class ConditionMatchStatus {
    MATCH,
    MISMATCH,
    UNKNOWN,
}

/**
 * Detailed similarity evaluation between two standard test sessions.
 *
 * ## Principles
 * - Missing metadata (null software snapshot, null temp) is [ConditionMatchStatus.UNKNOWN], never false [ConditionMatchStatus.MISMATCH].
 * - Start temp difference $|\Delta T| > 3.0^\circ\text{C}$ is flagged as a thermal discrepancy.
 * - Compares immutable [ChargingSetup] historical snapshots.
 */
data class ComparisonCondition(
    val rangeMatch: ConditionMatchStatus,
    val chargingTypeMatch: ConditionMatchStatus,
    val chargerMatch: ConditionMatchStatus,
    val cableMatch: ConditionMatchStatus,
    val modeMatch: ConditionMatchStatus,
    val temperatureMatch: ConditionMatchStatus,
    val softwareMatch: ConditionMatchStatus,
    val startTempDeltaDeciC: Int? = null,
    val mismatchWarnings: List<String> = emptyList(),
) {
    val isIdealComparison: Boolean
        get() = mismatchWarnings.isEmpty()

    companion object {
        fun evaluate(
            primaryTest: StandardTest?,
            comparedTest: StandardTest?,
            primarySetup: ChargingSetup?,
            comparedSetup: ChargingSetup?,
            primarySoftware: SoftwareSnapshot?,
            comparedSoftware: SoftwareSnapshot?,
            primaryStartTempDeciC: Int?,
            comparedStartTempDeciC: Int?,
        ): ComparisonCondition {
            val warnings = mutableListOf<String>()

            // 1. Percentage Range
            val rangeMatch = if (primaryTest != null && comparedTest != null) {
                if (primaryTest.targetStartPercent == comparedTest.targetStartPercent &&
                    primaryTest.targetEndPercent == comparedTest.targetEndPercent
                ) {
                    ConditionMatchStatus.MATCH
                } else {
                    warnings.add("Target range differs (${primaryTest.targetStartPercent}→${primaryTest.targetEndPercent}% vs ${comparedTest.targetStartPercent}→${comparedTest.targetEndPercent}%)")
                    ConditionMatchStatus.MISMATCH
                }
            } else ConditionMatchStatus.UNKNOWN

            // 2. Charging Type
            val typeMatch = if (primarySetup != null && comparedSetup != null) {
                if (primarySetup.chargingType == comparedSetup.chargingType) {
                    ConditionMatchStatus.MATCH
                } else {
                    warnings.add("Connection type differs (${primarySetup.chargingType} vs ${comparedSetup.chargingType})")
                    ConditionMatchStatus.MISMATCH
                }
            } else ConditionMatchStatus.UNKNOWN

            // 3. Charger & Wattage
            val chargerMatch = if (primarySetup != null && comparedSetup != null) {
                val sameBrand = primarySetup.chargerBrand.equals(comparedSetup.chargerBrand, ignoreCase = true)
                val sameWattage = primarySetup.advertisedWattageW == comparedSetup.advertisedWattageW
                val sameOfficial = primarySetup.isOfficialCharger == comparedSetup.isOfficialCharger
                if (sameBrand && sameWattage && sameOfficial) {
                    ConditionMatchStatus.MATCH
                } else {
                    val pWatt = primarySetup.advertisedWattageW?.let { "${it}W" } ?: "unspecified"
                    val cWatt = comparedSetup.advertisedWattageW?.let { "${it}W" } ?: "unspecified"
                    warnings.add("Charger differs (${primarySetup.chargerBrand ?: "Unknown"} $pWatt vs ${comparedSetup.chargerBrand ?: "Unknown"} $cWatt)")
                    ConditionMatchStatus.MISMATCH
                }
            } else ConditionMatchStatus.UNKNOWN

            // 4. Cable
            val cableMatch = if (primarySetup != null && comparedSetup != null) {
                val sameOfficial = primarySetup.isOfficialCable == comparedSetup.isOfficialCable
                if (sameOfficial) ConditionMatchStatus.MATCH else {
                    warnings.add("Cable official status differs")
                    ConditionMatchStatus.MISMATCH
                }
            } else ConditionMatchStatus.UNKNOWN

            // 5. Charging Mode
            val modeMatch = if (primarySetup != null && comparedSetup != null) {
                if (primarySetup.chargingMode == comparedSetup.chargingMode) {
                    ConditionMatchStatus.MATCH
                } else {
                    warnings.add("Charging mode differs (${primarySetup.chargingMode} vs ${comparedSetup.chargingMode})")
                    ConditionMatchStatus.MISMATCH
                }
            } else ConditionMatchStatus.UNKNOWN

            // 6. Starting Temperature Context
            val tempDelta: Int?
            val tempMatch = if (primaryStartTempDeciC != null && comparedStartTempDeciC != null) {
                val delta = comparedStartTempDeciC - primaryStartTempDeciC
                tempDelta = delta
                if (kotlin.math.abs(delta) > 30) { // > 3.0 °C
                    val deltaC = String.format(java.util.Locale.US, "%.1f", delta / 10.0)
                    warnings.add("Initial temperature difference is ${if (delta > 0) "+$deltaC" else deltaC}°C")
                    ConditionMatchStatus.MISMATCH
                } else {
                    ConditionMatchStatus.MATCH
                }
            } else {
                tempDelta = null
                ConditionMatchStatus.UNKNOWN
            }

            // 7. Software Snapshot
            val softwareMatch = if (primarySoftware != null && comparedSoftware != null) {
                val sameAndroid = primarySoftware.androidVersion == comparedSoftware.androidVersion
                val sameBuild = primarySoftware.buildFingerprint == comparedSoftware.buildFingerprint
                if (sameAndroid && sameBuild) {
                    ConditionMatchStatus.MATCH
                } else {
                    warnings.add("Software build differs (${primarySoftware.androidVersion} vs ${comparedSoftware.androidVersion})")
                    ConditionMatchStatus.MISMATCH
                }
            } else ConditionMatchStatus.UNKNOWN

            return ComparisonCondition(
                rangeMatch = rangeMatch,
                chargingTypeMatch = typeMatch,
                chargerMatch = chargerMatch,
                cableMatch = cableMatch,
                modeMatch = modeMatch,
                temperatureMatch = tempMatch,
                softwareMatch = softwareMatch,
                startTempDeltaDeciC = tempDelta,
                mismatchWarnings = warnings,
            )
        }
    }
}
