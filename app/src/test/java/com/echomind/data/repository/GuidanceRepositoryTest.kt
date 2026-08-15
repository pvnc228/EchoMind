package com.echomind.data.repository

import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.remote.BaseUrlProvider
import com.echomind.data.remote.GuidanceAnswer
import com.echomind.data.remote.LlmApi
import com.echomind.data.remote.RemoteAccessPolicy
import com.echomind.data.remote.dto.AnalysisResponse
import com.echomind.data.remote.dto.Choice
import com.echomind.data.remote.dto.Message
import com.echomind.data.settings.SettingsStore
import com.echomind.data.settings.StoredSettings
import com.echomind.domain.model.GuidanceRefusalReason
import com.echomind.domain.model.KnowledgeSearchResult
import com.echomind.domain.model.RelatedRecord
import com.echomind.domain.model.Relationship
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GuidanceRepositoryTest {

    private val llmApi: LlmApi = mockk()
    private val settingsStore: SettingsStore = mockk()
    private val knowledgeRepository: KnowledgeRepository = mockk()
    private val knowledgeDao: KnowledgeDao = mockk()
    private val remoteAccessPolicy = RemoteAccessPolicy()
    private val baseUrlProvider = BaseUrlProvider(remoteAccessPolicy)
    private lateinit var repository: GuidanceRepository

    @Before
    fun setup() {
        coEvery { settingsStore.load() } returns StoredSettings(
            apiEndpoint = "https://provider.example",
            localMode = false
        )
        coEvery { settingsStore.isLocalMode() } returns false
        baseUrlProvider.updateUrl("https://provider.example")
        repository = GuidanceRepository(
            llmApi = llmApi,
            settingsStore = settingsStore,
            knowledgeRepository = knowledgeRepository,
            knowledgeDao = knowledgeDao,
            baseUrlProvider = baseUrlProvider
        )
    }

    @Test
    fun `diagnosis question is refused locally without a network call`() = runTest {
        val result = repository.requestGuidance("Does my diary show that I have depression?")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(GuidanceRefusalReason.UNSAFE_PROMPT, (result as GuidanceRequestResult.Refused).reason)
        coVerify(exactly = 0) { knowledgeRepository.search(any()) }
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `hidden motive question is refused locally`() = runTest {
        val result = repository.requestGuidance("What is his hidden motive for acting this way?")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(GuidanceRefusalReason.UNSAFE_PROMPT, (result as GuidanceRequestResult.Refused).reason)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `unsupported certainty demand is refused locally`() = runTest {
        val result = repository.requestGuidance("Tell me with 100% certainty what I should choose")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(GuidanceRefusalReason.UNSAFE_PROMPT, (result as GuidanceRequestResult.Refused).reason)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `uncertain phrasing is not refused as a certainty demand`() = runTest {
        coEvery { knowledgeRepository.search(any()) } returns emptyList()

        val result = repository.requestGuidance("I am uncertain whether to switch jobs")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(
            GuidanceRefusalReason.INSUFFICIENT_EVIDENCE,
            (result as GuidanceRequestResult.Refused).reason
        )
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `motivation phrasing is not refused as a hidden motive`() = runTest {
        coEvery { knowledgeRepository.search(any()) } returns emptyList()

        val result = repository.requestGuidance("How do I keep my motivation for studying?")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(
            GuidanceRefusalReason.INSUFFICIENT_EVIDENCE,
            (result as GuidanceRequestResult.Refused).reason
        )
    }

    @Test
    fun `russian diagnosis question is refused locally`() = runTest {
        val result = repository.requestGuidance("Есть ли у меня депрессия по моим записям?")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(GuidanceRefusalReason.UNSAFE_PROMPT, (result as GuidanceRequestResult.Refused).reason)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `blank guidance question is refused without a network call`() = runTest {
        val result = repository.requestGuidance("   ")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(
            GuidanceRefusalReason.INSUFFICIENT_EVIDENCE,
            (result as GuidanceRequestResult.Refused).reason
        )
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `insufficient evidence returns a focused refusal without a network call`() = runTest {
        coEvery { knowledgeRepository.search("planning") } returns listOf(
            KnowledgeSearchResult.RawRecord(41L, 7L, "RAW PRIVATE TRANSCRIPT", 1L),
            KnowledgeSearchResult.Conclusion(8L, 10L, 7L, "historical private text", 1, 1L, false)
        )

        val result = repository.requestGuidance("planning")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(GuidanceRefusalReason.INSUFFICIENT_EVIDENCE, (result as GuidanceRequestResult.Refused).reason)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `ready preview includes citations counterevidence and outcomes and sends nothing yet`() = runTest {
        coEvery { knowledgeRepository.search("planning") } returns listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true)
        )
        coEvery { knowledgeRepository.getRelatedRecords(9L) } returns listOf(
            RelatedRecord(
                rawRecordId = 100L,
                relationship = Relationship.CONTRADICTS,
                sourceText = "an opposing planning record",
                recordedAt = 1L
            ),
            RelatedRecord(
                rawRecordId = 101L,
                relationship = Relationship.SUPPORTS,
                sourceText = "a supporting planning record",
                recordedAt = 2L
            )
        )
        coEvery { knowledgeDao.getDecisionsForSourceRevision(9L) } returns listOf(
            com.echomind.data.local.entity.DecisionEntity(
                id = 1L,
                question = "which plan?",
                sourceRevisionId = 9L,
                createdAt = 1L
            )
        )
        coEvery { knowledgeDao.getOutcomesForDecision(1L) } returns listOf(
            com.echomind.data.local.entity.OutcomeEntity(
                decisionId = 1L,
                report = "the plan worked",
                createdAt = 2L
            )
        )

        val result = repository.requestGuidance("planning")

        assertTrue(result is GuidanceRequestResult.Ready)
        val preview = (result as GuidanceRequestResult.Ready).preview
        assertEquals(listOf(7L), preview.sourceEntryIds)
        val grounds = preview.grounds.single()
        assertEquals(listOf("an opposing planning record"), grounds.contradictions)
        assertEquals(listOf("a supporting planning record"), grounds.supports)
        assertEquals(listOf("the plan worked"), grounds.outcomes)
        val system = preview.messages.first { it.role == "system" }.content
        assertTrue(system.contains("[Entry 7, revision v2]"))
        assertTrue(system.contains("an opposing planning record"))
        assertTrue(system.contains("the plan worked"))
        assertTrue(preview.messages.none { it.content.contains("RAW PRIVATE TRANSCRIPT") })
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `approval is one shot and returns a cited answer`() = runTest {
        coEvery { knowledgeRepository.search("planning") } returns listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true)
        )
        coEvery { knowledgeRepository.getRelatedRecords(9L) } returns listOf(
            RelatedRecord(
                rawRecordId = 100L,
                relationship = Relationship.SUPPORTS,
                sourceText = "a supporting planning record",
                recordedAt = 2L
            )
        )
        coEvery { knowledgeDao.getDecisionsForSourceRevision(9L) } returns emptyList()
        coEvery { llmApi.analyzeText(any(), any(), any()) } returns AnalysisResponse(
            choices = listOf(Choice(Message("assistant", "cautious answer")))
        )

        val ready = repository.requestGuidance("planning") as GuidanceRequestResult.Ready
        val first = repository.sendApprovedGuidance(ready.preview.requestId)
        val replay = repository.sendApprovedGuidance(ready.preview.requestId)

        assertEquals("cautious answer", first.getOrThrow().answer)
        assertEquals(listOf(7L), first.getOrThrow().sourceEntryIds)
        assertTrue(replay.exceptionOrNull() is StaleRemoteConsentException)
        coVerify(exactly = 1) {
            llmApi.analyzeText(ready.preview.destination, ready.preview.destination, any())
        }
    }

    @Test
    fun `local mode refuses guidance before any network or retrieval`() = runTest {
        coEvery { settingsStore.load() } returns StoredSettings(
            apiEndpoint = "https://provider.example",
            localMode = true
        )
        coEvery { settingsStore.isLocalMode() } returns true

        val result = repository.requestGuidance("planning")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(GuidanceRefusalReason.LOCAL_MODE, (result as GuidanceRequestResult.Refused).reason)
        coVerify(exactly = 0) { knowledgeRepository.search(any()) }
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }
}
