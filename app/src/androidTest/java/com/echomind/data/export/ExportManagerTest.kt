package com.echomind.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.repository.ReflectionRepository
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
                assertTrue(corruptFailure.exceptionOrNull()?.message?.contains("hash mismatch") == true)
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
                )
            )

            invalidManifests.forEachIndexed { index, invalidManifest ->
                val archive = File(context.cacheDir, "echomind-invalid-invariant-$index.zip")
                invalidArchives += archive
                rewriteArchive(requireNotNull(exportFile), archive, invalidManifest, emptyMap())
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
}
