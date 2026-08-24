package com.example.chargetrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.domain.battery.BatteryDataSource
import com.example.chargetrack.domain.battery.BatterySnapshot
import com.example.chargetrack.domain.util.PowerCalculation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val batteryDataSource: BatteryDataSource,
    private val database: AppDatabase,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        batteryDataSource: BatteryDataSource,
        database: AppDatabase,
        ioDispatcher: CoroutineDispatcher,
    ) : this(batteryDataSource, database) {
        this.ioDispatcher = ioDispatcher
    }

    // Passive polling flow of battery telemetry (active only while UI is visible)
    private val batteryFlow: Flow<BatterySnapshot> = flow {
        while (true) {
            try {
                val snapshot = batteryDataSource.readSnapshot()
                emit(snapshot)
            } catch (_: Exception) {
                // Ignore transient battery read errors
            }
            delay(2000)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        batteryFlow,
        database.chargingSessionDao().getActiveSessionFlow(),
        database.standardTestDao().getAllStandardTestsFlow(),
    ) { snapshot, activeSession, standardTests ->
        val powerUw = PowerCalculation.derivedPowerUw(snapshot.voltageMv, snapshot.currentNowUa)
        
        // Find latest valid completed benchmark
        val latestBenchmark = standardTests.lastOrNull { it.benchmarkEndedElapsedMs != null && it.benchmarkStartedElapsedMs != null }
        var latestDurationMs: Long? = null
        var latestAvgPowerUw: Long? = null

        if (latestBenchmark != null) {
            latestDurationMs = (latestBenchmark.benchmarkEndedElapsedMs ?: 0L) - (latestBenchmark.benchmarkStartedElapsedMs ?: 0L)
            // Calculate benchmark average power if transitions exist
            val transitions = database.chargeTransitionDao().getTransitionsForSession(latestBenchmark.sessionId)
            val powerTransitions = transitions.filter { (it.averagePowerUw ?: 0L) > 0L }
            if (powerTransitions.isNotEmpty()) {
                latestAvgPowerUw = powerTransitions.mapNotNull { it.averagePowerUw }.average().toLong()
            }
        }

        HomeUiState(
            batterySnapshot = snapshot,
            estimatedPowerUw = powerUw,
            activeSessionId = activeSession?.id,
            activeSessionTestType = activeSession?.testType,
            activeSessionStartPercent = activeSession?.startPercent,
            activeSessionStartedAt = activeSession?.startedAt,
            latestBenchmarkDurationMs = latestDurationMs,
            latestBenchmarkAveragePowerUw = latestAvgPowerUw,
            latestBenchmarkGroupKey = latestBenchmark?.comparisonGroupKey,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true),
    )
}
