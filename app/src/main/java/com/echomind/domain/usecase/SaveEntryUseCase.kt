package com.echomind.domain.usecase

import com.echomind.data.repository.EntryRepository
import com.echomind.domain.model.Entry
import javax.inject.Inject

class SaveEntryUseCase @Inject constructor(
    private val repository: EntryRepository
) {
    suspend operator fun invoke(entry: Entry) {
        repository.saveEntry(entry)
    }
}
