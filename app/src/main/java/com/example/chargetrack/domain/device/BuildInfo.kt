package com.example.chargetrack.domain.device

/**
 * A snapshot of all Android [android.os.Build] values read at first launch.
 *
 * This is a pure data holder with no Android framework dependency.
 * The Android layer (added in Prompt 04 onwards) constructs a [BuildInfo]
 * from the real [android.os.Build] fields and passes it into the domain logic.
 * Tests supply fakes directly.
 */
data class BuildInfo(
    /** android.os.Build.MANUFACTURER — e.g. "vivo" for iQOO devices */
    val manufacturer: String,
    /** android.os.Build.BRAND — e.g. "iQOO" */
    val brand: String,
    /** android.os.Build.MODEL — e.g. "iQOO 15" */
    val model: String,
    /** android.os.Build.DEVICE — hardware device code */
    val device: String,
    /** android.os.Build.PRODUCT — product/SKU code */
    val product: String,
    /** android.os.Build.VERSION.RELEASE — e.g. "16" */
    val androidVersionRelease: String,
    /** android.os.Build.VERSION.SDK_INT — e.g. 36 */
    val sdkInt: Int,
    /** android.os.Build.FINGERPRINT */
    val buildFingerprint: String,
    /**
     * android.os.Build.DISPLAY — user-visible build ID string.
     * On vivo/iQOO devices may contain OriginOS version information.
     */
    val buildDisplay: String,
    /**
     * android.os.Build.VERSION.INCREMENTAL — internal build number.
     * May contain OriginOS or vivo build identifiers.
     */
    val buildIncremental: String
)
