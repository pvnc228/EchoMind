package com.echomind.domain.usecase

import com.echomind.data.repository.EntryRepository
import com.echomind.domain.model.Entry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetEntriesUseCase @Inject constructor(
    private val repository: EntryRepository
) {
    fun getAllEntries(): Flow<List<Entry>> = repository.getAllEntries()

    fun getEntriesByCategory(category: String): Flow<List<Entry>> =
        repository.getEntriesByCategory(category)

    fun searchEntries(query: String): Flow<List<Entry>> =
        repository.searchEntries(query)

    suspend fun getRecentEntries(limit: Int): List<Entry> =
        repository.getRecentEntries(limit)
}
