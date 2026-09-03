package com.echomind.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import com.echomind.data.remote.RemoteAccessPolicy
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

import com.echomind.domain.model.TranscriptionEngine

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private const val DEFAULT_API_ENDPOINT = "http://localhost:1234"

data class StoredSettings(
    val apiEndpoint: String = DEFAULT_API_ENDPOINT,
    val localMode: Boolean = true,
    val transcriptionEngine: TranscriptionEngine = TranscriptionEngine.ON_DEVICE
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val remoteAccessPolicy: RemoteAccessPolicy
) {
    constructor(context: Context) : this(context, RemoteAccessPolicy())
    private val dataStore = context.settingsDataStore
    suspend fun load(): StoredSettings {
        val preferences = try {
            dataStore.data.first()
        } catch (_: IOException) {
            return StoredSettings(
                apiEndpoint = remoteAccessPolicy.endpoint(),
                localMode = remoteAccessPolicy.isLocalMode()
            )
        }
        val engineName = preferences[KEY_TRANSCRIPTION_ENGINE]
        val engine = engineName?.let {
            runCatching { TranscriptionEngine.valueOf(it) }.getOrNull()
        } ?: TranscriptionEngine.ON_DEVICE


        val settings = StoredSettings(
            apiEndpoint = preferences[KEY_API_ENDPOINT] ?: DEFAULT_API_ENDPOINT,
            localMode = preferences[KEY_LOCAL_MODE] ?: true,
            transcriptionEngine = engine
        )
        remoteAccessPolicy.hydratePersisted(settings)
        return settings.copy(
            apiEndpoint = remoteAccessPolicy.endpoint(),
            localMode = remoteAccessPolicy.isLocalMode(),
            transcriptionEngine = engine
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

    /**
     * Old suppression records were keyed only by theme ID and cannot be safely mapped to the
     * new graph fingerprint. Clear them once and let the caller show a non-blocking notice.
     */
    suspend fun resetLegacySuppressionsIfNeeded(): Boolean {
        var reset = false
        dataStore.edit { preferences ->
            if (preferences[KEY_LEGACY_SUPPRESSION_RESET] != true) {
                reset = preferences.contains(KEY_SUPPRESSED_CARDS)
                preferences.remove(KEY_SUPPRESSED_CARDS)
                preferences[KEY_LEGACY_SUPPRESSION_RESET] = true
            }
        }
        return reset
    }

    suspend fun isLocalMode(): Boolean = remoteAccessPolicy.isLocalMode()

    suspend fun setApiEndpoint(endpoint: String) {
        remoteAccessPolicy.updateEndpoint(endpoint)
        dataStore.edit { it[KEY_API_ENDPOINT] = endpoint }
    }

    fun updateLocalMode(enabled: Boolean) {
        remoteAccessPolicy.updateLocalMode(enabled)
    }

    suspend fun persistLocalMode(enabled: Boolean) {
        dataStore.edit { it[KEY_LOCAL_MODE] = enabled }
    }

    suspend fun setTranscriptionEngine(engine: TranscriptionEngine) {
        dataStore.edit { it[KEY_TRANSCRIPTION_ENGINE] = engine.name }
    }

    private companion object {
        val KEY_API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val KEY_LOCAL_MODE = booleanPreferencesKey("local_mode")
        val KEY_TRANSCRIPTION_ENGINE = stringPreferencesKey("transcription_engine")
        val KEY_SUPPRESSED_CARDS = stringPreferencesKey("suppressed_cards")
        val KEY_LEGACY_SUPPRESSION_RESET = booleanPreferencesKey("legacy_suppression_reset_v6")
    }
}
