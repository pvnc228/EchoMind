package com.echomind.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.repository.KnowledgeRepository
import com.echomind.domain.model.Entry
import com.echomind.domain.model.HomeCard
import com.echomind.domain.model.ThemeCoverage
import com.echomind.domain.usecase.GetEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val entries: List<Entry> = emptyList(),
    val card: HomeCard? = null,
    val coverage: List<ThemeCoverage> = emptyList(),
    val hasKnowledge: Boolean = false,
    val recent: List<Entry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedCategory: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getEntriesUseCase: GetEntriesUseCase,
    private val knowledgeRepository: KnowledgeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(category: String? = null) {
        viewModelScope.launch {
            val recent = runCatching { getEntriesUseCase.getRecentEntries(5) }.getOrDefault(emptyList())
            runCatching { knowledgeRepository.getHomeRelevance() }
                .onSuccess { relevance ->
                    _uiState.value = _uiState.value.copy(
                        card = relevance.card,
                        coverage = relevance.coverage,
                        hasKnowledge = relevance.hasKnowledge,
                        recent = recent,
                        isLoading = false,
                        error = null,
                        selectedCategory = category
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        recent = recent,
                        isLoading = false,
                        error = e.message
                    )
                }
            if (category != null) {
                getEntriesUseCase.getEntriesByCategory(category)
                    .catch { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    }.collect { entries ->
                        _uiState.value = _uiState.value.copy(entries = entries)
                    }
            } else {
                getEntriesUseCase.getAllEntries()
                    .catch { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    }.collect { entries ->
                        _uiState.value = _uiState.value.copy(entries = entries)
                    }
            }
        }
    }

    fun selectCategory(category: String?) {
        load(category)
    }
}
