package com.echomind.data.repository

import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.entity.EntryEntity
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao
) {
    fun getAllEntries(): Flow<List<Entry>> =
        entryDao.getAllEntries().map { entities -> entities.map { it.toDomain() } }

    fun getEntriesByCategory(category: String): Flow<List<Entry>> =
        entryDao.getEntriesByCategory(category).map { entities -> entities.map { it.toDomain() } }

    fun searchEntries(query: String): Flow<List<Entry>> =
        entryDao.searchEntries(query).map { entities -> entities.map { it.toDomain() } }

    suspend fun getEntryById(id: Long): Entry? =
        entryDao.getEntryById(id)?.toDomain()

    suspend fun saveEntry(entry: Entry) {
        if (entry.id == 0L) {
            entryDao.insertEntry(entry.toEntity())
        } else {
            entryDao.updateEntry(entry.toEntity())
        }
    }

    suspend fun deleteEntry(id: Long) =
        entryDao.deleteEntryById(id)

    suspend fun getRecentEntries(limit: Int): List<Entry> =
        entryDao.getRecentEntries(limit).map { it.toDomain() }

    private fun EntryEntity.toDomain() = Entry(
        id = id,
        transcript = transcript,
        audioPath = audioPath,
        durationMs = durationMs,
        createdAt = createdAt,
        category = EntryCategory.fromString(category),
        tags = tags,
        summary = summary,
        tasks = tasks,
        ideas = ideas,
        emotions = emotions
    )

    private fun Entry.toEntity() = EntryEntity(
        id = id,
        transcript = transcript,
        audioPath = audioPath,
        durationMs = durationMs,
        createdAt = createdAt,
        category = category.name.lowercase(),
        tags = tags,
        summary = summary,
        tasks = tasks,
        ideas = ideas,
        emotions = emotions
    )
}
