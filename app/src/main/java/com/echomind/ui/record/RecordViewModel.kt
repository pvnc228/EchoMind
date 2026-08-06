package com.echomind.ui.record

import android.app.Application
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.repository.ReflectionRepository
import com.echomind.domain.model.ReflectionDraft
import com.echomind.domain.model.ReflectionSession
import com.echomind.domain.model.ReflectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ReflectionStage {
    LOADING, CAPTURE, RECORDING, PROCESSING, REVIEW, CONFIRMED, REJECTED, ERROR
}

data class RecordUiState(
    val stage: ReflectionStage = ReflectionStage.LOADING,
    val thoughtText: String = "",
    val rawRecordId: Long? = null,
    val hypothesisId: Long? = null,
    val draft: ReflectionDraft? = null,
    val counterargument: String = "",
    val confirmationText: String = "",
    val confirmedConclusion: String? = null,
    val durationMs: Long = 0,
    val audioPath: String? = null,
    val error: String? = null,
    val amplitudes: List<Float> = emptyList(),
    val permissionDenied: Boolean = false
)

@HiltViewModel
class RecordViewModel @Inject constructor(
    application: Application,
    private val reflectionRepository: ReflectionRepository,
    private val audioEncryptionUtil: AudioEncryptionUtil
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var startTime: Long = 0
    private var amplitudeJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { reflectionRepository.loadLatestProposedReflection() }
                .onSuccess { session ->
                    if (session == null) {
                        _uiState.value = RecordUiState(stage = ReflectionStage.CAPTURE)
                    } else {
                        showSession(session)
                    }
                }
                .onFailure { error ->
                    _uiState.value = RecordUiState(
                        stage = ReflectionStage.ERROR,
                        error = error.message ?: "Could not restore the pending reflection."
                    )
                }
        }
    }

    fun updateThought(text: String) {
        if (_uiState.value.stage == ReflectionStage.CAPTURE) {
            _uiState.value = _uiState.value.copy(thoughtText = text)
        }
    }

    fun updateConfirmation(text: String) {
        if (_uiState.value.stage == ReflectionStage.REVIEW) {
            _uiState.value = _uiState.value.copy(confirmationText = text)
        }
    }

    fun submitThought() {
        val state = _uiState.value
        if (state.stage != ReflectionStage.CAPTURE || state.thoughtText.isBlank()) return

        viewModelScope.launch {
            _uiState.value = state.copy(stage = ReflectionStage.PROCESSING, error = null)
            var rawRecordId: Long? = null
            runCatching {
                rawRecordId = reflectionRepository.captureRawText(
                    originalText = state.thoughtText,
                    audioPath = state.audioPath,
                    durationMs = state.durationMs
                )
                _uiState.value = state.copy(
                    stage = ReflectionStage.PROCESSING,
                    rawRecordId = rawRecordId
                )
                reflectionRepository.createLocalProposal(requireNotNull(rawRecordId))
            }.onSuccess(::showSession)
                .onFailure { error ->
                    _uiState.value = state.copy(
                        stage = ReflectionStage.ERROR,
                        rawRecordId = rawRecordId,
                        error = if (rawRecordId == null) {
                            error.message ?: "Could not save the reflection."
                        } else {
                            "Your original text was saved, but the local proposal failed: " +
                                (error.message ?: "unknown error")
                        }
                    )
                }
        }
    }

    fun confirmProposal() {
        val state = _uiState.value
        val hypothesisId = state.hypothesisId ?: return
        if (state.stage != ReflectionStage.REVIEW || state.confirmationText.isBlank()) return

        viewModelScope.launch {
            _uiState.value = state.copy(stage = ReflectionStage.PROCESSING, error = null)
            runCatching {
                reflectionRepository.confirm(hypothesisId, state.confirmationText)
            }.onSuccess(::showSession)
                .onFailure { error ->
                    _uiState.value = state.copy(
                        stage = ReflectionStage.ERROR,
                        error = error.message ?: "Could not confirm the conclusion."
                    )
                }
        }
    }

    fun rejectProposal() {
        val state = _uiState.value
        val hypothesisId = state.hypothesisId ?: return
        if (state.stage != ReflectionStage.REVIEW) return

        viewModelScope.launch {
            _uiState.value = state.copy(stage = ReflectionStage.PROCESSING, error = null)
            runCatching { reflectionRepository.reject(hypothesisId) }
                .onSuccess(::showSession)
                .onFailure { error ->
                    _uiState.value = state.copy(
                        stage = ReflectionStage.ERROR,
                        error = error.message ?: "Could not reject the proposal."
                    )
                }
        }
    }

    fun retry() {
        val state = _uiState.value
        val rawRecordId = state.rawRecordId
        if (rawRecordId == null) {
            _uiState.value = state.copy(stage = ReflectionStage.CAPTURE, error = null)
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(stage = ReflectionStage.PROCESSING, error = null)
            runCatching { reflectionRepository.createLocalProposal(rawRecordId) }
                .onSuccess(::showSession)
                .onFailure { error ->
                    _uiState.value = state.copy(
                        stage = ReflectionStage.ERROR,
                        error = error.message ?: "Could not restore the saved reflection."
                    )
                }
        }
    }

    fun startRecording() {
        if (_uiState.value.stage != ReflectionStage.CAPTURE) return
        _uiState.value = _uiState.value.copy(permissionDenied = false)

        val context = getApplication<Application>()
        val audioDir = File(context.filesDir, "audio")
        audioDir.mkdirs()
        val audioFile = File(audioDir, "entry_${System.currentTimeMillis()}.m4a")

        val recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setOutputFile(audioFile.absolutePath)
        }

        try {
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            startTime = System.currentTimeMillis()
            _uiState.value = _uiState.value.copy(
                stage = ReflectionStage.RECORDING,
                audioPath = audioFile.absolutePath,
                amplitudes = emptyList()
            )
            amplitudeJob = viewModelScope.launch {
                while (isActive) {
                    val normalized = ((mediaRecorder?.maxAmplitude ?: 0) / 32767f)
                        .coerceIn(0f, 1f)
                    val current = _uiState.value.amplitudes
                        .plus(normalized)
                        .takeLast(MAX_AMPLITUDE_SAMPLES)
                    _uiState.value = _uiState.value.copy(amplitudes = current)
                    delay(100)
                }
            }
        } catch (error: Exception) {
            recorder.release()
            audioFile.delete()
            _uiState.value = _uiState.value.copy(
                stage = ReflectionStage.ERROR,
                audioPath = null,
                error = error.message ?: "Could not start recording."
            )
        }
    }

    fun permissionDenied() {
        if (_uiState.value.stage == ReflectionStage.CAPTURE) {
            _uiState.value = _uiState.value.copy(permissionDenied = true)
        }
    }

    fun startNewReflection() {
        val state = _uiState.value
        if (state.stage != ReflectionStage.CONFIRMED && state.stage != ReflectionStage.REJECTED) return
        _uiState.value = RecordUiState(stage = ReflectionStage.CAPTURE)
    }

    fun stopRecording() {
        val state = _uiState.value
        if (state.stage != ReflectionStage.RECORDING) return

        amplitudeJob?.cancel()
        amplitudeJob = null
        val recorder = mediaRecorder
        mediaRecorder = null

        try {
            recorder?.stop()
            recorder?.release()
        } catch (error: Exception) {
            recorder?.release()
            state.audioPath?.let { File(it).delete() }
            _uiState.value = state.copy(
                stage = ReflectionStage.ERROR,
                audioPath = null,
                error = error.message ?: "Could not finish recording."
            )
            return
        }

        val originalPath = state.audioPath
        val encryptedPath = originalPath?.let { path ->
            val original = File(path)
            val encrypted = File(original.parent, original.name + AudioEncryptionUtil.ENCRYPTED_EXTENSION)
            try {
                audioEncryptionUtil.encryptFile(original, encrypted)
                encrypted.absolutePath
            } catch (error: Exception) {
                original.delete()
                encrypted.delete()
                _uiState.value = state.copy(
                    stage = ReflectionStage.ERROR,
                    audioPath = null,
                    error = "Encryption failed: ${error.message ?: "unknown error"}."
                )
                return
            }
        }

        _uiState.value = state.copy(
            stage = ReflectionStage.CAPTURE,
            durationMs = System.currentTimeMillis() - startTime,
            audioPath = encryptedPath,
            amplitudes = emptyList()
        )
    }

    private fun showSession(session: ReflectionSession) {
        val stage = when (session.status) {
            ReflectionStatus.CONFIRMED -> ReflectionStage.CONFIRMED
            ReflectionStatus.REJECTED -> ReflectionStage.REJECTED
            else -> ReflectionStage.REVIEW
        }
        _uiState.value = RecordUiState(
            stage = stage,
            thoughtText = session.originalText,
            rawRecordId = session.rawRecordId,
            hypothesisId = session.hypothesisId,
            draft = session.draft,
            counterargument = session.counterargument,
            confirmationText = session.confirmedConclusion
                ?: session.draft.suggestedConclusion(),
            confirmedConclusion = session.confirmedConclusion
        )
    }

    override fun onCleared() {
        amplitudeJob?.cancel()
        mediaRecorder?.release()
        mediaRecorder = null
        val state = _uiState.value
        if (state.rawRecordId == null) {
            state.audioPath?.let { File(it).delete() }
        }
        super.onCleared()
    }

    private companion object {
        const val MAX_AMPLITUDE_SAMPLES = 120
    }
}
