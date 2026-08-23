package com.example.chargetrack.domain.model

import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ComparisonGroupKeyGeneratorTest {

    @Test
    fun `canonical 20-80 wired official key matches expected format`() {
        val key = ComparisonGroupKeyGenerator.generateKey(
            targetStartPercent = 20,
            targetEndPercent = 80,
            chargingType = ChargingType.WIRED,
            isOfficialCharger = true,
            isOfficialCable = true,
            chargerBrand = "iQOO",
            advertisedWattageW = 100,
            chargingMode = ChargingMode.FLASH_CHARGE,
        )

        assertEquals("standard_20_80_wired_official_iqoo_100w_flash_charge", key)
    }

    @Test
    fun `keys differ between official and third-party chargers`() {
        val officialKey = ComparisonGroupKeyGenerator.generateKey(
            targetStartPercent = 20,
            targetEndPercent = 80,
            chargingType = ChargingType.WIRED,
            isOfficialCharger = true,
            isOfficialCable = true,
            chargerBrand = "iQOO",
            advertisedWattageW = 100,
            chargingMode = ChargingMode.FLASH_CHARGE,
        )

        val thirdPartyKey = ComparisonGroupKeyGenerator.generateKey(
            targetStartPercent = 20,
            targetEndPercent = 80,
            chargingType = ChargingType.WIRED,
            isOfficialCharger = false,
            isOfficialCable = true,
            chargerBrand = "Anker",
            advertisedWattageW = 65,
            chargingMode = ChargingMode.NORMAL,
        )

        assertNotEquals(officialKey, thirdPartyKey)
        assertEquals("standard_20_80_wired_thirdparty_anker_65w_normal", thirdPartyKey)
    }

    @Test
    fun `keys differ between FlashCharge and Normal charging modes`() {
        val flashKey = ComparisonGroupKeyGenerator.generateKey(
            targetStartPercent = 20,
            targetEndPercent = 80,
            chargingType = ChargingType.WIRED,
            isOfficialCharger = true,
            isOfficialCable = true,
            chargerBrand = "iQOO",
            advertisedWattageW = 100,
            chargingMode = ChargingMode.FLASH_CHARGE,
        )

        val normalKey = ComparisonGroupKeyGenerator.generateKey(
            targetStartPercent = 20,
            targetEndPercent = 80,
            chargingType = ChargingType.WIRED,
            isOfficialCharger = true,
            isOfficialCable = true,
            chargerBrand = "iQOO",
            advertisedWattageW = 100,
            chargingMode = ChargingMode.NORMAL,
        )

        assertNotEquals(flashKey, normalKey)
    }

    @Test
    fun `keys differ for distinct target percentage ranges`() {
        val key20To80 = ComparisonGroupKeyGenerator.generateKey(
            targetStartPercent = 20,
            targetEndPercent = 80,
            chargingType = ChargingType.WIRED,
            isOfficialCharger = true,
            isOfficialCable = true,
            chargerBrand = "iQOO",
            advertisedWattageW = 100,
            chargingMode = ChargingMode.FLASH_CHARGE,
        )

        val key10To100 = ComparisonGroupKeyGenerator.generateKey(
            targetStartPercent = 10,
            targetEndPercent = 100,
            chargingType = ChargingType.WIRED,
            isOfficialCharger = true,
            isOfficialCable = true,
            chargerBrand = "iQOO",
            advertisedWattageW = 100,
            chargingMode = ChargingMode.FLASH_CHARGE,
        )

        assertNotEquals(key20To80, key10To100)
    }
}
