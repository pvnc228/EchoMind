package com.echomind.data.export

import android.content.Context
import android.os.Environment
import androidx.room.withTransaction
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.AiHypothesisEntity
import com.echomind.data.local.entity.ConclusionEntity
import com.echomind.data.local.entity.ConclusionRevisionEntity
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.EvidenceLinkEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.data.local.entity.ThemeEntity
import com.echomind.data.local.entity.ThemeLinkEntity
import com.echomind.data.local.security.AudioEncryptionUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

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
                themeLinks = knowledgeDao.getConfirmedThemeLinksAll()
            )
        }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "EchoMind"
        )
        exportDir.mkdirs()
        val zipFile = File(exportDir, "echomind_export_$dateStr.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val manifest = buildManifest(snapshot)
            val json = Json { prettyPrint = true }
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(json.encodeToString(manifest).toByteArray())
            zos.closeEntry()

            for (audioPath in snapshot.rawRecords.mapNotNull { it.audioPath }.distinct()) {
                val audioFile = File(audioPath)
                if (audioFile.exists()) {
                    try {
                        val decrypted = audioEncryptionUtil.decryptToTempFile(audioPath)
                        zos.putNextEntry(ZipEntry("audio/${audioFile.nameWithoutExtension}.wav"))
                        decrypted.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                        decrypted.delete()
                    } catch (_: Exception) {
                        zos.putNextEntry(ZipEntry("audio/${audioFile.nameWithoutExtension}.txt"))
                        zos.write("[encrypted audio - unable to decrypt]".toByteArray())
                        zos.closeEntry()
                    }
                }
            }
        }

        zipFile
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
    val themeLinks: List<ThemeLinkEntity>
)

internal fun buildManifest(snapshot: ExportSnapshot, exportedAt: Long = System.currentTimeMillis()) =
    ExportManifest(
        version = 3,
        exportedAt = exportedAt,
        entries = snapshot.entries.map { entity ->
            ExportEntry(
                id = entity.id,
                transcript = entity.transcript,
                audioFileName = entity.audioPath.toExportAudioName(),
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
                audioFileName = it.audioPath.toExportAudioName(),
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
                it.status
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
                it.createdAt
            )
        }
    )

private fun String?.toExportAudioName(): String? =
    this?.let { File(it).nameWithoutExtension + ".wav" }

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
    val themeLinks: List<ExportThemeLink> = emptyList()
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
    val status: String
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
    val createdAt: Long
)
