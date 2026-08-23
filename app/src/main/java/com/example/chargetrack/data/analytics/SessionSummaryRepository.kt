package com.example.chargetrack.data.analytics

import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.domain.analytics.SessionSummary
import com.example.chargetrack.domain.analytics.SessionSummaryAnalyticsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for loading session entities, ordered raw samples, and transitions
 * from Room and computing [SessionSummary] domain analytics.
 */
@Singleton
class SessionSummaryRepository @Inject constructor(
    private val database: AppDatabase,
) {
    /**
     * Synchronously computes [SessionSummary] for the given [sessionId].
     *
     * @param sessionId The session identifier.
     * @param explicitDurationMs Authoritative monotonic duration if available.
     * @return [SessionSummary] if session exists in database; null otherwise.
     */
    suspend fun getSessionSummary(
        sessionId: String,
        explicitDurationMs: Long? = null,
    ): SessionSummary? {
        val sessionEntity = database.chargingSessionDao().getById(sessionId) ?: return null
        val standardTestEntity = database.standardTestDao().getForSession(sessionId)
        val sampleEntities = database.batterySampleDao().getSamplesForSessionOrdered(sessionId)
        val transitionEntities = database.chargeTransitionDao().getTransitionsForSession(sessionId)

        return SessionSummaryAnalyticsCalculator.calculateSummary(
            session = sessionEntity.toDomain(),
            standardTest = standardTestEntity,
            samples = sampleEntities.map { it.toDomain() },
            transitions = transitionEntities.map { it.toDomain() },
            explicitDurationMs = explicitDurationMs,
        )
    }

    /**
     * Observes real-time updates to [SessionSummary] as new samples and transitions arrive.
     */
    fun getSessionSummaryFlow(
        sessionId: String,
        explicitDurationMs: Long? = null,
    ): Flow<SessionSummary?> {
        val samplesFlow = database.batterySampleDao().getSamplesForSessionFlow(sessionId)
        val transitionsFlow = database.chargeTransitionDao().getTransitionsForSessionFlow(sessionId)

        return combine(samplesFlow, transitionsFlow) { sampleEntities, transitionEntities ->
            val sessionEntity = database.chargingSessionDao().getById(sessionId) ?: return@combine null
            val standardTestEntity = database.standardTestDao().getForSession(sessionId)

            SessionSummaryAnalyticsCalculator.calculateSummary(
                session = sessionEntity.toDomain(),
                standardTest = standardTestEntity,
                samples = sampleEntities.map { it.toDomain() },
                transitions = transitionEntities.map { it.toDomain() },
                explicitDurationMs = explicitDurationMs,
            )
        }
    }
}
