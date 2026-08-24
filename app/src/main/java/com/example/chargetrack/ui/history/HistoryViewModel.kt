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

import com.example.chargetrack.data.export.ExportImportRepository
import com.example.chargetrack.domain.export.DuplicateStrategy
import com.example.chargetrack.domain.export.ExportPayload
import com.example.chargetrack.domain.export.ImportResult
import java.io.InputStream

import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val exportImportRepository: ExportImportRepository,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        historyRepository: HistoryRepository,
        exportImportRepository: ExportImportRepository,
        ioDispatcher: CoroutineDispatcher,
    ) : this(historyRepository, exportImportRepository) {
        this.ioDispatcher = ioDispatcher
    }

    private val _filter = MutableStateFlow(HistoryFilter())
    private val _pendingDeleteId = MutableStateFlow<String?>(null)
    private val _pendingDuplicatePayload = MutableStateFlow<ExportPayload?>(null)
    private val _importStatusMessage = MutableStateFlow<String?>(null)

    private val _dialogAndStatusFlow = combine(
        _pendingDeleteId,
        _pendingDuplicatePayload,
        _importStatusMessage,
    ) { deleteId, duplicatePayload, importMsg ->
        Triple(deleteId, duplicatePayload, importMsg)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        _filter.flatMapLatest { filter ->
            historyRepository.getFilteredSessionsFlow(filter)
        },
        historyRepository.getAvailableSetupsFlow(),
        _filter,
        _dialogAndStatusFlow,
    ) { sessions, setups, filter, (deleteId, duplicatePayload, importMsg) ->
        HistoryUiState(
            filter = filter,
            sessions = sessions,
            availableSetups = setups,
            isLoading = false,
            isDeleteConfirmDialogOpen = deleteId != null,
            pendingDeleteSessionId = deleteId,
            pendingDuplicatePayload = duplicatePayload,
            importStatusMessage = importMsg,
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

    fun importSessionFromStream(inputStream: InputStream) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                exportImportRepository.importSession(inputStream, DuplicateStrategy.REJECT)
            }
            when (result) {
                is ImportResult.Success -> {
                    _importStatusMessage.value = "Imported session successfully (${result.sampleCount} samples)."
                }
                is ImportResult.Duplicate -> {
                    _pendingDuplicatePayload.value = result.payload
                }
                is ImportResult.Error -> {
                    _importStatusMessage.value = "Import failed: ${result.message}"
                }
            }
        }
    }

    fun resolveDuplicateImport(strategy: DuplicateStrategy) {
        val payload = _pendingDuplicatePayload.value ?: return
        _pendingDuplicatePayload.value = null
        viewModelScope.launch {
            val prepared = com.example.chargetrack.domain.export.SessionImportEngine.prepareEntities(payload, strategy)
            val result = withContext(ioDispatcher) {
                // Re-export and import or direct repository import
                val bundle = com.example.chargetrack.domain.export.FullSessionBundle(
                    session = prepared.session,
                    setup = prepared.setup,
                    softwareSnapshot = prepared.softwareSnapshot,
                    deviceProfile = prepared.deviceProfile,
                    standardTest = prepared.standardTest,
                    samples = prepared.samples,
                    transitions = prepared.transitions,
                )
                val json = com.example.chargetrack.domain.export.SessionExportEngine.generateJson(bundle, null)
                val stream = json.byteInputStream(java.nio.charset.StandardCharsets.UTF_8)
                exportImportRepository.importSession(stream, strategy)
            }
            when (result) {
                is ImportResult.Success -> {
                    _importStatusMessage.value = "Imported session successfully as copy / overwrite (${result.sampleCount} samples)."
                }
                is ImportResult.Error -> {
                    _importStatusMessage.value = "Import resolution failed: ${result.message}"
                }
                is ImportResult.Duplicate -> {}
            }
        }
    }

    fun dismissDuplicateDialog() {
        _pendingDuplicatePayload.value = null
    }

    fun clearImportStatusMessage() {
        _importStatusMessage.value = null
    }
}
