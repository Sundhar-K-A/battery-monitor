package com.example.chargetrack.domain.device

/**
 * Static manufacturer reference data for the iQOO 15.
 *
 * Source: https://www.iqoo.com/in/products/param/iqoo15
 *
 * These are manufacturer-stated specifications — NOT runtime measurements.
 * They must be displayed with appropriate labels (e.g. "Manufacturer reference") and
 * must never be presented as measured battery capacity or measured charging power.
 */
object Iqoo15ReferenceData {

    /** Typical (marketing) battery capacity: 7000 mAh */
    const val TYPICAL_CAPACITY_MAH: Int = 7000
    /** Rated battery capacity: 6830 mAh */
    const val RATED_CAPACITY_MAH: Int = 6830
    /** Typical energy: 26.25 Wh */
    const val TYPICAL_ENERGY_WH: Double = 26.25
    /** Rated energy: 25.62 Wh */
    const val RATED_ENERGY_WH: Double = 25.62
    /** Maximum wired charging power per spec: 100 W (FlashCharge) */
    const val WIRED_REFERENCE_W: Int = 100
    /** Maximum wireless charging power per spec: 40 W */
    const val WIRELESS_REFERENCE_W: Int = 40

    /**
     * Known [android.os.Build.BRAND] values for iQOO devices (lowercase).
     * Brand detection is case-insensitive.
     */
    val KNOWN_BRANDS: Set<String> = setOf("iqoo")

    /**
     * Known [android.os.Build.MODEL] strings for iQOO 15 variants (lowercase).
     *
     * "iqoo 15" covers the display model name.
     * Internal hardware codes (e.g. Vxxx codes) will be added after Prompt 05
     * Diagnostics validation on the physical device.
     */
    val KNOWN_MODEL_NAMES: Set<String> = setOf("iqoo 15")
}
