package com.echomind.domain.model

data class Decision(
    val id: Long,
    val question: String,
    val suggestion: String? = null,
    val suggestionAuthor: String? = null,
    val suggestionSource: String? = null,
    val suggestionStatus: String? = null,
    val choice: String? = null,
    val sourceRevisionId: Long? = null,
    val sourceConclusionText: String? = null,
    val createdAt: Long,
    val outcomes: List<DecisionOutcome> = emptyList()
) {
    val hasOutcome: Boolean get() = outcomes.isNotEmpty()
    val isDecided: Boolean get() = choice != null
    val state: DecisionState
        get() = when {
            choice.isNullOrBlank() -> DecisionState.CREATED
            outcomes.isEmpty() -> DecisionState.CHOSEN
            else -> DecisionState.OUTCOME_REPORTED
        }
}

enum class DecisionState { CREATED, CHOSEN, OUTCOME_REPORTED }

data class DecisionOutcome(
    val id: Long,
    val decisionId: Long,
    val report: String,
    val createdAt: Long
)

data class OutcomeImpactReview(
    val decisionId: Long,
    val sourceRevisionId: Long,
    val originalText: String,
    val choice: String,
    val outcomes: List<String>,
    val proposedText: String
)

data class DecisionSourceOption(
    val revisionId: Long,
    val version: Int,
    val text: String
)
