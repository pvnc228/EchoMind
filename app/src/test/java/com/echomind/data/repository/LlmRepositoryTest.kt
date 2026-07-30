package com.echomind.data.repository

import com.echomind.data.analysis.SimpleTextAnalyzer
import com.echomind.data.remote.LlmApi
import com.echomind.data.remote.dto.Message
import com.echomind.data.settings.SettingsStore
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class LlmRepositoryTest {

    private val llmApi: LlmApi = mockk()
    private val settingsStore: SettingsStore = mockk()
    private lateinit var repository: LlmRepository

    @Before
    fun setup() {
        coEvery { settingsStore.isLocalMode() } returns true
        repository = LlmRepository(llmApi, SimpleTextAnalyzer(), settingsStore)
    }

    @Test
    fun `local mode analyzes offline without a network request`() = runTest {
        val entry = testEntry("I need to review the roadmap.")

        val result = repository.analyzeEntry(entry).getOrThrow()

        assertEquals("I need to review the roadmap.", result.summary)
        coVerify(exactly = 0) { llmApi.analyzeText(any()) }
    }

    @Test
    fun `local mode blocks question network requests`() = runTest {
        val result = repository.askQuestion(listOf(Message("user", "What changed?")))

        assertTrue(result.exceptionOrNull() is AiNetworkDisabledException)
        coVerify(exactly = 0) { llmApi.analyzeText(any()) }
    }

    @Test
    fun `local mode blocks transcription network requests`() = runTest {
        val audioFile = File.createTempFile("echomind-test", ".m4a")
        try {
            val result = repository.transcribeAudio(audioFile)

            assertTrue(result.exceptionOrNull() is AiNetworkDisabledException)
            coVerify(exactly = 0) { llmApi.transcribeAudio(any(), any(), any()) }
        } finally {
            audioFile.delete()
        }
    }

    @Test
    fun `remote mode still analyzes raw entries only on device`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false

        val result = repository.analyzeEntry(testEntry("Raw private thought.")).getOrThrow()

        assertEquals("Raw private thought.", result.summary)
        coVerify(exactly = 0) { llmApi.analyzeText(any()) }
    }

    @Test
    fun `remote mode blocks raw question context before the api`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false

        val result = repository.askQuestion(
            listOf(Message("system", "Raw private history"), Message("user", "What changed?"))
        )

        assertTrue(result.exceptionOrNull() is RemoteApprovalRequiredException)
        coVerify(exactly = 0) { llmApi.analyzeText(any()) }
    }

    @Test
    fun `remote mode blocks raw audio before the api`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false
        val audioFile = File.createTempFile("echomind-private", ".m4a")
        try {
            val result = repository.transcribeAudio(audioFile)

            assertTrue(result.exceptionOrNull() is RemoteApprovalRequiredException)
            coVerify(exactly = 0) { llmApi.transcribeAudio(any(), any(), any()) }
        } finally {
            audioFile.delete()
        }
    }

    private fun testEntry(transcript: String) = Entry(
        transcript = transcript,
        audioPath = null,
        durationMs = 0,
        createdAt = 1,
        category = EntryCategory.GENERAL,
        tags = emptyList(),
        summary = "",
        tasks = emptyList(),
        ideas = emptyList(),
        emotions = emptyList()
    )
}
