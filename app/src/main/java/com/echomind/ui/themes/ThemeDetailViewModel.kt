package com.echomind.ui.themes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.repository.DecisionRepository
import com.echomind.data.repository.KnowledgeRepository
import com.echomind.domain.model.ThemeConclusion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ThemeDetailUiState(
    val themeName: String = "",
    val conclusions: List<ThemeConclusion> = emptyList(),
    val conclusionsWithOutcome: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ThemeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: KnowledgeRepository,
    private val decisionRepository: DecisionRepository
) : ViewModel() {

    private val themeId = savedStateHandle.get<Long>("themeId") ?: 0L

    private val _uiState = MutableStateFlow(ThemeDetailUiState())
    val uiState: StateFlow<ThemeDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                val conclusions = repository.getThemeConclusions(themeId)
                val name = repository.getThemeName(themeId)
                val withOutcome = conclusions
                    .mapNotNull { it.revisionId }
                    .filter { revisionId ->
                        decisionRepository.hasOutcomeForRevision(revisionId)
                    }
                    .toSet()
                ThemeDetailUiState(
                    themeName = name,
                    conclusions = conclusions,
                    conclusionsWithOutcome = withOutcome,
                    isLoading = false
                )
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
            }
        }
    }
}
