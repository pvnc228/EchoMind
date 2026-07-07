package com.echomind.domain.usecase

import com.echomind.data.repository.LlmRepository
import com.echomind.domain.model.Entry
import javax.inject.Inject

class AnalyzeEntryUseCase @Inject constructor(
    private val llmRepository: LlmRepository
) {
    suspend operator fun invoke(entry: Entry): Result<Entry> =
        llmRepository.analyzeEntry(entry)
}
