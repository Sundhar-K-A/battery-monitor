package com.example.chargetrack.data.degradation

import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.data.health.BatteryHealthRepository
import com.example.chargetrack.domain.enums.ChargingMode
import com.example.chargetrack.domain.enums.SessionEndReason
import com.example.chargetrack.domain.degradation.CapacityDegradationAnalysis
import com.example.chargetrack.domain.degradation.GroupTrendAnalysis
import com.example.chargetrack.domain.degradation.LongitudinalAnalyticsCalculator
import com.example.chargetrack.domain.degradation.StandardTestPerformanceInput
import com.example.chargetrack.domain.health.BatteryHealthEstimator
import com.example.chargetrack.domain.health.FullChargeCapacityObservation
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LongitudinalRepository @Inject constructor(
    private val database: AppDatabase,
    private val batteryHealthRepository: BatteryHealthRepository,
) {

    /**
     * Returns all distinct comparison group keys that contain completed standard tests.
     */
    suspend fun getAvailableComparisonGroups(): List<String> {
        return database.standardTestDao().getDistinctComparisonGroupKeys()
    }

    /**
     * Computes the longitudinal charging-performance trend for a specific comparison group.
     */
    suspend fun getGroupTrendAnalysis(comparisonGroupKey: String): GroupTrendAnalysis {
        val testEntities = database.standardTestDao().getTestsForGroup(comparisonGroupKey)
        val inputs = mutableListOf<StandardTestPerformanceInput>()

        for (testEntity in testEntities) {
            val session = database.chargingSessionDao().getById(testEntity.sessionId) ?: continue

            // Exclude measurement-lost or restarted sessions from charging-performance trends
            if (session.endReason == SessionEndReason.MEASUREMENT_LOST || session.endReason == SessionEndReason.DEVICE_RESTARTED) {
                continue
            }

            val sampleEntities = database.batterySampleDao().getSamplesForSessionOrdered(testEntity.sessionId)
            val domainSamples = sampleEntities.map { it.toDomain() }

            inputs.add(
                StandardTestPerformanceInput(
                    test = testEntity.toDomain(),
                    sessionStartedAt = session.startedAt,
                    samples = domainSamples,
                )
            )
        }

        val baselineEntity = database.standardTestDao().getBaselineForGroup(comparisonGroupKey)
        return LongitudinalAnalyticsCalculator.calculatePerformanceTrend(
            groupKey = comparisonGroupKey,
            testsWithMetadata = inputs,
            designatedBaselineTestId = baselineEntity?.id,
        )
    }

    /**
     * Computes the longitudinal capacity degradation analysis from all historical full-charge events.
     */
    suspend fun getCapacityDegradationAnalysis(): CapacityDegradationAnalysis {
        val refCap = batteryHealthRepository.getReferenceCapacityMah() ?: 7000
        val candidateSessions = database.chargingSessionDao().getSessionsReachedFull()
        val observations = mutableListOf<FullChargeCapacityObservation>()

        for (sessionEntity in candidateSessions) {
            val setupEntity = database.chargingSetupDao().getById(sessionEntity.chargingSetupId)
            val chargingMode = setupEntity?.chargingMode
            if (chargingMode == ChargingMode.BYPASS) continue

            val sampleEntities = database.batterySampleDao().getSamplesForSessionOrdered(sessionEntity.id)
            val domainSamples = sampleEntities.map { it.toDomain() }

            val observation = BatteryHealthEstimator.extractSessionObservation(
                sessionId = sessionEntity.id,
                sessionTimestamp = sessionEntity.startedAt,
                samples = domainSamples,
                chargingMode = chargingMode,
                referenceCapacityMah = refCap,
            )

            if (observation != null) {
                observations.add(observation)
            }
        }

        return LongitudinalAnalyticsCalculator.calculateCapacityTrend(observations, refCap)
    }

    /**
     * Atomically designates a specific completed standard test as the group baseline.
     */
    suspend fun setGroupBaseline(testId: String, comparisonGroupKey: String): Boolean {
        val testEntities = database.standardTestDao().getTestsForGroup(comparisonGroupKey)
        val target = testEntities.find { it.id == testId } ?: return false

        if (target.benchmarkStartedElapsedMs == null || target.benchmarkEndedElapsedMs == null) {
            return false
        }

        database.standardTestDao().setBaselineForGroup(testId, comparisonGroupKey, Instant.now())
        return true
    }
}
