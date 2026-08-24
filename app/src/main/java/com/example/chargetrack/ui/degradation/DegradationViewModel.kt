package com.example.chargetrack.ui.degradation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.degradation.LongitudinalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DegradationViewModel @Inject constructor(
    private val longitudinalRepository: LongitudinalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DegradationUiState>(DegradationUiState.Loading)
    val uiState: StateFlow<DegradationUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData(selectedGroupKey: String? = null) {
        viewModelScope.launch {
            _uiState.value = DegradationUiState.Loading
            runCatching {
                val groups = longitudinalRepository.getAvailableComparisonGroups()
                val targetGroup = selectedGroupKey ?: groups.firstOrNull()

                val performanceTrend = targetGroup?.let {
                    longitudinalRepository.getGroupTrendAnalysis(it)
                }
                val capacityTrend = longitudinalRepository.getCapacityDegradationAnalysis()

                if (groups.isEmpty() && capacityTrend.observationCount == 0) {
                    _uiState.value = DegradationUiState.Empty()
                } else {
                    _uiState.value = DegradationUiState.Ready(
                        availableGroups = groups,
                        selectedGroupKey = targetGroup,
                        performanceTrend = performanceTrend,
                        capacityTrend = capacityTrend,
                    )
                }
            }.onFailure { e ->
                _uiState.value = DegradationUiState.Error(e.message ?: "Failed to load longitudinal analysis")
            }
        }
    }

    fun selectGroup(groupKey: String) {
        loadData(groupKey)
    }

    fun setBaselineTest(testId: String, groupKey: String) {
        viewModelScope.launch {
            val success = longitudinalRepository.setGroupBaseline(testId, groupKey)
            if (success) {
                loadData(groupKey)
            }
        }
    }
}
