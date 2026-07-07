package com.echomind.ui.settings

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            _uiState.value = SettingsUiState(
                apiEndpoint = prefs[KEY_API_ENDPOINT] ?: "http://localhost:1234",
                apiKey = prefs[KEY_API_KEY] ?: "",
                localMode = true
            )
        }
    }

    fun updateApiEndpoint(endpoint: String) {
        _uiState.value = _uiState.value.copy(apiEndpoint = endpoint)
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

    companion object {
        private val KEY_API_ENDPOINT = stringPreferencesKey("api_endpoint")
        private val KEY_API_KEY = stringPreferencesKey("api_key")
    }
}
