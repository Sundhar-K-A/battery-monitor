package com.example.chargetrack.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.history.HistoryRepository
import com.example.chargetrack.domain.enums.ChargingType
import com.example.chargetrack.domain.history.DateFilterOption
import com.example.chargetrack.domain.history.HistoryFilter
import com.example.chargetrack.domain.history.HistorySortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        historyRepository: HistoryRepository,
        ioDispatcher: CoroutineDispatcher,
    ) : this(historyRepository) {
        this.ioDispatcher = ioDispatcher
    }

    private val _filter = MutableStateFlow(HistoryFilter())
    private val _pendingDeleteId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        _filter.flatMapLatest { filter ->
            historyRepository.getFilteredSessionsFlow(filter)
        },
        historyRepository.getAvailableSetupsFlow(),
        _filter,
        _pendingDeleteId,
    ) { sessions, setups, filter, deleteId ->
        HistoryUiState(
            filter = filter,
            sessions = sessions,
            availableSetups = setups,
            isLoading = false,
            isDeleteConfirmDialogOpen = deleteId != null,
            pendingDeleteSessionId = deleteId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true),
    )

    fun updateFilter(transform: (HistoryFilter) -> HistoryFilter) {
        _filter.update(transform)
    }

    fun toggleCanonical2080() {
        _filter.update { it.copy(canonical2080Only = !it.canonical2080Only) }
    }

    fun toggleStandardTestOnly() {
        _filter.update { it.copy(standardTestOnly = !it.standardTestOnly) }
    }

    fun setDateOption(option: DateFilterOption) {
        _filter.update { it.copy(dateOption = option) }
    }

    fun setChargingType(type: ChargingType?) {
        _filter.update { it.copy(chargingType = type) }
    }

    fun setChargingSetup(setupId: String?) {
        _filter.update { it.copy(chargingSetupId = setupId) }
    }

    fun setSortOption(sort: HistorySortOption) {
        _filter.update { it.copy(sortBy = sort) }
    }

    fun resetFilters() {
        _filter.value = HistoryFilter()
    }

    fun requestDeleteSession(sessionId: String) {
        _pendingDeleteId.value = sessionId
    }

    fun dismissDeleteDialog() {
        _pendingDeleteId.value = null
    }

    fun confirmDeleteSession() {
        val sessionId = _pendingDeleteId.value ?: return
        viewModelScope.launch(ioDispatcher) {
            historyRepository.deleteSession(sessionId)
            _pendingDeleteId.value = null
        }
    }
}
