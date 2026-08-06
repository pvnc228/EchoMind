package com.echomind.domain.model

data class Theme(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val archivedAt: Long? = null,
    val conclusionCount: Int = 0
)

data class ThemeConclusion(
    val themeId: Long,
    val conclusionText: String,
    val revisionVersion: Int
)

data class RelatedRecord(
    val rawRecordId: Long,
    val relationship: String,
    val sourceText: String,
    val recordedAt: Long
)

object Relationship {
    const val SUPPORTS = "supports"
    const val CONTRADICTS = "contradicts"
}
