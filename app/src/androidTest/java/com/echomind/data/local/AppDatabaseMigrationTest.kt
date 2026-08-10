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
            .addMigrations(
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8
            )
            .allowMainThreadQueries()
            .build()

        try {
            val rawRecords = runBlocking { database.knowledgeDao().getAllRawRecords() }

            assertEquals(1, rawRecords.size)
            assertEquals(42L, rawRecords.single().id)
            assertEquals(42L, rawRecords.single().legacyEntryId)
            assertEquals("КАРЬЕРА", rawRecords.single().originalText)
            assertEquals("карьера", rawRecords.single().originalTextSearchKey)
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
                database.knowledgeDao(),
                context
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
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8
            )
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

    @Test
    fun migration4To5AddsDecisionAndOutcomeTablesWithoutLosingProvenance() {
        createVersion4DatabaseWithProvenance()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .addMigrations(
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8
            )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = migrated.knowledgeDao()
            assertEquals(1, runBlocking { dao.getAllRawRecords() }.size)
            assertEquals(1, runBlocking { dao.getAllRevisions() }.size)
            assertTrue(runBlocking { dao.getAllDecisions() }.isEmpty())
            assertTrue(runBlocking { dao.getAllOutcomes() }.isEmpty())

            val revisionId = runBlocking { dao.getAllRevisions().single().id }
            val decisionId = runBlocking {
                dao.insertDecision(
                    com.echomind.data.local.entity.DecisionEntity(
                        question = "Should I?",
                        suggestion = null,
                        choice = null,
                        sourceRevisionId = revisionId,
                        createdAt = 40
                    )
                )
            }
            runBlocking {
                dao.insertOutcome(
                    com.echomind.data.local.entity.OutcomeEntity(
                        decisionId = decisionId,
                        report = "It worked",
                        createdAt = 41
                    )
                )
            }
            assertEquals(1, runBlocking { dao.getAllDecisions() }.size)
            assertEquals(1, runBlocking { dao.getOutcomesForDecision(decisionId) }.size)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun schema6AddsImmutableLinkMetadataDraftDispositionAndDecisionForeignKey() {
        val database = inMemoryDatabase()
        try {
            val sqlite = database.openHelper.writableDatabase

            assertTableExists(sqlite, "capture_drafts")
            assertTableExists(sqlite, "home_card_dispositions")
            assertColumnExists(sqlite, "evidence_links", "origin")
            assertColumnExists(sqlite, "evidence_links", "created_at")
            assertColumnExists(sqlite, "evidence_links", "created_at_estimated")
            assertColumnExists(sqlite, "evidence_links", "review_metadata")
            assertColumnExists(sqlite, "theme_links", "origin")
            assertColumnExists(sqlite, "theme_links", "review_required")
            assertIndexExists(sqlite, "evidence_links", "index_evidence_links_revision_source_unique")
            assertIndexExists(sqlite, "theme_links", "index_theme_links_theme_revision_unique")

            var decisionReferencesRevision = false
            sqlite.query("PRAGMA foreign_key_list(`decisions`)").use { cursor ->
                val tableColumn = cursor.getColumnIndexOrThrow("table")
                while (cursor.moveToNext()) {
                    if (cursor.getString(tableColumn) == "conclusion_revisions") {
                        decisionReferencesRevision = true
                    }
                }
            }
            assertTrue(decisionReferencesRevision)
        } finally {
            database.close()
        }
    }

    @Test
    fun migration5To6DeterministicallyNormalizesDuplicateLinks() {
        createVersion5DatabaseWithDuplicateLinks()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DATABASE)
            .addMigrations(
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8
            )
            .allowMainThreadQueries()
            .build()
        try {
            val dao = migrated.knowledgeDao()
            val evidence = runBlocking { dao.getAllEvidenceLinks() }
            val themes = runBlocking { dao.getConfirmedThemeLinksAll() }

            assertEquals(2, evidence.size)
            val conflict = evidence.single { it.sourceRawRecordId == 9L }
            assertEquals(31L, conflict.id)
            assertEquals("needs_review", conflict.status)
            assertEquals("legacy_rebase_unknown", conflict.origin)
            assertTrue(conflict.reviewMetadata!!.contains("30|supports|confirmed"))
            assertTrue(conflict.reviewMetadata!!.contains("31|contradicts|confirmed"))

            val statusConflict = evidence.single { it.sourceRawRecordId == 6L }
            assertEquals(32L, statusConflict.id)
            assertEquals("confirmed", statusConflict.status)
            assertEquals("user_confirmed", statusConflict.origin)
            assertEquals(1, themes.size)
            assertEquals(40L, themes.single().id)
        } finally {
            migrated.close()
        }
    }

    private fun assertTableExists(db: SupportSQLiteDatabase, table: String) {
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { cursor -> assertTrue(cursor.moveToFirst()) }
    }

    private fun assertColumnExists(db: SupportSQLiteDatabase, table: String, column: String) {
        var found = false
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == column) found = true
            }
        }
        assertTrue("Missing $table.$column", found)
    }

    private fun assertIndexExists(db: SupportSQLiteDatabase, table: String, index: String) {
        var found = false
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == index) found = true
            }
        }
        assertTrue("Missing index $index on $table", found)
    }

    private fun createVersion5DatabaseWithDuplicateLinks() {
        createVersion4DatabaseWithProvenance()
        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                AppDatabase.MIGRATION_4_5.migrate(db)
            }
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO raw_records " +
                "(id, legacy_entry_id, original_text, audio_path, duration_ms, created_at) " +
                "VALUES (9, NULL, 'External source', NULL, 0, 20)"
        )
        db.execSQL("INSERT INTO themes (id, name, created_at, archived_at) VALUES (20, 'Theme', 20, NULL)")
        db.execSQL(
            "INSERT INTO evidence_links " +
                "(id, conclusion_revision_id, source_raw_record_id, relationship, status) " +
                "VALUES (30, 8, 9, 'supports', 'confirmed')"
        )
        db.execSQL(
            "INSERT INTO evidence_links " +
                "(id, conclusion_revision_id, source_raw_record_id, relationship, status) " +
                "VALUES (31, 8, 9, 'contradicts', 'confirmed')"
        )
        db.execSQL(
            "INSERT INTO evidence_links " +
                "(id, conclusion_revision_id, source_raw_record_id, relationship, status) " +
                "VALUES (32, 8, 6, 'supports', 'confirmed')"
        )
        db.execSQL(
            "INSERT INTO evidence_links " +
                "(id, conclusion_revision_id, source_raw_record_id, relationship, status) " +
                "VALUES (33, 8, 6, 'supports', 'proposed')"
        )
        db.execSQL(
            "INSERT INTO theme_links " +
                "(id, theme_id, conclusion_revision_id, confirmed, created_at) " +
                "VALUES (40, 20, 8, 1, 20)"
        )
        db.execSQL(
            "INSERT INTO theme_links " +
                "(id, theme_id, conclusion_revision_id, confirmed, created_at) " +
                "VALUES (41, 20, 8, 1, 21)"
        )
        helper.close()
    }

    private fun createVersion4DatabaseWithProvenance() {
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
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
                "КАРЬЕРА",
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
