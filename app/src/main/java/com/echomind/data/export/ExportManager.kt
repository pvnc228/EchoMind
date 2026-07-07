package com.echomind.data.export

import android.content.Context
import android.os.Environment
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.entity.EntryEntity
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
    private val entryDao: EntryDao,
    private val audioEncryptionUtil: AudioEncryptionUtil
) {
    suspend fun exportToZip(): Result<File> = runCatching {
        val entries = entryDao.getAllEntriesOnce()
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "EchoMind"
        )
        exportDir.mkdirs()
        val zipFile = File(exportDir, "echomind_export_$dateStr.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val manifest = buildManifest(entries)
            val json = Json { prettyPrint = true }
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(json.encodeToString(manifest).toByteArray())
            zos.closeEntry()

            for (entity in entries) {
                val audioPath = entity.audioPath
                if (audioPath != null) {
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
        }

        zipFile
    }

    private fun buildManifest(entries: List<EntryEntity>): ExportManifest {
        return ExportManifest(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            entries = entries.map { entity ->
                ExportEntry(
                    id = entity.id,
                    transcript = entity.transcript,
                    audioFileName = entity.audioPath?.let {
                        File(it).nameWithoutExtension + ".wav"
                    },
                    durationMs = entity.durationMs,
                    createdAt = entity.createdAt,
                    category = entity.category,
                    tags = entity.tags.split(",").filter { it.isNotBlank() },
                    summary = entity.summary,
                    tasks = entity.tasks.split("|").filter { it.isNotBlank() },
                    ideas = entity.ideas.split("|").filter { it.isNotBlank() },
                    emotions = entity.emotions.split("|").filter { it.isNotBlank() }
                )
            }
        )
    }
}

@Serializable
data class ExportManifest(
    val version: Int,
    val exportedAt: Long,
    val entries: List<ExportEntry>
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
    val emotions: List<String>
)
