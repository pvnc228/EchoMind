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
import kotlinx.coroutines.flow.MutableStateFlow
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
        every { audioEncryptionUtil.decryptToUniqueTempFile(any(), any(), any()) } answers {
            val prefix = secondArg<String>()
            val ext = thirdArg<String>()
            File(tempCacheDir, "${prefix}${java.util.UUID.randomUUID()}.$ext").apply {
                writeText("decrypted test audio bytes")
            }
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
        coVerify(exactly = 1) { llmRepository.previewAudioTranscription(any(), 4500L) }
    }

    @Test
    fun `approveTranscription uses prepared preview temp file, sends approved transcription, and cleans up file`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
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
        coEvery { llmRepository.sendApprovedAudioTranscription("req-456", any()) } returns Result.success(
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
        coVerify(exactly = 1) { llmRepository.sendApprovedAudioTranscription("req-456", any()) }
    }

    @Test
    fun `approveTranscription failure reports error without removing audio draft or existing text`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
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
        coEvery { llmRepository.sendApprovedAudioTranscription("req-789", any()) } returns Result.failure(
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
    }

    @Test
    fun `stopRecording immediately transitions stage to PROCESSING on Main and ignores duplicate stop`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val uiStateField = RecordViewModel::class.java.getDeclaredField("_uiState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val stateFlow = uiStateField.get(viewModel) as MutableStateFlow<RecordUiState>
        stateFlow.value = stateFlow.value.copy(stage = ReflectionStage.RECORDING, audioPath = null)

        viewModel.stopRecording()
        assertEquals(ReflectionStage.PROCESSING, viewModel.uiState.value.stage)

        // Call again while in PROCESSING - should be ignored
        viewModel.stopRecording()
        assertEquals(ReflectionStage.PROCESSING, viewModel.uiState.value.stage)
    }

    @Test
    fun `multiple transcription previews do not overwrite or delete other active preview files`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted bytes") }
        val draft = CaptureDraft(
            id = 1L,
            text = "Thought",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 4000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { settingsStore.isLocalMode() } returns false
        val preview1 = RemoteTranscriptionPreview(
            requestId = "req-1",
            purpose = "transcribe_audio",
            destination = "https://provider.example/v1/audio/transcriptions",
            audioFileName = "draft.m4a.enc",
            audioDurationMs = 4000L,
            audioFileSizeBytes = audioFile.length()
        )
        val preview2 = RemoteTranscriptionPreview(
            requestId = "req-2",
            purpose = "transcribe_audio",
            destination = "https://provider.example/v1/audio/transcriptions",
            audioFileName = "draft.m4a.enc",
            audioDurationMs = 4000L,
            audioFileSizeBytes = audioFile.length()
        )
        coEvery { llmRepository.previewAudioTranscription(any(), any()) } returnsMany listOf(
            Result.success(preview1),
            Result.success(preview2)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestTranscription()
        advanceUntilIdle()
        assertEquals("req-1", viewModel.uiState.value.transcriptionPreview?.requestId)

        viewModel.cancelTranscription()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.transcriptionPreview)

        viewModel.requestTranscription()
        advanceUntilIdle()
        assertEquals("req-2", viewModel.uiState.value.transcriptionPreview?.requestId)
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
        every { audioEncryptionUtil.decryptToUniqueTempFile(audioFile.absolutePath) } returns decryptedTemp
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

    @Test
    fun `requestTranscription cleans up previous preview temp file when superseded by new request`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted audio") }
        val draft = CaptureDraft(
            id = 1L,
            text = "Draft",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 4000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { settingsStore.isLocalMode() } returns false

        var firstCreatedFile: File? = null
        var secondCreatedFile: File? = null
        var callCount = 0
        every { audioEncryptionUtil.decryptToUniqueTempFile(any(), any(), any()) } answers {
            callCount++
            val file = File(tempCacheDir, "transcription_test_$callCount.m4a").apply { writeText("decrypted $callCount") }
            if (callCount == 1) firstCreatedFile = file else secondCreatedFile = file
            file
        }
        coEvery { audioRepository.previewRemoteTranscription(any(), any()) } answers {
            val file = firstArg<File>()
            Result.success(
                RemoteTranscriptionPreview(
                    requestId = "req-${file.name}",
                    purpose = "transcribe_audio",
                    destination = "https://provider.example/v1/chat/completions",
                    audioFileName = file.name,
                    audioDurationMs = 4000L,
                    audioFileSizeBytes = file.length()
                )
            )
        }

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestTranscription()
        advanceUntilIdle()
        val firstState = viewModel.uiState.value
        assertEquals("req-transcription_test_1.m4a", firstState.transcriptionPreview?.requestId)
        assertTrue(firstCreatedFile?.exists() == true)

        viewModel.requestTranscription()
        advanceUntilIdle()
        val secondState = viewModel.uiState.value
        assertEquals("req-transcription_test_2.m4a", secondState.transcriptionPreview?.requestId)
        assertFalse("Previous preview file must be deleted upon replacement", firstCreatedFile?.exists() == true)
        assertTrue("New preview file must exist", secondCreatedFile?.exists() == true)
    }

    @Test
    fun `requestTranscription while isTranscribing ignores concurrent second request`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted audio") }
        val draft = CaptureDraft(
            id = 1L,
            text = "Draft",
            encryptedAudioPath = audioFile.absolutePath,
            durationMs = 4000L,
            captureStage = "CAPTURE",
            createdAt = 1000L,
            updatedAt = 2000L
        )
        coEvery { reflectionRepository.loadCaptureDraft() } returns draft
        coEvery { settingsStore.isLocalMode() } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestTranscription()
        assertTrue("isTranscribing must be true immediately on Main", viewModel.uiState.value.isTranscribing)

        viewModel.requestTranscription()

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isTranscribing)
        coVerify(exactly = 1) { audioRepository.previewRemoteTranscription(any(), any()) }
    }

    @Test
    fun `stopRecording failure cleans up original unencrypted file and sets state to ERROR`() = runTest(testDispatcher) {
        val captureTmp = File(tempCacheDir, "capture_tmp").apply { mkdirs() }
        every { application.noBackupFilesDir } returns tempCacheDir
        val originalFile = File(captureTmp, "entry_test.m4a").apply { writeText("unencrypted raw speech") }

        val viewModel = createViewModel()
        advanceUntilIdle()

        val recordingState = viewModel.uiState.value.copy(
            stage = ReflectionStage.RECORDING,
            audioPath = originalFile.absolutePath
        )
        val field = RecordViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as MutableStateFlow<RecordUiState>).value = recordingState

        every { audioEncryptionUtil.encryptFile(any(), any()) } throws RuntimeException("Simulated encryption failure")

        viewModel.stopRecording()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReflectionStage.ERROR, state.stage)
        assertNull(state.audioPath)
        assertTrue(state.error?.contains("Encryption failed") == true)
        assertFalse("Original unencrypted file must be deleted on encryption failure", originalFile.exists())
    }

    @Test
    fun `cancel at decrypt return deletes plaintext before it can escape to Main`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("encrypted fixture") }
        coEvery { reflectionRepository.loadCaptureDraft() } returns CaptureDraft(
            id = 1L, text = "Draft", encryptedAudioPath = audioFile.absolutePath,
            durationMs = 1000L, captureStage = "CAPTURE", createdAt = 1L, updatedAt = 1L
        )
        coEvery { settingsStore.isLocalMode() } returns false
        val viewModel = createViewModel(StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        val plaintext = File(tempCacheDir, "transcription_cancelled.m4a")
        every { audioEncryptionUtil.decryptToUniqueTempFile(any(), any(), any()) } answers {
            plaintext.writeText("private audio")
            // Queue cancellation on Main before withContext can deliver the File there.
            testDispatcher.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable {
                viewModel.cancelTranscription()
            })
            plaintext
        }
        viewModel.requestTranscription()
        advanceUntilIdle()
        assertFalse("cancelled IO result must not retain plaintext", plaintext.exists())
        assertFalse(viewModel.uiState.value.isTranscribing)
        assertNull(viewModel.uiState.value.transcriptionPreview)
        coVerify(exactly = 0) { audioRepository.previewRemoteTranscription(any(), any()) }
    }

    @Test
    fun `engine settings failure releases transcription busy state`() = runTest(testDispatcher) {
        val audioFile = File(tempCacheDir, "draft.m4a.enc").apply { writeText("fixture") }
        coEvery { reflectionRepository.loadCaptureDraft() } returns CaptureDraft(
            id = 1L, text = "Draft", encryptedAudioPath = audioFile.absolutePath,
            durationMs = 1000L, captureStage = "CAPTURE", createdAt = 1L, updatedAt = 1L
        )
        coEvery { audioRepository.getEngine() } throws IllegalStateException("settings unavailable")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.requestTranscription()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isTranscribing)
        assertEquals("settings unavailable", vm.uiState.value.error)
    }

    @Test
    fun `cancel approval before send dispatch still deletes prepared plaintext`() = runTest(testDispatcher) {
        val source = File(tempCacheDir, "draft.m4a.enc").apply { writeText("fixture") }
        coEvery { reflectionRepository.loadCaptureDraft() } returns CaptureDraft(
            id = 1L, text = "Draft", encryptedAudioPath = source.absolutePath,
            durationMs = 1000L, captureStage = "CAPTURE", createdAt = 1L, updatedAt = 1L
        )
        coEvery { settingsStore.isLocalMode() } returns false
        coEvery { audioRepository.previewRemoteTranscription(any(), any()) } returns Result.success(
            RemoteTranscriptionPreview("cancel-send", "transcribe_audio", "https://provider.example",
                "prepared.m4a", 1000L, 10L)
        )
        val vm = createViewModel()
        advanceUntilIdle()
        vm.requestTranscription()
        advanceUntilIdle()
        val temp = tempCacheDir.listFiles()!!.single { it.name.startsWith("transcription_") }
        vm.approveTranscription()
        vm.cancelTranscription()
        advanceUntilIdle()
        assertFalse("ownership must survive cancellation before send starts", temp.exists())
        coVerify(exactly = 0) { audioRepository.sendApprovedRemoteTranscription(any(), any()) }
    }

    @Test
    fun `retry waits for failed startup cleanup before enabling capture`() = runTest(testDispatcher) {
        coEvery { reflectionRepository.loadCaptureDraft() } returns CaptureDraft(
            id = 1L, text = "Preserved draft", encryptedAudioPath = null,
            durationMs = 0L, captureStage = "CAPTURE", createdAt = 1L, updatedAt = 1L
        )
        var attempts = 0
        every { audioEncryptionUtil.cleanupOrphanedTempFiles() } answers {
            if (attempts++ == 0) throw IllegalStateException("cleanup temporarily unavailable")
        }
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals(ReflectionStage.ERROR, vm.uiState.value.stage)
        vm.retry()
        assertEquals(ReflectionStage.LOADING, vm.uiState.value.stage)
        advanceUntilIdle()
        assertEquals(ReflectionStage.CAPTURE, vm.uiState.value.stage)
        assertEquals("Preserved draft", vm.uiState.value.thoughtText)
        verify(exactly = 2) { audioEncryptionUtil.cleanupOrphanedTempFiles() }
    }

    private fun createViewModel(io: kotlinx.coroutines.CoroutineDispatcher = testDispatcher) = RecordViewModel(
        application = application,
        reflectionRepository = reflectionRepository,
        audioEncryptionUtil = audioEncryptionUtil,
        llmRepository = llmRepository,
        settingsStore = settingsStore,
        savedStateHandle = savedStateHandle,
        audioRepository = audioRepository,
        ioDispatcher = io
    )
}
