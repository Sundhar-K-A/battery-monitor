package com.example.chargetrack.domain.export

import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.enums.DataQuality
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.enums.TestValidity
import com.example.chargetrack.domain.model.BatterySample
import com.example.chargetrack.domain.model.ChargeTransition
import com.example.chargetrack.domain.model.ChargingSession
import com.example.chargetrack.domain.model.ChargingSetup
import com.example.chargetrack.domain.model.DeviceProfile
import com.example.chargetrack.domain.model.SoftwareSnapshot
import com.example.chargetrack.domain.model.StandardTest
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Data bundle ready to be written atomically into the Room database.
 */
data class PreparedImportBundle(
    val session: ChargingSession,
    val setup: ChargingSetup,
    val softwareSnapshot: SoftwareSnapshot,
    val deviceProfile: DeviceProfile?,
    val standardTest: StandardTest?,
    val samples: List<BatterySample>,
    val transitions: List<ChargeTransition>,
)

/**
 * Pure domain engine for validating and preparing imported session payloads.
 */
object SessionImportEngine {

    fun validateAndParse(jsonString: String): ImportValidationResult {
        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return ImportValidationResult.Invalid("Malformed JSON syntax: ${e.message}")
        }

        val version = root.optInt("schemaVersion", -1)
        if (version <= 0) {
            return ImportValidationResult.Invalid("Missing or invalid 'schemaVersion' field")
        }
        if (version > SessionExportEngine.CURRENT_SCHEMA_VERSION) {
            return ImportValidationResult.UnsupportedVersion(
                version = version,
                maxSupported = SessionExportEngine.CURRENT_SCHEMA_VERSION,
            )
        }

        // Required top-level blocks
        if (!root.has("session")) {
            return ImportValidationResult.Invalid("Missing required 'session' object")
        }
        if (!root.has("chargingSetup")) {
            return ImportValidationResult.Invalid("Missing required 'chargingSetup' object")
        }
        if (!root.has("softwareSnapshot")) {
            return ImportValidationResult.Invalid("Missing required 'softwareSnapshot' object")
        }
        if (!root.has("samples")) {
            return ImportValidationResult.Invalid("Missing required 'samples' array")
        }

