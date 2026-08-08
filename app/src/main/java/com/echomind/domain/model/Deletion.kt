package com.echomind.domain.model

data class EvidenceDeletionDependency(
    val linkId: Long,
    val revisionId: Long,
    val relationship: String
)

data class DecisionDeletionDependency(
    val decisionId: Long,
    val sourceRevisionId: Long,
    val outcomeCount: Int
)

data class EntryDeletionPlan(
    val entryId: Long,
    val rawRecordId: Long,
    val ownConclusionId: Long?,
    val revisionIds: List<Long>,
    val incomingEvidence: List<EvidenceDeletionDependency>,
    val decisions: List<DecisionDeletionDependency>,
    val audioPath: String?
) {
    val requiresExplicitChoice: Boolean
        get() = ownConclusionId != null || incomingEvidence.isNotEmpty() || decisions.isNotEmpty()
}

data class EntryDeletionChoice(
    val deleteOwnConclusion: Boolean = false,
    val unlinkIncomingEvidenceLinkIds: Set<Long> = emptySet(),
    val deleteDecisionIds: Set<Long> = emptySet()
)
