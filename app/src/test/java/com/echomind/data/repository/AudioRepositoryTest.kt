package com.echomind.data.repository

import com.echomind.data.local.speech.SpeechRecognizerWrapper
import com.echomind.data.remote.RemoteTranscriptionPreview
import com.echomind.data.settings.SettingsStore
import com.echomind.data.settings.StoredSettings
import com.echomind.domain.model.TranscriptionEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AudioRepositoryTest {

    private val speechRecognizerWrapper: SpeechRecognizerWrapper = mockk()
    private val llmRepository: LlmRepository = mockk()
    private val settingsStore: SettingsStore = mockk()
    private lateinit var repository: AudioRepository

    @Before
    fun setup() {
        repository = AudioRepository(speechRecognizerWrapper, llmRepository, settingsStore)
    }

    @Test
    fun `getEngine reads transcriptionEngine from SettingsStore`() = runTest {
        coEvery { settingsStore.load() } returns StoredSettings(
            transcriptionEngine = TranscriptionEngine.WHISPER
        )

        val engine = repository.getEngine()
        assertEquals(TranscriptionEngine.WHISPER, engine)
    }

    @Test
    fun `setEngine writes transcriptionEngine to SettingsStore`() = runTest {
        coEvery { settingsStore.setTranscriptionEngine(TranscriptionEngine.GEMINI) } returns Unit

        repository.setEngine(TranscriptionEngine.GEMINI)
        coVerify(exactly = 1) { settingsStore.setTranscriptionEngine(TranscriptionEngine.GEMINI) }
    }


    @Test
    fun `isOfflineRecognitionAvailable queries SpeechRecognizerWrapper`() {
        every { speechRecognizerWrapper.isAvailable() } returns true
        assertTrue(repository.isOfflineRecognitionAvailable())
    }

    @Test
    fun `transcribeOffline delegates directly to SpeechRecognizerWrapper`() = runTest {
        val testFile = File("test.m4a")
        coEvery { speechRecognizerWrapper.transcribeAudioFile(testFile) } returns Result.success("Offline text")

        val result = repository.transcribeOffline(testFile)
        assertEquals("Offline text", result.getOrThrow())
        coVerify(exactly = 1) { speechRecognizerWrapper.transcribeAudioFile(testFile) }
    }

    @Test
    fun `previewRemoteTranscription delegates to LlmRepository`() = runTest {
        val testFile = File("test.m4a")
        val preview = RemoteTranscriptionPreview(
            requestId = "req-1",
            purpose = "transcribe_audio",
            destination = "http://localhost:1234/v1/audio/transcriptions",
            audioFileName = "test.m4a",
            audioDurationMs = 5000L,
            audioFileSizeBytes = 1024L
        )
        coEvery { llmRepository.previewAudioTranscription(testFile, 5000L) } returns Result.success(preview)

        val result = repository.previewRemoteTranscription(testFile, 5000L)
        assertEquals(preview, result.getOrThrow())
        coVerify(exactly = 1) { llmRepository.previewAudioTranscription(testFile, 5000L) }
    }

    @Test
    fun `sendApprovedRemoteTranscription delegates to LlmRepository`() = runTest {
        val testFile = File("test.m4a")
        coEvery { llmRepository.sendApprovedAudioTranscription("req-1", testFile) } returns Result.success("Remote text")

        val result = repository.sendApprovedRemoteTranscription("req-1", testFile)
        assertEquals("Remote text", result.getOrThrow())
        coVerify(exactly = 1) { llmRepository.sendApprovedAudioTranscription("req-1", testFile) }
    }
}
