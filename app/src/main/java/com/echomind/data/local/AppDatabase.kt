package com.echomind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.echomind.data.local.converter.Converters
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.CaptureDraftEntity
import com.echomind.data.local.entity.DecisionEntity
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.OutcomeEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.local.entity.ThemeEntity
import com.echomind.data.local.entity.ThemeLinkEntity
import com.echomind.data.local.entity.HomeCardDispositionEntity
import com.echomind.data.local.entity.AudioCleanupEntity

@Database(
    entities = [
        EntryEntity::class,
        RawRecordEntity::class,
        AiHypothesisEntity::class,
        ConclusionEntity::class,
        ConclusionRevisionEntity::class,
        EvidenceLinkEntity::class,
        ThemeEntity::class,
        ThemeLinkEntity::class,
        DecisionEntity::class,
        OutcomeEntity::class,
        CaptureDraftEntity::class,
        HomeCardDispositionEntity::class,
        AudioCleanupEntity::class
    ],
    version = 7,
    exportSchema = false
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `raw_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `legacy_entry_id` INTEGER,
                        `original_text` TEXT NOT NULL,
                        `audio_path` TEXT,
                        `duration_ms` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_records_legacy_entry_id` " +
                        "ON `raw_records` (`legacy_entry_id`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_hypotheses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `raw_record_id` INTEGER NOT NULL,
                        `draft_json` TEXT NOT NULL,
                        `counterargument` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`raw_record_id`) REFERENCES `raw_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_hypotheses_raw_record_id` " +
                        "ON `ai_hypotheses` (`raw_record_id`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conclusions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `raw_record_id` INTEGER NOT NULL,
                        `current_revision_id` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`raw_record_id`) REFERENCES `raw_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conclusions_raw_record_id` " +
                        "ON `conclusions` (`raw_record_id`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conclusion_revisions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conclusion_id` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`conclusion_id`) REFERENCES `conclusions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conclusion_revisions_conclusion_id` " +
                        "ON `conclusion_revisions` (`conclusion_id`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_conclusion_revisions_conclusion_id_version` " +
                        "ON `conclusion_revisions` (`conclusion_id`, `version`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `evidence_links` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conclusion_revision_id` INTEGER NOT NULL,
                        `source_raw_record_id` INTEGER NOT NULL,
                        `relationship` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        FOREIGN KEY(`conclusion_revision_id`)
                            REFERENCES `conclusion_revisions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`source_raw_record_id`) REFERENCES `raw_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_evidence_links_conclusion_revision_id` " +
                        "ON `evidence_links` (`conclusion_revision_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_evidence_links_source_raw_record_id` " +
                        "ON `evidence_links` (`source_raw_record_id`)"
                )
                db.execSQL(
                    """
                    INSERT INTO `raw_records` (
                        `id`,
                        `legacy_entry_id`,
                        `original_text`,
                        `audio_path`,
                        `duration_ms`,
                        `created_at`
                    )
                    SELECT
                        `id`,
                        `id`,
                        `transcript`,
                        `audio_path`,
                        `duration_ms`,
                        `created_at`
                    FROM `entries`
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `themes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `archived_at` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `theme_links` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `theme_id` INTEGER NOT NULL,
                        `conclusion_revision_id` INTEGER NOT NULL,
                        `confirmed` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`theme_id`) REFERENCES `themes`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`conclusion_revision_id`)
                            REFERENCES `conclusion_revisions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_theme_links_theme_id` " +
                        "ON `theme_links` (`theme_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_theme_links_conclusion_revision_id` " +
                        "ON `theme_links` (`conclusion_revision_id`)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `decisions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `question` TEXT NOT NULL,
                        `suggestion` TEXT,
                        `suggestion_author` TEXT,
                        `suggestion_source` TEXT,
                        `suggestion_status` TEXT,
                        `choice` TEXT,
                        `source_revision_id` INTEGER,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_decisions_source_revision_id` " +
                        "ON `decisions` (`source_revision_id`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `outcomes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `decision_id` INTEGER NOT NULL,
                        `report` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`decision_id`) REFERENCES `decisions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_outcomes_decision_id` " +
                        "ON `outcomes` (`decision_id`)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                data class LegacyEvidence(
                    val id: Long,
                    val revisionId: Long,
                    val sourceId: Long,
                    val relationship: String,
                    val status: String
                )
                data class LegacyThemeLink(
                    val id: Long,
                    val themeId: Long,
                    val revisionId: Long,
                    val confirmed: Boolean,
                    val createdAt: Long
                )

                val revisionCreatedAt = mutableMapOf<Long, Long>()
                db.query("SELECT id, created_at FROM conclusion_revisions").use { cursor ->
                    while (cursor.moveToNext()) {
                        revisionCreatedAt[cursor.getLong(0)] = cursor.getLong(1)
                    }
                }

                val orphanDecisionCount = db.query(
                    "SELECT COUNT(*) FROM decisions d " +
                        "WHERE d.source_revision_id IS NOT NULL " +
                        "AND NOT EXISTS (SELECT 1 FROM conclusion_revisions r " +
                        "WHERE r.id = d.source_revision_id)"
                ).use { cursor ->
                    cursor.moveToFirst()
                    cursor.getLong(0)
                }
                check(orphanDecisionCount == 0L) {
                    "MIGRATION_5_6 blocked: decisions contain dangling source revisions."
                }

                val evidenceRows = buildList {
                    db.query(
                        "SELECT id, conclusion_revision_id, source_raw_record_id, relationship, status " +
                            "FROM evidence_links ORDER BY id"
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            add(
                                LegacyEvidence(
                                    id = cursor.getLong(0),
                                    revisionId = cursor.getLong(1),
                                    sourceId = cursor.getLong(2),
                                    relationship = cursor.getString(3),
                                    status = cursor.getString(4)
                                )
                            )
                        }
                    }
                }
                val themeRows = buildList {
                    db.query(
                        "SELECT id, theme_id, conclusion_revision_id, confirmed, created_at " +
                            "FROM theme_links ORDER BY id"
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            add(
                                LegacyThemeLink(
                                    id = cursor.getLong(0),
                                    themeId = cursor.getLong(1),
                                    revisionId = cursor.getLong(2),
                                    confirmed = cursor.getInt(3) != 0,
                                    createdAt = cursor.getLong(4)
                                )
                            )
                        }
                    }
                }

                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL(
                    """
                    CREATE TABLE `evidence_links_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conclusion_revision_id` INTEGER NOT NULL,
                        `source_raw_record_id` INTEGER NOT NULL,
                        `relationship` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `origin` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `created_at_estimated` INTEGER NOT NULL,
                        `review_metadata` TEXT,
                        FOREIGN KEY(`conclusion_revision_id`) REFERENCES `conclusion_revisions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`source_raw_record_id`) REFERENCES `raw_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                evidenceRows.groupBy { it.revisionId to it.sourceId }.values.forEach { rows ->
                    val relationships = rows.map { it.relationship }.toSet()
                    val conflicting = relationships.size > 1
                    val chosen = if (conflicting) {
                        rows.maxBy { it.id }
                    } else {
                        rows.filter { it.status == "confirmed" }.minByOrNull { it.id }
                            ?: rows.minBy { it.id }
                    }
                    val status = if (conflicting) "needs_review" else chosen.status
                    val origin = if (conflicting) {
                        "legacy_rebase_unknown"
                    } else if (status == "confirmed") {
                        "user_confirmed"
                    } else {
                        "legacy_pending"
                    }
                    val metadata = if (conflicting) {
                        rows.sortedBy { it.id }.joinToString(";") {
                            "${it.id}|${it.relationship}|${it.status}"
                        }
                    } else {
                        null
                    }
                    db.execSQL(
                        "INSERT INTO evidence_links_new " +
                            "(id, conclusion_revision_id, source_raw_record_id, relationship, status, " +
                            "origin, created_at, created_at_estimated, review_metadata) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(
                            chosen.id,
                            chosen.revisionId,
                            chosen.sourceId,
                            chosen.relationship,
                            status,
                            origin,
                            revisionCreatedAt[chosen.revisionId] ?: 0L,
                            1,
                            metadata
                        )
                    )
                }
                db.execSQL("DROP TABLE evidence_links")
                db.execSQL("ALTER TABLE evidence_links_new RENAME TO evidence_links")
                db.execSQL(
                    "CREATE INDEX `index_evidence_links_conclusion_revision_id` " +
                        "ON `evidence_links` (`conclusion_revision_id`)"
                )
                db.execSQL(
                    "CREATE INDEX `index_evidence_links_source_raw_record_id` " +
                        "ON `evidence_links` (`source_raw_record_id`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_evidence_links_revision_source_unique` " +
                        "ON `evidence_links` (`conclusion_revision_id`, `source_raw_record_id`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE `theme_links_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `theme_id` INTEGER NOT NULL,
                        `conclusion_revision_id` INTEGER NOT NULL,
                        `confirmed` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `origin` TEXT NOT NULL,
                        `review_required` INTEGER NOT NULL,
                        FOREIGN KEY(`theme_id`) REFERENCES `themes`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`conclusion_revision_id`) REFERENCES `conclusion_revisions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                themeRows.groupBy { it.themeId to it.revisionId }.values.forEach { rows ->
                    val chosen = rows.filter { it.confirmed }.minByOrNull { it.id }
                        ?: rows.minBy { it.id }
                    db.execSQL(
                        "INSERT INTO theme_links_new " +
                            "(id, theme_id, conclusion_revision_id, confirmed, created_at, origin, review_required) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(
                            chosen.id,
                            chosen.themeId,
                            chosen.revisionId,
                            if (chosen.confirmed) 1 else 0,
                            rows.minOf { it.createdAt },
                            if (chosen.confirmed) "user_confirmed" else "legacy_pending",
                            0
                        )
                    )
                }
                db.execSQL("DROP TABLE theme_links")
                db.execSQL("ALTER TABLE theme_links_new RENAME TO theme_links")
                db.execSQL("CREATE INDEX `index_theme_links_theme_id` ON `theme_links` (`theme_id`)")
                db.execSQL(
                    "CREATE INDEX `index_theme_links_conclusion_revision_id` " +
                        "ON `theme_links` (`conclusion_revision_id`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_theme_links_theme_revision_unique` " +
                        "ON `theme_links` (`theme_id`, `conclusion_revision_id`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE `decisions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `question` TEXT NOT NULL,
                        `suggestion` TEXT,
                        `suggestion_author` TEXT,
                        `suggestion_source` TEXT,
                        `suggestion_status` TEXT,
                        `choice` TEXT,
                        `source_revision_id` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`source_revision_id`) REFERENCES `conclusion_revisions`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO decisions_new " +
                        "(id, question, suggestion, suggestion_author, suggestion_source, suggestion_status, " +
                        "choice, source_revision_id, created_at) " +
                        "SELECT id, question, suggestion, " +
                        "CASE WHEN suggestion IS NULL THEN NULL ELSE 'legacy_unknown' END, " +
                        "CASE WHEN suggestion IS NULL THEN NULL ELSE 'legacy_data' END, " +
                        "CASE WHEN suggestion IS NULL THEN NULL ELSE 'needs_review' END, " +
                        "choice, source_revision_id, created_at FROM decisions"
                )
                db.execSQL("ALTER TABLE decisions RENAME TO decisions_old")
                db.execSQL("ALTER TABLE decisions_new RENAME TO decisions")
                db.execSQL(
                    """
                    CREATE TABLE `outcomes_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `decision_id` INTEGER NOT NULL,
                        `report` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`decision_id`) REFERENCES `decisions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO outcomes_new (id, decision_id, report, created_at) " +
                        "SELECT id, decision_id, report, created_at FROM outcomes"
                )
                db.execSQL("DROP TABLE outcomes")
                db.execSQL("DROP TABLE decisions_old")
                db.execSQL("ALTER TABLE outcomes_new RENAME TO outcomes")
                db.execSQL("CREATE INDEX `index_decisions_source_revision_id` ON `decisions` (`source_revision_id`)")
                db.execSQL("CREATE INDEX `index_outcomes_decision_id` ON `outcomes` (`decision_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `capture_drafts` (
                        `id` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `encrypted_audio_path` TEXT,
                        `duration_ms` INTEGER NOT NULL,
                        `capture_stage` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `home_card_dispositions` (
                        `card_key` TEXT NOT NULL,
                        `card_type` TEXT NOT NULL,
                        `scope_type` TEXT NOT NULL,
                        `scope_id` INTEGER NOT NULL,
                        `dismissed_at` INTEGER,
                        `postponed_until` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`card_key`)
                    )
                    """.trimIndent()
                )
                db.execSQL("PRAGMA foreign_keys=ON")
                db.query("PRAGMA foreign_key_check").use { cursor ->
                    check(!cursor.moveToFirst()) {
                        "MIGRATION_5_6 produced a foreign-key violation."
                    }
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audio_cleanup_queue` (
                        `path` TEXT NOT NULL,
                        `entry_id` INTEGER NOT NULL,
                        `failed_at` INTEGER NOT NULL,
                        `attempt_count` INTEGER NOT NULL,
                        PRIMARY KEY(`path`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
