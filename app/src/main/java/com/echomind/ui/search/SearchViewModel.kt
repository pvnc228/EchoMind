package com.echomind.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.repository.KnowledgeRepository
import com.echomind.domain.model.Entry
import com.echomind.domain.model.KnowledgeSearchResult
import com.echomind.domain.usecase.GetEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Entry> = emptyList(),
    val knowledgeResults: List<KnowledgeSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getEntriesUseCase: GetEntriesUseCase,
    private val knowledgeRepository: KnowledgeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    results = emptyList(),
                    knowledgeResults = emptyList(),
                    isLoading = false
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true)
            getEntriesUseCase.searchEntries(query)
                .map { entries ->
                    val knowledge = kotlin.runCatching {
                        knowledgeRepository.search(query)
                    }.getOrDefault(emptyList())
                    entries to knowledge
                }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message,
                        results = emptyList(),
                        knowledgeResults = emptyList()
                    )
                }
                .collect { (entries, knowledge) ->
                    _uiState.value = _uiState.value.copy(
                        results = entries,
                        knowledgeResults = knowledge,
                        isLoading = false
                    )
                }
        }
    }
}
