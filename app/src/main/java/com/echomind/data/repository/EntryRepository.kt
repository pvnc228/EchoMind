package com.echomind.data.repository

import androidx.room.withTransaction
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.entity.EntryEntity
import com.echomind.data.local.entity.RawRecordEntity
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val database: AppDatabase,
    private val entryDao: EntryDao,
    private val knowledgeDao: KnowledgeDao
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
            database.withTransaction {
                val entryId = entryDao.insertEntry(entry.toEntity())
                knowledgeDao.insertRawRecord(entry.toRawRecordEntity(entryId))
            }
        } else {
            entryDao.updateEntry(entry.toEntity())
        }
    }

    suspend fun deleteEntry(id: Long) {
        val audioPath = entryDao.getEntryById(id)?.audioPath
        database.withTransaction {
            knowledgeDao.deleteRawRecordByLegacyEntryId(id)
            entryDao.deleteEntryById(id)
        }
        // ponytail: DB and filesystem deletion cannot be atomic; add orphan cleanup if failures appear.
        audioPath?.let { File(it).delete() }
    }

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

    private fun Entry.toRawRecordEntity(entryId: Long) = RawRecordEntity(
        legacyEntryId = entryId,
        originalText = transcript,
        audioPath = audioPath,
        durationMs = durationMs,
        createdAt = createdAt
    )
}
