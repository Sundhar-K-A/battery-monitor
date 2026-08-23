package com.example.chargetrack.domain.model

import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType

/**
 * Deterministically generates canonical comparison group keys for standard tests.
 *
 * Ensures that two tests with materially different charging conditions (e.g. 100W official vs 65W 3rd party,
 * FlashCharge vs Normal) never share the same comparison group identity.
 */
object ComparisonGroupKeyGenerator {

    /**
     * Generates a canonical comparison group key.
     *
     * Format: `standard_{start}_{target}_{type}_{official}_{charger}_{mode}`
     *
     * Examples:
     * - `standard_20_80_wired_official_iqoo_100w_flash_charge`
     * - `standard_20_80_wired_thirdparty_anker_65w_normal`
     */
    fun generateKey(
        targetStartPercent: Int,
        targetEndPercent: Int,
        chargingType: ChargingType,
        isOfficialCharger: Boolean,
        isOfficialCable: Boolean,
        chargerBrand: String?,
        advertisedWattageW: Int?,
        chargingMode: ChargingMode,
    ): String {
        val typeStr = chargingType.name.lowercase()
        val officialStr = if (isOfficialCharger && isOfficialCable) "official" else "thirdparty"
        val cleanBrand = (chargerBrand ?: "generic").trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        val brandStr = if (cleanBrand.isNotEmpty()) cleanBrand else "generic"
        val wattStr = if (advertisedWattageW != null && advertisedWattageW > 0) "${advertisedWattageW}w" else "unspecified"
        val chargerStr = "${brandStr}_${wattStr}"
        val modeStr = chargingMode.name.lowercase()

        return "standard_${targetStartPercent}_${targetEndPercent}_${typeStr}_${officialStr}_${chargerStr}_${modeStr}"
    }

    /**
     * Overload using [ChargingSetup] domain model.
     */
    fun generateKey(
        targetStartPercent: Int,
        targetEndPercent: Int,
        setup: ChargingSetup,
    ): String = generateKey(
        targetStartPercent = targetStartPercent,
        targetEndPercent = targetEndPercent,
        chargingType = setup.chargingType,
        isOfficialCharger = setup.isOfficialCharger,
        isOfficialCable = setup.isOfficialCable,
        chargerBrand = setup.chargerBrand,
        advertisedWattageW = setup.advertisedWattageW,
        chargingMode = setup.chargingMode,
    )
}
