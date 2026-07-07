package com.echomind.ui.detail

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.local.security.AudioEncryptionUtil
import com.echomind.data.repository.EntryRepository
import com.echomind.domain.model.Entry
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
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val tempAudioPath: String? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    application: Application,
    private val entryRepository: EntryRepository,
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
                _uiState.value = _uiState.value.copy(entry = entry, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
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

    fun deleteEntry() {
        viewModelScope.launch {
            _uiState.value.entry?.let { entryRepository.deleteEntry(it.id) }
            stopPlayback()
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
