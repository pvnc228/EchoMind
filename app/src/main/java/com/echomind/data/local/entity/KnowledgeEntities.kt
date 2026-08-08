package com.echomind.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_records",
    indices = [Index(value = ["legacy_entry_id"], unique = true)]
)
data class RawRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "legacy_entry_id")
    val legacyEntryId: Long? = null,
    @ColumnInfo(name = "original_text")
    val originalText: String,
    @ColumnInfo(name = "audio_path")
    val audioPath: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "ai_hypotheses",
    foreignKeys = [
        ForeignKey(
            entity = RawRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["raw_record_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("raw_record_id")]
)
data class AiHypothesisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "raw_record_id")
    val rawRecordId: Long,
    @ColumnInfo(name = "draft_json")
    val draftJson: String,
    @ColumnInfo(name = "counterargument")
    val counterargument: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "conclusions",
    foreignKeys = [
        ForeignKey(
            entity = RawRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["raw_record_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("raw_record_id")]
)
data class ConclusionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "raw_record_id")
    val rawRecordId: Long,
    @ColumnInfo(name = "current_revision_id")
    val currentRevisionId: Long?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "conclusion_revisions",
    foreignKeys = [
        ForeignKey(
            entity = ConclusionEntity::class,
            parentColumns = ["id"],
            childColumns = ["conclusion_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conclusion_id"),
        Index(value = ["conclusion_id", "version"], unique = true)
    ]
)
data class ConclusionRevisionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "conclusion_id")
    val conclusionId: Long,
    @ColumnInfo(name = "version")
    val version: Int,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "author")
    val author: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "evidence_links",
    foreignKeys = [
        ForeignKey(
            entity = ConclusionRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["conclusion_revision_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RawRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_raw_record_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("conclusion_revision_id"),
        Index("source_raw_record_id"),
        Index(
            name = "index_evidence_links_revision_source_unique",
            value = ["conclusion_revision_id", "source_raw_record_id"],
            unique = true
        )
    ]
)
data class EvidenceLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "conclusion_revision_id")
    val conclusionRevisionId: Long,
    @ColumnInfo(name = "source_raw_record_id")
    val sourceRawRecordId: Long,
    @ColumnInfo(name = "relationship")
    val relationship: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "origin")
    val origin: String = "user_confirmed",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "created_at_estimated")
    val createdAtEstimated: Boolean = false,
    @ColumnInfo(name = "review_metadata")
    val reviewMetadata: String? = null
)

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long? = null
)

@Entity(
    tableName = "theme_links",
    foreignKeys = [
        ForeignKey(
            entity = ThemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["theme_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ConclusionRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["conclusion_revision_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("theme_id"),
        Index("conclusion_revision_id"),
        Index(
            name = "index_theme_links_theme_revision_unique",
            value = ["theme_id", "conclusion_revision_id"],
            unique = true
        )
    ]
)
data class ThemeLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "theme_id")
    val themeId: Long,
    @ColumnInfo(name = "conclusion_revision_id")
    val conclusionRevisionId: Long,
    @ColumnInfo(name = "confirmed")
    val confirmed: Boolean,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "origin")
    val origin: String = "user_confirmed",
    @ColumnInfo(name = "review_required")
    val reviewRequired: Boolean = false
)

@Entity(
    tableName = "decisions",
    foreignKeys = [
        ForeignKey(
            entity = ConclusionRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_revision_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["source_revision_id"])]
)
data class DecisionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "question")
    val question: String,
    @ColumnInfo(name = "suggestion")
    val suggestion: String? = null,
    @ColumnInfo(name = "suggestion_author")
    val suggestionAuthor: String? = null,
    @ColumnInfo(name = "suggestion_source")
    val suggestionSource: String? = null,
    @ColumnInfo(name = "suggestion_status")
    val suggestionStatus: String? = null,
    @ColumnInfo(name = "choice")
    val choice: String? = null,
    @ColumnInfo(name = "source_revision_id")
    val sourceRevisionId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "outcomes",
    foreignKeys = [
        ForeignKey(
            entity = DecisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["decision_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("decision_id")]
)
data class OutcomeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "decision_id")
    val decisionId: Long,
    @ColumnInfo(name = "report")
    val report: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(tableName = "capture_drafts")
data class CaptureDraftEntity(
    @PrimaryKey
    val id: Long = 1L,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "encrypted_audio_path")
    val encryptedAudioPath: String? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,
    @ColumnInfo(name = "capture_stage")
    val captureStage: String = "CAPTURE",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(tableName = "home_card_dispositions")
data class HomeCardDispositionEntity(
    @PrimaryKey
    @ColumnInfo(name = "card_key")
    val cardKey: String,
    @ColumnInfo(name = "card_type")
    val cardType: String,
    @ColumnInfo(name = "scope_type")
    val scopeType: String,
    @ColumnInfo(name = "scope_id")
    val scopeId: Long,
    @ColumnInfo(name = "dismissed_at")
    val dismissedAt: Long? = null,
    @ColumnInfo(name = "postponed_until")
    val postponedUntil: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
