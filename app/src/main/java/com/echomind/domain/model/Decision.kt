package com.echomind.domain.model

data class Decision(
    val id: Long,
    val question: String,
    val suggestion: String? = null,
    val choice: String? = null,
    val sourceRevisionId: Long? = null,
    val sourceConclusionText: String? = null,
    val createdAt: Long,
    val outcomes: List<DecisionOutcome> = emptyList()
) {
    val hasOutcome: Boolean get() = outcomes.isNotEmpty()
    val isDecided: Boolean get() = choice != null
}

data class DecisionOutcome(
    val id: Long,
    val decisionId: Long,
    val report: String,
    val createdAt: Long
)
