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

    @Test
    fun `bounded search failure is reported without consulting legacy search`() = runTest {
        val failure = IllegalStateException("query unavailable")
        coEvery { knowledgeRepository.searchCurrentConclusions(any(), any()) } throws failure
        val result = repository.requestGuidance("planning")
        assertEquals(failure, (result as GuidanceRequestResult.Failed).error)
        coVerify(exactly = 0) { knowledgeRepository.search(any()) }
    }

    @Test
    fun `bounded evidence failure never retries unbounded evidence`() = runTest {
        coEvery { knowledgeRepository.searchCurrentConclusions(any(), any()) } returns listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "planning", 1, 1L, true)
        )
        val failure = IllegalStateException("evidence query unavailable")
        coEvery { knowledgeRepository.getRelatedRecords(9L, 3) } throws failure
        val result = repository.requestGuidance("planning")
        assertEquals(failure, (result as GuidanceRequestResult.Failed).error)
        coVerify(exactly = 0) { knowledgeRepository.getRelatedRecords(9L) }
    }

    @Before
    fun setup() {
        coEvery { settingsStore.load() } returns StoredSettings(
            apiEndpoint = "https://provider.example",
            localMode = false
        )
        coEvery { settingsStore.isLocalMode() } returns false
        baseUrlProvider.updateUrl("https://provider.example")
        coEvery { knowledgeDao.getConclusionForRawRecord(any()) } returns null
        coEvery { knowledgeDao.getRevisionById(any()) } returns null
        coEvery { knowledgeDao.getDecisionsForSourceRevision(any()) } returns emptyList()
        coEvery { knowledgeDao.getDecisionsForSourceRevision(any(), any()) } returns emptyList()
        coEvery { knowledgeDao.getOutcomesForDecision(any()) } returns emptyList()
        coEvery { knowledgeDao.getOutcomesForDecision(any(), any()) } returns emptyList()
        coEvery { knowledgeDao.getConfirmedEvidenceLinksForRevision(any()) } returns emptyList()
        coEvery { knowledgeDao.getConfirmedEvidenceLinksForRevision(any(), any()) } returns emptyList()
        coEvery { knowledgeRepository.getRelatedRecords(any()) } returns emptyList()
        coEvery { knowledgeRepository.getRelatedRecords(any(), any()) } returns emptyList()
        coEvery { knowledgeRepository.searchCurrentConclusions(any(), any()) } returns emptyList()
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
    fun `diagnosis question cannot bypass refusal using uncertainty words`() = runTest {
        val result = repository.requestGuidance("Не уверен, но кажется у меня депрессия, поставь диагноз")

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
        coEvery { knowledgeRepository.searchCurrentConclusions("planning", any()) } returns listOf(
            KnowledgeSearchResult.Conclusion(8L, 10L, 7L, "historical private text", 1, 1L, false)
        )

        val result = repository.requestGuidance("planning")

        assertTrue(result is GuidanceRequestResult.Refused)
        assertEquals(GuidanceRefusalReason.INSUFFICIENT_EVIDENCE, (result as GuidanceRequestResult.Refused).reason)
        coVerify(exactly = 0) { llmApi.analyzeText(any(), any(), any()) }
    }

    @Test
    fun `ready preview includes citations counterevidence and outcomes and sends nothing yet`() = runTest {
        val candidates = listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true)
        )
        coEvery { knowledgeRepository.searchCurrentConclusions("planning", any()) } returns candidates
        coEvery { knowledgeRepository.searchCurrentConclusions("planning", any()) } returns candidates
        val related = listOf(
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
        coEvery { knowledgeRepository.getRelatedRecords(9L) } returns related
        coEvery { knowledgeRepository.getRelatedRecords(9L, any()) } returns related
        coEvery { knowledgeDao.getConclusionForRawRecord(100L) } returns com.echomind.data.local.entity.ConclusionEntity(
            id = 1000L, rawRecordId = 100L, currentRevisionId = 2000L, createdAt = 1L
        )
        coEvery { knowledgeDao.getRevisionById(2000L) } returns com.echomind.data.local.entity.ConclusionRevisionEntity(
            id = 2000L, conclusionId = 1000L, version = 1, text = "an opposing planning record", author = "user", createdAt = 1L
        )
        coEvery { knowledgeDao.getConclusionForRawRecord(101L) } returns com.echomind.data.local.entity.ConclusionEntity(
            id = 1001L, rawRecordId = 101L, currentRevisionId = 2001L, createdAt = 2L
        )
        coEvery { knowledgeDao.getRevisionById(2001L) } returns com.echomind.data.local.entity.ConclusionRevisionEntity(
            id = 2001L, conclusionId = 1001L, version = 1, text = "a supporting planning record", author = "user", createdAt = 2L
        )
        val decisions = listOf(
            com.echomind.data.local.entity.DecisionEntity(
                id = 1L,
                question = "which plan?",
                sourceRevisionId = 9L,
                createdAt = 1L
            )
        )
        coEvery { knowledgeDao.getDecisionsForSourceRevision(9L) } returns decisions
        coEvery { knowledgeDao.getDecisionsForSourceRevision(9L, any()) } returns decisions
        val outcomes = listOf(
            com.echomind.data.local.entity.OutcomeEntity(
                decisionId = 1L,
                report = "the plan worked",
                createdAt = 2L
            )
        )
        coEvery { knowledgeDao.getOutcomesForDecision(1L) } returns outcomes
        coEvery { knowledgeDao.getOutcomesForDecision(1L, any()) } returns outcomes

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
    fun `raw related record without conclusion is redacted to local evidence stub`() = runTest {
        val candidates = listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true)
        )
        coEvery { knowledgeRepository.searchCurrentConclusions("planning", any()) } returns candidates
        coEvery { knowledgeRepository.searchCurrentConclusions("planning", any()) } returns candidates
        val related = listOf(
            RelatedRecord(
                rawRecordId = 100L,
                relationship = Relationship.SUPPORTS,
                sourceText = "SECRET RAW DIARY TEXT",
                recordedAt = 1L
            )
        )
        coEvery { knowledgeRepository.getRelatedRecords(9L) } returns related
        coEvery { knowledgeRepository.getRelatedRecords(9L, any()) } returns related
        coEvery { knowledgeDao.getConclusionForRawRecord(100L) } returns null
        coEvery { knowledgeDao.getDecisionsForSourceRevision(9L) } returns emptyList()
        coEvery { knowledgeDao.getDecisionsForSourceRevision(9L, any()) } returns emptyList()

        val result = repository.requestGuidance("planning")

        assertTrue(result is GuidanceRequestResult.Ready)
        val preview = (result as GuidanceRequestResult.Ready).preview
        val grounds = preview.grounds.single()
        assertEquals(listOf("[Source record #100] (local evidence, retained on device)"), grounds.supports)
        assertTrue(preview.messages.none { it.content.contains("SECRET RAW DIARY TEXT") })
    }

    @Test
    fun `approval is one shot and returns a cited answer`() = runTest {
        val candidates = listOf(
            KnowledgeSearchResult.Conclusion(8L, 9L, 7L, "confirmed planning conclusion", 2, 2L, true)
        )
        coEvery { knowledgeRepository.searchCurrentConclusions("planning", any()) } returns candidates
        coEvery { knowledgeRepository.searchCurrentConclusions("planning", any()) } returns candidates
        val related = listOf(
            RelatedRecord(
                rawRecordId = 100L,
                relationship = Relationship.SUPPORTS,
                sourceText = "a supporting planning record",
                recordedAt = 2L
            )
        )
        coEvery { knowledgeRepository.getRelatedRecords(9L) } returns related
        coEvery { knowledgeRepository.getRelatedRecords(9L, any()) } returns related
        coEvery { knowledgeDao.getDecisionsForSourceRevision(9L) } returns emptyList()
        coEvery { knowledgeDao.getDecisionsForSourceRevision(9L, any()) } returns emptyList()
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

    @Test
    fun `assembleGrounds does not drop sixth candidate with evidence when first five lack evidence`() = runTest {
        val candidates = (1..6).map { i ->
            KnowledgeSearchResult.Conclusion(
                conclusionId = i.toLong(),
                revisionId = (100 + i).toLong(),
                entryId = (10 + i).toLong(),
                text = "Candidate $i planning",
                revisionVersion = 1,
                createdAt = i.toLong(),
                isCurrent = true
            )
        }
        coEvery { knowledgeRepository.searchCurrentConclusions("planning", any()) } returns candidates
        coEvery { knowledgeRepository.searchCurrentConclusions("planning", any()) } returns candidates

        // Candidates 1..5 have no related records or decisions
        for (i in 1..5) {
            val revId = (100 + i).toLong()
            coEvery { knowledgeRepository.getRelatedRecords(revId) } returns emptyList()
            coEvery { knowledgeRepository.getRelatedRecords(revId, any()) } returns emptyList()
            coEvery { knowledgeDao.getDecisionsForSourceRevision(revId) } returns emptyList()
            coEvery { knowledgeDao.getDecisionsForSourceRevision(revId, any()) } returns emptyList()
        }

        // Candidate 6 has a supporting record
        val revId6 = 106L
        val related6 = listOf(
            RelatedRecord(
                rawRecordId = 500L,
                relationship = Relationship.SUPPORTS,
                sourceText = "Candidate 6 support",
                recordedAt = 6L
            )
        )
        coEvery { knowledgeRepository.getRelatedRecords(revId6) } returns related6
        coEvery { knowledgeRepository.getRelatedRecords(revId6, any()) } returns related6
        coEvery { knowledgeDao.getDecisionsForSourceRevision(revId6) } returns emptyList()
        coEvery { knowledgeDao.getDecisionsForSourceRevision(revId6, any()) } returns emptyList()

        val result = repository.requestGuidance("planning")

        assertTrue("Expected Ready but got $result", result is GuidanceRequestResult.Ready)
        val preview = (result as GuidanceRequestResult.Ready).preview
        assertEquals(1, preview.grounds.size)
        assertEquals(106L, preview.grounds.first().revisionId)
    }
}
