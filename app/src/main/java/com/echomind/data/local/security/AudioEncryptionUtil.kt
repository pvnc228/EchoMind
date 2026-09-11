package com.echomind.data.local.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEncryptionUtil @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var startupCleanupCompleted = false
    private var interruptedRecording = false
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun encryptFile(inputFile: File, outputFile: File) {
        val encryptedFile = EncryptedFile.Builder(
            context,
            outputFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        try {
            inputFile.inputStream().use { input ->
                encryptedFile.openFileOutput().use { output ->
                    input.copyTo(output)
                }
            }
            inputFile.delete()
        } catch (t: Throwable) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            if (inputFile.exists()) {
                inputFile.delete()
            }
            throw t
        }
    }

    fun getDecryptedInputStream(encryptedFile: File): InputStream {
        val file = EncryptedFile.Builder(
            context,
            encryptedFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        return file.openFileInput()
    }

    fun decryptToTempFile(encryptedPath: String, targetFile: File? = null): File {
        cleanupOrphanedTempFiles()
        val encryptedFile = File(encryptedPath)
        val tempFile = targetFile ?: File(context.cacheDir, "playback_${encryptedFile.nameWithoutExtension}.wav")
        try {
            val decrypted = EncryptedFile.Builder(
                context,
                encryptedFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            decrypted.openFileInput().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (t: Throwable) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw t
        }
    }

    fun decryptToUniqueTempFile(encryptedPath: String, prefix: String = "transcription_", extension: String = "m4a"): File {
        val uniqueName = "${prefix}${java.util.UUID.randomUUID()}.$extension"
        val uniqueFile = File(context.cacheDir, uniqueName)
        return decryptToTempFile(encryptedPath, uniqueFile)
    }

    /** IO-only startup barrier shared by Application and audio producers. */
    @Synchronized
    fun cleanupOrphanedTempFiles() {
        if (startupCleanupCompleted) return
        val cacheFiles = context.cacheDir.listFiles { file ->
            file.isFile && (file.name.startsWith("transcription_") || file.name.startsWith("playback_"))
        }.orEmpty()
        val captureFiles = File(context.noBackupFilesDir, "capture_tmp").listFiles { file ->
            file.isFile && (file.extension == "m4a" || file.name.endsWith(".tmp"))
        }.orEmpty()
        val staleFiles = cacheFiles.toList() + captureFiles.toList()
        staleFiles.forEach { file ->
            check(file.delete() || !file.exists()) { "Could not clean interrupted audio file." }
        }
        startupCleanupCompleted = true
        interruptedRecording = captureFiles.isNotEmpty()
    }

    @Synchronized
    fun consumeInterruptedRecordingNotice(): Boolean {
        val interrupted = interruptedRecording
        interruptedRecording = false
        return interrupted
    }

    fun deleteTempFile(tempPath: String) {
        val file = File(tempPath)
        if (file.exists()) file.delete()
    }

    companion object {
        const val ENCRYPTED_EXTENSION = ".enc"
    }
}
