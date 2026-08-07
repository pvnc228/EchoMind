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

    suspend fun getSuppressedCards(): Map<Long, Long> {
        val prefs = try {
            dataStore.data.first()
        } catch (_: IOException) {
            return emptyMap()
        }
        val raw = prefs[KEY_SUPPRESSED_CARDS] ?: return emptyMap()
        return raw.split(",")
            .mapNotNull { part ->
                val idx = part.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                val id = part.substring(0, idx).toLongOrNull() ?: return@mapNotNull null
                val until = part.substring(idx + 1).toLongOrNull() ?: return@mapNotNull null
                id to until
            }
            .toMap()
    }

    suspend fun suppressCard(themeId: Long, until: Long) {
        dataStore.edit {
            val raw = it[KEY_SUPPRESSED_CARDS].orEmpty()
            val entries = raw.split(",").filter { p -> p.isNotBlank() && !p.startsWith("$themeId:") }.toMutableList()
            entries.add("$themeId:$until")
            it[KEY_SUPPRESSED_CARDS] = entries.joinToString(",")
        }
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
        val KEY_SUPPRESSED_CARDS = stringPreferencesKey("suppressed_cards")
    }
}
