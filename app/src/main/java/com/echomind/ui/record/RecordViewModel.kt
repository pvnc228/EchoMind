package com.echomind.ui.record

import android.app.Application
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val audioEncryptionUtil: AudioEncryptionUtil,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var startTime: Long = 0
    private var amplitudeJob: Job? = null
    private var draftSaveJob: Job? = null
    private val draftWriteMutex = Mutex()
    private var draftActionInProgress = false
    private val requestedHypothesisId: Long? = savedStateHandle.get<Long>("hypothesisId")

    init {
        viewModelScope.launch {
            if (requestedHypothesisId != null) {
                runCatching { reflectionRepository.loadReflection(requestedHypothesisId) }
                    .onSuccess(::showSession)
                    .onFailure { error ->
                        _uiState.value = RecordUiState(
                            stage = ReflectionStage.ERROR,
                            error = error.message ?: "Could not restore the reflection proposal."
                        )
                    }
                return@launch
            }
            val interrupted = cleanupInterruptedRecording()
            runCatching { reflectionRepository.loadCaptureDraft() }
                .onSuccess { draft ->
                    if (draft != null) {
                        _uiState.value = RecordUiState(
                            stage = ReflectionStage.CAPTURE,
                            thoughtText = draft.text,
                            durationMs = draft.durationMs,
                            audioPath = draft.encryptedAudioPath,
                            error = if (interrupted) {
                                "Recording was interrupted. Your text draft was restored."
                            } else {
                                null
                            }
                        )
                    } else {
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
                .onFailure { error ->
                    _uiState.value = RecordUiState(
                        stage = ReflectionStage.ERROR,
                        error = error.message ?: "Could not restore the capture draft."
                    )
                }
        }
    }

    fun updateThought(text: String) {
        if (_uiState.value.stage == ReflectionStage.CAPTURE && !draftActionInProgress) {
            _uiState.value = _uiState.value.copy(thoughtText = text)
            scheduleDraftPersist()
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

        draftActionInProgress = true
        viewModelScope.launch {
            waitForPendingDraftWrite()
            _uiState.value = state.copy(stage = ReflectionStage.PROCESSING, error = null)
            var rawRecordId: Long? = null
            runCatching {
                rawRecordId = reflectionRepository.submitCaptureDraft(
                    originalText = state.thoughtText,
                    audioPath = state.audioPath,
                    durationMs = state.durationMs
                )
                _uiState.value = state.copy(
                    stage = ReflectionStage.PROCESSING,
                    rawRecordId = rawRecordId
                )
                val session = reflectionRepository.createLocalProposal(requireNotNull(rawRecordId))
                session
            }.onSuccess {
                draftActionInProgress = false
                showSession(it)
            }
                .onFailure { error ->
                    draftActionInProgress = false
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
        persistDraftNow()
        val audioDir = File(context.noBackupFilesDir, "capture_tmp")
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
        viewModelScope.launch {
            reflectionRepository.clearCaptureDraft()
            _uiState.value = RecordUiState(stage = ReflectionStage.CAPTURE)
        }
    }

    fun keepDraft(onComplete: () -> Unit = {}) {
        val state = _uiState.value
        if (state.stage != ReflectionStage.CAPTURE || draftActionInProgress) return
        draftActionInProgress = true
        viewModelScope.launch {
            waitForPendingDraftWrite()
            persistDraft(state).fold(
                onSuccess = {
                    draftActionInProgress = false
                    onComplete()
                },
                onFailure = { error ->
                    draftActionInProgress = false
                    _uiState.value = state.copy(error = error.message ?: "Could not save the draft.")
                }
            )
        }
    }

    fun discardDraft(onComplete: () -> Unit = {}) {
        val state = _uiState.value
        if (state.stage != ReflectionStage.CAPTURE || draftActionInProgress) return
        draftActionInProgress = true
        viewModelScope.launch {
            waitForPendingDraftWrite()
            runCatching {
                draftWriteMutex.withLock { reflectionRepository.clearCaptureDraft() }
                state.audioPath?.let { path ->
                    if (path.endsWith(AudioEncryptionUtil.ENCRYPTED_EXTENSION)) {
                        check(!File(path).exists() || File(path).delete()) {
                            "Could not remove the draft audio."
                        }
                    }
                }
            }.fold(
                onSuccess = {
                    draftActionInProgress = false
                    _uiState.value = RecordUiState(stage = ReflectionStage.CAPTURE)
                    onComplete()
                },
                onFailure = { error ->
                    draftActionInProgress = false
                    _uiState.value = state.copy(error = error.message ?: "Could not discard the draft.")
                }
            )
        }
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
        persistDraftNow()
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
        draftSaveJob?.cancel()
        amplitudeJob?.cancel()
        mediaRecorder?.release()
        mediaRecorder = null
        super.onCleared()
    }

    private fun scheduleDraftPersist() {
        val state = _uiState.value
        if (state.stage != ReflectionStage.CAPTURE) return

        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            persistDraft(state)
        }
    }

    private fun persistDraftNow() {
        val state = _uiState.value
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch { persistDraft(state) }
    }

    private suspend fun persistDraft(state: RecordUiState): Result<Unit> =
        draftWriteMutex.withLock {
            runCatching {
                reflectionRepository.saveCaptureDraft(
                    text = state.thoughtText,
                    encryptedAudioPath = state.audioPath?.takeIf {
                        it.endsWith(AudioEncryptionUtil.ENCRYPTED_EXTENSION)
                    },
                    durationMs = state.durationMs
                )
            }
        }

    private suspend fun waitForPendingDraftWrite() {
        draftSaveJob?.cancelAndJoin()
        draftSaveJob = null
    }

    private fun cleanupInterruptedRecording(): Boolean {
        val directory = File(getApplication<Application>().noBackupFilesDir, "capture_tmp")
        val staleFiles = directory.listFiles()
            ?.filter { it.isFile && it.extension == "m4a" }
            .orEmpty()
        staleFiles.forEach { it.delete() }
        return staleFiles.isNotEmpty()
    }

    private companion object {
        const val MAX_AMPLITUDE_SAMPLES = 120
        const val DRAFT_DEBOUNCE_MS = 500L
    }
}
