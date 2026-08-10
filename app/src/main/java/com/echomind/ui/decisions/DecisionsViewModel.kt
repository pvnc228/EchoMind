package com.echomind.ui.decisions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.followup.FollowUpCoordinator
import com.echomind.data.followup.FollowUpRecord
import com.echomind.data.repository.DecisionRepository
import com.echomind.domain.model.Decision
import com.echomind.domain.model.DecisionSourceOption
import com.echomind.domain.model.OutcomeImpactReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DecisionsUiState(
    val decisions: List<Decision> = emptyList(),
    val sources: List<DecisionSourceOption> = emptyList(),
    val followUps: Map<Long, FollowUpRecord> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val followUpLoadingDecisionId: Long? = null,
    val followUpErrorDecisionId: Long? = null,
    val followUpError: String? = null,
    val impactDecisionId: Long? = null,
    val impactReview: OutcomeImpactReview? = null,
    val impactLoading: Boolean = false,
    val impactError: String? = null
)

@HiltViewModel
class DecisionsViewModel @Inject constructor(
    private val repository: DecisionRepository,
    private val followUpCoordinator: FollowUpCoordinator? = null
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
                val decisions = repository.getDecisions()
                val sources = repository.getDecisionSources()
                val followUps = followUpCoordinator?.getFor(decisions.map { it.id }).orEmpty()
                Triple(decisions, sources, followUps)
            }
                .onSuccess { (decisions, sources, followUps) ->
                    _uiState.value = DecisionsUiState(
                        decisions = decisions,
                        sources = sources,
                        followUps = followUps,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun reviewImpact(decisionId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                impactDecisionId = decisionId,
                impactReview = null,
                impactLoading = true,
                impactError = null
            )
            runCatching { repository.getOutcomeImpact(decisionId) }
                .onSuccess { review ->
                    _uiState.value = _uiState.value.copy(
                        impactReview = review,
                        impactLoading = false,
                        impactError = if (review == null) {
                            "This decision's grounds are no longer current; review the latest conclusion instead."
                        } else {
                            null
                        }
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        impactLoading = false,
                        impactError = e.message
                    )
                }
        }
    }

    fun dismissImpactReview() {
        _uiState.value = _uiState.value.copy(
            impactDecisionId = null,
            impactReview = null,
            impactLoading = false,
            impactError = null
        )
    }

    fun applyImpact(decisionId: Long, acceptedText: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(impactLoading = true, impactError = null)
            runCatching { repository.applyOutcomeImpact(decisionId, acceptedText) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        impactDecisionId = null,
                        impactReview = null,
                        impactLoading = false
                    )
                    load()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        impactLoading = false,
                        impactError = e.message
                    )
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

    fun scheduleFollowUp(decisionId: Long, days: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                followUpLoadingDecisionId = decisionId,
                followUpErrorDecisionId = null,
                followUpError = null
            )
            try {
                requireNotNull(followUpCoordinator) { "Follow-up service is unavailable." }
                    .schedule(decisionId, days)
                load()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showFollowUpError(decisionId, error, "Follow-up could not be scheduled.")
            }
        }
    }

    fun postponeFollowUp(decisionId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                followUpLoadingDecisionId = decisionId,
                followUpErrorDecisionId = null,
                followUpError = null
            )
            try {
                requireNotNull(followUpCoordinator) { "Follow-up service is unavailable." }
                    .postpone(decisionId)
                load()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showFollowUpError(decisionId, error, "Follow-up could not be postponed.")
            }
        }
    }

    fun cancelFollowUp(decisionId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                followUpLoadingDecisionId = decisionId,
                followUpErrorDecisionId = null,
                followUpError = null
            )
            try {
                requireNotNull(followUpCoordinator) { "Follow-up service is unavailable." }
                    .cancel(decisionId)
                load()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showFollowUpError(decisionId, error, "Follow-up could not be canceled.")
            }
        }
    }

    private suspend fun showFollowUpError(
        decisionId: Long,
        error: Exception,
        fallback: String
    ) {
        val refreshed = runCatching {
            followUpCoordinator?.getFor(listOf(decisionId)).orEmpty()
        }.getOrDefault(emptyMap())
        _uiState.value = _uiState.value.copy(
            followUps = _uiState.value.followUps + refreshed,
            followUpLoadingDecisionId = null,
            followUpErrorDecisionId = decisionId,
            followUpError = error.message ?: fallback
        )
    }
}
