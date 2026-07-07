package com.echomind.data.local.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEncryptionUtil @Inject constructor(
    private val context: Context
) {
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

        inputFile.inputStream().use { input ->
            encryptedFile.openFileOutput().use { output ->
                input.copyTo(output)
            }
        }
        inputFile.delete()
    }

    fun getDecryptedInputStream(encryptedFile: File): java.io.InputStream {
        val file = EncryptedFile.Builder(
            context,
            encryptedFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        return file.openFileInput()
    }

    fun decryptToTempFile(encryptedPath: String): File {
        val encryptedFile = File(encryptedPath)
        val tempFile = File(context.cacheDir, "playback_${encryptedFile.nameWithoutExtension}.wav")
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
        tempFile.deleteOnExit()
        return tempFile
    }

    companion object {
        const val ENCRYPTED_EXTENSION = ".enc"
    }
}
