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
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.RawRecordEntity

@Database(
    entities = [
        EntryEntity::class,
        RawRecordEntity::class,
        AiHypothesisEntity::class,
        ConclusionEntity::class,
        ConclusionRevisionEntity::class,
        EvidenceLinkEntity::class
    ],
    version = 3,
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
    }
}
