package com.example.chargetrack.data.system

import android.os.Build
import com.example.chargetrack.BuildConfig
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.system.SoftwareSnapshotProvider
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSoftwareSnapshotProvider @Inject constructor() : SoftwareSnapshotProvider {

    override fun captureCurrentSnapshot(): SoftwareSnapshot {
        return SoftwareSnapshot(
            id = UUID.randomUUID().toString(),
            capturedAt = Instant.now(),
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            originOsVersion = Build.DISPLAY,
            buildFingerprint = Build.FINGERPRINT ?: "Unknown",
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
        )
    }
}
