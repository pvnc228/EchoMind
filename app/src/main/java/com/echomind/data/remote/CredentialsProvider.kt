package com.echomind.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialsProvider @Inject constructor(
    @ApplicationContext context: Context
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

    @Volatile
    var apiKey: String = ""
        private set

    fun updateApiKey(key: String) {
        apiKey = key
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun loadApiKey() {
        apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
    }

    companion object {
        private const val PREFS_NAME = "echomind_credentials"
        private const val KEY_API_KEY = "api_key"
    }
}
