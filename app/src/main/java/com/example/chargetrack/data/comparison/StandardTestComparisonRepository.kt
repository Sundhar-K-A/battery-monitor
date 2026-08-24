package com.example.chargetrack.data.comparison

import com.example.chargetrack.data.analytics.SessionSummaryRepository
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.entity.StandardTestEntity
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.domain.comparison.StandardTestDataBundle
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StandardTestComparisonRepository @Inject constructor(
    private val database: AppDatabase,
    private val sessionSummaryRepository: SessionSummaryRepository,
) {

    fun getAllStandardTestsFlow(): Flow<List<StandardTestEntity>> {
        return database.standardTestDao().getAllStandardTestsFlow()
    }

    suspend fun getBaselineForGroup(comparisonGroupKey: String): StandardTestEntity? {
        return database.standardTestDao().getBaselineForGroup(comparisonGroupKey)
    }

    suspend fun setBaselineForGroup(testId: String, comparisonGroupKey: String) {
        database.standardTestDao().setBaselineForGroup(testId, comparisonGroupKey, Instant.now())
    }

    suspend fun getStandardTestDataBundle(sessionId: String): StandardTestDataBundle? {
        val sessionEntity = database.chargingSessionDao().getById(sessionId) ?: return null
        val summary = sessionSummaryRepository.getSessionSummary(sessionId) ?: return null
        val stdEntity = database.standardTestDao().getForSession(sessionId)
        val setupEntity = database.chargingSetupDao().getById(sessionEntity.chargingSetupId)
        val softwareEntity = database.softwareSnapshotDao().getById(sessionEntity.softwareSnapshotId)
        val transitionEntities = database.chargeTransitionDao().getTransitionsForSession(sessionId)
        val sampleEntities = database.batterySampleDao().getSamplesForSessionOrdered(sessionId)

        return StandardTestDataBundle(
            session = sessionEntity.toDomain(),
            summary = summary,
            standardTest = stdEntity?.toDomain(),
            setup = setupEntity?.toDomain(),
            software = softwareEntity?.toDomain(),
            transitions = transitionEntities.map { it.toDomain() },
            samples = sampleEntities.map { it.toDomain() },
        )
    }
}
