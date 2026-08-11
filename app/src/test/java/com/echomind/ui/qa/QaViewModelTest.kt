package com.echomind.ui.qa

import com.echomind.data.remote.RemoteQuestionPreview
import com.echomind.domain.usecase.AskQuestionUseCase
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QaViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val useCase: AskQuestionUseCase = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clearing the view model synchronously abandons its exact pending preview`() = runTest(dispatcher) {
        val preview = RemoteQuestionPreview(
            requestId = "request-1",
            purpose = "answer_question_from_confirmed_conclusions",
            destination = "https://provider.example/v1/chat/completions",
            question = "planning",
            context = emptyList(),
            messages = emptyList(),
            sourceEntryIds = emptyList()
        )
        coEvery { useCase.preview("planning") } returns Result.success(preview)
        every { useCase.cancelNow(any()) } returns Unit
        val viewModel = QaViewModel(useCase)

        viewModel.onInputChanged("planning")
        viewModel.sendMessage()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.pendingPreview)

        val store = ViewModelStore()
        store.put("qa", viewModel)
        store.clear()

        verify(exactly = 1) { useCase.cancelNow("request-1") }
    }
}
