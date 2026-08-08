package com.echomind.data.export

import android.content.Context
import android.os.Environment
import androidx.room.withTransaction
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.CaptureDraftEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.DecisionEntity
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.HomeCardDispositionEntity
import com.echomind.data.local.entity.OutcomeEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.local.entity.ThemeEntity
import com.echomind.data.local.entity.ThemeLinkEntity
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.domain.model.CoverageScopeType
import com.echomind.domain.model.HomeCardType
import com.echomind.domain.model.Relationship
import com.echomind.domain.model.ReflectionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

internal const val MAX_RESTORE_ARCHIVE_BYTES = 512L * 1024 * 1024
private const val MAX_RESTORE_MANIFEST_BYTES = 8L * 1024 * 1024
private const val MAX_RESTORE_COMPRESSION_RATIO = 100L

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val entryDao: EntryDao,
    private val knowledgeDao: KnowledgeDao,
    private val audioEncryptionUtil: AudioEncryptionUtil
) {
    suspend fun exportToZip(): Result<File> = runCatching {
        val snapshot = database.withTransaction {
            ExportSnapshot(
                entries = entryDao.getAllEntriesOnce(),
                rawRecords = knowledgeDao.getAllRawRecords(),
                hypotheses = knowledgeDao.getAllHypotheses(),
                conclusions = knowledgeDao.getAllConclusions(),
                revisions = knowledgeDao.getAllRevisions(),
                evidenceLinks = knowledgeDao.getAllEvidenceLinks(),
                themes = knowledgeDao.getAllThemes(),
                themeLinks = knowledgeDao.getAllThemeLinks(),
                decisions = knowledgeDao.getAllDecisions(),
                outcomes = knowledgeDao.getAllOutcomes(),
                captureDraft = knowledgeDao.getCaptureDraft(),
                dispositions = knowledgeDao.getAllHomeCardDispositions()
            )
        }
        val audioNames = buildMap {
            snapshot.rawRecords
                .filter { it.audioPath != null }
                .distinctBy { it.audioPath }
                .forEach { raw -> put(raw.audioPath!!, "audio/raw_${raw.id}.wav") }
            snapshot.entries
                .filter { it.audioPath != null && !containsKey(it.audioPath) }
                .forEach { entry -> put(entry.audioPath!!, "audio/entry_${entry.id}.wav") }
            snapshot.captureDraft?.encryptedAudioPath?.let { put(it, "audio/draft_1.wav") }
        }
        val preparedAudio = mutableListOf<PreparedAudio>()
        try {
            audioNames.forEach { (path, archiveName) ->
                val source = File(path)
                require(source.exists()) { "Referenced audio is missing: $path" }
                val decrypted = audioEncryptionUtil.decryptToTempFile(path)
                preparedAudio += PreparedAudio(
                    archiveName = archiveName,
                    file = decrypted,
                    size = decrypted.length(),
                    sha256 = sha256(decrypted)
                )
            }
        } catch (error: Exception) {
            preparedAudio.forEach { it.file.delete() }
            throw error
        }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "EchoMind"
        )
        exportDir.mkdirs()
        val zipFile = File(exportDir, "echomind_export_$dateStr.zip")

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val manifest = buildManifest(
                    snapshot = snapshot,
                    audioNames = audioNames,
                    files = preparedAudio.map { ExportFile(it.archiveName, it.size, it.sha256) }
                )
            val json = Json { prettyPrint = true }
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(json.encodeToString(manifest).toByteArray())
            zos.closeEntry()

                preparedAudio.forEach { audio ->
                    zos.putNextEntry(ZipEntry(audio.archiveName))
                    audio.file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        } catch (error: Exception) {
            zipFile.delete()
            throw error
        } finally {
            preparedAudio.forEach { it.file.delete() }
        }

        zipFile
    }

    suspend fun restoreFromZip(archive: File): Result<Unit> = runCatching {
        require(archive.isFile) { "Restore archive does not exist." }
        val manifestAndAudio = readAndValidateArchive(archive)
        require(
            entryDao.getAllEntriesOnce().isEmpty() &&
                knowledgeDao.getAllRawRecords().isEmpty() &&
                knowledgeDao.getAllThemes().isEmpty() &&
                knowledgeDao.getAllDecisions().isEmpty() &&
                knowledgeDao.getCaptureDraft() == null &&
                knowledgeDao.getAllHomeCardDispositions().isEmpty()
        ) { "Restore requires an empty profile" }

        val sessionDir = File(context.noBackupFilesDir, "restore_${UUID.randomUUID()}")
        val audioDir = File(context.noBackupFilesDir, "restored_audio")
        val stagedAudio = mutableMapOf<String, String>()
        try {
            sessionDir.mkdirs()
            audioDir.mkdirs()
            ZipFile(archive).use { zip ->
                manifestAndAudio.manifest.files.forEach { fileMetadata ->
                    val source = requireNotNull(zip.getEntry(fileMetadata.name))
                    val plaintext = File(sessionDir, "${stagedAudio.size}.wav")
                    zip.getInputStream(source).use { input -> plaintext.outputStream().use { input.copyTo(it) } }
                    val encrypted = File(
                        audioDir,
                        "restore_${stagedAudio.size}_${fileMetadata.sha256}.enc"
                    )
                    audioEncryptionUtil.encryptFile(plaintext, encrypted)
                    stagedAudio[fileMetadata.name] = encrypted.absolutePath
                }
            }
            val manifest = manifestAndAudio.manifest
            database.withTransaction {
                entryDao.insertEntries(manifest.entries.map { entry ->
                    EntryEntity(
                        id = entry.id,
                        transcript = entry.transcript,
                        audioPath = entry.audioFileName?.let(stagedAudio::get),
                        durationMs = entry.durationMs,
                        createdAt = entry.createdAt,
                        category = entry.category,
                        tags = entry.tags,
                        summary = entry.summary,
                        tasks = entry.tasks,
                        ideas = entry.ideas,
                        emotions = entry.emotions
                    )
                })
                knowledgeDao.insertRawRecords(manifest.rawRecords.map { raw ->
                    RawRecordEntity(
                        id = raw.id,
                        legacyEntryId = raw.legacyEntryId,
                        originalText = raw.originalText,
                        audioPath = raw.audioFileName?.let(stagedAudio::get),
                        durationMs = raw.durationMs,
                        createdAt = raw.createdAt
                    )
                })
                knowledgeDao.insertHypotheses(manifest.hypotheses.map {
                    AiHypothesisEntity(it.id, it.rawRecordId, it.draftJson, it.counterargument, it.status, it.createdAt)
                })
                knowledgeDao.insertConclusions(manifest.conclusions.map {
                    ConclusionEntity(it.id, it.rawRecordId, it.currentRevisionId, it.createdAt)
                })
                knowledgeDao.insertRevisions(manifest.revisions.map {
                    ConclusionRevisionEntity(it.id, it.conclusionId, it.version, it.text, it.author, it.createdAt)
                })
                knowledgeDao.insertThemes(manifest.themes.map {
                    ThemeEntity(it.id, it.name, it.createdAt, it.archivedAt)
                })
                knowledgeDao.insertEvidenceLinks(manifest.evidenceLinks.map {
                    EvidenceLinkEntity(
                        id = it.id,
                        conclusionRevisionId = it.conclusionRevisionId,
                        sourceRawRecordId = it.sourceRawRecordId,
                        relationship = it.relationship,
                        status = it.status,
                        origin = it.origin,
                        createdAt = it.createdAt,
                        createdAtEstimated = it.createdAtEstimated,
                        reviewMetadata = it.reviewMetadata
                    )
                })
                knowledgeDao.insertThemeLinks(manifest.themeLinks.map {
                    ThemeLinkEntity(
                        id = it.id,
                        themeId = it.themeId,
                        conclusionRevisionId = it.conclusionRevisionId,
                        confirmed = it.confirmed,
                        createdAt = it.createdAt,
                        origin = it.origin,
                        reviewRequired = it.reviewRequired
                    )
                })
                knowledgeDao.insertDecisions(manifest.decisions.map {
                    DecisionEntity(
                        id = it.id,
                        question = it.question,
                        suggestion = it.suggestion,
                        choice = it.choice,
                        sourceRevisionId = it.sourceRevisionId,
                        createdAt = it.createdAt,
                        suggestionAuthor = it.suggestionAuthor,
                        suggestionSource = it.suggestionSource,
                        suggestionStatus = it.suggestionStatus
                    )
                })
                knowledgeDao.insertOutcomes(manifest.outcomes.map {
                    OutcomeEntity(it.id, it.decisionId, it.report, it.createdAt)
                })
                manifest.captureDraft?.let { draft ->
                    knowledgeDao.upsertCaptureDraft(
                        CaptureDraftEntity(
                            id = draft.id,
                            text = draft.text,
                            encryptedAudioPath = draft.encryptedAudioFileName?.let(stagedAudio::get),
                            durationMs = draft.durationMs,
                            captureStage = draft.captureStage,
                            createdAt = draft.createdAt,
                            updatedAt = draft.updatedAt
                        )
                    )
                }
                manifest.dispositions.forEach { disposition ->
                    knowledgeDao.upsertHomeCardDisposition(
                        HomeCardDispositionEntity(
                            cardKey = disposition.cardKey,
                            cardType = disposition.cardType,
                            scopeType = disposition.scopeType,
                            scopeId = disposition.scopeId,
                            dismissedAt = disposition.dismissedAt,
                            postponedUntil = disposition.postponedUntil,
                            createdAt = disposition.createdAt
                        )
                    )
                }
            }
        } catch (error: Exception) {
            stagedAudio.values.map(::File).forEach { it.delete() }
            throw error
        } finally {
            sessionDir.deleteRecursively()
        }
    }

    private fun readAndValidateArchive(archive: File): ValidatedArchive {
        require(archive.length() in 1..MAX_RESTORE_ARCHIVE_BYTES) {
            "Restore archive is missing or too large."
        }
        ZipFile(archive).use { zip ->
            val entries = zip.entries().toList()
            require(entries.size <= MAX_ARCHIVE_ENTRIES) { "Restore archive has too many files." }
            require(entries.map { it.name }.distinct().size == entries.size) {
                "Duplicate archive entry names are not allowed."
            }
            val manifestEntry = requireNotNull(zip.getEntry("manifest.json")) {
                "Restore archive has no manifest."
            }
            require(entries.count { it.name == "manifest.json" } == 1) {
                "Restore archive must contain exactly one manifest."
            }
            require(manifestEntry.size in 0..MAX_RESTORE_MANIFEST_BYTES) {
                "Restore manifest is too large."
            }
            val manifest = Json { ignoreUnknownKeys = false }
                .decodeFromString<ExportManifest>(
                    zip.getInputStream(manifestEntry).use { it.readBoundedText(MAX_RESTORE_MANIFEST_BYTES) }
                )
            require(manifest.version == 5 && manifest.schemaVersion == 6) {
                "Unsupported restore manifest version."
            }
            validateCounts(manifest)
            validateIdsAndReferences(manifest)
            val metadataNames = manifest.files.map { it.name }
            require(metadataNames.distinct().size == metadataNames.size) { "Duplicate archive file metadata." }
            require(entries.map { it.name }.filterNot { it == "manifest.json" }.toSet() == metadataNames.toSet()) {
                "Archive files do not match the manifest."
            }
            var totalSize = 0L
            manifest.files.forEach { file ->
                require(isSafeArchivePath(file.name)) { "Unsafe archive path: ${file.name}" }
                require(file.size in 0..MAX_AUDIO_BYTES) { "Archive file is too large." }
                totalSize += file.size
                require(totalSize <= MAX_TOTAL_AUDIO_BYTES) { "Archive payload is too large." }
                val entry = requireNotNull(zip.getEntry(file.name))
                require(entry.isDirectory.not()) { "Archive file is a directory." }
                require(entry.size in 0..MAX_AUDIO_BYTES) { "Invalid archive file size." }
                require(entry.size == file.size) { "Archive size hash metadata mismatch." }
                require(file.sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
                    "Archive hash metadata is invalid."
                }
                require(entry.compressedSize >= 0L) { "Archive compression metadata is invalid." }
                require(
                    file.size == 0L ||
                        (entry.compressedSize > 0L && file.size <= entry.compressedSize * MAX_RESTORE_COMPRESSION_RATIO)
                ) { "Archive compression ratio is excessive." }
                val actualHash = zip.getInputStream(entry).use(::sha256)
                require(actualHash == file.sha256) { "Archive hash mismatch for ${file.name}." }
            }
            return ValidatedArchive(manifest)
        }
    }

    private fun validateCounts(manifest: ExportManifest) {
        val c = manifest.counts
        require(c.entries == manifest.entries.size)
        require(c.rawRecords == manifest.rawRecords.size)
        require(c.hypotheses == manifest.hypotheses.size)
        require(c.conclusions == manifest.conclusions.size)
        require(c.revisions == manifest.revisions.size)
        require(c.evidenceLinks == manifest.evidenceLinks.size)
        require(c.themes == manifest.themes.size)
        require(c.themeLinks == manifest.themeLinks.size)
        require(c.decisions == manifest.decisions.size)
        require(c.outcomes == manifest.outcomes.size)
        require(c.hasCaptureDraft == (manifest.captureDraft != null))
        require(c.dispositions == manifest.dispositions.size)
    }

    private fun validateIdsAndReferences(manifest: ExportManifest) {
        fun unique(ids: List<Long>) {
            require(ids.all { it > 0L } && ids.distinct().size == ids.size) { "Duplicate or invalid stable ID." }
        }
        unique(manifest.entries.map { it.id })
        unique(manifest.rawRecords.map { it.id })
        unique(manifest.hypotheses.map { it.id })
        unique(manifest.conclusions.map { it.id })
        unique(manifest.revisions.map { it.id })
        unique(manifest.evidenceLinks.map { it.id })
        unique(manifest.themes.map { it.id })
        unique(manifest.themeLinks.map { it.id })
        unique(manifest.decisions.map { it.id })
        unique(manifest.outcomes.map { it.id })
        require(manifest.evidenceLinks.map { it.conclusionRevisionId to it.sourceRawRecordId }.distinct().size == manifest.evidenceLinks.size)
        require(manifest.themeLinks.map { it.themeId to it.conclusionRevisionId }.distinct().size == manifest.themeLinks.size)
        val entries = manifest.entries.map { it.id }.toSet()
        val raws = manifest.rawRecords.map { it.id }.toSet()
        val hypotheses = manifest.hypotheses.map { it.id }.toSet()
        val conclusions = manifest.conclusions.map { it.id }.toSet()
        val revisions = manifest.revisions.map { it.id }.toSet()
        val themes = manifest.themes.map { it.id }.toSet()
        val decisions = manifest.decisions.map { it.id }.toSet()
        val revisionById = manifest.revisions.associateBy { it.id }
        require(manifest.rawRecords.all { it.legacyEntryId == null || it.legacyEntryId in entries })
        require(manifest.hypotheses.all { it.rawRecordId in raws })
        require(manifest.conclusions.all { it.rawRecordId in raws })
        require(manifest.revisions.all { it.conclusionId in conclusions })
        require(manifest.conclusions.all { conclusion ->
            val currentRevisionId = conclusion.currentRevisionId
            currentRevisionId != null && revisionById[currentRevisionId]?.conclusionId == conclusion.id
        }) { "Every conclusion must point to its own current revision." }
        manifest.revisions.groupBy { it.conclusionId }.forEach { (conclusionId, revisionsForConclusion) ->
            val versions = revisionsForConclusion.map { it.version }.sorted()
            require(versions == (1..versions.size).toList()) {
                "Revision versions must be unique and contiguous for conclusion $conclusionId."
            }
        }
        require(manifest.entries.all { it.category.lowercase() in setOf("general", "task", "idea", "feeling", "plan") }) {
            "Entry has an invalid category."
        }
        require(manifest.entries.all { it.transcript.isNotBlank() && it.durationMs >= 0L }) {
            "Entry has invalid capture data."
        }
        require(manifest.rawRecords.all { it.originalText.isNotBlank() && it.durationMs >= 0L }) {
            "Raw record has invalid capture data."
        }
        require(manifest.hypotheses.all { it.status in setOf(
            ReflectionStatus.PROPOSED,
            ReflectionStatus.CONFIRMED,
            ReflectionStatus.REJECTED
        ) }) { "Hypothesis has an invalid status." }
        require(manifest.revisions.all { it.version > 0 && it.text.isNotBlank() && it.author.isNotBlank() }) {
            "Revision has invalid content."
        }
        require(manifest.evidenceLinks.all { it.conclusionRevisionId in revisions && it.sourceRawRecordId in raws })
        require(manifest.evidenceLinks.all {
            it.relationship in setOf(Relationship.SUPPORTS, Relationship.CONTRADICTS) &&
                it.status in setOf(ReflectionStatus.CONFIRMED, "needs_review") &&
                it.origin in setOf(
                    "intrinsic_source",
                    "user_confirmed",
                    "proposed_inherited",
                    "legacy_pending",
                    "legacy_rebase_unknown"
                )
        }) { "Evidence link has an invalid relationship or provenance state." }
        require(manifest.themeLinks.all { it.themeId in themes && it.conclusionRevisionId in revisions })
        require(manifest.themeLinks.all {
            it.origin in setOf("user_confirmed", "proposed_inherited", "legacy_pending") &&
                (it.origin == "legacy_pending" || it.reviewRequired == !it.confirmed)
        }) { "Theme link has an invalid review state." }
        require(manifest.decisions.all { decision ->
            val sourceRevisionId = decision.sourceRevisionId
            sourceRevisionId == null || (
                sourceRevisionId in revisions &&
                    revisionById[sourceRevisionId]?.conclusionId?.let { it in conclusions } == true
                )
        }) { "Every decision must reference an existing conclusion revision." }
        require(manifest.decisions.all { decision ->
            decision.question.isNotBlank() &&
                (decision.choice == null || decision.choice.isNotBlank()) &&
            (decision.suggestion == null || (
                    (decision.suggestionAuthor == "echomind" &&
                        !decision.suggestionSource.isNullOrBlank() &&
                        decision.suggestionStatus in setOf("proposal", "confirmed", "rejected")) ||
                        (decision.suggestionAuthor == "legacy_unknown" &&
                            decision.suggestionSource == "legacy_data" &&
                            decision.suggestionStatus == "needs_review")
                    ))
        }) { "Decision has invalid state or suggestion metadata." }
        require(manifest.outcomes.all { it.decisionId in decisions })
        require(manifest.outcomes.all { it.report.isNotBlank() && manifest.decisions.first { decision -> decision.id == it.decisionId }.choice?.isNotBlank() == true }) {
            "An outcome requires a non-empty decision choice."
        }
        manifest.captureDraft?.let { draft ->
            require(draft.id == 1L) { "Capture draft must use the singleton id." }
            require(draft.captureStage == "CAPTURE") { "Capture draft has an invalid stage." }
            require(draft.updatedAt >= draft.createdAt) { "Capture draft timestamps are invalid." }
        }
        require(manifest.dispositions.all {
            it.cardKey.isNotBlank() &&
                it.cardType in HomeCardType.entries.map { type -> type.name } &&
                it.scopeType in CoverageScopeType.entries.map { scope -> scope.name } &&
                it.scopeId != 0L
        }) { "Home-card disposition has an invalid scope or type." }
        val files = manifest.files.map { it.name }.toSet()
        val referencedAudioFiles = buildSet {
            manifest.entries.mapNotNullTo(this) { it.audioFileName }
            manifest.rawRecords.mapNotNullTo(this) { it.audioFileName }
            manifest.captureDraft?.encryptedAudioFileName?.let(::add)
        }
        require(referencedAudioFiles == files) {
            "Archive audio files must be referenced exactly once by the manifest graph; " +
                "unreferenced payloads are not allowed."
        }
    }

    private fun isSafeArchivePath(path: String): Boolean =
        path.startsWith("audio/") &&
            !path.contains('\\') &&
            !path.contains(":") &&
            path.split('/').none { it.isBlank() || it == "." || it == ".." }

    private fun sha256(input: InputStream): String = MessageDigest.getInstance("SHA-256").let { digest ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count = input.read(buffer)
        while (count >= 0) {
            if (count > 0) digest.update(buffer, 0, count)
            count = input.read(buffer)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun InputStream.readBoundedText(maxBytes: Long): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        var count = read(buffer)
        while (count >= 0) {
            if (count > 0) {
                total += count
                require(total <= maxBytes) { "Restore manifest is too large." }
                output.write(buffer, 0, count)
            }
            count = read(buffer)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private data class ValidatedArchive(val manifest: ExportManifest)

    private companion object {
        const val MAX_ARCHIVE_ENTRIES = 2_000
        const val MAX_AUDIO_BYTES = 256L * 1024 * 1024
        const val MAX_TOTAL_AUDIO_BYTES = 512L * 1024 * 1024
    }
}

internal data class ExportSnapshot(
    val entries: List<EntryEntity>,
    val rawRecords: List<RawRecordEntity>,
    val hypotheses: List<AiHypothesisEntity>,
    val conclusions: List<ConclusionEntity>,
    val revisions: List<ConclusionRevisionEntity>,
    val evidenceLinks: List<EvidenceLinkEntity>,
    val themes: List<ThemeEntity>,
    val themeLinks: List<ThemeLinkEntity>,
    val decisions: List<DecisionEntity>,
    val outcomes: List<OutcomeEntity>,
    val captureDraft: com.echomind.data.local.entity.CaptureDraftEntity? = null,
    val dispositions: List<com.echomind.data.local.entity.HomeCardDispositionEntity> = emptyList()
)

private data class PreparedAudio(
    val archiveName: String,
    val file: File,
    val size: Long,
    val sha256: String
)

internal fun buildManifest(
    snapshot: ExportSnapshot,
    exportedAt: Long = System.currentTimeMillis(),
    audioNames: Map<String, String> = emptyMap(),
    files: List<ExportFile> = emptyList()
) =
    ExportManifest(
        version = 5,
        schemaVersion = 6,
        exportedAt = exportedAt,
        entries = snapshot.entries.map { entity ->
            ExportEntry(
                id = entity.id,
                transcript = entity.transcript,
                audioFileName = entity.audioPath.toExportAudioName(audioNames),
                durationMs = entity.durationMs,
                createdAt = entity.createdAt,
                category = entity.category,
                tags = entity.tags,
                summary = entity.summary,
                tasks = entity.tasks,
                ideas = entity.ideas,
                emotions = entity.emotions
            )
        },
        rawRecords = snapshot.rawRecords.map {
            ExportRawRecord(
                id = it.id,
                legacyEntryId = it.legacyEntryId,
                originalText = it.originalText,
                audioFileName = it.audioPath.toExportAudioName(audioNames),
                durationMs = it.durationMs,
                createdAt = it.createdAt
            )
        },
        hypotheses = snapshot.hypotheses.map {
            ExportHypothesis(
                id = it.id,
                rawRecordId = it.rawRecordId,
                draftJson = it.draftJson,
                counterargument = it.counterargument,
                status = it.status,
                createdAt = it.createdAt
            )
        },
        conclusions = snapshot.conclusions.map {
            ExportConclusion(it.id, it.rawRecordId, it.currentRevisionId, it.createdAt)
        },
        revisions = snapshot.revisions.map {
            ExportConclusionRevision(
                it.id,
                it.conclusionId,
                it.version,
                it.text,
                it.author,
                it.createdAt
            )
        },
        evidenceLinks = snapshot.evidenceLinks.map {
            ExportEvidenceLink(
                it.id,
                it.conclusionRevisionId,
                it.sourceRawRecordId,
                it.relationship,
                it.status,
                it.origin,
                it.createdAt,
                it.createdAtEstimated,
                it.reviewMetadata
            )
        },
        themes = snapshot.themes.map {
            ExportTheme(it.id, it.name, it.createdAt, it.archivedAt)
        },
        themeLinks = snapshot.themeLinks.map {
            ExportThemeLink(
                it.id,
                it.themeId,
                it.conclusionRevisionId,
                it.confirmed,
                it.createdAt,
                it.origin,
                it.reviewRequired
            )
        },
        decisions = snapshot.decisions.map {
            ExportDecision(
                it.id,
                it.question,
                it.suggestion,
                it.choice,
                it.sourceRevisionId,
                it.createdAt,
                it.suggestionAuthor,
                it.suggestionSource,
                it.suggestionStatus
            )
        },
        outcomes = snapshot.outcomes.map {
            ExportOutcome(
                it.id,
                it.decisionId,
                it.report,
                it.createdAt
            )
        },
        captureDraft = snapshot.captureDraft?.let {
            ExportCaptureDraft(it.id, it.text, it.encryptedAudioPath.toExportAudioName(audioNames), it.durationMs, it.captureStage, it.createdAt, it.updatedAt)
        },
        dispositions = snapshot.dispositions.map {
            ExportDisposition(it.cardKey, it.cardType, it.scopeType, it.scopeId, it.dismissedAt, it.postponedUntil, it.createdAt)
        },
        files = files,
        counts = ExportCounts(
            entries = snapshot.entries.size,
            rawRecords = snapshot.rawRecords.size,
            hypotheses = snapshot.hypotheses.size,
            conclusions = snapshot.conclusions.size,
            revisions = snapshot.revisions.size,
            evidenceLinks = snapshot.evidenceLinks.size,
            themes = snapshot.themes.size,
            themeLinks = snapshot.themeLinks.size,
            decisions = snapshot.decisions.size,
            outcomes = snapshot.outcomes.size,
            hasCaptureDraft = snapshot.captureDraft != null,
            dispositions = snapshot.dispositions.size
        )
    )

private fun String?.toExportAudioName(audioNames: Map<String, String> = emptyMap()): String? =
    this?.let { audioNames[it] ?: File(it).nameWithoutExtension + ".wav" }

private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count = input.read(buffer)
        while (count >= 0) {
            if (count > 0) digest.update(buffer, 0, count)
            count = input.read(buffer)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}

@Serializable
data class ExportManifest(
    val version: Int,
    val exportedAt: Long,
    val entries: List<ExportEntry>,
    val rawRecords: List<ExportRawRecord>,
    val hypotheses: List<ExportHypothesis>,
    val conclusions: List<ExportConclusion>,
    val revisions: List<ExportConclusionRevision>,
    val evidenceLinks: List<ExportEvidenceLink>,
    val themes: List<ExportTheme> = emptyList(),
    val themeLinks: List<ExportThemeLink> = emptyList(),
    val decisions: List<ExportDecision> = emptyList(),
    val outcomes: List<ExportOutcome> = emptyList(),
    val schemaVersion: Int = 6,
    val files: List<ExportFile> = emptyList(),
    val counts: ExportCounts = ExportCounts(),
    val captureDraft: ExportCaptureDraft? = null,
    val dispositions: List<ExportDisposition> = emptyList()
)

@Serializable
data class ExportFile(val name: String, val size: Long, val sha256: String)

@Serializable
data class ExportCounts(
    val entries: Int = 0,
    val rawRecords: Int = 0,
    val hypotheses: Int = 0,
    val conclusions: Int = 0,
    val revisions: Int = 0,
    val evidenceLinks: Int = 0,
    val themes: Int = 0,
    val themeLinks: Int = 0,
    val decisions: Int = 0,
    val outcomes: Int = 0,
    val hasCaptureDraft: Boolean = false,
    val dispositions: Int = 0
)

@Serializable
data class ExportEntry(
    val id: Long,
    val transcript: String,
    val audioFileName: String?,
    val durationMs: Long,
    val createdAt: Long,
    val category: String,
    val tags: List<String>,
    val summary: String,
    val tasks: List<String>,
    val ideas: List<String>,
    val emotions: List<String>,
    val analysisStatus: String = "legacy_unconfirmed"
)

@Serializable
data class ExportRawRecord(
    val id: Long,
    val legacyEntryId: Long?,
    val originalText: String,
    val audioFileName: String?,
    val durationMs: Long,
    val createdAt: Long
)

@Serializable
data class ExportHypothesis(
    val id: Long,
    val rawRecordId: Long,
    val draftJson: String,
    val counterargument: String,
    val status: String,
    val createdAt: Long
)

@Serializable
data class ExportConclusion(
    val id: Long,
    val rawRecordId: Long,
    val currentRevisionId: Long?,
    val createdAt: Long
)

@Serializable
data class ExportConclusionRevision(
    val id: Long,
    val conclusionId: Long,
    val version: Int,
    val text: String,
    val author: String,
    val createdAt: Long
)

@Serializable
data class ExportEvidenceLink(
    val id: Long,
    val conclusionRevisionId: Long,
    val sourceRawRecordId: Long,
    val relationship: String,
    val status: String,
    val origin: String = "user_confirmed",
    val createdAt: Long = 0L,
    val createdAtEstimated: Boolean = false,
    val reviewMetadata: String? = null
)

@Serializable
data class ExportTheme(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val archivedAt: Long?
)

@Serializable
data class ExportThemeLink(
    val id: Long,
    val themeId: Long,
    val conclusionRevisionId: Long,
    val confirmed: Boolean,
    val createdAt: Long,
    val origin: String = "user_confirmed",
    val reviewRequired: Boolean = false
)

@Serializable
data class ExportDecision(
    val id: Long,
    val question: String,
    val suggestion: String?,
    val choice: String?,
    val sourceRevisionId: Long?,
    val createdAt: Long,
    val suggestionAuthor: String? = null,
    val suggestionSource: String? = null,
    val suggestionStatus: String? = null
)

@Serializable
data class ExportOutcome(
    val id: Long,
    val decisionId: Long,
    val report: String,
    val createdAt: Long
)

@Serializable
data class ExportCaptureDraft(
    val id: Long,
    val text: String,
    val encryptedAudioFileName: String?,
    val durationMs: Long,
    val captureStage: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ExportDisposition(
    val cardKey: String,
    val cardType: String,
    val scopeType: String,
    val scopeId: Long,
    val dismissedAt: Long?,
    val postponedUntil: Long?,
    val createdAt: Long
)
