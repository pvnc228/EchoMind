package com.echomind.di

import android.content.Context
import androidx.room.Room
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.local.security.PassphraseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    const val DATABASE_NAME = "echomind.db"

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: PassphraseProvider
    ): AppDatabase {
        val passphrase = passphraseProvider.getPassphrase()
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        ensureDatabaseKeyCompatibility(context, dbFile, passphrase)

        val supportFactory = SupportFactory(passphrase)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME
        ).openHelperFactory(supportFactory)
            .addMigrations(
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9
            )
            .build()
    }

    internal fun ensureDatabaseKeyCompatibility(
        context: Context,
        dbFile: File,
        targetPassphrase: ByteArray
    ) {
        if (!dbFile.exists() || dbFile.length() == 0L) return

        SQLiteDatabase.loadLibs(context)
        var opensWithTargetKey = false
        try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                targetPassphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null,
                null
            )
            try {
                db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                    cursor.moveToFirst()
                }
                opensWithTargetKey = true
            } finally {
                db.close()
            }
        } catch (_: Exception) {
            opensWithTargetKey = false
        }

        if (opensWithTargetKey) return

        val zeroKey = ByteArray(32)
        try {
            val zeroDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                zeroKey,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null,
                null
            )
            try {
                zeroDb.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                    cursor.moveToFirst()
                }
                try {
                    val rekeyMethod = SQLiteDatabase::class.java.getDeclaredMethod("rekey", ByteArray::class.java).apply {
                        isAccessible = true
                    }
                    rekeyMethod.invoke(zeroDb, targetPassphrase)
                    android.util.Log.i("DatabaseModule", "Successfully rekeyed legacy zero-key database via native rekey")
                } catch (rekeyEx: Exception) {
                    android.util.Log.w("DatabaseModule", "Native rekey failed, falling back to PRAGMA rekey", rekeyEx)
                    val hexPassphrase = targetPassphrase.joinToString("") { "%02x".format(it) }
                    zeroDb.rawExecSQL("PRAGMA rekey = \"x'$hexPassphrase'\"")
                }
            } finally {
                zeroDb.close()
            }
        } catch (e: Exception) {
            android.util.Log.w("DatabaseModule", "Zero-key compatibility check/rekey failed: ${e.message}", e)
        } finally {
            zeroKey.fill(0)
        }
    }

    @Provides
    @Singleton
    fun provideEntryDao(database: AppDatabase): EntryDao = database.entryDao()

    @Provides
    @Singleton
    fun provideKnowledgeDao(database: AppDatabase): KnowledgeDao = database.knowledgeDao()
}
