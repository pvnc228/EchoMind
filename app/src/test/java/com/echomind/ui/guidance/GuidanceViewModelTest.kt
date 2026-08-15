package com.echomind.ui.guidance

import com.echomind.data.guidance.GuidanceFeedbackStore
import com.echomind.data.guidance.GuidanceRating
import com.echomind.data.remote.GuidancePreview
import com.echomind.data.repository.GuidanceRequestResult
import com.echomind.domain.model.GuidanceRefusalReason
import com.echomind.domain.usecase.GuidanceUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val useCase: GuidanceUseCase = mockk()
    private val feedbackStore: GuidanceFeedbackStore = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unsafe prompt shows a refusal and no pending preview`() = runTest(dispatcher) {
        coEvery { useCase.request("diagnosis") } returns GuidanceRequestResult.Refused(
            GuidanceRefusalReason.UNSAFE_PROMPT
        )
        val viewModel = GuidanceViewModel(useCase, feedbackStore)

        viewModel.onInputChanged("diagnosis")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingPreview)
        assertTrue(viewModel.uiState.value.refusal!!.contains("cannot diagnose"))
    }

    @Test
    fun `ready request surfaces the exact preview for approval`() = runTest(dispatcher) {
        val preview = GuidancePreview(
            requestId = "guidance-1",
            purpose = "guidance_from_confirmed_conclusions",
            destination = "https://provider.example/v1/chat/completions",
            question = "planning",
            grounds = emptyList(),
            messages = emptyList(),
            sourceEntryIds = listOf(7L)
        )
        coEvery { useCase.request("planning") } returns GuidanceRequestResult.Ready(preview)
        val viewModel = GuidanceViewModel(useCase, feedbackStore)

        viewModel.onInputChanged("planning")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pendingPreview)
        assertNull(viewModel.uiState.value.refusal)
    }

    @Test
    fun `clearing the view model synchronously abandons its exact pending preview`() = runTest(dispatcher) {
        val preview = GuidancePreview(
            requestId = "guidance-1",
            purpose = "guidance_from_confirmed_conclusions",
            destination = "https://provider.example/v1/chat/completions",
            question = "planning",
            grounds = emptyList(),
            messages = emptyList(),
            sourceEntryIds = listOf(7L)
        )
        coEvery { useCase.request("planning") } returns GuidanceRequestResult.Ready(preview)
        every { useCase.cancelNow(any()) } returns Unit
        val viewModel = GuidanceViewModel(useCase, feedbackStore)

        viewModel.onInputChanged("planning")
        viewModel.sendMessage()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.pendingPreview)

        val store = ViewModelStore()
        store.put("guidance", viewModel)
        store.clear()

        verify(exactly = 1) { useCase.cancelNow("guidance-1") }
    }

    @Test
    fun `rating a message records opt-in feedback and marks it`() = runTest(dispatcher) {
        val preview = GuidancePreview(
            requestId = "guidance-1",
            purpose = "guidance_from_confirmed_conclusions",
            destination = "https://provider.example/v1/chat/completions",
            question = "planning",
            grounds = emptyList(),
            messages = emptyList(),
            sourceEntryIds = listOf(7L)
        )
        coEvery { useCase.request("planning") } returns GuidanceRequestResult.Ready(preview)
        coEvery { useCase.sendApproved("guidance-1") } returns Result.success(
            com.echomind.domain.usecase.GuidanceOutcome(answer = "cautious answer", sourceEntryIds = listOf(7L))
        )
        coEvery { feedbackStore.record("guidance-1", GuidanceRating.HELPFUL, null) } returns Unit
        val viewModel = GuidanceViewModel(useCase, feedbackStore)

        viewModel.onInputChanged("planning")
        viewModel.sendMessage()
        advanceUntilIdle()
        viewModel.approveRemoteRequest()
        advanceUntilIdle()

        val answer = viewModel.uiState.value.messages.first { !it.isUser }
        viewModel.rateMessage(answer.id, GuidanceRating.HELPFUL)
        advanceUntilIdle()

        val rated = viewModel.uiState.value.messages.first { !it.isUser }
        assertTrue(rated.hasFeedback)
        org.junit.Assert.assertEquals(GuidanceRating.HELPFUL, rated.rating)
        io.mockk.coVerify(exactly = 1) { feedbackStore.record("guidance-1", GuidanceRating.HELPFUL, null) }
    }
}
