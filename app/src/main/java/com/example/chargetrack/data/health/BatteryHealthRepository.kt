package com.example.chargetrack.data.health

import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.data.device.BuildInfoReader
import com.example.chargetrack.domain.device.DeviceProfileFactory
import com.example.chargetrack.domain.health.BatteryHealthEstimate
import com.example.chargetrack.domain.health.BatteryHealthEstimator
import com.example.chargetrack.domain.health.FullChargeCapacityObservation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryHealthRepository @Inject constructor(
    private val database: AppDatabase,
) {

    /**
     * Resolves the device's reference capacity in mAh (e.g. 7000 mAh typical for iQOO 15).
     */
    suspend fun getReferenceCapacityMah(): Int? {
        val persisted = database.deviceProfileDao().getProfile()
        if (persisted?.typicalCapacityMah != null) return persisted.typicalCapacityMah
        if (persisted?.ratedCapacityMah != null) return persisted.ratedCapacityMah

        val buildInfo = BuildInfoReader.read()
        val proposal = DeviceProfileFactory.buildProposal(buildInfo)
        return proposal.proposedProfile.typicalCapacityMah
            ?: proposal.proposedProfile.ratedCapacityMah
    }

    /**
     * Queries all completed sessions that reached 100%, loads their telemetry samples,
     * extracts qualifying full-charge capacity observations, and computes the estimated health.
     */
    suspend fun getEstimatedBatteryHealth(): BatteryHealthEstimate {
        val referenceCapacity = getReferenceCapacityMah() ?: return BatteryHealthEstimate.Unavailable

        val candidateSessions = database.chargingSessionDao().getSessionsReachedFull()
        val observations = mutableListOf<FullChargeCapacityObservation>()

        for (sessionEntity in candidateSessions) {
            val setupEntity = database.chargingSetupDao().getById(sessionEntity.chargingSetupId)
            val chargingMode = setupEntity?.chargingMode

            val sampleEntities = database.batterySampleDao().getSamplesForSessionOrdered(sessionEntity.id)
            val domainSamples = sampleEntities.map { it.toDomain() }

            val observation = BatteryHealthEstimator.extractSessionObservation(
                sessionId = sessionEntity.id,
                sessionTimestamp = sessionEntity.startedAt,
                samples = domainSamples,
                chargingMode = chargingMode,
                referenceCapacityMah = referenceCapacity,
            )

            if (observation != null) {
                observations.add(observation)
            }
        }

        return BatteryHealthEstimator.calculateHealth(observations, referenceCapacity)
    }
}
