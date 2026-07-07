package com.echomind.ui.detail

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val error: String? = null
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    application: Application,
    private val entryRepository: EntryRepository
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
        val entry = _uiState.value.entry ?: return
        val audioPath = entry.audioPath ?: return

        if (player == null) {
            player = ExoPlayer.Builder(getApplication()).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(audioPath)))
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

    fun deleteEntry() {
        viewModelScope.launch {
            _uiState.value.entry?.let { entryRepository.deleteEntry(it.id) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }
}
