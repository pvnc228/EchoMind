package com.echomind.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.echomind.data.local.converter.Converters

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "transcript")
    val transcript: String,
    @ColumnInfo(name = "audio_path")
    val audioPath: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "category")
    val category: String = "general",
    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),
    @ColumnInfo(name = "summary")
    val summary: String = "",
    @ColumnInfo(name = "tasks")
    val tasks: List<String> = emptyList(),
    @ColumnInfo(name = "ideas")
    val ideas: List<String> = emptyList(),
    @ColumnInfo(name = "emotions")
    val emotions: List<String> = emptyList()
)
