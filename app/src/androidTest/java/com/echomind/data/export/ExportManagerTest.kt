package com.echomind.data.export

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.local.entity.DecisionEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.ThemeLinkEntity
import com.echomind.data.local.entity.ThemeEntity
import com.echomind.data.repository.DecisionRepository
import com.echomind.data.repository.ReflectionRepository
import com.echomind.domain.model.Relationship
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportManagerTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun zipContainsTheConfirmedReflectionAndItsStableProvenance() {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var exportFile: java.io.File? = null

        try {
            val repository = ReflectionRepository(
                database = database,
                entryDao = database.entryDao(),
                knowledgeDao = database.knowledgeDao(),
                analyzer = LocalReflectionAnalyzer(),
                json = Json { ignoreUnknownKeys = true }
            )
            runBlocking {
                val rawRecordId = repository.captureRawText("My original words")
                val proposal = repository.createLocalProposal(rawRecordId)
                repository.confirm(proposal.hypothesisId, "My confirmed conclusion")

                exportFile = ExportManager(
                    context = context,
                    database = database,
                    entryDao = database.entryDao(),
                    knowledgeDao = database.knowledgeDao(),
                    audioEncryptionUtil = AudioEncryptionUtil(context)
                ).exportToZip().getOrThrow()
            }

            val manifest = ZipFile(requireNotNull(exportFile)).use { zip ->
                val entry = requireNotNull(zip.getEntry("manifest.json"))
                val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Json.decodeFromString<ExportManifest>(content)
            }

            assertEquals(5, manifest.version)
            assertEquals("My original words", manifest.rawRecords.single().originalText)
            assertEquals("confirmed", manifest.hypotheses.single().status)
            assertEquals("My confirmed conclusion", manifest.revisions.single().text)
            assertEquals(
                manifest.rawRecords.single().id,
                manifest.evidenceLinks.single().sourceRawRecordId
            )
            assertTrue(manifest.entries.single().analysisStatus == "legacy_unconfirmed")
        } finally {
            database.close()
            exportFile?.delete()
        }
    }

    @Test
    fun emptyProfileRestoreRoundTripsStableGraphAndRejectsNonEmptyTarget() {
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var exportFile: java.io.File? = null
        try {
            val sourceRepository = ReflectionRepository(
                database = source,
                entryDao = source.entryDao(),
                knowledgeDao = source.knowledgeDao(),
                analyzer = LocalReflectionAnalyzer(),
                json = Json { ignoreUnknownKeys = true }
            )
            runBlocking {
                val rawId = sourceRepository.captureRawText("Round-trip source")
                val proposal = sourceRepository.createLocalProposal(rawId)
                sourceRepository.confirm(proposal.hypothesisId, "Round-trip conclusion")
                exportFile = ExportManager(
                    context,
                    source,
                    source.entryDao(),
                    source.knowledgeDao(),
                    AudioEncryptionUtil(context)
                ).exportToZip().getOrThrow()

                val originalManifest = ZipFile(requireNotNull(exportFile)).use { zip ->
                    val entry = requireNotNull(zip.getEntry("manifest.json"))
                    Json.decodeFromString<ExportManifest>(
                        zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    )
                }

                val restore = ExportManager(
                    context,
                    target,
                    target.entryDao(),
                    target.knowledgeDao(),
                    AudioEncryptionUtil(context)
                )
                val corruptHash = File(context.cacheDir, "echomind-corrupt-hash.zip")
                rewriteArchive(
                    requireNotNull(exportFile),
                    corruptHash,
                    originalManifest.copy(
                        files = listOf(
                            ExportFile(
                                name = "audio/payload.wav",
                                size = 1L,
                                sha256 = "00".repeat(32)
                            )
                        )
                    ),
                    extraEntries = mapOf("audio/payload.wav" to byteArrayOf(1))
                )
                val corruptFailure = restore.restoreFromZip(corruptHash)
                assertTrue(corruptFailure.isFailure)
                assertTrue(corruptFailure.exceptionOrNull()?.message != null)
                assertTrue(target.knowledgeDao().getAllRawRecords().isEmpty())

                val traversal = File(context.cacheDir, "echomind-path-traversal.zip")
                rewriteArchive(
                    requireNotNull(exportFile),
                    traversal,
                    originalManifest.copy(
                        files = listOf(ExportFile("../escape", 0L, ""))
                    ),
                    extraEntries = mapOf("../escape" to byteArrayOf())
                )
                val traversalFailure = restore.restoreFromZip(traversal)
                assertTrue(traversalFailure.isFailure)
                assertTrue(traversalFailure.exceptionOrNull()?.message != null)
                assertTrue(target.knowledgeDao().getAllRawRecords().isEmpty())

                val orphanPayload = File(context.cacheDir, "echomind-orphan-payload.zip")
                rewriteArchive(
                    requireNotNull(exportFile),
                    orphanPayload,
                    originalManifest.copy(
                        files = originalManifest.files + ExportFile(
                            name = "audio/orphan.wav",
                            size = 0L,
                            sha256 = EMPTY_SHA256
                        )
                    ),
                    extraEntries = mapOf("audio/orphan.wav" to byteArrayOf())
                )
                val orphanFailure = restore.restoreFromZip(orphanPayload)
                assertTrue(orphanFailure.isFailure)
                assertTrue(
                    orphanFailure.exceptionOrNull()?.message?.contains("unreferenced") == true
                )
                assertTrue(target.knowledgeDao().getAllRawRecords().isEmpty())

                restore.restoreFromZip(requireNotNull(exportFile)).getOrThrow()
                assertEquals(
                    source.knowledgeDao().getAllRawRecords().map { it.id },
                    target.knowledgeDao().getAllRawRecords().map { it.id }
                )
                assertEquals(
                    source.knowledgeDao().getAllRevisions().map { it.text },
                    target.knowledgeDao().getAllRevisions().map { it.text }
                )
                assertEquals(0, target.knowledgeDao().getAllHypotheses().count { it.status == "proposed" })

                val nonEmptyFailure = restore.restoreFromZip(requireNotNull(exportFile))
                assertTrue(nonEmptyFailure.isFailure)
                assertTrue(nonEmptyFailure.exceptionOrNull()?.message?.contains("empty profile") == true)
                assertEquals(1, target.knowledgeDao().getAllRawRecords().size)
            }
        } finally {
            source.close()
            target.close()
            exportFile?.delete()
            File(context.cacheDir, "echomind-corrupt-hash.zip").delete()
            File(context.cacheDir, "echomind-path-traversal.zip").delete()
            File(context.cacheDir, "echomind-orphan-payload.zip").delete()
        }
    }

    @Test
    fun migratedLegacyProfileRoundTripsThroughCanonicalManifest() {
        val databaseName = "export-migration-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        createLegacyVersion2Database(databaseName)
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
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
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var migratedExport: File? = null
        var canonicalSource: File? = null
        var restoredExport: File? = null
        try {
            runBlocking {
                migratedExport = exportManager(migrated).exportToZip().getOrThrow()
                canonicalSource = File(context.cacheDir, "migrated-canonical-${System.nanoTime()}.zip")
                requireNotNull(migratedExport).copyTo(requireNotNull(canonicalSource), overwrite = true)
                val firstManifest = readManifest(requireNotNull(canonicalSource))

                exportManager(target).restoreFromZip(requireNotNull(canonicalSource)).getOrThrow()
                restoredExport = exportManager(target).exportToZip().getOrThrow()
                val secondManifest = readManifest(requireNotNull(restoredExport))

                assertEquals(
                    "Migration -> export -> empty restore -> export must preserve the canonical manifest",
                    firstManifest.copy(exportedAt = 0L),
                    secondManifest.copy(exportedAt = 0L)
                )
            }
        } finally {
            migrated.close()
            target.close()
            migratedExport?.delete()
            canonicalSource?.delete()
            restoredExport?.delete()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun historicalDecisionGroundsSurviveConclusionRevisionAndRestore() {
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var exportFile: File? = null
        try {
            val reflection = reflectionRepository(source)
            runBlocking {
                val rawId = reflection.captureRawText("Historical decision source")
                val proposal = reflection.createLocalProposal(rawId)
                val first = reflection.confirm(proposal.hypothesisId, "First conclusion")
                val firstRevisionId = requireNotNull(first.revisionId)
                DecisionRepository(source, source.knowledgeDao(), reflection).createDecision(
                    question = "Keep the original plan?",
                    sourceRevisionId = firstRevisionId
                )
                reflection.revise(proposal.hypothesisId, "Revised conclusion")

                exportFile = exportManager(source).exportToZip().getOrThrow()
                val restore = exportManager(target)
                restore.restoreFromZip(requireNotNull(exportFile)).getOrThrow()

                assertEquals(1, target.knowledgeDao().getAllDecisions().size)
                assertEquals(
                    firstRevisionId,
                    target.knowledgeDao().getAllDecisions().single().sourceRevisionId
                )
            }
        } finally {
            source.close()
            target.close()
            exportFile?.delete()
        }
    }

    @Test
    fun decisionChoiceAndOutcomeSurviveExportAndRestore() {
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var exportFile: File? = null
        try {
            val reflection = reflectionRepository(source)
            runBlocking {
                val rawId = reflection.captureRawText("Decision export source")
                val proposal = reflection.createLocalProposal(rawId)
                val revisionId = requireNotNull(
                    reflection.confirm(proposal.hypothesisId, "The exported grounds").revisionId
                )
                val decisions = DecisionRepository(source, source.knowledgeDao(), reflection)
                val decisionId = decisions.createDecision(
                    question = "Should this decision survive export?",
                    sourceRevisionId = revisionId
                )
                decisions.setChoice(decisionId, "Keep the decision")
                decisions.recordOutcome(decisionId, "The decision remained useful after restore.")

                exportFile = exportManager(source).exportToZip().getOrThrow()
                exportManager(target).restoreFromZip(requireNotNull(exportFile)).getOrThrow()

                val restored = DecisionRepository(
                    target,
                    target.knowledgeDao(),
                    reflectionRepository(target)
                ).getDecision(decisionId)
                assertEquals("Should this decision survive export?", restored?.question)
                assertEquals("Keep the decision", restored?.choice)
                assertEquals(revisionId, restored?.sourceRevisionId)
                assertEquals(1, restored?.outcomes?.size)
                assertEquals(
                    "The decision remained useful after restore.",
                    restored?.outcomes?.single()?.report
                )
            }
        } finally {
            source.close()
            target.close()
            exportFile?.delete()
        }
    }

    @Test
    fun legacyPersistedStatesSurviveExportAndRestore() {
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var exportFile: File? = null
        try {
            val reflection = reflectionRepository(source)
            runBlocking {
                val conclusionRawId = reflection.captureRawText("Legacy conclusion source")
                val externalRawId = reflection.captureRawText("Legacy external source")
                val proposal = reflection.createLocalProposal(conclusionRawId)
                val session = reflection.confirm(proposal.hypothesisId, "Legacy conclusion")
                val revisionId = requireNotNull(session.revisionId)
                source.knowledgeDao().insertEvidenceLink(
                    EvidenceLinkEntity(
                        conclusionRevisionId = revisionId,
                        sourceRawRecordId = externalRawId,
                        relationship = Relationship.SUPPORTS,
                        status = "needs_review",
                        origin = "legacy_rebase_unknown",
                        createdAtEstimated = true,
                        reviewMetadata = "legacy conflict"
                    )
                )
                val themeId = source.knowledgeDao().insertTheme(
                    ThemeEntity(name = "Legacy theme", createdAt = 1L)
                )
                source.knowledgeDao().insertThemeLink(
                    ThemeLinkEntity(
                        themeId = themeId,
                        conclusionRevisionId = revisionId,
                        confirmed = false,
                        createdAt = 1L,
                        origin = "legacy_pending",
                        reviewRequired = false
                    )
                )
                source.knowledgeDao().insertDecision(
                    DecisionEntity(
                        question = "Legacy question",
                        suggestion = "Legacy suggestion",
                        suggestionAuthor = "legacy_unknown",
                        suggestionSource = "legacy_data",
                        suggestionStatus = "needs_review",
                        sourceRevisionId = null,
                        createdAt = 1L
                    )
                )

                exportFile = exportManager(source).exportToZip().getOrThrow()
                exportManager(target).restoreFromZip(requireNotNull(exportFile)).getOrThrow()

                assertEquals(2, target.knowledgeDao().getAllEvidenceLinks().size)
                assertEquals(1, target.knowledgeDao().getAllThemeLinks().size)
                assertEquals(1, target.knowledgeDao().getAllDecisions().size)
            }
        } finally {
            source.close()
            target.close()
            exportFile?.delete()
        }
    }

    @Test
    fun restoreRejectsInvalidGraphInvariantsBeforeWritingAnyRows() {
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var exportFile: File? = null
        val targets = mutableListOf<AppDatabase>()
        val invalidArchives = mutableListOf<File>()
        try {
            val sourceRepository = ReflectionRepository(
                database = source,
                entryDao = source.entryDao(),
                knowledgeDao = source.knowledgeDao(),
                analyzer = LocalReflectionAnalyzer(),
                json = Json { ignoreUnknownKeys = true }
            )
            val manifest = runBlocking {
                val rawId = sourceRepository.captureRawText("Invariant source")
                val proposal = sourceRepository.createLocalProposal(rawId)
                val session = sourceRepository.confirm(proposal.hypothesisId, "Invariant conclusion")
                exportFile = ExportManager(
                    context,
                    source,
                    source.entryDao(),
                    source.knowledgeDao(),
                    AudioEncryptionUtil(context)
                ).exportToZip().getOrThrow()
                requireNotNull(exportFile).let { file ->
                    ZipFile(file).use { zip ->
                        Json.decodeFromString<ExportManifest>(
                            zip.getInputStream(requireNotNull(zip.getEntry("manifest.json")))
                                .bufferedReader().use { it.readText() }
                        )
                    }
                }.also { exported ->
                    assertEquals(session.revisionId, exported.conclusions.single().currentRevisionId)
                }
            }
            val currentRevisionId = requireNotNull(manifest.conclusions.single().currentRevisionId)
            val invalidManifests = listOf(
                manifest.copy(
                    conclusions = manifest.conclusions.map { it.copy(currentRevisionId = 9999L) }
                ),
                manifest.copy(
                    evidenceLinks = manifest.evidenceLinks.map { it.copy(relationship = "unknown") }
                ),
                manifest.copy(
                    decisions = listOf(
                        ExportDecision(
                            id = 1L,
                            question = "Outcome without choice",
                            suggestion = null,
                            choice = null,
                            sourceRevisionId = currentRevisionId,
                            createdAt = 1L
                        )
                    ),
                    outcomes = listOf(ExportOutcome(1L, 1L, "Impossible", 1L)),
                    counts = manifest.counts.copy(decisions = 1, outcomes = 1)
                ),
                manifest.copy(
                    captureDraft = ExportCaptureDraft(
                        id = 2L,
                        text = "draft",
                        encryptedAudioFileName = null,
                        durationMs = 0L,
                        captureStage = "CAPTURE",
                        createdAt = 1L,
                        updatedAt = 1L
                    ),
                    counts = manifest.counts.copy(hasCaptureDraft = true)
                ),
                manifest.copy(
                    conclusions = manifest.conclusions + ExportConclusion(
                        id = manifest.conclusions.single().id + 1,
                        rawRecordId = manifest.rawRecords.single().id,
                        currentRevisionId = currentRevisionId,
                        createdAt = 1L
                    ),
                    counts = manifest.counts.copy(conclusions = 2)
                ),
                manifest.copy(
                    rawRecords = manifest.rawRecords + manifest.rawRecords.single(),
                    counts = manifest.counts.copy(rawRecords = 2)
                ),
                manifest.copy(version = 4),
                manifest.copy(
                    hypotheses = manifest.hypotheses.map { it.copy(rawRecordId = 9999L) }
                ),
                manifest.copy(
                    rawRecords = manifest.rawRecords.map {
                        it.copy(audioFileName = "audio/missing.wav")
                    },
                    files = listOf(ExportFile("audio/missing.wav", 0L, EMPTY_SHA256))
                )
            )

            invalidManifests.forEachIndexed { index, invalidManifest ->
                val archive = File(context.cacheDir, "echomind-invalid-invariant-$index.zip")
                invalidArchives += archive
                rewriteArchive(requireNotNull(exportFile), archive, invalidManifest, emptyMap())
                val artifactsBeforeRestore = restoreArtifactsSnapshot()
                val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
                targets += target
                val failure = runBlocking {
                    ExportManager(
                        context,
                        target,
                        target.entryDao(),
                        target.knowledgeDao(),
                        AudioEncryptionUtil(context)
                    ).restoreFromZip(archive)
                }
                assertTrue("case $index should be rejected", failure.isFailure)
                runBlocking {
                    assertTrue(target.entryDao().getAllEntriesOnce().isEmpty())
                    assertTrue(target.knowledgeDao().getAllRawRecords().isEmpty())
                    assertTrue(target.knowledgeDao().getAllRevisions().isEmpty())
                }
                assertEquals(
                    "case $index should not leave restore artifacts",
                    artifactsBeforeRestore,
                    restoreArtifactsSnapshot()
                )
            }
        } finally {
            targets.forEach { it.close() }
            source.close()
            exportFile?.delete()
            invalidArchives.forEach { it.delete() }
        }
    }

    private fun rewriteArchive(
        source: java.io.File,
        target: java.io.File,
        manifest: ExportManifest,
        extraEntries: Map<String, ByteArray>
    ) {
        val json = Json { prettyPrint = true }.encodeToString(manifest).toByteArray()
        ZipFile(source).use { zip ->
            ZipOutputStream(target.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("manifest.json"))
                output.write(json)
                output.closeEntry()
                extraEntries.forEach { (name, bytes) ->
                    output.putNextEntry(ZipEntry(name))
                    output.write(bytes)
                    output.closeEntry()
                }
                zip.entries().asSequence()
                    .filter { it.name != "manifest.json" }
                    .forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name))
                        zip.getInputStream(entry).use { it.copyTo(output) }
                        output.closeEntry()
                    }
            }
        }
    }

    private fun readManifest(archive: File): ExportManifest =
        ZipFile(archive).use { zip ->
            val entry = requireNotNull(zip.getEntry("manifest.json"))
            Json.decodeFromString(
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            )
        }

    private fun createLegacyVersion2Database(name: String) {
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`transcript` TEXT NOT NULL, `audio_path` TEXT, " +
                        "`duration_ms` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, " +
                        "`category` TEXT NOT NULL, `tags` TEXT NOT NULL, " +
                        "`summary` TEXT NOT NULL, `tasks` TEXT NOT NULL, " +
                        "`ideas` TEXT NOT NULL, `emotions` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO entries (id, transcript, audio_path, duration_ms, created_at, " +
                        "category, tags, summary, tasks, ideas, emotions) VALUES " +
                        "(42, 'Original thought', NULL, 1000, 2000, 'idea', '[]', " +
                        "'Generated summary', '[]', '[]', '[]')"
                )
            }

            override fun onUpgrade(
                db: androidx.sqlite.db.SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(callback)
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).close()
    }

    private fun reflectionRepository(database: AppDatabase) = ReflectionRepository(
        database = database,
        entryDao = database.entryDao(),
        knowledgeDao = database.knowledgeDao(),
        analyzer = LocalReflectionAnalyzer(),
        json = Json { ignoreUnknownKeys = true }
    )

    private fun restoreArtifactsSnapshot(): Set<String> =
        context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter { it.name == "restored_audio" || it.name.startsWith("restore_") }
            .flatMap { root -> root.walkTopDown().map(File::getAbsolutePath).toList() }
            .toSet()

    private fun exportManager(database: AppDatabase) = ExportManager(
        context = context,
        database = database,
        entryDao = database.entryDao(),
        knowledgeDao = database.knowledgeDao(),
        audioEncryptionUtil = AudioEncryptionUtil(context)
    )

    private companion object {
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
