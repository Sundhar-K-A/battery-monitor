package com.example.chargetrack.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.analytics.SessionSummaryRepository
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.mapper.toDomain
import com.example.chargetrack.ui.charts.transform.ChartDataTransformer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SessionChartsViewModel @Inject constructor(
    private val database: AppDatabase,
    private val sessionSummaryRepository: SessionSummaryRepository,
) : ViewModel() {

    internal var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    constructor(
        database: AppDatabase,
        sessionSummaryRepository: SessionSummaryRepository,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) : this(database, sessionSummaryRepository) {
        this.ioDispatcher = ioDispatcher
    }

    private val _uiState = MutableStateFlow<SessionChartsUiState>(SessionChartsUiState.Loading)
    val uiState: StateFlow<SessionChartsUiState> = _uiState.asStateFlow()

    fun loadSessionCharts(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = SessionChartsUiState.Loading
            try {
                val state = withContext(ioDispatcher) {
                    val sampleEntities = database.batterySampleDao().getSamplesForSessionOrdered(sessionId)
                    val transitionEntities = database.chargeTransitionDao().getTransitionsForSession(sessionId)
                    val standardTestEntity = database.standardTestDao().getForSession(sessionId)
                    val summary = sessionSummaryRepository.getSessionSummary(sessionId)

                    val samples = sampleEntities.map { it.toDomain() }
                    val transitions = transitionEntities.map { it.toDomain() }
                    val standardTest = standardTestEntity?.toDomain()

                    val percentVsTime = ChartDataTransformer.buildBatteryPercentVsTime(samples, summary, standardTest)
                    val powerVsPercent = ChartDataTransformer.buildPowerVsBatteryPercent(samples, summary, standardTest)
                    val powerVsTime = ChartDataTransformer.buildPowerVsTime(samples, summary, standardTest)
                    val tempVsPercent = ChartDataTransformer.buildTemperatureVsBatteryPercent(samples, summary, standardTest)
                    val currentVsPercent = ChartDataTransformer.buildCurrentVsBatteryPercent(samples, summary, standardTest)
                    val timePerPercentBars = ChartDataTransformer.buildTimePerPercentBars(transitions)

                    SessionChartsUiState.Success(
                        sessionId = sessionId,
                        batteryPercentVsTime = percentVsTime,
                        powerVsBatteryPercent = powerVsPercent,
                        powerVsTime = powerVsTime,
                        temperatureVsBatteryPercent = tempVsPercent,
                        currentVsBatteryPercent = currentVsPercent,
                        timePerPercentBars = timePerPercentBars,
                        summary = summary,
                        standardTest = standardTest,
                        sampleCount = samples.size,
                    )
                }
                _uiState.value = state
            } catch (e: Exception) {
                _uiState.value = SessionChartsUiState.Error("Failed to load charts: ${e.message}")
            }
        }
    }

    fun selectTab(tab: ChartTab) {
        val current = _uiState.value
        if (current is SessionChartsUiState.Success) {
            _uiState.update { (it as SessionChartsUiState.Success).copy(selectedTab = tab) }
        }
    }
}
