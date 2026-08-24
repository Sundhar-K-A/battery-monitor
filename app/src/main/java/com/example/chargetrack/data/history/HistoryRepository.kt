package com.example.chargetrack.data.history

import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.domain.enums.TestType
import com.example.chargetrack.domain.history.DateFilterOption
import com.example.chargetrack.domain.history.HistoryFilter
import com.example.chargetrack.domain.history.HistorySessionItem
import com.example.chargetrack.domain.history.HistorySortOption
import com.example.chargetrack.domain.model.ChargingSetup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val database: AppDatabase,
) {

    /**
     * Observes historical charging sessions matching the provided [filter].
     *
     * ## Performance Guarantee
     * Executes queries over session metadata only ($O(N)$), without loading full raw sample lists.
     */
    fun getFilteredSessionsFlow(
        filter: HistoryFilter,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): Flow<List<HistorySessionItem>> {
        val sessionsFlow = database.chargingSessionDao().getAllSessionsFlow()
        val setupsFlow = database.chargingSetupDao().getAllSetupsFlow()
        val standardTestsFlow = database.standardTestDao().getAllStandardTestsFlow()
        val snapshotsFlow = database.softwareSnapshotDao().getAllSnapshotsFlow()

        return combine(sessionsFlow, setupsFlow, standardTestsFlow, snapshotsFlow) { sessions, setups, standardTests, snapshots ->
            val setupMap = setups.associate { it.id to it.toDomain() }
            val standardTestMap = standardTests.associateBy { it.sessionId }
            val snapshotMap = snapshots.associate { it.id to it.toDomain() }

            // Sort chronologically ascending to evaluate chronological firmware/app transitions
            val chronologicalSessions = sessions.sortedBy { it.startedAt }
            var prevFwKey: String? = null
            var prevAppKey: String? = null

            val items = chronologicalSessions.map { sessionEntity ->
                val setup = setupMap[sessionEntity.chargingSetupId]
                val stdEntity = standardTestMap[sessionEntity.id]
                val stdDomain = stdEntity?.toDomain()
                val software = snapshotMap[sessionEntity.softwareSnapshotId]

                val isComplete = if (sessionEntity.testType == TestType.STANDARD && stdEntity != null && sessionEntity.endPercent != null) {
                    sessionEntity.endPercent >= stdEntity.targetEndPercent
                } else null

                val durationMs = if (sessionEntity.endedAt != null) {
                    java.time.Duration.between(sessionEntity.startedAt, sessionEntity.endedAt).toMillis()
                } else null

                var isFwUpdate = false
                var isAppUpdate = false

                if (software != null) {
                    val currFwKey = com.example.chargetrack.domain.correlation.SoftwareIdentityUtils.computeFirmwareKey(software)
                    val currAppKey = com.example.chargetrack.domain.correlation.SoftwareIdentityUtils.computeAppKey(software)

                    if (prevFwKey != null && prevFwKey != currFwKey) {
                        isFwUpdate = true
                    }
                    if (prevAppKey != null && prevAppKey != currAppKey) {
                        isAppUpdate = true
                    }

                    prevFwKey = currFwKey
                    prevAppKey = currAppKey
                }

                HistorySessionItem(
                    sessionId = sessionEntity.id,
                    startedAt = sessionEntity.startedAt,
                    endedAt = sessionEntity.endedAt,
                    durationMs = durationMs,
                    startPercent = sessionEntity.startPercent,
                    endPercent = sessionEntity.endPercent,
                    testType = sessionEntity.testType,
                    chargingSetup = setup,
                    endReason = sessionEntity.endReason,
                    standardTest = stdDomain,
                    isStandardTestComplete = isComplete,
                    softwareSnapshot = software,
                    isFirmwareUpdateSession = isFwUpdate,
                    isAppUpdateSession = isAppUpdate,
                )
            }

            applyFilter(items, filter, zoneId, now)
        }
    }

    fun getAvailableSetupsFlow(): Flow<List<ChargingSetup>> {
        return database.chargingSetupDao().getAllSetupsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun deleteSession(sessionId: String) {
        database.chargingSessionDao().deleteSession(sessionId)
    }

    internal fun applyFilter(
        items: List<HistorySessionItem>,
        filter: HistoryFilter,
        zoneId: ZoneId,
        now: Instant,
    ): List<HistorySessionItem> {
        val (startDate, endDate) = computeDateBounds(filter, zoneId, now)

        val filtered = items.filter { item ->
            // 1. Date Range
            val matchesDate = when {
                startDate != null && endDate != null -> !item.startedAt.isBefore(startDate) && !item.startedAt.isAfter(endDate)
                startDate != null -> !item.startedAt.isBefore(startDate)
                endDate != null -> !item.startedAt.isAfter(endDate)
                else -> true
            }
            if (!matchesDate) return@filter false

            // 2. Canonical 20->80 Benchmark Filter
            if (filter.canonical2080Only && !item.isCanonical2080) {
                return@filter false
            }

            // 3. Standard Test only
            if (filter.standardTestOnly && item.testType != TestType.STANDARD) {
                return@filter false
            }

            // 4. Charging Type (Wired / Wireless)
            if (filter.chargingType != null && item.chargingSetup?.chargingType != filter.chargingType) {
                return@filter false
            }

            // 5. Charging Setup ID
            if (filter.chargingSetupId != null && item.chargingSetup?.id != filter.chargingSetupId) {
                return@filter false
            }

            // 6. Percentage Containment
            if (filter.minStartPercent != null && item.startPercent > filter.minStartPercent) {
                return@filter false
            }
            if (filter.maxEndPercent != null && (item.endPercent == null || item.endPercent < filter.maxEndPercent)) {
                return@filter false
            }

            true
        }

        return when (filter.sortBy) {
            HistorySortOption.DATE_DESC -> filtered.sortedByDescending { it.startedAt }
            HistorySortOption.DATE_ASC -> filtered.sortedBy { it.startedAt }
            HistorySortOption.DURATION_DESC -> filtered.sortedByDescending { it.durationMs ?: 0L }
            HistorySortOption.DURATION_ASC -> filtered.sortedBy { it.durationMs ?: Long.MAX_VALUE }
        }
    }

    private fun computeDateBounds(
        filter: HistoryFilter,
        zoneId: ZoneId,
        now: Instant,
    ): Pair<Instant?, Instant?> {
        val todayLocalDate = now.atZone(zoneId).toLocalDate()

        return when (filter.dateOption) {
            DateFilterOption.ALL -> Pair(null, null)
            DateFilterOption.TODAY -> {
                val start = todayLocalDate.atStartOfDay(zoneId).toInstant()
                Pair(start, now)
            }
            DateFilterOption.LAST_7_DAYS -> {
                val start = todayLocalDate.minusDays(6).atStartOfDay(zoneId).toInstant()
                Pair(start, now)
            }
            DateFilterOption.LAST_30_DAYS -> {
                val start = todayLocalDate.minusDays(29).atStartOfDay(zoneId).toInstant()
                Pair(start, now)
            }
            DateFilterOption.CUSTOM -> {
                Pair(filter.customStartDate, filter.customEndDate)
            }
        }
    }
}
