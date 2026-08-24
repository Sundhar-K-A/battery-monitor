package com.example.chargetrack.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chargetrack.data.device.DeviceProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceProfileRepository: DeviceProfileRepository,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        deviceProfileRepository: DeviceProfileRepository,
        ioDispatcher: CoroutineDispatcher,
    ) : this(deviceProfileRepository) {
        this.ioDispatcher = ioDispatcher
    }

    private data class DeviceFormState(
        val nickname: String? = null,
        val ramStorage: String? = null,
        val notes: String? = null,
        val statusMessage: String? = null,
        val isSaving: Boolean = false,
    )

    private val _formState = MutableStateFlow(DeviceFormState())

    val uiState: StateFlow<DeviceUiState> = combine(
        deviceProfileRepository.getProfileFlow(),
        deviceProfileRepository.getSavedSetupsFlow(),
        _formState,
    ) { profile, setups, form ->
        DeviceUiState(
            profile = profile,
            savedSetups = setups,
            isLoading = false,
            isSaving = form.isSaving,
            editNickname = form.nickname ?: profile?.nickname.orEmpty(),
            editRamStorage = form.ramStorage ?: profile?.ramStorageVariant.orEmpty(),
            editNotes = form.notes ?: profile?.notes.orEmpty(),
            statusMessage = form.statusMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceUiState(isLoading = true),
    )

    fun onNicknameChange(value: String) {
        _formState.update { it.copy(nickname = value) }
    }

    fun onRamStorageChange(value: String) {
        _formState.update { it.copy(ramStorage = value) }
    }

    fun onNotesChange(value: String) {
        _formState.update { it.copy(notes = value) }
    }

    fun saveUserMetadata() {
        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            val form = _formState.value
            withContext(ioDispatcher) {
                val profile = deviceProfileRepository.getProfile()
                deviceProfileRepository.updateUserMetadata(
                    nickname = form.nickname ?: profile?.nickname,
                    purchaseDate = profile?.purchaseDate,
                    firstUseDate = profile?.firstUseDate,
                    ramStorageVariant = form.ramStorage ?: profile?.ramStorageVariant,
                    notes = form.notes ?: profile?.notes,
                )
            }
            _formState.update {
                it.copy(
                    isSaving = false,
                    statusMessage = "Device metadata saved successfully.",
                )
            }
        }
    }

    fun clearStatusMessage() {
        _formState.update { it.copy(statusMessage = null) }
    }
}
