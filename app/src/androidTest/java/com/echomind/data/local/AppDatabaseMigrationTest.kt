package com.echomind.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.repository.EntryRepository
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppDatabaseMigrationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration2To3PreservesRawDataWithoutConfirmingLegacyAnalysis() {
        createVersion2Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            val rawRecords = runBlocking { database.knowledgeDao().getAllRawRecords() }

            assertEquals(1, rawRecords.size)
            assertEquals(42L, rawRecords.single().id)
            assertEquals(42L, rawRecords.single().legacyEntryId)
            assertEquals("Original thought", rawRecords.single().originalText)
            assertTrue(runBlocking { database.knowledgeDao().getAllHypotheses() }.isEmpty())
            assertTrue(runBlocking { database.knowledgeDao().getAllConclusions() }.isEmpty())
            assertTrue(runBlocking { database.knowledgeDao().getAllThemes() }.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun graphDeletionKeepsRawSourceUntilConclusionIsRemoved() {
        val database = inMemoryDatabase()

        try {
            val dao = database.knowledgeDao()
            runBlocking {
                val rawId = dao.insertRawRecord(
                    RawRecordEntity(
                        legacyEntryId = 7,
                        originalText = "Source",
                        audioPath = null,
                        durationMs = 0,
                        createdAt = 1
                    )
                )
                dao.insertHypothesis(
                    AiHypothesisEntity(
                        rawRecordId = rawId,
                        draftJson = "{}",
                        counterargument = "",
                        status = "proposed",
                        createdAt = 2
                    )
                )
                val conclusionId = dao.insertConclusion(
                    ConclusionEntity(
                        rawRecordId = rawId,
                        currentRevisionId = null,
                        createdAt = 3
                    )
                )
                val revisionId = dao.insertRevision(
                    ConclusionRevisionEntity(
                        conclusionId = conclusionId,
                        version = 1,
                        text = "Confirmed",
                        author = "user",
                        createdAt = 4
                    )
                )
                dao.insertEvidenceLink(
                    EvidenceLinkEntity(
                        conclusionRevisionId = revisionId,
                        sourceRawRecordId = rawId,
                        relationship = "supports",
                        status = "confirmed"
                    )
                )

                var deletionBlocked = false
                try {
                    dao.deleteRawRecordByLegacyEntryId(7)
                } catch (_: SQLiteConstraintException) {
                    deletionBlocked = true
                }
                assertTrue(deletionBlocked)

                dao.deleteConclusion(
                    ConclusionEntity(conclusionId, rawId, revisionId, 3)
                )

                assertTrue(dao.getAllRevisions().isEmpty())
                assertTrue(dao.getAllEvidenceLinks().isEmpty())
                assertEquals(1, dao.getAllRawRecords().size)

                dao.deleteRawRecordByLegacyEntryId(7)
                assertTrue(dao.getAllRawRecords().isEmpty())
                assertTrue(dao.getAllHypotheses().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun entryRepositoryWritesAndDeletesItsRawRecordInOneDatabase() {
        val database = inMemoryDatabase()

        try {
            val repository = EntryRepository(
                database,
                database.entryDao(),
                database.knowledgeDao()
            )
            runBlocking {
                repository.saveEntry(
                    Entry(
                        transcript = "New thought",
                        audioPath = null,
                        durationMs = 0,
                        createdAt = 5,
                        category = EntryCategory.GENERAL,
                        tags = emptyList(),
                        summary = "Legacy analysis",
                        tasks = emptyList(),
                        ideas = emptyList(),
                        emotions = emptyList()
                    )
                )

                val rawRecord = database.knowledgeDao().getAllRawRecords().single()
                assertEquals("New thought", rawRecord.originalText)
                assertTrue(database.knowledgeDao().getAllConclusions().isEmpty())

                repository.deleteEntry(rawRecord.legacyEntryId!!)
                assertTrue(database.entryDao().getAllEntriesOnce().isEmpty())
                assertTrue(database.knowledgeDao().getAllRawRecords().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migration3To4AddsThemeTablesWithoutLosingProvenance() {
        val database = createVersion3DatabaseWithProvenance()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = migrated.knowledgeDao()
            val rawRecords = runBlocking { dao.getAllRawRecords() }
            assertEquals(1, rawRecords.size)
            assertEquals("Source text", rawRecords.single().originalText)
            assertEquals(1, runBlocking { dao.getAllRevisions() }.size)
            assertTrue(runBlocking { dao.getAllThemes() }.isEmpty())
            assertTrue(runBlocking { dao.getConfirmedThemeLinksAll() }.isEmpty())

            val themeId = runBlocking { dao.insertTheme(
                com.echomind.data.local.entity.ThemeEntity(
                    name = "Test Theme", createdAt = 30
                )
            ) }
            val revisionId = runBlocking { dao.getAllRevisions().single().id }
            runBlocking { dao.insertThemeLink(
                com.echomind.data.local.entity.ThemeLinkEntity(
                    themeId = themeId,
                    conclusionRevisionId = revisionId,
                    confirmed = true,
                    createdAt = 31
                )
            ) }
            assertEquals(1, runBlocking { dao.getConfirmedLinksForTheme(themeId) }.size)
        } finally {
            migrated.close()
        }
    }

    private fun createVersion3DatabaseWithProvenance() {
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_ENTRIES)
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
                    "INSERT INTO `entries` (" +
                        "`id`,`transcript`,`audio_path`,`duration_ms`,`created_at`," +
                        "`category`,`tags`,`summary`,`tasks`,`ideas`,`emotions`" +
                        ") VALUES (5,'Source text',NULL,0,10,'idea','[]','', '[]','[]','[]')"
                )
                db.execSQL(
                    "INSERT INTO `raw_records` (" +
                        "`id`,`legacy_entry_id`,`original_text`,`audio_path`,`duration_ms`,`created_at`" +
                        ") VALUES (6,5,'Source text',NULL,0,10)"
                )
                db.execSQL(
                    "INSERT INTO `conclusions` (" +
                        "`id`,`raw_record_id`,`current_revision_id`,`created_at`" +
                        ") VALUES (7,6,8,11)"
                )
                db.execSQL(
                    "INSERT INTO `conclusion_revisions` (" +
                        "`id`,`conclusion_id`,`version`,`text`,`author`,`created_at`" +
                        ") VALUES (8,7,1,'Confirmed','user',12)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        helper.writableDatabase
        helper.close()
    }

    private fun inMemoryDatabase() =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun createVersion2Database() {
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_ENTRIES)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = helper.writableDatabase
        database.execSQL(
            """
            INSERT INTO entries (
                id, transcript, audio_path, duration_ms, created_at, category,
                tags, summary, tasks, ideas, emotions
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                42,
                "Original thought",
                "entry_42.m4a.enc",
                1_000,
                2_000,
                "idea",
                "[]",
                "Generated summary",
                "[]",
                "[]",
                "[]"
            )
        )
        helper.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-2-3-test.db"
        const val CREATE_ENTRIES =
            "CREATE TABLE IF NOT EXISTS `entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`transcript` TEXT NOT NULL, " +
                "`audio_path` TEXT, " +
                "`duration_ms` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`category` TEXT NOT NULL, " +
                "`tags` TEXT NOT NULL, " +
                "`summary` TEXT NOT NULL, " +
                "`tasks` TEXT NOT NULL, " +
                "`ideas` TEXT NOT NULL, " +
                "`emotions` TEXT NOT NULL)"
    }
}
