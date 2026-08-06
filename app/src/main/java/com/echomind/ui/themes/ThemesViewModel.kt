package com.echomind.ui.themes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.repository.KnowledgeRepository
import com.echomind.domain.model.Theme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ThemesUiState(
    val themes: List<Theme> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ThemesViewModel @Inject constructor(
    private val repository: KnowledgeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThemesUiState())
    val uiState: StateFlow<ThemesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                repository.getThemes()
            }.onSuccess { themes ->
                _uiState.value = ThemesUiState(themes = themes, isLoading = false)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
            }
        }
    }

    fun createTheme(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.createTheme(trimmed) }
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }

    fun renameTheme(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.renameTheme(id, trimmed) }
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }

    fun archiveTheme(id: Long) {
        viewModelScope.launch {
            runCatching { repository.archiveTheme(id) }
                .onSuccess { refresh() }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                }
        }
    }
}
