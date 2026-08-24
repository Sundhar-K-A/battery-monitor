package com.example.chargetrack.data.export

import androidx.room.withTransaction
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.BatterySampleEntity
import com.example.chargetrack.data.db.entity.ChargeTransitionEntity
import com.example.chargetrack.data.db.entity.ChargingSessionEntity
import com.example.chargetrack.data.db.entity.ChargingSetupEntity
import com.example.chargetrack.data.db.entity.DeviceProfileEntity
import com.example.chargetrack.data.db.entity.SoftwareSnapshotEntity
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.domain.analytics.SessionSummaryAnalyticsCalculator
import com.example.chargetrack.domain.export.DuplicateStrategy
import com.example.chargetrack.domain.export.ExportFormat
import com.example.chargetrack.domain.export.FullSessionBundle
import com.example.chargetrack.domain.export.ImportResult
import com.example.chargetrack.domain.export.ImportValidationResult
import com.example.chargetrack.domain.export.SessionExportEngine
import com.example.chargetrack.domain.export.SessionImportEngine
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportImportRepository @Inject constructor(
    private val database: AppDatabase,
) {

    /**
     * Loads the complete relational graph for a session into a [FullSessionBundle].
     */
    suspend fun loadFullSessionBundle(sessionId: String): FullSessionBundle? {
        val sessionEntity = database.chargingSessionDao().getById(sessionId) ?: return null
        val setupEntity = database.chargingSetupDao().getById(sessionEntity.chargingSetupId) ?: return null
        val snapshotEntity = database.softwareSnapshotDao().getById(sessionEntity.softwareSnapshotId) ?: return null
        val deviceProfileEntity = database.deviceProfileDao().getProfile()
        val standardTestEntity = database.standardTestDao().getForSession(sessionId)

        val sampleEntities = database.batterySampleDao().getSamplesForSessionOrdered(sessionId)
        val transitionEntities = database.chargeTransitionDao().getTransitionsForSession(sessionId)

        return FullSessionBundle(
            session = sessionEntity.toDomain(),
            setup = setupEntity.toDomain(),
            softwareSnapshot = snapshotEntity.toDomain(),
            deviceProfile = deviceProfileEntity?.toDomain(),
            standardTest = standardTestEntity?.toDomain(),
            samples = sampleEntities.map { it.toDomain() },
            transitions = transitionEntities.map { it.toDomain() },
        )
    }

    /**
     * Serializes and writes a session to the provided OutputStream in the specified format.
     */
    suspend fun exportSession(
        sessionId: String,
        format: ExportFormat,
        outputStream: OutputStream,
    ) {
        val bundle = loadFullSessionBundle(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        when (format) {
            ExportFormat.CSV -> {
                val csvContent = SessionExportEngine.generateCsv(bundle)
                outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.write(csvContent)
                    writer.flush()
                }
            }
            ExportFormat.JSON -> {
                val analytics = SessionSummaryAnalyticsCalculator.calculateSummary(
                    session = bundle.session,
                    samples = bundle.samples,
                    transitions = bundle.transitions,
                )
                val jsonContent = SessionExportEngine.generateJson(bundle, analytics)
                outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.write(jsonContent)
                    writer.flush()
                }
            }
        }
    }

    /**
     * Validates and imports a session from the provided InputStream.
     *
     * Invariants:
     * - Fully atomic database transaction with rollback on any failure.
     * - Preserves immutable setup/software/device provenance.
     * - Raw telemetry is the ground truth.
     */
    suspend fun importSession(
        inputStream: InputStream,
        strategy: DuplicateStrategy = DuplicateStrategy.REJECT,
    ): ImportResult {
        val jsonString = inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

        val validation = SessionImportEngine.validateAndParse(jsonString)
        when (validation) {
            is ImportValidationResult.Invalid -> return ImportResult.Error(validation.reason)
            is ImportValidationResult.UnsupportedVersion -> return ImportResult.Error("Unsupported schema version: ${validation.version}")
            is ImportValidationResult.Valid -> {
                val payload = validation.payload
                val existingSession = database.chargingSessionDao().getById(payload.session.id)

                if (existingSession != null && strategy == DuplicateStrategy.REJECT) {
                    return ImportResult.Duplicate(
                        existingSessionId = existingSession.id,
                        payload = payload,
                    )
                }

                val prepared = SessionImportEngine.prepareEntities(payload, strategy)

                return try {
                    database.withTransaction {
                        // 1. If OVERWRITE, delete existing session first to release foreign key RESTRICT constraints
                        if (existingSession != null && strategy == DuplicateStrategy.OVERWRITE) {
                            database.chargingSessionDao().deleteSession(prepared.session.id)
                        }

                        // 2. Device Profile (if foreign/provided, insert or update)
                        prepared.deviceProfile?.let { dp ->
                            database.deviceProfileDao().insertOrUpdate(
                                DeviceProfileEntity(
                                    id = dp.id,
                                    manufacturer = dp.manufacturer,
                                    brand = dp.brand,
                                    model = dp.model,
                                    device = dp.device,
                                    product = dp.product,
                                    androidVersion = dp.androidVersion,
                                    sdkInt = dp.sdkInt,
                                    buildFingerprint = dp.buildFingerprint ?: "",
                                    buildDisplay = dp.originOsBuildLabel ?: "",
                                    buildIncremental = "",
                                    originOsBuildLabel = dp.originOsBuildLabel,
                                    typicalCapacityMah = dp.typicalCapacityMah,
                                    ratedCapacityMah = dp.ratedCapacityMah,
                                    createdAt = dp.createdAt,
                                    updatedAt = dp.updatedAt,
                                )
                            )
                        }

                        // 3. Software Snapshot (immutable provenance)
                        val snap = prepared.softwareSnapshot
                        database.softwareSnapshotDao().insertOrReplace(
                            SoftwareSnapshotEntity(
                                id = snap.id,
                                capturedAt = snap.capturedAt,
                                androidVersion = snap.androidVersion,
                                sdkInt = snap.sdkInt,
                                originOsVersion = snap.originOsVersion,
                                buildFingerprint = snap.buildFingerprint,
                                appVersionName = snap.appVersionName,
                                appVersionCode = snap.appVersionCode,
                            )
                        )

                        // 4. Charging Setup (immutable provenance)
                        val setup = prepared.setup
                        database.chargingSetupDao().insertOrReplace(
                            ChargingSetupEntity(
                                id = setup.id,
                                chargerBrand = setup.chargerBrand,
                                chargerModel = setup.chargerModel,
                                advertisedWattageW = setup.advertisedWattageW,
                                protocol = setup.protocol,
                                isOfficialCharger = setup.isOfficialCharger,
                                cableBrand = setup.cableBrand,
                                cableModel = setup.cableModel,
                                isOfficialCable = setup.isOfficialCable,
                                chargingType = setup.chargingType,
                                chargingMode = setup.chargingMode,
                                notes = setup.notes,
                                isTemplate = false, // Imported setups are immutable instance records
                                createdAt = setup.createdAt,
                            )
                        )

                        // 5. Session
                        val s = prepared.session
                        database.chargingSessionDao().insertOrReplace(
                            ChargingSessionEntity(
                                id = s.id,
                                startedAt = s.startedAt,
                                endedAt = s.endedAt,
                                startPercent = s.startPercent,
                                endPercent = s.endPercent,
                                chargingSetupId = s.chargingSetupId,
                                softwareSnapshotId = s.softwareSnapshotId,
                                testType = s.testType,
                                userNotes = s.userNotes,
                                endReason = s.endReason,
                            )
                        )

                        // 6. Standard Test
                        prepared.standardTest?.let { st ->
                            database.standardTestDao().insert(
                                StandardTestEntity(
                                    id = st.id,
                                    sessionId = st.sessionId,
                                    comparisonGroupKey = st.comparisonGroupKey,
                                    targetStartPercent = st.targetStartPercent,
                                    targetEndPercent = st.targetEndPercent,
                                    isBaseline = false,
                                    baselineSetAt = st.baselineSetAt,
                                    benchmarkStartedElapsedMs = st.benchmarkStartedElapsedMs,
                                    benchmarkEndedElapsedMs = st.benchmarkEndedElapsedMs,
                                    validity = com.example.chargetrack.domain.enums.TestValidity.VALID,
                                )
                            )
                        }

                        // 7. Transitions
                        if (prepared.transitions.isNotEmpty()) {
                            val trEntities = prepared.transitions.map { tr ->
                                ChargeTransitionEntity(
                                    id = tr.id,
                                    sessionId = tr.sessionId,
                                    fromPercent = tr.fromPercent,
                                    toPercent = tr.toPercent,
                                    startedAt = tr.startedAt,
                                    endedAt = tr.endedAt,
                                    durationMs = tr.durationMs,
                                    averagePowerUw = tr.averagePowerUw,
                                    medianPowerUw = tr.medianPowerUw,
                                    peakPowerUw = tr.peakPowerUw,
                                    averageTemperatureDeciC = tr.averageTemperatureDeciC,
                                    maxTemperatureDeciC = tr.maxTemperatureDeciC,
                                    sampleCount = tr.sampleCount,
                                    quality = tr.quality,
                                )
                            }
                            database.chargeTransitionDao().insertAll(trEntities)
                        }

                        // 8. Samples
                        val sampleEntities = prepared.samples.map { sample ->
                            BatterySampleEntity(
                                id = sample.id,
                                sessionId = sample.sessionId,
                                timestamp = sample.timestamp,
                                elapsedMs = sample.elapsedMs,
                                percent = sample.percent,
                                voltageMv = sample.voltageMv,
                                currentNowUa = sample.currentNowUa,
                                derivedPowerUw = sample.derivedPowerUw,
                                temperatureDeciC = sample.temperatureDeciC,
                                chargeCounterUah = sample.chargeCounterUah,
                                batteryStatus = sample.batteryStatus,
                                pluggedType = sample.pluggedType,
                            )
                        }
                        database.batterySampleDao().insertSamples(sampleEntities)
                    }

                    ImportResult.Success(
                        sessionId = prepared.session.id,
                        sampleCount = prepared.samples.size,
                        strategyUsed = strategy,
                    )
                } catch (e: Exception) {
                    ImportResult.Error("Database insertion failed: ${e.message}")
                }
            }
        }
    }
}
