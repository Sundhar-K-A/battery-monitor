package com.example.chargetrack.domain.device

/**
 * Identifies the device from a [BuildInfo] snapshot against a catalogue of
 * known devices with manufacturer reference data.
 *
 * Detection uses only public [android.os.Build] fields — no runtime measurements,
 * no private extras.
 *
 * New device entries should be added to [KnownDevice] after validating the exact
 * [android.os.Build.MODEL] and [android.os.Build.BRAND] values from the physical device
 * (see Prompt 05 — Diagnostics screen).
 */
object DeviceIdentifier {

    /**
     * Devices with known manufacturer reference data.
     *
     * [UNKNOWN] is not an error — it means no reference profile is available.
     * The app remains fully functional for any device; reference data is optional context.
     */
    enum class KnownDevice {
        IQOO_15,
        UNKNOWN
    }

    /**
     * Matches [buildInfo] against the catalogue of known devices.
     *
     * Matching is ordered: first match wins.
     * Currently only the iQOO 15 is in the catalogue.
     */
    fun identify(buildInfo: BuildInfo): KnownDevice = when {
        isIqoo15(buildInfo) -> KnownDevice.IQOO_15
        else -> KnownDevice.UNKNOWN
    }

    /**
     * Returns true if [buildInfo] matches the iQOO 15.
     *
     * Match criteria:
     * 1. [BuildInfo.brand] or [BuildInfo.manufacturer] is "iQOO" or "vivo" (case-insensitive).
     * 2. [BuildInfo.model], [BuildInfo.device], [BuildInfo.product], or [BuildInfo.buildDisplay]
     *    matches recognized iQOO 15 hardware identifiers ("iqoo 15", "i2501", "i2501i", "pd2505").
     */
    fun isIqoo15(buildInfo: BuildInfo): Boolean {
        val brand = buildInfo.brand.trim().lowercase()
        val manufacturer = buildInfo.manufacturer.trim().lowercase()

        val isBrandValid = brand in Iqoo15ReferenceData.KNOWN_BRANDS ||
            (brand.isBlank() && manufacturer in Iqoo15ReferenceData.KNOWN_BRANDS)
        val isManufacturerValid = manufacturer.isBlank() || manufacturer in Iqoo15ReferenceData.KNOWN_BRANDS

        if (!isBrandValid || !isManufacturerValid) return false

        val model = buildInfo.model.trim().lowercase()
        val device = buildInfo.device.trim().lowercase()
        val product = buildInfo.product.trim().lowercase()
        val display = buildInfo.buildDisplay.trim().lowercase()

        return model in Iqoo15ReferenceData.KNOWN_MODEL_NAMES ||
            device in Iqoo15ReferenceData.KNOWN_MODEL_NAMES ||
            product in Iqoo15ReferenceData.KNOWN_MODEL_NAMES ||
            display.contains("pd2505")
    }
}
