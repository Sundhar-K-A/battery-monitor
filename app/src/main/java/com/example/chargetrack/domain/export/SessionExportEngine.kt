package com.example.chargetrack.domain.export

import com.example.chargetrack.domain.analytics.SessionSummary
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
import java.util.Locale

/**
 * Pure domain engine for serializing charging sessions into standard CSV and JSON formats.
 */
object SessionExportEngine {

    const val CURRENT_SCHEMA_VERSION = 1

    /**
     * Generates a standard machine-reprocessable CSV of raw telemetry samples.
     *
     * Invariants:
     * - derived_power_w is explicitly documented as estimated battery-side power, not charger/wall power.
     * - Null values are preserved as empty strings.
     * - Signed battery-side current and power are preserved without clamping.
     */
    fun generateCsv(bundle: FullSessionBundle): String {
        val sb = StringBuilder()
        sb.append("# ChargeTrack Battery Telemetry CSV\n")
        sb.append("# derived_power_w: Estimated battery-side power in Watts (positive = charging, negative = net discharging). Not charger/wall power.\n")
        sb.append("elapsed_ms,timestamp_iso,battery_percent,voltage_mv,current_now_ua,derived_power_w,temperature_celsius,charge_counter_uah\n")

        for (sample in bundle.samples) {
            val elapsed = sample.elapsedMs
            val ts = sample.timestamp.toString()
            val pct = sample.percent?.toString() ?: ""
            val volt = sample.voltageMv?.toString() ?: ""
            val curr = sample.currentNowUa?.toString() ?: ""
            val pwr = sample.derivedPowerUw?.let { String.format(Locale.US, "%.4f", it / 1_000_000.0) } ?: ""
            val temp = sample.temperatureDeciC?.let { String.format(Locale.US, "%.1f", it / 10.0) } ?: ""
            val cc = sample.chargeCounterUah?.toString() ?: ""

            sb.append("$elapsed,$ts,$pct,$volt,$curr,$pwr,$temp,$cc\n")
        }

        return sb.toString()
    }

    /**
     * Generates a structured JSON string containing the complete relational data graph for the session.
     */
    fun generateJson(bundle: FullSessionBundle, analytics: SessionSummary? = null): String {
        val root = JSONObject()

        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("exportedAt", Instant.now().toString())
        root.put("appVersion", "ChargeTrack v1.0")
        root.put(
            "measurementSemanticsNote",
            "derivedPowerUw represents estimated battery-side power (positive = charging, negative = discharge) and never charger/wall power."
        )

        // 1. Device Profile
        bundle.deviceProfile?.let { dp ->
            root.put("deviceProfile", serializeDeviceProfile(dp))
        }

        // 2. Software Snapshot
        root.put("softwareSnapshot", serializeSoftwareSnapshot(bundle.softwareSnapshot))

        // 3. Charging Setup
        root.put("chargingSetup", serializeChargingSetup(bundle.setup))

        // 4. Session
        root.put("session", serializeSession(bundle.session))

        // 5. Standard Test (if any)
        bundle.standardTest?.let { st ->
            root.put("standardTest", serializeStandardTest(st))
        }

        // 6. Charge Transitions
        val transitionsArr = JSONArray()
        for (tr in bundle.transitions) {
            transitionsArr.put(serializeTransition(tr))
        }
        root.put("chargeTransitions", transitionsArr)

        // 7. Samples
        val samplesArr = JSONArray()
        for (sample in bundle.samples) {
            samplesArr.put(serializeSample(sample))
        }
        root.put("samples", samplesArr)

        // 8. Derived Analytics (included for convenience; never overrides raw telemetry on import)
        analytics?.let {
            root.put("derivedAnalytics", serializeAnalytics(it))
        }

        return root.toString(2)
    }

    private fun serializeDeviceProfile(dp: DeviceProfile): JSONObject {
        return JSONObject().apply {
            put("id", dp.id)
            put("manufacturer", dp.manufacturer)
            put("brand", dp.brand)
            put("model", dp.model)
            put("device", dp.device)
            put("product", dp.product)
            put("androidVersion", dp.androidVersion)
            put("sdkInt", dp.sdkInt)
            put("originOsBuildLabel", dp.originOsBuildLabel)
            put("buildFingerprint", dp.buildFingerprint)
            put("typicalCapacityMah", dp.typicalCapacityMah)
            put("ratedCapacityMah", dp.ratedCapacityMah)
            put("createdAt", dp.createdAt.toString())
            put("updatedAt", dp.updatedAt.toString())
        }
    }

