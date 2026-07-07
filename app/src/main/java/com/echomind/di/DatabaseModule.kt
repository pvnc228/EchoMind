package com.echomind.di

import android.content.Context
import androidx.room.Room
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.dao.EntryDao
import com.echomind.data.local.security.PassphraseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: PassphraseProvider
    ): AppDatabase {
        val passphrase = passphraseProvider.getPassphrase()
        val supportFactory = SupportFactory(passphrase)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "echomind.db"
        ).openHelperFactory(supportFactory)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideEntryDao(database: AppDatabase): EntryDao = database.entryDao()
}
