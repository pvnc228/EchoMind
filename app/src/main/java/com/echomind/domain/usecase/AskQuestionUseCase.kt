package com.echomind.domain.usecase

import com.echomind.data.remote.RemoteQuestionPreview
import com.echomind.data.repository.LlmRepository
import javax.inject.Inject

data class QaResult(
    val answer: String,
    val sourceEntryIds: List<Long>
)

class AskQuestionUseCase @Inject constructor(
    private val llmRepository: LlmRepository
) {

    suspend fun preview(question: String): Result<RemoteQuestionPreview> = runCatching {
        val trimmedQuestion = question.trim()
        require(trimmedQuestion.isNotBlank()) { "A question cannot be blank." }
        llmRepository.previewQuestion(trimmedQuestion).getOrThrow()
    }

    suspend fun sendApproved(previewId: String): Result<QaResult> =
        llmRepository.sendApprovedQuestion(previewId).map { answer ->
            QaResult(answer = answer.answer, sourceEntryIds = answer.sourceEntryIds)
        }

    suspend fun cancel(previewId: String) = llmRepository.cancelQuestionPreview(previewId)

    fun cancelNow(previewId: String) = llmRepository.cancelQuestionPreviewNow(previewId)

}
