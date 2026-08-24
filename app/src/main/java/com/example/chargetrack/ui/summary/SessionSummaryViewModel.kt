package com.example.chargetrack.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.analytics.SessionSummaryRepository
import com.example.chargetrack.data.db.AppDatabase
import com.example.chargetrack.data.db.mapper.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    private val database: AppDatabase,
    private val sessionSummaryRepository: SessionSummaryRepository,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        database: AppDatabase,
        sessionSummaryRepository: SessionSummaryRepository,
        ioDispatcher: CoroutineDispatcher,
    ) : this(database, sessionSummaryRepository) {
        this.ioDispatcher = ioDispatcher
    }

    private val _uiState = MutableStateFlow<SessionSummaryUiState>(SessionSummaryUiState.Loading)
    val uiState: StateFlow<SessionSummaryUiState> = _uiState.asStateFlow()

    fun loadSessionSummary(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = SessionSummaryUiState.Loading
            try {
                val state = withContext(ioDispatcher) {
                    val sessionEntity = database.chargingSessionDao().getById(sessionId)
                        ?: return@withContext SessionSummaryUiState.Error("Session $sessionId not found")
                    val summary = sessionSummaryRepository.getSessionSummary(sessionId)
                        ?: return@withContext SessionSummaryUiState.Error("Summary could not be generated for session $sessionId")

                    val setupEntity = database.chargingSetupDao().getById(sessionEntity.chargingSetupId)
                    val standardTestEntity = database.standardTestDao().getForSession(sessionId)
                    val softwareEntity = database.softwareSnapshotDao().getById(sessionEntity.softwareSnapshotId)
                    val transitionEntities = database.chargeTransitionDao().getTransitionsForSession(sessionId)

                    SessionSummaryUiState.Success(
                        session = sessionEntity.toDomain(),
                        summary = summary,
                        setup = setupEntity?.toDomain(),
                        standardTest = standardTestEntity?.toDomain(),
                        software = softwareEntity?.toDomain(),
                        transitions = transitionEntities.map { it.toDomain() },
                    )
                }
                _uiState.value = state
            } catch (e: Exception) {
                _uiState.value = SessionSummaryUiState.Error("Error loading session summary: ${e.message}")
            }
        }
    }
}
