package com.example.chargetrack.domain.history

import com.example.chargetrack.domain.enums.ChargingType
import java.time.Instant

enum class DateFilterOption(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    CUSTOM("Custom"),
}

enum class HistorySortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    DURATION_DESC("Longest Duration"),
    DURATION_ASC("Shortest Duration"),
}

/**
 * Filter and sorting parameters for querying historical charging sessions.
 *
 * ## Principles
 * - [canonical2080Only]: Explicitly targets standard tests configured for 20% -> 80%.
 *   Distinct from arbitrary percentage containment.
 * - Date filters use device-local timezone boundaries converted to UTC [Instant].
 */
data class HistoryFilter(
    val dateOption: DateFilterOption = DateFilterOption.ALL,
    val customStartDate: Instant? = null,
    val customEndDate: Instant? = null,
    val canonical2080Only: Boolean = false,
    val standardTestOnly: Boolean = false,
    val chargingType: ChargingType? = null,
    val chargingSetupId: String? = null,
    val minStartPercent: Int? = null,
    val maxEndPercent: Int? = null,
    val sortBy: HistorySortOption = HistorySortOption.DATE_DESC,
)
