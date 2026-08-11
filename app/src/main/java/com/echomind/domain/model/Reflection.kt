package com.echomind.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReflectionDraft(
    val tentativeThesis: String,
    val observations: List<String>,
    val interpretations: List<String>,
    val assumptions: List<String>,
    val openQuestions: List<String>
) {
    fun suggestedConclusion(): String =
        tentativeThesis.ifBlank { interpretations.firstOrNull().orEmpty() }
}

data class ReflectionSession(
    val rawRecordId: Long,
    val hypothesisId: Long,
    val originalText: String,
    val draft: ReflectionDraft,
    val counterargument: String,
    val status: String,
    val confirmedConclusion: String? = null,
    val revisionVersion: Int? = null,
    val revisionId: Long? = null,
    val sourceRelationship: String? = null,
    val sourceLinkStatus: String? = null,
    val parentHypothesisId: Long? = null,
    val followUpQuestion: String? = null
)

data class CaptureDraft(
    val id: Long = 1L,
    val text: String,
    val encryptedAudioPath: String? = null,
    val durationMs: Long = 0L,
    val captureStage: String = "CAPTURE",
    val createdAt: Long,
    val updatedAt: Long
)

object ReflectionStatus {
    const val PROPOSED = "proposed"
    const val CONFIRMED = "confirmed"
    const val REJECTED = "rejected"
}
