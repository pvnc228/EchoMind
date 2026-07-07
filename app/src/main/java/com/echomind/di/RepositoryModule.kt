package com.echomind.di

import com.echomind.data.repository.EntryRepository
import com.echomind.data.repository.LlmRepository
import com.echomind.domain.usecase.AnalyzeEntryUseCase
import com.echomind.domain.usecase.GetEntriesUseCase
import com.echomind.domain.usecase.SaveEntryUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSaveEntryUseCase(repository: EntryRepository): SaveEntryUseCase =
        SaveEntryUseCase(repository)

    @Provides
    @Singleton
    fun provideGetEntriesUseCase(repository: EntryRepository): GetEntriesUseCase =
        GetEntriesUseCase(repository)

    @Provides
    @Singleton
    fun provideAnalyzeEntryUseCase(repository: LlmRepository): AnalyzeEntryUseCase =
        AnalyzeEntryUseCase(repository)
}
