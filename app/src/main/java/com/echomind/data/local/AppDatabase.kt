package com.echomind.data.local

import androidx.room.Database
import androidx.room.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v1 had the same schema as v2. The only change is
                // the addition of SQLCipher encryption at the database level.
                // SQLCipher's SupportFactory transparently re-encrypts
                // the existing data when the database is opened.
                // No schema DDL changes needed.
            }
        }
    }
}
