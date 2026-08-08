package com.echomind.domain.model

data class Theme(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val archivedAt: Long? = null,
    val conclusionCount: Int = 0
)

data class PendingThemeLink(
    val linkId: Long,
    val themeId: Long,
    val themeName: String,
    val revisionId: Long
)

data class ThemeConclusion(
    val themeId: Long,
    val conclusionText: String,
    val revisionVersion: Int,
    val revisionId: Long
)

data class RelatedRecord(
    val rawRecordId: Long,
    val relationship: String,
    val sourceText: String,
    val recordedAt: Long,
    val suggestedReason: String? = null,
    val score: Int = 0,
    val linkId: Long = 0L,
    val status: String = "confirmed"
)

object Relationship {
    const val SUPPORTS = "supports"
    const val CONTRADICTS = "contradicts"
}
