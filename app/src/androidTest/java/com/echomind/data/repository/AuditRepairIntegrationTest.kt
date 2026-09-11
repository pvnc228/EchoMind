package com.echomind.data.repository

import android.app.Application
import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.echomind.data.analysis.LocalReflectionAnalyzer
import com.echomind.data.analysis.SimpleTextAnalyzer
import com.echomind.data.local.AppDatabase
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.local.speech.SpeechRecognizerWrapper
import com.echomind.data.remote.*
import com.echomind.data.remote.dto.*
import com.echomind.data.settings.SettingsStore
import com.echomind.domain.model.Relationship
import com.echomind.domain.model.TranscriptionEngine
import com.echomind.ui.record.RecordViewModel
import com.echomind.ui.record.ReflectionStage
import java.io.File
import java.util.UUID
import java.util.Collections
import java.util.concurrent.Executor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class AuditRepairIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class RecordingApi : LlmApi {
        var sent: ByteArray? = null
        override suspend fun transcribeAudio(url: String, approvedDestination: String,
            audio: MultipartBody.Part, model: RequestBody, responseFormat: RequestBody): TranscriptionResponse {
            sent = Buffer().also { audio.body.writeTo(it) }.readByteArray()
            return TranscriptionResponse("Synthetic transcript")
        }
        override suspend fun analyzeText(url: String, approvedDestination: String, request: AnalysisRequest): AnalysisResponse =
            error("No network call is allowed during preview")
    }

    @Test
    fun encryptedDraftThroughViewModelHashesAndSendsSamePlaintextThenDeletesTemp() = runBlocking {
        val root = File(context.cacheDir, "repair-${UUID.randomUUID()}").apply { mkdirs() }
        val scopedContext = object : ContextWrapper(context) {
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val policy = RemoteAccessPolicy()
        val settings = SettingsStore(context, policy)
        val old = settings.load()
        val store = ViewModelStore()
        try {
            settings.setApiEndpoint("https://provider.example")
            settings.persistLocalMode(false)
            settings.updateLocalMode(false)
            settings.setTranscriptionEngine(TranscriptionEngine.WHISPER)
            val util = AudioEncryptionUtil(scopedContext)
            util.cleanupOrphanedTempFiles()
            val bytes = ByteArray(24_000) { (it % 251).toByte() }
            val source = File(root, "source.m4a").apply { writeBytes(bytes) }
            val encrypted = File(root, "source.m4a.enc")
            util.encryptFile(source, encrypted)
            assertFalse(source.exists())
            val reflection = ReflectionRepository(database, database.entryDao(), database.knowledgeDao(), LocalReflectionAnalyzer(), Json)
            reflection.saveCaptureDraft("Draft", encrypted.absolutePath, 1000L)
            val knowledge = KnowledgeRepository(database, database.knowledgeDao(), settings)
            val api = RecordingApi()
            val llm = LlmRepository(api, SimpleTextAnalyzer(), settings, knowledge, BaseUrlProvider(policy))
            val audio = AudioRepository(SpeechRecognizerWrapper(context), llm, settings)
            val vm = withContext(Dispatchers.Main) {
                RecordViewModel(context.applicationContext as Application, reflection, util, llm, settings,
                    SavedStateHandle(), audio).also { store.put("record", it) }
            }
            withTimeout(10_000) { vm.uiState.first { it.stage == ReflectionStage.CAPTURE } }
            withContext(Dispatchers.Main) { vm.requestTranscription() }
            val preview = withTimeout(10_000) { vm.uiState.first { it.transcriptionPreview != null } }.transcriptionPreview!!
            val temp = File(scopedContext.cacheDir, preview.audioFileName)
            assertArrayEquals(bytes, temp.readBytes())
            assertNull(api.sent)
            withContext(Dispatchers.Main) { vm.approveTranscription() }
            withTimeout(10_000) { vm.uiState.first { !it.isTranscribing } }
            assertNull(vm.uiState.value.error)
            assertTrue(vm.uiState.value.thoughtText.contains("Synthetic transcript"))
            assertArrayEquals(bytes, api.sent)
            withTimeout(10_000) { while (temp.exists()) delay(10) }
            assertTrue(encrypted.exists())
            // Byte substitution must still be rejected at the same real repository seam.
            val replacement = File(root, "replacement.m4a").apply { writeBytes(bytes) }
            val next = llm.previewAudioTranscription(replacement).getOrThrow()
            replacement.appendBytes(byteArrayOf(1))
            assertTrue(llm.sendApprovedAudioTranscription(next.requestId, replacement).exceptionOrNull() is StaleRemoteConsentException)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            settings.setApiEndpoint(old.apiEndpoint)
            settings.setTranscriptionEngine(old.transcriptionEngine)
            settings.persistLocalMode(old.localMode)
            settings.updateLocalMode(old.localMode)
            database.close()
            root.deleteRecursively()
        }
    }

    @Test
    fun guidanceFindsSixthGroundedCandidateWithBoundedRoomEvidence() = runBlocking {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, _ -> queries.add(sql) }, Executor { it.run() }).build()
        val settings = SettingsStore(context)
        val old = settings.load()
        try {
            settings.persistLocalMode(false)
            settings.updateLocalMode(false)
            val reflection = ReflectionRepository(database, database.entryDao(), database.knowledgeDao(), LocalReflectionAnalyzer(), Json)
            val knowledge = KnowledgeRepository(database, database.knowledgeDao(), settings)
            val raw = reflection.captureRawText("old source")
            val session = reflection.confirm(reflection.createLocalProposal(raw).hypothesisId, "planning choice")
            val revision = requireNotNull(session.revisionId)
            repeat(5) { index ->
                val evidence = reflection.captureRawText("external evidence $index")
                knowledge.linkRelatedRecord(revision, evidence, Relationship.SUPPORTS)
            }
            repeat(5) { index ->
                val candidate = reflection.captureRawText("new source $index")
                reflection.confirm(reflection.createLocalProposal(candidate).hypothesisId, "planning choice $index")
            }
            assertEquals("intrinsic source must not consume external evidence limit", 3,
                knowledge.getRelatedRecords(revision, 3).size)
            queries.clear()
            val result = GuidanceRepository(RecordingApi(), settings, knowledge, database.knowledgeDao(), BaseUrlProvider())
                .requestGuidance("planning choice")
            assertTrue(result is GuidanceRequestResult.Ready)
            val ready = result as GuidanceRequestResult.Ready
            assertEquals(listOf(revision), ready.preview.grounds.map { it.revisionId })
            val selects = queries.filter { it.trimStart().startsWith("SELECT", true) }
            assertTrue(selects.any { it.contains("LIMIT", true) && it.contains("evidence_links") })
            assertFalse(selects.any { it.contains("original_text LIKE", true) })
            println("REPAIR Room guidance selects=${selects.size}; groundedRevision=$revision; grounds=${ready.preview.grounds.size}")
        } finally {
            settings.persistLocalMode(old.localMode)
            settings.updateLocalMode(old.localMode)
            database.close()
        }
    }

    @Test
    fun questionPreviewUsesCurrentRoomConclusionAndRedactsIdentifiers() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val settings = SettingsStore(context)
        val old = settings.load()
        try {
            settings.persistLocalMode(false)
            settings.updateLocalMode(false)
            val reflection = ReflectionRepository(database, database.entryDao(), database.knowledgeDao(), LocalReflectionAnalyzer(), Json)
            val raw = reflection.captureRawText("planning RAW_PRIVATE audit.person@example.org")
            reflection.confirm(reflection.createLocalProposal(raw).hypothesisId, "planning contact audit.person@example.org")
            val knowledge = KnowledgeRepository(database, database.knowledgeDao(), settings)
            val preview = LlmRepository(RecordingApi(), SimpleTextAnalyzer(), settings, knowledge, BaseUrlProvider())
                .previewQuestion("planning").getOrThrow()
            assertEquals(1, preview.context.size)
            assertTrue(preview.messages.any { it.content.contains("planning contact") })
            assertFalse(preview.messages.any { it.content.contains("RAW_PRIVATE") || it.content.contains("audit.person@example.org") })
        } finally {
            settings.persistLocalMode(old.localMode)
            settings.updateLocalMode(old.localMode)
            database.close()
        }
    }
}
