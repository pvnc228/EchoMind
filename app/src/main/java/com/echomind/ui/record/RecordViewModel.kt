package com.echomind.ui.record

import android.app.Application
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import com.echomind.domain.usecase.AnalyzeEntryUseCase
import com.echomind.domain.usecase.SaveEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class RecordingState {
    IDLE, RECORDING, PROCESSING, DONE, ERROR
}

data class RecordUiState(
    val state: RecordingState = RecordingState.IDLE,
    val transcript: String = "",
    val durationMs: Long = 0,
    val audioPath: String? = null,
    val error: String? = null,
    val amplitudes: List<Float> = emptyList()
)

@HiltViewModel
class RecordViewModel @Inject constructor(
    application: Application,
    private val saveEntryUseCase: SaveEntryUseCase,
    private val analyzeEntryUseCase: AnalyzeEntryUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var startTime: Long = 0
    private var amplitudeJob: Job? = null

    fun startRecording() {
        val context = getApplication<Application>()
        val audioDir = File(context.filesDir, "audio")
        audioDir.mkdirs()
        val audioFile = File(audioDir, "entry_${System.currentTimeMillis()}.wav")

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFile.absolutePath)
            try {
                prepare()
                start()
                startTime = System.currentTimeMillis()
                _uiState.value = RecordUiState(state = RecordingState.RECORDING, audioPath = audioFile.absolutePath)
                amplitudeJob = viewModelScope.launch {
                    val maxSamples = 120
                    while (isActive) {
                        val amp = mediaRecorder?.maxAmplitude ?: 0
                        val normalized = (amp / 32767f).coerceIn(0f, 1f)
                        val current = _uiState.value.amplitudes.toMutableList()
                        current.add(normalized)
                        if (current.size > maxSamples) {
                            current.removeAt(0)
                        }
                        _uiState.value = _uiState.value.copy(amplitudes = current)
                        delay(100)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = RecordUiState(state = RecordingState.ERROR, error = e.message)
            }
        }
    }

    fun stopRecording() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                // release anyway
            }
        }
        mediaRecorder = null
        val duration = System.currentTimeMillis() - startTime
        _uiState.value = _uiState.value.copy(
            state = RecordingState.DONE,
            durationMs = duration
        )
    }

    fun saveEntry(transcript: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(state = RecordingState.PROCESSING)
            val entry = Entry(
                transcript = transcript,
                audioPath = _uiState.value.audioPath,
                durationMs = _uiState.value.durationMs,
                createdAt = System.currentTimeMillis(),
                category = EntryCategory.GENERAL,
                tags = emptyList(),
                summary = "",
                tasks = emptyList(),
                ideas = emptyList(),
                emotions = emptyList()
            )
            val analyzed = analyzeEntryUseCase(entry)
            val saved = analyzed.getOrDefault(entry)
            saveEntryUseCase(saved)
            _uiState.value = RecordUiState(state = RecordingState.IDLE)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}
