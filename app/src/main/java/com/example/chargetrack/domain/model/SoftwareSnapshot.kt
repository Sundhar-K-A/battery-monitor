package com.example.chargetrack.domain.model

import java.time.Instant
import java.util.UUID

/**
 * A snapshot of software/OS versions captured at the start of a charging session.
 *
 * Used to correlate charging-behaviour changes with Android/OriginOS updates over time.
 * A new snapshot is captured automatically when each session starts.
 */
data class SoftwareSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val capturedAt: Instant,
    /** android.os.Build.VERSION.RELEASE */
    val androidVersion: String,
    /** android.os.Build.VERSION.SDK_INT */
    val sdkInt: Int,
    /** OriginOS version string if available; null on non-iQOO/vivo builds. */
    val originOsVersion: String? = null,
    /** android.os.Build.FINGERPRINT */
    val buildFingerprint: String,
    /** From BuildConfig.VERSION_NAME */
    val appVersionName: String,
    /** From BuildConfig.VERSION_CODE */
    val appVersionCode: Int
)