        return try {
            val exportedAt = Instant.parse(root.getString("exportedAt"))
            val appVersion = root.optString("appVersion", "Unknown")

            val deviceProfile = if (root.has("deviceProfile")) {
                parseDeviceProfile(root.getJSONObject("deviceProfile"))
            } else null

            val softwareSnapshot = parseSoftwareSnapshot(root.getJSONObject("softwareSnapshot"))
            val chargingSetup = parseChargingSetup(root.getJSONObject("chargingSetup"))
            val session = parseSession(root.getJSONObject("session"))
            val standardTest = if (root.has("standardTest")) {
                parseStandardTest(root.getJSONObject("standardTest"))
            } else null

            val samples = parseSamples(root.getJSONArray("samples"))
            if (samples.isEmpty()) {
                return ImportValidationResult.Invalid("Samples array must contain at least one sample record")
            }

            val transitions = if (root.has("chargeTransitions")) {
                parseTransitions(root.getJSONArray("chargeTransitions"))
            } else emptyList()

            // Recalculate summary from source telemetry to guarantee ground-truth integrity
            val analytics = com.example.chargetrack.domain.analytics.SessionSummaryAnalyticsCalculator.calculateSummary(
                session = session,
                samples = samples,
                transitions = transitions,
            )

            // Validate integrity bounds
            if (session.startPercent !in 0..100 || (session.endPercent != null && session.endPercent !in 0..100)) {
                return ImportValidationResult.Invalid("Session battery percentages out of bounds (0..100)")
            }

            ImportValidationResult.Valid(
                ExportPayload(
                    schemaVersion = version,
                    exportedAt = exportedAt,
                    appVersion = appVersion,
                    deviceProfile = deviceProfile,
                    softwareSnapshot = softwareSnapshot,
                    chargingSetup = chargingSetup,
                    session = session,
                    standardTest = standardTest,
                    samples = samples,
                    transitions = transitions,
                    derivedAnalytics = analytics,
                )
            )
        } catch (e: Exception) {
            ImportValidationResult.Invalid("Validation error parsing entities: ${e.message}")
        }
    }

    /**
     * Prepares domain entities for atomic database insertion.
     * Enforces immutable provenance and complete foreign key remapping for [DuplicateStrategy.ASSIGN_NEW_ID].
     */
    fun prepareEntities(
        payload: ExportPayload,
        strategy: DuplicateStrategy,
    ): PreparedImportBundle {
        val originalSession = payload.session
        val originalSetup = payload.chargingSetup
        val originalSnapshot = payload.softwareSnapshot
        val originalTest = payload.standardTest

        return when (strategy) {
            DuplicateStrategy.REJECT,
            DuplicateStrategy.OVERWRITE -> {
                // Preserve original IDs and immutable provenance
                PreparedImportBundle(
                    session = originalSession,
                    setup = originalSetup,
                    softwareSnapshot = originalSnapshot,
                    deviceProfile = payload.deviceProfile,
                    standardTest = originalTest,
                    samples = payload.samples,
                    transitions = payload.transitions,
                )
            }
            DuplicateStrategy.ASSIGN_NEW_ID -> {
                val newSessionId = UUID.randomUUID().toString()
                val newSetupId = UUID.randomUUID().toString()
                val newSnapshotId = UUID.randomUUID().toString()

                val remappedSetup = originalSetup.copy(
                    id = newSetupId,
                )
                val remappedSnapshot = originalSnapshot.copy(
                    id = newSnapshotId,
                )
                val remappedSession = originalSession.copy(
                    id = newSessionId,
                    chargingSetupId = newSetupId,
                    softwareSnapshotId = newSnapshotId,
                    userNotes = if (originalSession.userNotes != null) "[Imported] ${originalSession.userNotes}" else "[Imported]",
                )
                val remappedTest = originalTest?.copy(
                    id = UUID.randomUUID().toString(),
                    sessionId = newSessionId,
                )
                val remappedSamples = payload.samples.map { sample ->
                    sample.copy(
                        id = UUID.randomUUID().toString(),
                        sessionId = newSessionId,
                    )
                }
                val remappedTransitions = payload.transitions.map { tr ->
                    tr.copy(
                        id = UUID.randomUUID().toString(),
                        sessionId = newSessionId,
                    )
                }

                PreparedImportBundle(
                    session = remappedSession,
                    setup = remappedSetup,
                    softwareSnapshot = remappedSnapshot,
                    deviceProfile = payload.deviceProfile,
                    standardTest = remappedTest,
                    samples = remappedSamples,
                    transitions = remappedTransitions,
                )
            }
        }
    }

    private fun parseDeviceProfile(json: JSONObject): DeviceProfile {
        return DeviceProfile(
            id = json.getString("id"),
            manufacturer = json.getString("manufacturer"),
            brand = json.getString("brand"),
            model = json.getString("model"),
            device = json.getString("device"),
            product = json.getString("product"),
            androidVersion = json.getString("androidVersion"),
            sdkInt = json.getInt("sdkInt"),
            originOsBuildLabel = if (json.has("originOsBuildLabel") && !json.isNull("originOsBuildLabel")) json.getString("originOsBuildLabel") else null,
            buildFingerprint = if (json.has("buildFingerprint") && !json.isNull("buildFingerprint")) json.getString("buildFingerprint") else null,
            typicalCapacityMah = if (json.has("typicalCapacityMah") && !json.isNull("typicalCapacityMah")) json.getInt("typicalCapacityMah") else null,
            ratedCapacityMah = if (json.has("ratedCapacityMah") && !json.isNull("ratedCapacityMah")) json.getInt("ratedCapacityMah") else null,
            createdAt = Instant.parse(json.getString("createdAt")),
            updatedAt = Instant.parse(json.getString("updatedAt")),
        )
    }

    private fun parseSoftwareSnapshot(json: JSONObject): SoftwareSnapshot {
        return SoftwareSnapshot(
            id = json.getString("id"),
            capturedAt = Instant.parse(json.getString("capturedAt")),
            androidVersion = json.getString("androidVersion"),
            sdkInt = json.getInt("sdkInt"),
            originOsVersion = if (json.has("originOsVersion") && !json.isNull("originOsVersion")) json.getString("originOsVersion") else null,
            buildFingerprint = json.getString("buildFingerprint"),
            appVersionName = json.getString("appVersionName"),
            appVersionCode = json.getInt("appVersionCode"),
        )
    }

    private fun parseChargingSetup(json: JSONObject): ChargingSetup {
        return ChargingSetup(
            id = json.getString("id"),
            chargerBrand = if (json.has("chargerBrand") && !json.isNull("chargerBrand")) json.getString("chargerBrand") else null,
            chargerModel = if (json.has("chargerModel") && !json.isNull("chargerModel")) json.getString("chargerModel") else null,
            advertisedWattageW = if (json.has("advertisedWattageW") && !json.isNull("advertisedWattageW")) json.getInt("advertisedWattageW") else null,
            protocol = if (json.has("protocol") && !json.isNull("protocol")) json.getString("protocol") else null,
            isOfficialCharger = json.optBoolean("isOfficialCharger", false),
            cableBrand = if (json.has("cableBrand") && !json.isNull("cableBrand")) json.getString("cableBrand") else null,
            cableModel = if (json.has("cableModel") && !json.isNull("cableModel")) json.getString("cableModel") else null,
            isOfficialCable = json.optBoolean("isOfficialCable", false),
            chargingType = ChargingType.valueOf(json.optString("chargingType", "WIRED")),
            chargingMode = ChargingMode.valueOf(json.optString("chargingMode", "NORMAL")),
            notes = if (json.has("notes") && !json.isNull("notes")) json.getString("notes") else null,
            createdAt = Instant.parse(json.getString("createdAt")),
        )
    }

    private fun parseSession(json: JSONObject): ChargingSession {
        return ChargingSession(
            id = json.getString("id"),
            startedAt = Instant.parse(json.getString("startedAt")),
            endedAt = if (json.has("endedAt") && !json.isNull("endedAt")) Instant.parse(json.getString("endedAt")) else null,
            startPercent = json.getInt("startPercent"),
            endPercent = if (json.has("endPercent") && !json.isNull("endPercent")) json.getInt("endPercent") else null,
            chargingSetupId = json.getString("chargingSetupId"),
            softwareSnapshotId = json.getString("softwareSnapshotId"),
            testType = TestType.valueOf(json.optString("testType", "FREE_FORM")),
            userNotes = if (json.has("userNotes") && !json.isNull("userNotes")) json.getString("userNotes") else null,
            endReason = if (json.has("endReason") && !json.isNull("endReason")) SessionEndReason.valueOf(json.getString("endReason")) else null,
        )
    }

    private fun parseStandardTest(json: JSONObject): StandardTest {
        return StandardTest(
            id = json.getString("id"),
            sessionId = json.getString("sessionId"),
            comparisonGroupKey = json.getString("comparisonGroupKey"),
            targetStartPercent = json.getInt("targetStartPercent"),
            targetEndPercent = json.getInt("targetEndPercent"),
            isBaseline = json.optBoolean("isBaseline", false),
            baselineSetAt = if (json.has("baselineSetAt") && !json.isNull("baselineSetAt")) Instant.parse(json.getString("baselineSetAt")) else null,
            validity = TestValidity.VALID,
            benchmarkStartedElapsedMs = if (json.has("benchmarkStartedElapsedMs") && !json.isNull("benchmarkStartedElapsedMs")) json.getLong("benchmarkStartedElapsedMs") else null,
            benchmarkEndedElapsedMs = if (json.has("benchmarkEndedElapsedMs") && !json.isNull("benchmarkEndedElapsedMs")) json.getLong("benchmarkEndedElapsedMs") else null,
        )
    }

    private fun parseTransitions(array: JSONArray): List<ChargeTransition> {
        val list = mutableListOf<ChargeTransition>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                ChargeTransition(
                    id = obj.getString("id"),
                    sessionId = obj.getString("sessionId"),
                    fromPercent = obj.getInt("fromPercent"),
                    toPercent = obj.getInt("toPercent"),
                    startedAt = Instant.parse(obj.getString("startedAt")),
                    endedAt = Instant.parse(obj.getString("endedAt")),
                    durationMs = obj.getLong("durationMs"),
                    averagePowerUw = if (obj.has("averagePowerUw") && !obj.isNull("averagePowerUw")) obj.getLong("averagePowerUw") else null,
                    medianPowerUw = if (obj.has("medianPowerUw") && !obj.isNull("medianPowerUw")) obj.getLong("medianPowerUw") else null,
                    peakPowerUw = if (obj.has("peakPowerUw") && !obj.isNull("peakPowerUw")) obj.getLong("peakPowerUw") else null,
                    averageTemperatureDeciC = if (obj.has("averageTemperatureDeciC") && !obj.isNull("averageTemperatureDeciC")) obj.getInt("averageTemperatureDeciC") else null,
                    maxTemperatureDeciC = if (obj.has("maxTemperatureDeciC") && !obj.isNull("maxTemperatureDeciC")) obj.getInt("maxTemperatureDeciC") else null,
                    sampleCount = obj.getInt("sampleCount"),
                    quality = DataQuality.valueOf(obj.optString("quality", "GOOD")),
                )
            )
        }
        return list
    }

    private fun parseSamples(array: JSONArray): List<BatterySample> {
        val list = mutableListOf<BatterySample>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                BatterySample(
                    id = obj.getString("id"),
                    sessionId = obj.getString("sessionId"),
                    timestamp = Instant.parse(obj.getString("timestamp")),
                    elapsedMs = obj.getLong("elapsedMs"),
                    percent = if (obj.has("percent") && !obj.isNull("percent")) obj.getInt("percent") else null,
                    voltageMv = if (obj.has("voltageMv") && !obj.isNull("voltageMv")) obj.getInt("voltageMv") else null,
                    currentNowUa = if (obj.has("currentNowUa") && !obj.isNull("currentNowUa")) obj.getInt("currentNowUa") else null,
                    derivedPowerUw = if (obj.has("derivedPowerUw") && !obj.isNull("derivedPowerUw")) obj.getLong("derivedPowerUw") else null,
                    temperatureDeciC = if (obj.has("temperatureDeciC") && !obj.isNull("temperatureDeciC")) obj.getInt("temperatureDeciC") else null,
                    chargeCounterUah = if (obj.has("chargeCounterUah") && !obj.isNull("chargeCounterUah")) obj.getInt("chargeCounterUah") else null,
                    batteryStatus = if (obj.has("batteryStatus") && !obj.isNull("batteryStatus")) obj.getInt("batteryStatus") else null,
                    pluggedType = if (obj.has("pluggedType") && !obj.isNull("pluggedType")) obj.getInt("pluggedType") else null,
                )
            )
        }
        return list
    }
}
