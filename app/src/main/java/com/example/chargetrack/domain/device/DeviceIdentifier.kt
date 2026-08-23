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
     * Match criteria (both must be true):
     * 1. [BuildInfo.brand] is "iQOO" (case-insensitive).
     * 2. [BuildInfo.model] (trimmed, lowercase) is in [Iqoo15ReferenceData.KNOWN_MODEL_NAMES].
     *
     * NOTE: Internal hardware model codes (Vxxx codes) will be added to
     * [Iqoo15ReferenceData.KNOWN_MODEL_NAMES] after Prompt 05 Diagnostics validation
     * on the physical iQOO 15.
     */
    fun isIqoo15(buildInfo: BuildInfo): Boolean {
        val brand = buildInfo.brand.trim().lowercase()
        val model = buildInfo.model.trim().lowercase()
        return brand in Iqoo15ReferenceData.KNOWN_BRANDS &&
            model in Iqoo15ReferenceData.KNOWN_MODEL_NAMES
    }
}
