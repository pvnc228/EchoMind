package com.echomind.ui.home

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import com.echomind.domain.usecase.GetEntriesUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val getEntriesUseCase: GetEntriesUseCase = mockk()
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
    fun `init loads entries`() = runTest(testDispatcher) {
        val entries = listOf(
            Entry(1, "Entry 1", null, 1000, 1000L, EntryCategory.GENERAL, emptyList(), "", emptyList(), emptyList(), emptyList()),
            Entry(2, "Entry 2", null, 2000, 2000L, EntryCategory.IDEA, emptyList(), "", emptyList(), emptyList(), emptyList())
        )
        coEvery { getEntriesUseCase.getAllEntries() } returns flowOf(entries)

        viewModel = HomeViewModel(getEntriesUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.entries.size)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `selectCategory filters entries`() = runTest(testDispatcher) {
        coEvery { getEntriesUseCase.getAllEntries() } returns flowOf(emptyList())
        val taskEntries = listOf(
            Entry(3, "Task", null, 1000, 1000L, EntryCategory.TASK, emptyList(), "", emptyList(), emptyList(), emptyList())
        )
        coEvery { getEntriesUseCase.getEntriesByCategory("task") } returns flowOf(taskEntries)

        viewModel = HomeViewModel(getEntriesUseCase)
        advanceUntilIdle()
        viewModel.selectCategory("task")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("task", state.selectedCategory)
        assertEquals(1, state.entries.size)
    }
}
