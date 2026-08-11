package com.echomind.domain.usecase

import com.echomind.data.remote.RemoteQuestionPreview
import com.echomind.data.repository.EntryRepository
import com.echomind.data.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AskQuestionUseCaseTest {

    private val llmRepository: LlmRepository = mockk()
    private lateinit var useCase: AskQuestionUseCase

    @Before
    fun setup() {
        useCase = AskQuestionUseCase(mockk<EntryRepository>(), llmRepository)
    }

    @Test
    fun `preview trims the question and delegates to the consent seam`() = runTest {
        coEvery { llmRepository.previewQuestion("planning") } returns Result.success(
            RemoteQuestionPreview(
                requestId = "request-1",
                purpose = "answer_question_from_confirmed_conclusions",
                destination = "https://provider.example",
                question = "planning",
                context = emptyList(),
                messages = emptyList(),
                sourceEntryIds = listOf(7L)
            )
        )

        val preview = useCase.preview("planning").getOrThrow()

        assertEquals("planning", preview.question)
        assertTrue(preview.context.isEmpty())
        coVerify(exactly = 1) { llmRepository.previewQuestion("planning") }
    }

    @Test
    fun `blank question never reaches the remote preview seam`() = runTest {
        val result = useCase.preview("  ")

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { llmRepository.previewQuestion(any()) }
    }
}
