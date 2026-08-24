package com.example.chargetrack.ui.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.comparison.StandardTestComparisonRepository
import com.example.chargetrack.data.history.HistoryRepository
import com.example.chargetrack.domain.comparison.StandardTestComparisonCalculator
import com.example.chargetrack.domain.comparison.StandardTestDataBundle
import com.example.chargetrack.domain.comparison.TestComparisonResult
import com.example.chargetrack.domain.history.HistoryFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class StandardTestComparisonViewModel @Inject constructor(
    private val comparisonRepository: StandardTestComparisonRepository,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        comparisonRepository: StandardTestComparisonRepository,
        historyRepository: HistoryRepository,
        ioDispatcher: CoroutineDispatcher,
    ) : this(comparisonRepository, historyRepository) {
        this.ioDispatcher = ioDispatcher
    }

    private val _uiState = MutableStateFlow<StandardTestComparisonUiState>(StandardTestComparisonUiState.Loading)
    val uiState: StateFlow<StandardTestComparisonUiState> = _uiState.asStateFlow()

    private var primarySessionId: String? = null
    private val selectedCandidateIds = mutableSetOf<String>()
    private var selectedTab: ComparisonChartTab = ComparisonChartTab.POWER_VS_PERCENT
    private var isSetBaselineDialogOpen: Boolean = false

    fun initialize(initialPrimaryId: String?, initialCandidateId: String?) {
        viewModelScope.launch {
            _uiState.value = StandardTestComparisonUiState.Loading
            try {
                withContext(ioDispatcher) {
                    val allStandardTests = historyRepository.getFilteredSessionsFlow(
                        HistoryFilter(standardTestOnly = true)
                    ).first()

                    if (allStandardTests.isEmpty()) {
                        _uiState.value = StandardTestComparisonUiState.Error("No Standard Tests available for comparison.")
                        return@withContext
                    }

                    val chosenPrimaryId = initialPrimaryId ?: allStandardTests.first().sessionId
                    primarySessionId = chosenPrimaryId

                    selectedCandidateIds.clear()
                    if (initialCandidateId != null && initialCandidateId != chosenPrimaryId) {
                        selectedCandidateIds.add(initialCandidateId)
                    } else {
                        // Auto-select the next available test if present
                        val nextCandidate = allStandardTests.firstOrNull { it.sessionId != chosenPrimaryId }
                        if (nextCandidate != null) {
                            selectedCandidateIds.add(nextCandidate.sessionId)
                        }
                    }

                    recalculateState()
                }
            } catch (e: Exception) {
                _uiState.value = StandardTestComparisonUiState.Error("Failed to initialize comparison: ${e.message}")
            }
        }
    }

    fun selectPrimary(sessionId: String) {
        if (primarySessionId == sessionId) return
        primarySessionId = sessionId
        selectedCandidateIds.remove(sessionId)
        viewModelScope.launch(ioDispatcher) {
            recalculateState()
        }
    }

    fun toggleCandidate(sessionId: String) {
        if (sessionId == primarySessionId) return

        if (selectedCandidateIds.contains(sessionId)) {
            selectedCandidateIds.remove(sessionId)
        } else {
            // Invariant: Max 5 total curves (1 primary + up to 4 candidates)
            if (selectedCandidateIds.size < 4) {
                selectedCandidateIds.add(sessionId)
            }
        }

        viewModelScope.launch(ioDispatcher) {
            recalculateState()
        }
    }

    fun selectTab(tab: ComparisonChartTab) {
        selectedTab = tab
        val current = _uiState.value
        if (current is StandardTestComparisonUiState.Success) {
            _uiState.value = current.copy(selectedTab = tab)
        }
    }

    fun openSetBaselineDialog() {
        isSetBaselineDialogOpen = true
        val current = _uiState.value
        if (current is StandardTestComparisonUiState.Success) {
            _uiState.value = current.copy(isSetBaselineDialogOpen = true)
        }
    }

    fun dismissSetBaselineDialog() {
        isSetBaselineDialogOpen = false
        val current = _uiState.value
        if (current is StandardTestComparisonUiState.Success) {
            _uiState.value = current.copy(isSetBaselineDialogOpen = false)
        }
    }

    fun confirmSetBaseline() {
        val current = _uiState.value as? StandardTestComparisonUiState.Success ?: return
        val primaryBundle = current.activePrimaryBundle
        val std = primaryBundle.standardTest ?: return

        viewModelScope.launch(ioDispatcher) {
            comparisonRepository.setBaselineForGroup(std.id, std.comparisonGroupKey)
            isSetBaselineDialogOpen = false
            recalculateState()
        }
    }

    private suspend fun recalculateState() {
        val pId = primarySessionId ?: return
        val allStandardTests = historyRepository.getFilteredSessionsFlow(
            HistoryFilter(standardTestOnly = true)
        ).first()

        val primaryBundle = comparisonRepository.getStandardTestDataBundle(pId)
            ?: return run { _uiState.value = StandardTestComparisonUiState.Error("Primary test $pId data not found") }

        val candidateBundles = selectedCandidateIds.mapNotNull {
            comparisonRepository.getStandardTestDataBundle(it)
        }

        val pairwiseResults = candidateBundles.map { candidate ->
            StandardTestComparisonCalculator.calculatePairwiseComparison(primaryBundle, candidate)
        }

        val allBundles = listOf(primaryBundle) + candidateBundles
        val formatter = DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.US).withZone(ZoneId.systemDefault())

        val powerSeries = allBundles.mapIndexed { idx, bundle ->
            val dateStr = formatter.format(bundle.session.startedAt)
            val name = if (idx == 0) "REF: $dateStr" else dateStr
            StandardTestComparisonCalculator.buildAlignedPowerSeries(bundle, name)
        }

        val tempSeries = allBundles.mapIndexed { idx, bundle ->
            val dateStr = formatter.format(bundle.session.startedAt)
            val name = if (idx == 0) "REF: $dateStr" else dateStr
            StandardTestComparisonCalculator.buildAlignedTemperatureSeries(bundle, name)
        }

        _uiState.value = StandardTestComparisonUiState.Success(
            allStandardTests = allStandardTests,
            primarySessionId = pId,
            selectedCandidateSessionIds = selectedCandidateIds.toSet(),
            activePrimaryBundle = primaryBundle,
            activeCandidateBundles = candidateBundles,
            pairwiseResults = pairwiseResults,
            alignedPowerSeries = powerSeries,
            alignedTempSeries = tempSeries,
            selectedTab = selectedTab,
            isSetBaselineDialogOpen = isSetBaselineDialogOpen,
        )
    }
}
