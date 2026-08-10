package com.echomind.data.followup

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

private val Context.followUpDataStore by preferencesDataStore(name = "follow_up")

@Serializable
private data class PersistedFollowUp(
    val decisionId: Long,
    val triggerAtMillis: Long,
    val status: String
)

@Serializable
private data class PersistedFollowUps(
    val records: List<PersistedFollowUp> = emptyList()
)

@Singleton
class FollowUpStore private constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.followUpDataStore)

    internal constructor(dataStore: DataStore<Preferences>, marker: Unit) : this(dataStore)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(decisionId: Long): FollowUpRecord? = readRecords()[decisionId]?.toDomain()

    suspend fun getFor(decisionIds: Collection<Long>): Map<Long, FollowUpRecord> {
        val ids = decisionIds.toSet()
        return readRecords()
            .filterKeys { it in ids }
            .mapValues { it.value.toDomain() }
    }

    suspend fun reserve(decisionId: Long, triggerAtMillis: Long): Boolean = update { records ->
        val existing = records[decisionId]
        if (existing == null || existing.status == FollowUpStatus.FAILED.name) {
            records[decisionId] = PersistedFollowUp(
                decisionId = decisionId,
                triggerAtMillis = triggerAtMillis,
                status = FollowUpStatus.PENDING.name
            )
            true
        } else {
            false
        }
    }

    suspend fun markScheduled(decisionId: Long): Boolean = transition(
        decisionId = decisionId,
        allowed = setOf(FollowUpStatus.PENDING),
        next = FollowUpStatus.SCHEDULED
    )

    suspend fun markFailed(decisionId: Long): Boolean = transition(
        decisionId = decisionId,
        allowed = setOf(FollowUpStatus.PENDING),
        next = FollowUpStatus.FAILED
    )

    suspend fun markPostponed(decisionId: Long, triggerAtMillis: Long): Boolean = update { records ->
        val existing = records[decisionId] ?: return@update false
        if (existing.status !in setOf(
                FollowUpStatus.SCHEDULED.name,
                FollowUpStatus.POSTPONED.name,
                FollowUpStatus.FIRED.name
            )
        ) {
            return@update false
        }
        records[decisionId] = existing.copy(
            triggerAtMillis = triggerAtMillis,
            status = FollowUpStatus.POSTPONED.name
        )
        true
    }

    suspend fun restoreScheduled(decisionId: Long, triggerAtMillis: Long): Boolean = update { records ->
        val existing = records[decisionId] ?: return@update false
        if (existing.status != FollowUpStatus.POSTPONED.name) return@update false
        records[decisionId] = existing.copy(
            triggerAtMillis = triggerAtMillis,
            status = FollowUpStatus.SCHEDULED.name
        )
        true
    }

    suspend fun markCanceled(decisionId: Long): Boolean = update { records ->
        val existing = records[decisionId] ?: return@update false
        if (existing.status in setOf(
                FollowUpStatus.CANCELED.name
            )
        ) {
            return@update false
        }
        records[decisionId] = existing.copy(status = FollowUpStatus.CANCELED.name)
        true
    }

    suspend fun markFired(decisionId: Long): Boolean = transition(
        decisionId = decisionId,
        allowed = setOf(FollowUpStatus.SCHEDULED, FollowUpStatus.POSTPONED),
        next = FollowUpStatus.FIRED
    )

    private suspend fun transition(
        decisionId: Long,
        allowed: Set<FollowUpStatus>,
        next: FollowUpStatus
    ): Boolean = update { records ->
        val existing = records[decisionId] ?: return@update false
        val current = existing.status.toStatus()
        if (current !in allowed) return@update false
        records[decisionId] = existing.copy(status = next.name)
        true
    }

    private suspend fun <T> update(transform: (MutableMap<Long, PersistedFollowUp>) -> T): T {
        var result: T? = null
        dataStore.edit { preferences ->
            val records = decode(preferences[RECORDS_KEY])
                .associateBy { it.decisionId }
                .toMutableMap()
            result = transform(records)
            preferences[RECORDS_KEY] = json.encodeToString(
                PersistedFollowUps.serializer(),
                PersistedFollowUps(records.values.sortedBy { it.decisionId })
            )
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private suspend fun readRecords(): Map<Long, PersistedFollowUp> =
        decode(dataStore.data.first()[RECORDS_KEY]).associateBy { it.decisionId }

    private fun decode(encoded: String?): List<PersistedFollowUp> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<PersistedFollowUps>(encoded).records
        }.getOrElse { error ->
            throw IllegalStateException("Follow-up state could not be read.", error)
        }
    }

    private fun PersistedFollowUp.toDomain(): FollowUpRecord = FollowUpRecord(
        decisionId = decisionId,
        triggerAtMillis = triggerAtMillis,
        status = status.toStatus()
    )

    private fun String.toStatus(): FollowUpStatus = runCatching {
        FollowUpStatus.valueOf(this)
    }.getOrElse { error ->
        throw IllegalStateException("Unknown follow-up state: $this", error)
    }

    private companion object {
        val RECORDS_KEY = stringPreferencesKey("records")
    }
}
