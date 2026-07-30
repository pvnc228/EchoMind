package com.echomind.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private const val DEFAULT_API_ENDPOINT = "http://localhost:1234"

data class StoredSettings(
    val apiEndpoint: String = DEFAULT_API_ENDPOINT,
    val localMode: Boolean = true
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.settingsDataStore
    @Volatile
    private var localModeOverride: Boolean? = null

    suspend fun load(): StoredSettings {
        val preferences = try {
            dataStore.data.first()
        } catch (_: IOException) {
            return StoredSettings(localMode = localModeOverride ?: true)
        }
        return StoredSettings(
            apiEndpoint = preferences[KEY_API_ENDPOINT] ?: DEFAULT_API_ENDPOINT,
            localMode = localModeOverride ?: preferences[KEY_LOCAL_MODE] ?: true
        )
    }

    suspend fun isLocalMode(): Boolean = localModeOverride ?: load().localMode

    suspend fun setApiEndpoint(endpoint: String) {
        dataStore.edit { it[KEY_API_ENDPOINT] = endpoint }
    }

    fun updateLocalMode(enabled: Boolean) {
        localModeOverride = enabled
    }

    suspend fun persistLocalMode(enabled: Boolean) {
        dataStore.edit { it[KEY_LOCAL_MODE] = enabled }
    }

    private companion object {
        val KEY_API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val KEY_LOCAL_MODE = booleanPreferencesKey("local_mode")
    }
}
