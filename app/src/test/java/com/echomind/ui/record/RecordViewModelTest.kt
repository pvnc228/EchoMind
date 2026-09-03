package com.echomind.ui.record

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.remote.RemoteTranscriptionPreview
import com.echomind.data.repository.AudioRepository
import com.echomind.data.repository.LlmRepository
import com.echomind.data.repository.ReflectionRepository
import com.echomind.data.settings.SettingsStore
import com.echomind.data.settings.StoredSettings
import com.echomind.domain.model.CaptureDraft
import com.echomind.domain.model.ReflectionDraft
import com.echomind.domain.model.ReflectionSession
import com.echomind.domain.model.ReflectionStatus
import com.echomind.domain.model.TranscriptionEngine

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import java.io.File

class RecordViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private val application: Application = mockk(relaxed = true)
    private val reflectionRepository: ReflectionRepository = mockk(relaxed = true)
    private val audioEncryptionUtil: AudioEncryptionUtil = mockk(relaxed = true)
    private val llmRepository: LlmRepository = mockk(relaxed = true)
    private val settingsStore: SettingsStore = mockk(relaxed = true)
    private val audioRepository: AudioRepository = mockk(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private lateinit var tempCacheDir: File

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        tempCacheDir = File.createTempFile("test_cache", "").apply {
            delete()
            mkdirs()
        }
        every { application.cacheDir } returns tempCacheDir
        every { application.noBackupFilesDir } returns tempCacheDir
        coEvery { reflectionRepository.loadCaptureDraft() } returns null
        coEvery { reflectionRepository.loadLatestProposedReflection() } returns null
        coEvery { audioRepository.getEngine() } returns TranscriptionEngine.WHISPER
        coEvery { audioRepository.previewRemoteTranscription(any(), any()) } coAnswers {
            llmRepository.previewAudioTranscription(firstArg(), secondArg())
        }
        coEvery { audioRepository.sendApprovedRemoteTranscription(any(), any()) } coAnswers {
            llmRepository.sendApprovedAudioTranscription(firstArg(), secondArg())
        }
        every { audioRepository.cancelRemoteTranscriptionPreview(any()) } answers {
            llmRepository.cancelTranscriptionPreviewNow(firstArg())
        }
    }


    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun `init loads capture draft with audio and text`() = runTest(testDispatcher) {
        val draft = CaptureDraft(
            id = 1L,
            text = "Saved text draft",
            encryptedAudioPath = "/data/audio/draft.m4a.enc",
            durationMs = 5000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReflectionStage.CAPTURE, state.stage)
        assertEquals("Saved text draft", state.thoughtText)
        assertEquals("/data/audio/draft.m4a.enc", state.audioPath)
        assertEquals(5000L, state.durationMs)
    }

    @Test
    fun `requestTranscription in local mode sets explanatory error without calling llmRepository`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
        val draft = CaptureDraft(
            id = 1L,
            text = "",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 3000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { settingsStore.isLocalMode() } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestTranscription()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.error?.contains("Local mode is enabled") == true)
        assertNull(state.transcriptionPreview)
        assertFalse(state.isTranscribing)
        coVerify(exactly = 0) { llmRepository.previewAudioTranscription(any(), any()) }
    }

    @Test
    fun `requestTranscription in remote mode prepares transcription preview and sets preview state`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
        val draft = CaptureDraft(
            id = 1L,
            text = "Pre-existing draft note",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 4500L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { settingsStore.isLocalMode() } returns false
        val expectedPreview = RemoteTranscriptionPreview(
            requestId = "req-123",
            purpose = "transcribe_audio",
            destination = "https://provider.example/v1/audio/transcriptions",
            audioFileName = "draft.m4a.enc",
            audioDurationMs = 4500L,
            audioFileSizeBytes = audioFile.length()
        )
        coEvery { llmRepository.previewAudioTranscription(any(), any()) } returns Result.success(expectedPreview)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestTranscription()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals(expectedPreview, state.transcriptionPreview)
        assertFalse(state.isTranscribing)
        coVerify(exactly = 1) { llmRepository.previewAudioTranscription(File(audioFile.absolutePath), 4500L) }
    }

    @Test
    fun `approveTranscription decrypts temp file, calls sendApprovedAudioTranscription, updates thoughtText, and cleans up temp file`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
        val decryptedTemp = File(tempCacheDir, "playback_draft.wav").apply { writeText("decrypted wav bytes") }
        val draft = CaptureDraft(
            id = 1L,
            text = "First thought.",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 4000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { settingsStore.isLocalMode() } returns false
        val preview = RemoteTranscriptionPreview(
            requestId = "req-456",
            purpose = "transcribe_audio",
            destination = "https://provider.example/v1/audio/transcriptions",
            audioFileName = "draft.m4a.enc",
            audioDurationMs = 4000L,
            audioFileSizeBytes = audioFile.length()
        )
        coEvery { llmRepository.previewAudioTranscription(any(), any()) } returns Result.success(preview)
        every { audioEncryptionUtil.decryptToTempFile(audioFile.absolutePath) } returns decryptedTemp
        coEvery { llmRepository.sendApprovedAudioTranscription("req-456", decryptedTemp) } returns Result.success(
            "Second thought from audio."
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestTranscription()
        advanceUntilIdle()

        viewModel.approveTranscription()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertNull(state.transcriptionPreview)
        assertFalse(state.isTranscribing)
        assertEquals("First thought.\nSecond thought from audio.", state.thoughtText)
        verify(exactly = 1) { audioEncryptionUtil.deleteTempFile(decryptedTemp.absolutePath) }
    }

    @Test
    fun `approveTranscription failure reports error without removing audio draft or existing text`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
        val decryptedTemp = File(tempCacheDir, "playback_draft.wav").apply { writeText("decrypted wav bytes") }
        val draft = CaptureDraft(
            id = 1L,
            text = "Original typed text",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 4000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { settingsStore.isLocalMode() } returns false
        val preview = RemoteTranscriptionPreview(
            requestId = "req-789",
            purpose = "transcribe_audio",
            destination = "https://provider.example/v1/audio/transcriptions",
            audioFileName = "draft.m4a.enc",
            audioDurationMs = 4000L,
            audioFileSizeBytes = audioFile.length()
        )
        coEvery { llmRepository.previewAudioTranscription(any(), any()) } returns Result.success(preview)
        every { audioEncryptionUtil.decryptToTempFile(audioFile.absolutePath) } returns decryptedTemp
        coEvery { llmRepository.sendApprovedAudioTranscription("req-789", decryptedTemp) } returns Result.failure(
            IllegalStateException("Network timeout")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestTranscription()
        advanceUntilIdle()

        viewModel.approveTranscription()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Network timeout", state.error)
        assertNull(state.transcriptionPreview)
        assertFalse(state.isTranscribing)
        assertEquals("Original typed text", state.thoughtText)
        assertEquals(audioFile.absolutePath, state.audioPath)
        verify(exactly = 1) { audioEncryptionUtil.deleteTempFile(decryptedTemp.absolutePath) }
    }

    @Test
    fun `cancelTranscription cancels preview and clears preview state`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
        val draft = CaptureDraft(
            id = 1L,
            text = "",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 4000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { settingsStore.isLocalMode() } returns false
        val preview = RemoteTranscriptionPreview(
            requestId = "req-cancel",
            purpose = "transcribe_audio",
            destination = "https://provider.example/v1/audio/transcriptions",
            audioFileName = "draft.m4a.enc",
            audioDurationMs = 4000L,
            audioFileSizeBytes = audioFile.length()
        )
        coEvery { llmRepository.previewAudioTranscription(any(), any()) } returns Result.success(preview)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestTranscription()
        advanceUntilIdle()

        viewModel.cancelTranscription()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.transcriptionPreview)
        coVerify(exactly = 1) { llmRepository.cancelTranscriptionPreviewNow("req-cancel") }
    }

    @Test
    fun `submitThought with transcribed thought passes original text and audio path to reflection repository`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
        val draft = CaptureDraft(
            id = 1L,
            text = "Transcribed and edited reflection",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 5000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { reflectionRepository.submitCaptureDraft("Transcribed and edited reflection", audioFile.absolutePath, 5000L) } returns 101L
        coEvery { reflectionRepository.createLocalProposal(101L) } returns ReflectionSession(
            rawRecordId = 101L,
            hypothesisId = 201L,
            originalText = "Transcribed and edited reflection",
            status = ReflectionStatus.PROPOSED,
            draft = ReflectionDraft(
                tentativeThesis = "Thesis",
                observations = listOf("Obs"),
                interpretations = listOf("Interp"),
                assumptions = listOf("Assump"),
                openQuestions = listOf("Q")
            ),
            counterargument = "Alt perspective",
            confirmedConclusion = null,
            followUpQuestion = null
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.submitThought()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReflectionStage.REVIEW, state.stage)
        assertEquals(101L, state.rawRecordId)
        assertEquals(201L, state.hypothesisId)
        assertEquals("Transcribed and edited reflection", state.thoughtText)
        coVerify(exactly = 1) {
            reflectionRepository.submitCaptureDraft("Transcribed and edited reflection", audioFile.absolutePath, 5000L)
        }
        coVerify(exactly = 1) {
            reflectionRepository.createLocalProposal(101L)
        }
    }

    @Test
    fun `requestTranscription with ON_DEVICE engine transcribes offline without network preview`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
        val decryptedTemp = File(tempCacheDir, "decrypted.m4a").apply { writeText("plain audio") }
        val draft = CaptureDraft(
            id = 1L,
            text = "Initial text",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 3000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { audioRepository.getEngine() } returns TranscriptionEngine.ON_DEVICE
        every { audioEncryptionUtil.decryptToTempFile(audioFile.absolutePath) } returns decryptedTemp
        coEvery { audioRepository.transcribeOffline(decryptedTemp) } returns Result.success("Offline words")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestTranscription()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.transcriptionPreview)
        assertFalse(state.isTranscribing)
        assertEquals("Initial text\nOffline words", state.thoughtText)
        verify(exactly = 1) { audioEncryptionUtil.deleteTempFile(decryptedTemp.absolutePath) }
        coVerify(exactly = 0) { audioRepository.previewRemoteTranscription(any(), any()) }
    }

    private fun createViewModel() = RecordViewModel(
        application = application,
        reflectionRepository = reflectionRepository,
        audioEncryptionUtil = audioEncryptionUtil,
        llmRepository = llmRepository,
        settingsStore = settingsStore,
        savedStateHandle = savedStateHandle,
        audioRepository = audioRepository
    )
}
