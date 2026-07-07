package com.echomind.ui.settings

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.remote.BaseUrlProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Application.dataStore by preferencesDataStore(name = "settings")

data class SettingsUiState(
    val apiEndpoint: String = "http://localhost:1234",
    val apiKey: String = "",
    val localMode: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val baseUrlProvider: BaseUrlProvider
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            _uiState.value = SettingsUiState(
                apiEndpoint = prefs[KEY_API_ENDPOINT] ?: "http://localhost:1234",
                apiKey = prefs[KEY_API_KEY] ?: "",
                localMode = prefs[KEY_LOCAL_MODE] ?: true
            )
        }
    }

    fun updateApiEndpoint(endpoint: String) {
        _uiState.value = _uiState.value.copy(apiEndpoint = endpoint)
        baseUrlProvider.updateUrl(endpoint)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_API_ENDPOINT] = endpoint
            }
        }
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_API_KEY] = key
            }
        }
    }

    fun toggleLocalMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(localMode = enabled)
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[KEY_LOCAL_MODE] = enabled
            }
        }
    }

    companion object {
        private val KEY_API_ENDPOINT = stringPreferencesKey("api_endpoint")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
        private val KEY_LOCAL_MODE = booleanPreferencesKey("local_mode")
    }
}
