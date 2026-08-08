package com.echomind.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.echomind.data.repository.KnowledgeRepository
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import com.echomind.domain.model.HomeCard
import com.echomind.domain.model.HomeCardType
import com.echomind.domain.model.HomeRelevance
import com.echomind.domain.usecase.GetEntriesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val getEntriesUseCase: GetEntriesUseCase = mockk()
    private val knowledgeRepository: KnowledgeRepository = mockk()
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads recent entries and relevance without legacy timeline collector`() = runTest(testDispatcher) {
        val entries = listOf(
            Entry(1, "Entry 1", null, 1000, 1000L, EntryCategory.GENERAL, emptyList(), "", emptyList(), emptyList(), emptyList()),
            Entry(2, "Entry 2", null, 2000, 2000L, EntryCategory.IDEA, emptyList(), "", emptyList(), emptyList(), emptyList())
        )
        coEvery { getEntriesUseCase.getRecentEntries(any()) } returns entries
        coEvery { knowledgeRepository.getHomeRelevance() } returns com.echomind.domain.model.HomeRelevance(hasKnowledge = false)

        viewModel = HomeViewModel(getEntriesUseCase, knowledgeRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.recent.size)
        assertFalse(state.isLoading)
        coVerify(exactly = 0) { getEntriesUseCase.getAllEntries() }
    }

    @Test
    fun `refresh does not expose legacy category selection`() = runTest(testDispatcher) {
        coEvery { getEntriesUseCase.getRecentEntries(any()) } returns emptyList()
        coEvery { knowledgeRepository.getHomeRelevance() } returns com.echomind.domain.model.HomeRelevance(hasKnowledge = false)

        viewModel = HomeViewModel(getEntriesUseCase, knowledgeRepository)
        advanceUntilIdle()
        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.recent.isEmpty())
        coVerify(exactly = 0) { getEntriesUseCase.getEntriesByCategory(any()) }
    }

    @Test
    fun `dismissCard clears card and calls repository`() = runTest(testDispatcher) {
        coEvery { getEntriesUseCase.getRecentEntries(any()) } returns emptyList()
        val card = HomeCard(
            type = HomeCardType.SUPPORTED_THEME,
            themeId = 7,
            themeName = "A",
            title = "T",
            detail = "D",
            reason = "R",
            capability = com.echomind.domain.model.Capability.CONNECTION
        )
        coEvery { knowledgeRepository.getHomeRelevance() } returns HomeRelevance(card = card, hasKnowledge = true)
        coEvery { knowledgeRepository.dismissCard(card) } returns Unit

        viewModel = HomeViewModel(getEntriesUseCase, knowledgeRepository)
        advanceUntilIdle()
        assertEquals(card, viewModel.uiState.value.card)

        viewModel.dismissCard()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.card)
        coVerify { knowledgeRepository.dismissCard(card) }
    }
}
