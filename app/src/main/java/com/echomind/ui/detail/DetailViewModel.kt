package com.echomind.ui.detail

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.repository.EntryRepository
import com.echomind.data.repository.KnowledgeRepository
import com.echomind.data.repository.ReflectionRepository
import com.echomind.domain.model.Entry
import com.echomind.domain.model.RelatedRecord
import com.echomind.domain.model.ReflectionSession
import com.echomind.domain.model.Theme
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val entry: Entry? = null,
    val reflection: ReflectionSession? = null,
    val themes: List<Theme> = emptyList(),
    val availableThemes: List<Theme> = emptyList(),
    val relatedRecords: List<RelatedRecord> = emptyList(),
    val otherEntries: List<RelatedRecord> = emptyList(),
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
    val tempAudioPath: String? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    application: Application,
    private val entryRepository: EntryRepository,
    private val reflectionRepository: ReflectionRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val audioEncryptionUtil: AudioEncryptionUtil
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var player: ExoPlayer? = null

    fun loadEntry(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val entry = entryRepository.getEntryById(id)
                val reflection = reflectionRepository.loadReflectionForEntry(id)
                loadLinked(reflection)
                _uiState.value = _uiState.value.copy(
                    entry = entry,
                    reflection = reflection,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun loadLinked(reflection: ReflectionSession?) {
        val revisionId = reflection?.revisionId
        if (revisionId == null) {
            _uiState.value = _uiState.value.copy(
                themes = emptyList(),
                availableThemes = emptyList(),
                relatedRecords = emptyList(),
                otherEntries = emptyList()
            )
            return
        }
        val themes = knowledgeRepository.getConclusionsForRevision(revisionId)
        val availableThemes = knowledgeRepository.getThemes()
        val relatedRecords = knowledgeRepository.getRelatedRecords(revisionId)
        val otherEntries = knowledgeRepository.getLinkCandidates(revisionId)
        _uiState.value = _uiState.value.copy(
            themes = themes,
            availableThemes = availableThemes,
            relatedRecords = relatedRecords,
            otherEntries = otherEntries
        )
    }

    fun linkToTheme(themeId: Long, revisionId: Long) {
        viewModelScope.launch {
            runCatching {
                knowledgeRepository.linkConclusionToTheme(themeId, revisionId)
                loadLinked(_uiState.value.reflection)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun unlinkFromTheme(themeId: Long, revisionId: Long) {
        viewModelScope.launch {
            runCatching {
                knowledgeRepository.unlinkConclusionFromTheme(themeId, revisionId)
                loadLinked(_uiState.value.reflection)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun linkRelatedRecord(revisionId: Long, candidate: RelatedRecord, relationship: String) {
        viewModelScope.launch {
            runCatching {
                knowledgeRepository.linkRelatedRecord(revisionId, candidate.rawRecordId, relationship)
                loadLinked(_uiState.value.reflection)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun unlinkRelatedRecord(revisionId: Long, sourceRecordId: Long) {
        viewModelScope.launch {
            runCatching {
                knowledgeRepository.unlinkRelatedRecord(revisionId, sourceRecordId)
                loadLinked(_uiState.value.reflection)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun togglePlayback() {
        viewModelScope.launch {
            val entry = _uiState.value.entry ?: return@launch
            val audioPath = entry.audioPath ?: return@launch

            if (player == null) {
                val playbackUri = if (audioPath.endsWith(AudioEncryptionUtil.ENCRYPTED_EXTENSION)) {
                    val tempFile = audioEncryptionUtil.decryptToTempFile(audioPath)
                    _uiState.value = _uiState.value.copy(tempAudioPath = tempFile.absolutePath)
                    Uri.fromFile(tempFile)
                } else {
                    Uri.parse(audioPath)
                }
                player = ExoPlayer.Builder(getApplication()).build().apply {
                    setMediaItem(MediaItem.fromUri(playbackUri))
                    prepare()
                    play()
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                        }
                    })
                }
                _uiState.value = _uiState.value.copy(isPlaying = true)
            } else if (player!!.isPlaying) {
                player!!.pause()
                _uiState.value = _uiState.value.copy(isPlaying = false)
            } else {
                player!!.play()
                _uiState.value = _uiState.value.copy(isPlaying = true)
            }
        }
    }

    fun stopPlayback() {
        player?.stop()
        player?.release()
        player = null
        _uiState.value = _uiState.value.copy(isPlaying = false)
        cleanupTempFile()
    }

    fun deleteEntry(includeConfirmedConclusion: Boolean) {
        if (_uiState.value.isDeleting) return

        viewModelScope.launch {
            val entry = _uiState.value.entry ?: return@launch
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)
            runCatching {
                entryRepository.deleteEntry(entry.id, includeConfirmedConclusion)
            }.onSuccess {
                stopPlayback()
                _uiState.value = _uiState.value.copy(isDeleting = false, deleted = true)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = error.message ?: "Could not delete the reflection."
                )
            }
        }
    }

    private fun cleanupTempFile() {
        _uiState.value.tempAudioPath?.let {
            audioEncryptionUtil.deleteTempFile(it)
            _uiState.value = _uiState.value.copy(tempAudioPath = null)
        }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
