package com.example.chargetrack.domain.export

import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.DeviceProfile
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest
import java.time.Instant

enum class ExportFormat {
    CSV,
    JSON,
}

enum class DuplicateStrategy {
    REJECT,
    OVERWRITE,
    ASSIGN_NEW_ID,
}

/**
 * Complete relational bundle representing a single charging session.
 */
data class FullSessionBundle(
    val session: ChargingSession,
    val setup: ChargingSetup,
    val softwareSnapshot: SoftwareSnapshot,
    val deviceProfile: DeviceProfile?,
    val standardTest: StandardTest?,
    val samples: List<BatterySample>,
    val transitions: List<ChargeTransition>,
)

/**
 * Parsed payload from an imported JSON file.
 */
data class ExportPayload(
    val schemaVersion: Int,
    val exportedAt: Instant,
    val appVersion: String,
    val deviceProfile: DeviceProfile?,
    val softwareSnapshot: SoftwareSnapshot,
    val chargingSetup: ChargingSetup,
    val session: ChargingSession,
    val standardTest: StandardTest?,
    val samples: List<BatterySample>,
    val transitions: List<ChargeTransition>,
    val derivedAnalytics: SessionSummary?,
)

/**
 * Result of validating an exported JSON file structure.
 * Note: Cryptographic checksum/hash validation is out of scope for Prompt 20;
 * validation covers structural format, schema versioning, required fields, and value bounds.
 */
sealed interface ImportValidationResult {
    data class Valid(val payload: ExportPayload) : ImportValidationResult
    data class Invalid(val reason: String) : ImportValidationResult
    data class UnsupportedVersion(val version: Int, val maxSupported: Int = 1) : ImportValidationResult
}

/**
 * Result of an import operation into the database.
 */
sealed interface ImportResult {
    data class Success(val sessionId: String, val sampleCount: Int, val strategyUsed: DuplicateStrategy) : ImportResult
    data class Duplicate(val existingSessionId: String, val payload: ExportPayload) : ImportResult
    data class Error(val message: String) : ImportResult
}
