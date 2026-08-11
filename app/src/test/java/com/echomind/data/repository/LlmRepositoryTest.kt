package com.echomind.data.repository

import com.echomind.data.analysis.SimpleTextAnalyzer
import com.echomind.data.remote.BaseUrlProvider
import com.echomind.data.remote.LlmApi
import com.echomind.data.remote.RemoteDestinationChangedException
import com.echomind.data.remote.dto.AnalysisResponse
import com.echomind.data.remote.dto.Choice
import com.echomind.data.remote.dto.Message
import com.echomind.data.settings.SettingsStore
import com.echomind.domain.model.KnowledgeSearchResult
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async

class LlmRepositoryTest {

    private val llmApi: LlmApi = mockk()
    private val settingsStore: SettingsStore = mockk()
    private val knowledgeRepository: KnowledgeRepository = mockk()
    private val baseUrlProvider = BaseUrlProvider()
    private lateinit var repository: LlmRepository

    @Before
    fun setup() {
        coEvery { settingsStore.isLocalMode() } returns true
        baseUrlProvider.updateUrl("https://provider.example")
        repository = LlmRepository(llmApi, SimpleTextAnalyzer(), settingsStore, knowledgeRepository, baseUrlProvider)
    }

    @Test
    fun `local mode analyzes offline without a network request`() = runTest {
        val entry = testEntry("I need to review the roadmap.")

        val result = repository.analyzeEntry(entry).getOrThrow()

        assertEquals("I need to review the roadmap.", result.summary)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `local mode blocks question network requests`() = runTest {
        val result = repository.askQuestion(listOf(Message("user", "What changed?")))

        assertTrue(result.exceptionOrNull() is AiNetworkDisabledException)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
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
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `remote mode blocks raw question context before the api`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false

        val result = repository.askQuestion(
            listOf(Message("system", "Raw private history"), Message("user", "What changed?"))
        )

        assertTrue(result.exceptionOrNull() is RemoteApprovalRequiredException)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
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

    @Test
    fun `preview includes only current conclusions and exact bounded payload`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false
        coEvery { knowledgeRepository.search("planning") } returns listOf(
            KnowledgeSearchResult.RawRecord(41L, 7L, "RAW PRIVATE TRANSCRIPT", 1L),
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true),
            KnowledgeSearchResult.Conclusion(8L, 10L, 7L, "historical private text", 1, 1L, false)
        )

        val preview = repository.previewQuestion(" planning ").getOrThrow()

        assertEquals("https://provider.example/v1/chat/completions", preview.destination)
        assertEquals("planning", preview.question)
        assertEquals(listOf(9L), preview.context.map { it.revisionId })
        assertEquals(listOf(7L), preview.sourceEntryIds)
        assertTrue(preview.messages.joinToString { it.content }.contains("confirmed planning conclusion"))
        assertTrue(preview.messages.none { it.content.contains("RAW PRIVATE TRANSCRIPT") })
        assertTrue(preview.messages.none { it.content.contains("historical private text") })
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `approval is one shot and stale approval cannot resend`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false
        coEvery { knowledgeRepository.search("planning") } returns listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true)
        )
        coEvery { llmApi.analyzeText(any(), any(), any()) } returns AnalysisResponse(
            choices = listOf(Choice(Message("assistant", "answer")))
        )

        val preview = repository.previewQuestion("planning").getOrThrow()
        val first = repository.sendApprovedQuestion(preview.requestId)
        val replay = repository.sendApprovedQuestion(preview.requestId)

        assertEquals("answer", first.getOrThrow().answer)
        assertTrue(replay.exceptionOrNull() is StaleRemoteConsentException)
        coVerify(exactly = 1) {
            llmApi.analyzeText(preview.destination, preview.destination, any())
        }
    }

    @Test
    fun `local mode blocks an approved preview before the api and consumes it`() = runTest {
        coEvery { settingsStore.isLocalMode() } returnsMany listOf(false, true)
        coEvery { knowledgeRepository.search("planning") } returns listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true)
        )

        val preview = repository.previewQuestion("planning").getOrThrow()
        val result = repository.sendApprovedQuestion(preview.requestId)
        val replay = repository.sendApprovedQuestion(preview.requestId)

        assertTrue(result.exceptionOrNull() is AiNetworkDisabledException)
        assertTrue(replay.exceptionOrNull() is StaleRemoteConsentException)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `provider failure consumes approval and leaves no replayable request`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false
        coEvery { knowledgeRepository.search("planning") } returns listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true)
        )
        coEvery { llmApi.analyzeText(any(), any(), any()) } throws IllegalStateException("provider unavailable")

        val preview = repository.previewQuestion("planning").getOrThrow()
        val result = repository.sendApprovedQuestion(preview.requestId)
        val replay = repository.sendApprovedQuestion(preview.requestId)

        assertEquals("provider unavailable", result.exceptionOrNull()?.message)
        assertTrue(replay.exceptionOrNull() is StaleRemoteConsentException)
        coVerify(exactly = 1) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `endpoint change after preview rejects approval without an api call and consumes it`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false
        baseUrlProvider.updateUrl("https://provider-a.example/api")
        coEvery { knowledgeRepository.search("planning") } returns listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true)
        )

        val preview = repository.previewQuestion("planning").getOrThrow()
        baseUrlProvider.updateUrl("https://provider-b.example/api")

        val result = repository.sendApprovedQuestion(preview.requestId)
        val replay = repository.sendApprovedQuestion(preview.requestId)

        assertTrue(result.exceptionOrNull() is RemoteDestinationChangedException)
        assertTrue(replay.exceptionOrNull() is StaleRemoteConsentException)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `slower older preview cannot replace newer preview consent`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false
        val slowStarted = CompletableDeferred<Unit>()
        val releaseSlowSearch = CompletableDeferred<Unit>()
        coEvery { knowledgeRepository.search("slow") } coAnswers {
            slowStarted.complete(Unit)
            releaseSlowSearch.await()
            listOf(KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "slow conclusion", 2, 2L, true))
        }
        coEvery { knowledgeRepository.search("fast") } returns listOf(
            KnowledgeSearchResult.Conclusion(8L, 10L, 7L, "fast conclusion", 2, 2L, true)
        )

        val slow = async { repository.previewQuestion("slow") }
        slowStarted.await()
        val fast = async { repository.previewQuestion("fast") }.await()
        releaseSlowSearch.complete(Unit)
        val slowResult = slow.await()

        assertTrue(fast.isSuccess)
        assertTrue(slowResult.exceptionOrNull() is StaleRemoteConsentException)
        assertEquals("fast", fast.getOrThrow().question)
    }

    @Test
    fun `preview refuses when search has no current confirmed conclusion`() = runTest {
        coEvery { settingsStore.isLocalMode() } returns false
        coEvery { knowledgeRepository.search("planning") } returns listOf(
            KnowledgeSearchResult.RawRecord(41L, 7L, "RAW PRIVATE TRANSCRIPT", 1L),
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "historical conclusion", 1, 1L, false)
        )

        val result = repository.previewQuestion("planning")

        assertTrue(result.exceptionOrNull() is NoConfirmedContextException)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
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
