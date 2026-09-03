package com.echomind.data.repository

import com.echomind.data.local.speech.SpeechRecognizerWrapper
import com.echomind.data.remote.RemoteTranscriptionPreview
import com.echomind.data.settings.SettingsStore
import com.echomind.domain.model.TranscriptionEngine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRepository @Inject constructor(
    private val speechRecognizerWrapper: SpeechRecognizerWrapper,
    private val llmRepository: LlmRepository,
    private val settingsStore: SettingsStore
) {
    suspend fun getEngine(): TranscriptionEngine {
        return settingsStore.load().transcriptionEngine
    }

    suspend fun setEngine(engine: TranscriptionEngine) {
        settingsStore.setTranscriptionEngine(engine)
    }

    fun isOfflineRecognitionAvailable(): Boolean {
        return speechRecognizerWrapper.isAvailable()
    }

    suspend fun transcribeOffline(audioFile: File): Result<String> {
        return speechRecognizerWrapper.transcribeAudioFile(audioFile)
    }

    suspend fun previewRemoteTranscription(
        audioFile: File,
        durationMs: Long
    ): Result<RemoteTranscriptionPreview> {
        return llmRepository.previewAudioTranscription(audioFile, durationMs)
    }

    suspend fun sendApprovedRemoteTranscription(
        requestId: String,
        audioFile: File
    ): Result<String> {
        return llmRepository.sendApprovedAudioTranscription(requestId, audioFile)
    }

    fun cancelRemoteTranscriptionPreview(requestId: String) {
        llmRepository.cancelTranscriptionPreviewNow(requestId)
    }
}
