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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val card: HomeCard? = null,
    val dismissedCard: HomeCard? = null,
    val coverage: List<ThemeCoverage> = emptyList(),
    val hasKnowledge: Boolean = false,
    val recent: List<Entry> = emptyList(),
    val legacySuppressionReset: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
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

    fun load() {
        viewModelScope.launch {
            val recent = runCatching { getEntriesUseCase.getRecentEntries(5) }.getOrDefault(emptyList())
            runCatching { knowledgeRepository.getHomeRelevance() }
                .onSuccess { relevance ->
                    _uiState.value = _uiState.value.copy(
                        card = relevance.card,
                        coverage = relevance.coverage,
                        hasKnowledge = relevance.hasKnowledge,
                        recent = recent,
                        legacySuppressionReset = relevance.legacySuppressionReset,
                        isLoading = false,
                        error = null
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        recent = recent,
                        isLoading = false,
                        error = e.message
                    )
                }
        }
    }

    fun dismissCard() {
        val card = _uiState.value.card ?: return
        viewModelScope.launch {
            runCatching { knowledgeRepository.dismissCard(card) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(card = null, dismissedCard = card)
                }
        }
    }

    fun postponeCard(until: Long) {
        val card = _uiState.value.card ?: return
        viewModelScope.launch {
            runCatching { knowledgeRepository.postponeCard(card, until) }
                .onSuccess { _uiState.value = _uiState.value.copy(card = null) }
        }
    }

    fun undoDismissedCard() {
        val card = _uiState.value.dismissedCard ?: return
        viewModelScope.launch {
            runCatching { knowledgeRepository.restoreCard(card.cardKey) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(card = card, dismissedCard = null)
                }
        }
    }

}
