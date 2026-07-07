package com.echomind.data.local.converter

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromTimestamp(value: Long?): Long? = value

    @TypeConverter
    fun toTimestamp(value: Long?): Long? = value

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (_: Exception) {
            value.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
    }

    @TypeConverter
    fun toStringList(value: List<String>?): String {
        if (value.isNullOrEmpty()) return ""
        return json.encodeToString(value)
    }
}
