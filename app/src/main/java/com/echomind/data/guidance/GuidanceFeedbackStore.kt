package com.echomind.data.guidance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.guidanceFeedbackDataStore by preferencesDataStore(name = "guidance_feedback")

enum class GuidanceRating { HELPFUL, NOT_HELPFUL }

@Serializable
private data class PersistedFeedback(
    val requestId: String,
    val rating: String,
    val outcome: String? = null
)

@Serializable
private data class PersistedFeedbackList(
    val records: List<PersistedFeedback> = emptyList()
)

/**
 * Opt-in, local-only guidance usefulness feedback. Storing a rating never grants
 * transmission permission and carries no obligation; the user may also report an
 * eventual outcome without it triggering any automatic model change.
 */
@Singleton
class GuidanceFeedbackStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.guidanceFeedbackDataStore
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun record(requestId: String, rating: GuidanceRating, outcome: String? = null) {
        val trimmedOutcome = outcome?.trim()?.takeIf { it.isNotBlank() }
        dataStore.edit { preferences ->
            val records = decode(preferences[RECORDS_KEY])
                .filterNot { it.requestId == requestId }
                .toMutableList()
            records.add(
                PersistedFeedback(
                    requestId = requestId,
                    rating = rating.name,
                    outcome = trimmedOutcome
                )
            )
            preferences[RECORDS_KEY] = json.encodeToString(
                PersistedFeedbackList.serializer(),
                PersistedFeedbackList(records)
            )
        }
    }

    suspend fun hasRated(requestId: String): Boolean =
        decode(dataStore.data.first()[RECORDS_KEY]).any { it.requestId == requestId }

    private fun decode(encoded: String?): List<PersistedFeedback> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<PersistedFeedbackList>(encoded).records
        }.getOrElse { error ->
            throw IllegalStateException("Guidance feedback could not be read.", error)
        }
    }

    private companion object {
        val RECORDS_KEY = stringPreferencesKey("records")
    }
}
