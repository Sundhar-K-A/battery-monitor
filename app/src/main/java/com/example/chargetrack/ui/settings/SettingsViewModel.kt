package com.example.chargetrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.db.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val database: AppDatabase,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        database: AppDatabase,
        ioDispatcher: CoroutineDispatcher,
    ) : this(database) {
        this.ioDispatcher = ioDispatcher
    }

    private val _isResetDialogOpen = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        database.chargingSessionDao().getAllSessionsFlow(),
        _isResetDialogOpen,
        _statusMessage,
    ) { sessions, isResetOpen, msg ->
        SettingsUiState(
            sessionCount = sessions.size,
            appVersion = "1.0",
            appBuildCode = 1,
            isResetDialogOpen = isResetOpen,
            statusMessage = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
    )

    fun openResetDialog() {
        _isResetDialogOpen.value = true
    }

    fun dismissResetDialog() {
        _isResetDialogOpen.value = false
    }

    fun confirmResetDatabase() {
        _isResetDialogOpen.value = false
        viewModelScope.launch {
            withContext(ioDispatcher) {
                database.clearAllTables()
            }
            _statusMessage.value = "All session and telemetry records cleared."
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
