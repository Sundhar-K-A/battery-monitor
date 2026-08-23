package com.example.chargetrack.domain.device

import com.example.chargetrack.domain.model.DeviceProfile

/**
 * The output of the device detection step.
 *
 * Represents a pre-filled [DeviceProfile] ready for user confirmation.
 * The profile is **not persisted** until the user confirms it on the
 * onboarding screen. Persistence is handled in Prompt 06 when Room is added.
 *
 * @param buildInfo        The raw Build fields read at first launch.
 * @param matchedDevice    The identified [DeviceIdentifier.KnownDevice].
 * @param proposedProfile  A fully constructed [DeviceProfile] with reference data
 *                         pre-filled if [matchedDevice] is a known device, or
 *                         with all reference fields null if [matchedDevice] is UNKNOWN.
 */
data class DeviceProfileProposal(
    val buildInfo: BuildInfo,
    val matchedDevice: DeviceIdentifier.KnownDevice,
    val proposedProfile: DeviceProfile
) {
    /** True if the detected device is the iQOO 15. */
    val isIqoo15: Boolean get() = matchedDevice == DeviceIdentifier.KnownDevice.IQOO_15

    /**
     * True if manufacturer reference data was populated.
     * When false, the user should be informed that reference capacity and
     * wattage values are unavailable for their device.
     */
    val hasReferenceData: Boolean get() = proposedProfile.typicalCapacityMah != null
}
