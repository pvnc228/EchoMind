package com.echomind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.echomind.data.local.converter.Converters
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.entity.EntryEntity

@Database(
    entities = [EntryEntity::class],
    version = 2,
    exportSchema = false
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
}
