package com.example.chargetrack.domain.correlation

import com.example.chargetrack.domain.model.SoftwareSnapshot
import java.time.Instant

/**
 * Helper utilities to compute canonical firmware identity and app version identity.
 */
object SoftwareIdentityUtils {

    /**
     * Computes a canonical key identifying the device's firmware and OS build.
     * Independent of the ChargeTrack app version.
     */
    fun computeFirmwareKey(snapshot: SoftwareSnapshot): String {
        val os = snapshot.originOsVersion?.takeIf { it.isNotBlank() } ?: "standard"
        return "android_${snapshot.androidVersion}_sdk${snapshot.sdkInt}_${os}_${snapshot.buildFingerprint.hashCode()}"
    }

    /**
     * Human-readable label for the firmware/OS build.
     */
    fun formatFirmwareDisplayLabel(snapshot: SoftwareSnapshot): String {
        val os = snapshot.originOsVersion?.takeIf { it.isNotBlank() }
        return if (os != null) {
            "Android ${snapshot.androidVersion} • $os"
        } else {
            "Android ${snapshot.androidVersion} (SDK ${snapshot.sdkInt})"
        }
    }

    /**
     * Identifies the ChargeTrack application version.
     */
    fun computeAppKey(snapshot: SoftwareSnapshot): String {
        return "v${snapshot.appVersionName} (${snapshot.appVersionCode})"
    }
}

/**
 * Summary of benchmark charging metrics recorded under a specific canonical firmware build.
 * Strictly scoped to a single comparison group.
 */
data class FirmwareBuildBenchmarkSummary(
    val firmwareKey: String,
    val firmwareDisplayLabel: String,
    val androidVersion: String,
    val originOsVersion: String?,
    val buildFingerprint: String,
    val appVersionsSeen: List<String>,
    val sessionCount: Int,
    val isLowEvidence: Boolean, // True when sessionCount < 3
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val medianBenchmarkDurationMs: Long?,
    val meanBenchmarkAveragePowerUw: Long?,
    val maxBenchmarkTempDeciC: Int?,
)

/**
 * Represents a point in time when either the firmware/OS or ChargeTrack app version changed.
 */
data class SoftwareVersionTransition(
    val timestamp: Instant,
    val sessionId: String,
    val isFirmwareChanged: Boolean,
    val isAppVersionChanged: Boolean,
    val previousFirmwareLabel: String?,
    val newFirmwareLabel: String,
    val previousAppVersion: String?,
    val newAppVersion: String,
)

/**
 * Neutral comparative delta between two consecutive firmware builds in the same comparison group.
 */
data class FirmwareBuildComparison(
    val priorBuild: FirmwareBuildBenchmarkSummary,
    val currentBuild: FirmwareBuildBenchmarkSummary,
    val durationShiftMs: Long?,
    val durationShiftPercent: Double?,
    val powerShiftUw: Long?,
    val powerShiftPercent: Double?,
    val isLowEvidence: Boolean, // True if either build has sessionCount < 3
)

/**
 * Full software version correlation analysis for a comparison group.
 */
data class SoftwareCorrelationAnalysis(
    val comparisonGroupKey: String,
    val firmwareSummaries: List<FirmwareBuildBenchmarkSummary>, // Chronologically ordered by firstSeenAt
    val firmwareTransitions: List<SoftwareVersionTransition>,
    val buildComparisons: List<FirmwareBuildComparison>,
)
