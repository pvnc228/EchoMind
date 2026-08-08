package com.echomind.ui.decisions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.repository.DecisionRepository
import com.echomind.domain.model.Decision
import com.echomind.domain.model.DecisionSourceOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DecisionsUiState(
    val decisions: List<Decision> = emptyList(),
    val sources: List<DecisionSourceOption> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DecisionsViewModel @Inject constructor(
    private val repository: DecisionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DecisionsUiState())
    val uiState: StateFlow<DecisionsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching {
                repository.getDecisions() to repository.getDecisionSources()
            }
                .onSuccess { (decisions, sources) ->
                    _uiState.value = DecisionsUiState(
                        decisions = decisions,
                        sources = sources,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun add(
        question: String,
        suggestion: String?,
        sourceRevisionId: Long?
    ) {
        viewModelScope.launch {
            runCatching {
                repository.createDecision(question, suggestion, sourceRevisionId)
                load()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun choose(decisionId: Long, choice: String) {
        viewModelScope.launch {
            runCatching {
                repository.setChoice(decisionId, choice)
                load()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun replaceChoice(decisionId: Long, choice: String) {
        viewModelScope.launch {
            runCatching {
                repository.replaceChoice(decisionId, choice)
                load()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun replaceGrounds(decisionId: Long, sourceRevisionId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.replaceGrounds(decisionId, sourceRevisionId)
                load()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun reportOutcome(decisionId: Long, report: String) {
        viewModelScope.launch {
            runCatching {
                repository.recordOutcome(decisionId, report)
                load()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteOutcome(decisionId: Long, outcomeId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteOutcome(decisionId, outcomeId)
                load()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun delete(decisionId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteDecision(decisionId)
                load()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
