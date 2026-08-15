package com.echomind.domain.usecase

import com.echomind.data.remote.GuidanceAnswer
import com.echomind.data.repository.GuidanceRepository
import com.echomind.data.repository.GuidanceRequestResult
import javax.inject.Inject

data class GuidanceOutcome(
    val answer: String,
    val sourceEntryIds: List<Long>
)
class GuidanceUseCase @Inject constructor(
    private val guidanceRepository: GuidanceRepository
) {
    suspend fun request(question: String): GuidanceRequestResult {
        val trimmed = question.trim()
        if (trimmed.isBlank()) {
            return GuidanceRequestResult.Refused(
                com.echomind.domain.model.GuidanceRefusalReason.INSUFFICIENT_EVIDENCE,
                focusedQuestion = "What concrete decision or question do you want to think through?"
            )
        }
        return guidanceRepository.requestGuidanceSafely(trimmed)
    }

    suspend fun sendApproved(previewId: String): Result<GuidanceOutcome> =
        guidanceRepository.sendApprovedGuidance(previewId).map { answer ->
            GuidanceOutcome(answer = answer.answer, sourceEntryIds = answer.sourceEntryIds)
        }

    suspend fun cancel(previewId: String) = guidanceRepository.cancelGuidancePreview(previewId)

    fun cancelNow(previewId: String) = guidanceRepository.cancelGuidancePreviewNow(previewId)
}
