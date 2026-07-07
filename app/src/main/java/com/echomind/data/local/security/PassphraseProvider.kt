package com.echomind.data.local.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassphraseProvider @Inject constructor(
    private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Synchronized
    fun getPassphrase(): ByteArray {
        val stored = prefs.getString(KEY_PASSPHRASE, null)
        if (stored != null) {
            return hexStringToByteArray(stored)
        }
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        prefs.edit().putString(KEY_PASSPHRASE, byteArrayToHexString(random)).apply()
        return random
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private const val PREFS_NAME = "echomind_db_passphrase"
        private const val KEY_PASSPHRASE = "db_passphrase"
    }
}
