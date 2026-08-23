package com.example.chargetrack.domain.device

import com.example.chargetrack.domain.model.DeviceProfile

/**
 * Creates a [DeviceProfileProposal] from a [BuildInfo] snapshot.
 *
 * This is the entry point for Prompt 03 logic. It:
 * 1. Identifies the device using [DeviceIdentifier].
 * 2. Extracts a best-effort OriginOS label using [OriginOsBuildLabelExtractor].
 * 3. Constructs a pre-filled [DeviceProfile] with iQOO 15 reference data if matched,
 *    or a generic profile with null reference fields for any other device.
 *
 * ## What this does NOT do
 * - Does not persist the profile (deferred to Prompt 06).
 * - Does not store a first-run flag (deferred to Prompt 06).
 * - Does not read [android.os.Build] directly (the caller supplies a [BuildInfo]).
 * - Does not overwrite user edits (that is enforced in the repository layer, Prompt 06).
 */
object DeviceProfileFactory {

    /**
     * Builds a [DeviceProfileProposal] from the given [buildInfo].
     *
     * For a recognised iQOO 15: the proposal includes all manufacturer reference values.
     * For any other device: reference fields are null — the profile is still usable,
     * but without spec-level capacity or wattage reference data.
     */
    fun buildProposal(buildInfo: BuildInfo): DeviceProfileProposal {
        val matchedDevice = DeviceIdentifier.identify(buildInfo)
        val originOsLabel = OriginOsBuildLabelExtractor.extract(buildInfo)

        val profile = when (matchedDevice) {
            DeviceIdentifier.KnownDevice.IQOO_15 -> buildIqoo15Profile(buildInfo, originOsLabel)
            DeviceIdentifier.KnownDevice.UNKNOWN  -> buildGenericProfile(buildInfo, originOsLabel)
        }

        return DeviceProfileProposal(
            buildInfo = buildInfo,
            matchedDevice = matchedDevice,
            proposedProfile = profile
        )
    }

    // ── Profile builders ──────────────────────────────────────────────────

    private fun buildIqoo15Profile(
        buildInfo: BuildInfo,
        originOsLabel: String?,
    ) = DeviceProfile(
        manufacturer        = buildInfo.manufacturer,
        brand               = buildInfo.brand,
        model               = buildInfo.model,
        device              = buildInfo.device,
        product             = buildInfo.product,
        androidVersion      = buildInfo.androidVersionRelease,
        sdkInt              = buildInfo.sdkInt,
        originOsBuildLabel  = originOsLabel,
        buildFingerprint    = buildInfo.buildFingerprint,
        // iQOO 15 manufacturer reference values — applied only after confirmed match
        typicalCapacityMah  = Iqoo15ReferenceData.TYPICAL_CAPACITY_MAH,
        ratedCapacityMah    = Iqoo15ReferenceData.RATED_CAPACITY_MAH,
        typicalEnergyWh     = Iqoo15ReferenceData.TYPICAL_ENERGY_WH,
        ratedEnergyWh       = Iqoo15ReferenceData.RATED_ENERGY_WH,
        wiredReferenceW     = Iqoo15ReferenceData.WIRED_REFERENCE_W,
        wirelessReferenceW  = Iqoo15ReferenceData.WIRELESS_REFERENCE_W
    )

    private fun buildGenericProfile(
        buildInfo: BuildInfo,
        originOsLabel: String?,
    ) = DeviceProfile(
        manufacturer        = buildInfo.manufacturer,
        brand               = buildInfo.brand,
        model               = buildInfo.model,
        device              = buildInfo.device,
        product             = buildInfo.product,
        androidVersion      = buildInfo.androidVersionRelease,
        sdkInt              = buildInfo.sdkInt,
        originOsBuildLabel  = originOsLabel,
        buildFingerprint    = buildInfo.buildFingerprint
        // All reference fields default to null — unknown device
    )
}
