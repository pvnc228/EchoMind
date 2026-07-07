package com.echomind.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.echomind.data.local.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY created_at DESC")
    fun getAllEntries(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getEntryById(id: Long): EntryEntity?

    @Query("SELECT * FROM entries WHERE category = :category ORDER BY created_at DESC")
    fun getEntriesByCategory(category: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE transcript LIKE '%' || :query || '%' ORDER BY created_at DESC")
    fun searchEntries(query: String): Flow<List<EntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: EntryEntity): Long

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    @Delete
    suspend fun deleteEntry(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("SELECT * FROM entries ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<EntryEntity>

    @Query("SELECT * FROM entries ORDER BY created_at DESC")
    suspend fun getAllEntriesOnce(): List<EntryEntity>
}
