package com.echomind.domain.model

sealed class KnowledgeSearchResult {
    abstract val entryId: Long?
    abstract val snippet: String
    abstract val label: String

    data class RawRecord(
        val rawRecordId: Long,
        override val entryId: Long?,
        val text: String,
        val createdAt: Long
    ) : KnowledgeSearchResult() {
        override val snippet: String get() = text
        override val label: String get() = "Raw record"
    }

    data class Conclusion(
        val conclusionId: Long,
        val revisionId: Long,
        override val entryId: Long?,
        val text: String,
        val revisionVersion: Int,
        val createdAt: Long,
        val isCurrent: Boolean
    ) : KnowledgeSearchResult() {
        override val snippet: String get() = text
        override val label: String get() =
            "Conclusion · revision $revisionVersion" + if (isCurrent) "" else " · historical"
    }

    data class Theme(
        val themeId: Long,
        val text: String,
        val conclusionCount: Int
    ) : KnowledgeSearchResult() {
        override val entryId: Long? get() = null
        override val snippet: String get() = text
        override val label: String get() =
            if (conclusionCount == 1) "Theme · 1 conclusion" else "Theme · $conclusionCount conclusions"
    }
}
