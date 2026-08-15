package com.echomind.data.repository

import com.echomind.data.local.dao.KnowledgeDao
import com.echomind.data.remote.BaseUrlProvider
import com.echomind.data.remote.LlmApi
import com.echomind.data.remote.RemoteAccessPolicy
import com.echomind.data.settings.SettingsStore
import com.echomind.data.settings.StoredSettings
import com.echomind.domain.model.KnowledgeSearchResult
import com.echomind.domain.model.RelatedRecord
import com.echomind.domain.model.Relationship
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

/**
 * Live local-LLM evaluation through the production remote seam (`requestGuidance` →
 * `sendApprovedGuidance` with a real Retrofit `LlmApi`). Hermetic by default: it only
 * runs when the environment variable `ECHOMIND_LIVE_LLM_URL` points to a reachable
 * OpenAI-compatible base URL (e.g. a running Ollama `http://127.0.0.1:11434`).
 * This is a technical seam evaluation, not a user-usefulness claim.
 */
class GuidanceLiveLlmEvaluationTest {

    private val liveUrl: String? = System.getenv("ECHOMIND_LIVE_LLM_URL")

    @Test
    fun liveGuidanceRefusesDiagnosisLocallyWithZeroNetwork() = runBlocking {
        if (liveUrl == null) return@runBlocking
        val repository = liveRepository()

        val result = repository.requestGuidance("Do my records show I have depression?")

        assertTrue(result is GuidanceRequestResult.Refused)
    }

    @Test
    fun liveGuidanceReturnsNonEmptyGroundedAnswerForConfirmedConclusion() = runBlocking {
        if (liveUrl == null) return@runBlocking
        val repository = liveRepository()

        val ready = repository.requestGuidance("Should I prioritize salary or commute?")
        assertTrue(ready is GuidanceRequestResult.Ready)
        val preview = (ready as GuidanceRequestResult.Ready).preview
        assertTrue(preview.messages.first { it.role == "system" }.content.contains("supports:"))

        val answer = repository.sendApprovedGuidance(preview.requestId)
        if (!answer.isSuccess) {
            throw AssertionError(
                answer.exceptionOrNull()?.let { e ->
                    e.javaClass.name + ": " + e.message + " | cause: " + (e.cause?.let { c -> c.javaClass.name + ": " + c.message })
                }
            )
        }
        assertTrue(answer.getOrThrow().answer.isNotBlank())
    }

    private fun liveRepository(): GuidanceRepository {
        val url = checkNotNull(liveUrl)
        val policy = RemoteAccessPolicy()
        val provider = BaseUrlProvider(policy)
        policy.updateLocalMode(false)
        provider.updateUrl(url)
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
        val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        }
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost:1234/")
            .client(OkHttpClient.Builder().addInterceptor(logging).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(LlmApi::class.java)
        val settingsStore: SettingsStore = mockk()
        coEvery { settingsStore.isLocalMode() } returns false
        coEvery { settingsStore.load() } returns StoredSettings(
            apiEndpoint = url,
            localMode = false
        )

        val knowledgeRepository: KnowledgeRepository = mockk()
        coEvery { knowledgeRepository.search(any()) } returns listOf(
            KnowledgeSearchResult.Conclusion(
                conclusionId = 8L,
                revisionId = 9L,
                entryId = 7L,
                text = "A longer commute matters more to me than a higher salary.",
                revisionVersion = 1,
                createdAt = 1L,
                isCurrent = true
            )
        )
        coEvery { knowledgeRepository.getRelatedRecords(9L) } returns listOf(
            RelatedRecord(
                rawRecordId = 100L,
                relationship = Relationship.SUPPORTS,
                sourceText = "I felt much better on the shorter-commute week.",
                recordedAt = 1L
            )
        )

        val knowledgeDao: KnowledgeDao = mockk()
        coEvery { knowledgeDao.getDecisionsForSourceRevision(any()) } returns emptyList()

        return GuidanceRepository(
            llmApi = api,
            settingsStore = settingsStore,
            knowledgeRepository = knowledgeRepository,
            knowledgeDao = knowledgeDao,
            baseUrlProvider = provider
        )
    }
}
