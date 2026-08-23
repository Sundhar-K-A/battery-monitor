package com.example.chargetrack.data.device

import android.os.Build
import com.example.chargetrack.domain.device.BuildInfo

/**
 * Android-side adapter that reads [android.os.Build] fields into a [BuildInfo].
 *
 * Only public, documented [Build] constants are used. No hidden APIs or system properties.
 * This object has no state; call [read] freely without lifecycle concerns.
 */
object BuildInfoReader {
    fun read(): BuildInfo = BuildInfo(
        manufacturer        = Build.MANUFACTURER,
        brand               = Build.BRAND,
        model               = Build.MODEL,
        device              = Build.DEVICE,
        product             = Build.PRODUCT,
        androidVersionRelease = Build.VERSION.RELEASE,
        sdkInt              = Build.VERSION.SDK_INT,
        buildFingerprint    = Build.FINGERPRINT,
        buildDisplay        = Build.DISPLAY,
        buildIncremental    = Build.VERSION.INCREMENTAL,
    )
}
