package com.echomind.domain.usecase

import com.echomind.data.repository.EntryRepository
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetEntriesUseCaseTest {

    private val repository: EntryRepository = mockk()
    private lateinit var useCase: GetEntriesUseCase

    @Before
    fun setup() {
        useCase = GetEntriesUseCase(repository)
    }

    @Test
    fun `getAllEntries returns entries from repository`() = runTest {
        val entries = listOf(
            Entry(1, "Test transcript", null, 1000, 1000L, EntryCategory.GENERAL, emptyList(), "", emptyList(), emptyList(), emptyList())
        )
        coEvery { repository.getAllEntries() } returns flowOf(entries)

        val result = useCase.getAllEntries().first()

        assertEquals(1, result.size)
        assertEquals("Test transcript", result[0].transcript)
    }

    @Test
    fun `getEntriesByCategory filters by category`() = runTest {
        val entries = listOf(
            Entry(2, "Task entry", null, 1000, 1000L, EntryCategory.TASK, emptyList(), "", emptyList(), emptyList(), emptyList())
        )
        coEvery { repository.getEntriesByCategory("task") } returns flowOf(entries)

        val result = useCase.getEntriesByCategory("task").first()

        assertEquals(1, result.size)
        assertEquals(EntryCategory.TASK, result[0].category)
    }

    @Test
    fun `searchEntries returns matching entries`() = runTest {
        val entries = listOf(
            Entry(3, "Search result", null, 1000, 1000L, EntryCategory.GENERAL, emptyList(), "", emptyList(), emptyList(), emptyList())
        )
        coEvery { repository.searchEntries("search") } returns flowOf(entries)

        val result = useCase.searchEntries("search").first()

        assertEquals(1, result.size)
    }
}