    private fun serializeSoftwareSnapshot(s: SoftwareSnapshot): JSONObject {
        return JSONObject().apply {
            put("id", s.id)
            put("capturedAt", s.capturedAt.toString())
            put("androidVersion", s.androidVersion)
            put("sdkInt", s.sdkInt)
            put("originOsVersion", s.originOsVersion)
            put("buildFingerprint", s.buildFingerprint)
            put("appVersionName", s.appVersionName)
            put("appVersionCode", s.appVersionCode)
        }
    }

    private fun serializeChargingSetup(cs: ChargingSetup): JSONObject {
        return JSONObject().apply {
            put("id", cs.id)
            put("chargerBrand", cs.chargerBrand)
            put("chargerModel", cs.chargerModel)
            put("advertisedWattageW", cs.advertisedWattageW)
            put("protocol", cs.protocol)
            put("isOfficialCharger", cs.isOfficialCharger)
            put("cableBrand", cs.cableBrand)
            put("cableModel", cs.cableModel)
            put("isOfficialCable", cs.isOfficialCable)
            put("chargingType", cs.chargingType.name)
            put("chargingMode", cs.chargingMode.name)
            put("notes", cs.notes)
            put("createdAt", cs.createdAt.toString())
        }
    }

    private fun serializeSession(s: ChargingSession): JSONObject {
        return JSONObject().apply {
            put("id", s.id)
            put("startedAt", s.startedAt.toString())
            put("endedAt", s.endedAt?.toString())
            put("startPercent", s.startPercent)
            put("endPercent", s.endPercent)
            put("chargingSetupId", s.chargingSetupId)
            put("softwareSnapshotId", s.softwareSnapshotId)
            put("testType", s.testType.name)
            put("userNotes", s.userNotes)
            put("endReason", s.endReason?.name)
        }
    }

    private fun serializeStandardTest(st: StandardTest): JSONObject {
        return JSONObject().apply {
            put("id", st.id)
            put("sessionId", st.sessionId)
            put("comparisonGroupKey", st.comparisonGroupKey)
            put("targetStartPercent", st.targetStartPercent)
            put("targetEndPercent", st.targetEndPercent)
            put("isBaseline", st.isBaseline)
            put("baselineSetAt", st.baselineSetAt?.toString())
            put("benchmarkStartedElapsedMs", st.benchmarkStartedElapsedMs)
            put("benchmarkEndedElapsedMs", st.benchmarkEndedElapsedMs)
        }
    }

    private fun serializeTransition(tr: ChargeTransition): JSONObject {
        return JSONObject().apply {
            put("id", tr.id)
            put("sessionId", tr.sessionId)
            put("fromPercent", tr.fromPercent)
            put("toPercent", tr.toPercent)
            put("startedAt", tr.startedAt.toString())
            put("endedAt", tr.endedAt.toString())
            put("durationMs", tr.durationMs)
            put("averagePowerUw", tr.averagePowerUw)
            put("medianPowerUw", tr.medianPowerUw)
            put("peakPowerUw", tr.peakPowerUw)
            put("averageTemperatureDeciC", tr.averageTemperatureDeciC)
            put("maxTemperatureDeciC", tr.maxTemperatureDeciC)
            put("sampleCount", tr.sampleCount)
            put("quality", tr.quality.name)
        }
    }

    private fun serializeSample(s: BatterySample): JSONObject {
        return JSONObject().apply {
            put("id", s.id)
            put("sessionId", s.sessionId)
            put("timestamp", s.timestamp.toString())
            put("elapsedMs", s.elapsedMs)
            put("percent", s.percent)
            put("voltageMv", s.voltageMv)
            put("currentNowUa", s.currentNowUa)
            put("derivedPowerUw", s.derivedPowerUw)
            put("temperatureDeciC", s.temperatureDeciC)
            put("chargeCounterUah", s.chargeCounterUah)
            put("batteryStatus", s.batteryStatus)
            put("pluggedType", s.pluggedType)
        }
    }

    private fun serializeAnalytics(a: SessionSummary): JSONObject {
        return JSONObject().apply {
            put("sessionId", a.sessionId)
            put("durationMs", a.durationMs)
            put("percentGained", a.percentGained)
            put("isCompleteStandardTest", a.isCompleteStandardTest)
            put("averagePowerUw", a.averagePowerUw)
            put("medianPowerUw", a.medianPowerUw)
            put("peakPowerUw", a.peakPowerUw)
            put("startTemperatureDeciC", a.startTemperatureDeciC)
            put("endTemperatureDeciC", a.endTemperatureDeciC)
            put("peakTemperatureDeciC", a.peakTemperatureDeciC)
            put("overallQuality", a.overallQuality.name)
            put("outlierSampleCount", a.outlierSampleCount)
        }
    }
}
