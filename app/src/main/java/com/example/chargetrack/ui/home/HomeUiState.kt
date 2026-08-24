package com.example.chargetrack.ui.home

import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.enums.TestType
import java.time.Instant

data class HomeUiState(
    val batterySnapshot: BatterySnapshot? = null,
    val estimatedPowerUw: Long? = null,
    val activeSessionId: String? = null,
    val activeSessionTestType: TestType? = null,
    val activeSessionStartPercent: Int? = null,
    val activeSessionStartedAt: Instant? = null,
    val latestBenchmarkDurationMs: Long? = null,
    val latestBenchmarkAveragePowerUw: Long? = null,
    val latestBenchmarkDate: Instant? = null,
    val latestBenchmarkGroupKey: String? = null,
    val isLoading: Boolean = false,
) {
    val isCharging: Boolean
        get() = (batterySnapshot?.currentNowUa ?: 0) > 0 || (estimatedPowerUw ?: 0L) > 0L

    val hasActiveSession: Boolean
        get() = activeSessionId != null
}
