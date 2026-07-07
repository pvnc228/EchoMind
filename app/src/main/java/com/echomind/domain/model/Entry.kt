package com.echomind.domain.model

data class Entry(
    val id: Long = 0,
    val transcript: String,
    val audioPath: String?,
    val durationMs: Long,
    val createdAt: Long,
    val category: EntryCategory,
    val tags: List<String>,
    val summary: String,
    val tasks: List<String>,
    val ideas: List<String>,
    val emotions: List<String>
)
