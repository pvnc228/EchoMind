package com.echomind.ui.settings

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.export.ExportManager
import com.echomind.data.remote.BaseUrlProvider
import com.echomind.data.remote.CredentialsProvider
import com.echomind.data.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiEndpoint: String = "http://localhost:1234",
    val apiKey: String = "",
    val localMode: Boolean = true,
    val exportState: ExportState = ExportState.Idle,
    val showEndpointWarning: Boolean = false
)

sealed interface ExportState {
    data object Idle : ExportState
    data object InProgress : ExportState
    data class Success(val uri: Uri) : ExportState
    data class Error(val message: String) : ExportState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val baseUrlProvider: BaseUrlProvider,
    private val exportManager: ExportManager,
    private val credentialsProvider: CredentialsProvider,
    private val settingsStore: SettingsStore
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsStore.load()
            baseUrlProvider.updateUrl(settings.apiEndpoint)
            _uiState.value = SettingsUiState(
                apiEndpoint = settings.apiEndpoint,
                apiKey = credentialsProvider.apiKey,
                localMode = settings.localMode
            )
        }
    }

    fun updateApiEndpoint(endpoint: String) {
        val isNonLocal = endpoint.contains("://") &&
            !endpoint.contains("localhost") &&
            !endpoint.contains("127.0.0.1") &&
            !endpoint.contains("10.0.2.2")
        _uiState.value = _uiState.value.copy(
            apiEndpoint = endpoint,
            showEndpointWarning = isNonLocal
        )
        baseUrlProvider.updateUrl(endpoint)
        viewModelScope.launch {
            settingsStore.setApiEndpoint(endpoint)
        }
    }

    fun dismissEndpointWarning() {
        _uiState.value = _uiState.value.copy(showEndpointWarning = false)
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key)
        credentialsProvider.updateApiKey(key)
    }

    fun toggleLocalMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(localMode = enabled)
        settingsStore.updateLocalMode(enabled)
        viewModelScope.launch {
            settingsStore.persistLocalMode(enabled)
        }
    }

    fun exportData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(exportState = ExportState.InProgress)
            val result = exportManager.exportToZip()
            result.fold(
                onSuccess = { file ->
                    val uri = FileProvider.getUriForFile(
                        getApplication(),
                        "${getApplication<Application>().packageName}.fileprovider",
                        file
                    )
                    _uiState.value = _uiState.value.copy(exportState = ExportState.Success(uri))
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        exportState = ExportState.Error(e.message ?: "Export failed")
                    )
                }
            )
        }
    }

    fun clearExportState() {
        _uiState.value = _uiState.value.copy(exportState = ExportState.Idle)
    }
}
