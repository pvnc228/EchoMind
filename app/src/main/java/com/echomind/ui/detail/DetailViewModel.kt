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
import com.echomind.domain.model.EntryDeletionChoice
import com.echomind.domain.model.EntryDeletionPlan
import com.echomind.domain.model.RelatedRecord
import com.echomind.domain.model.ReflectionSession
import com.echomind.domain.model.Revision
import com.echomind.domain.model.Theme
import com.echomind.domain.model.PendingThemeLink
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
    val pendingThemes: List<PendingThemeLink> = emptyList(),
    val relatedRecords: List<RelatedRecord> = emptyList(),
    val pendingRelatedRecords: List<RelatedRecord> = emptyList(),
    val otherEntries: List<RelatedRecord> = emptyList(),
    val manualCandidates: List<RelatedRecord> = emptyList(),
    val manualCandidatesHasMore: Boolean = false,
    val manualQuery: String = "",
    val isManualLoading: Boolean = false,
    val revisions: List<Revision> = emptyList(),
    val isRevising: Boolean = false,
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val isDeleting: Boolean = false,
    val deletionPlan: EntryDeletionPlan? = null,
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
    private var graphRequestGeneration = 0L
    private var manualRequestGeneration = 0L
    private var manualRevisionId: Long? = null

    fun loadEntry(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val entry = entryRepository.getEntryById(id)
                val deletionPlan = entryRepository.getDeletionPlan(id)
                val reflection = reflectionRepository.loadReflectionForEntry(id)
                _uiState.value = _uiState.value.copy(
                    entry = entry,
                    reflection = reflection,
                    deletionPlan = deletionPlan
                )
                loadLinked(reflection)
                val revisions = if (reflection != null) {
                    reflectionRepository.getRevisionHistory(reflection.hypothesisId)
                } else {
                    emptyList()
                }
                _uiState.value = _uiState.value.copy(
                    revisions = revisions,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun loadLinked(reflection: ReflectionSession?) {
        val requestGeneration = ++graphRequestGeneration
        val manualGenerationAtStart = ++manualRequestGeneration
        val revisionId = reflection?.revisionId
        manualRevisionId = revisionId
        _uiState.value = _uiState.value.copy(
            manualCandidates = emptyList(),
            manualCandidatesHasMore = false,
            manualQuery = "",
            isManualLoading = false
        )
        if (revisionId == null) {
            _uiState.value = _uiState.value.copy(
                themes = emptyList(),
                availableThemes = emptyList(),
                pendingThemes = emptyList(),
                relatedRecords = emptyList(),
                pendingRelatedRecords = emptyList(),
                otherEntries = emptyList()
            )
            return
        }
        val themes = knowledgeRepository.getConclusionsForRevision(revisionId)
        val availableThemes = knowledgeRepository.getThemes()
        val pendingThemes = knowledgeRepository.getPendingThemesForRevision(revisionId)
        val relatedRecords = knowledgeRepository.getRelatedRecords(revisionId)
        val pendingRelatedRecords = knowledgeRepository.getPendingRelatedRecords(revisionId)
        val suggestions = knowledgeRepository.getLinkCandidates(revisionId)
            .associateBy { it.rawRecordId }
        val manualPage = knowledgeRepository.getManualLinkCandidates(
            currentRevisionId = revisionId,
            limit = KnowledgeRepository.DEFAULT_MANUAL_LINK_PAGE_SIZE + 1
        )
        if (requestGeneration != graphRequestGeneration) return
        val preserveManualState =
            manualGenerationAtStart != manualRequestGeneration && manualRevisionId == revisionId
        val currentState = _uiState.value
        val manualCandidates = manualPage.take(KnowledgeRepository.DEFAULT_MANUAL_LINK_PAGE_SIZE)
        _uiState.value = currentState.copy(
            themes = themes,
            availableThemes = availableThemes,
            pendingThemes = pendingThemes,
            relatedRecords = relatedRecords,
            pendingRelatedRecords = pendingRelatedRecords,
            otherEntries = suggestions.values.toList(),
            manualCandidates = if (preserveManualState) {
                currentState.manualCandidates
            } else {
                manualCandidates
            },
            manualCandidatesHasMore = if (preserveManualState) {
                currentState.manualCandidatesHasMore
            } else {
                manualPage.size > KnowledgeRepository.DEFAULT_MANUAL_LINK_PAGE_SIZE
            },
            manualQuery = if (preserveManualState) currentState.manualQuery else "",
            isManualLoading = if (preserveManualState) {
                currentState.isManualLoading
            } else {
                false
            }
        )
    }

    fun searchManualCandidates(query: String) {
        val revisionId = _uiState.value.reflection?.revisionId ?: return
        val requestGeneration = ++manualRequestGeneration
        manualRevisionId = revisionId
        _uiState.value = _uiState.value.copy(
            manualCandidates = emptyList(),
            manualCandidatesHasMore = false,
            manualQuery = query,
            isManualLoading = true
        )
        loadManualCandidates(
            revisionId = revisionId,
            query = query,
            append = false,
            offset = 0,
            requestGeneration = requestGeneration
        )
    }

    fun loadMoreManualCandidates() {
        val state = _uiState.value
        if (state.isManualLoading || !state.manualCandidatesHasMore) return
        val revisionId = state.reflection?.revisionId ?: return
        val requestGeneration = ++manualRequestGeneration
        _uiState.value = state.copy(isManualLoading = true)
        loadManualCandidates(
            revisionId = revisionId,
            query = state.manualQuery,
            append = true,
            offset = state.manualCandidates.size,
            requestGeneration = requestGeneration
        )
    }

    private fun loadManualCandidates(
        revisionId: Long,
        query: String,
        append: Boolean,
        offset: Int,
        requestGeneration: Long
    ) {
        viewModelScope.launch {
            runCatching {
                val page = knowledgeRepository.getManualLinkCandidates(
                    currentRevisionId = revisionId,
                    query = query,
                    limit = KnowledgeRepository.DEFAULT_MANUAL_LINK_PAGE_SIZE + 1,
                    offset = offset
                )
                val visiblePage = page.take(KnowledgeRepository.DEFAULT_MANUAL_LINK_PAGE_SIZE)
                val isCurrentRequest = requestGeneration == manualRequestGeneration &&
                    manualRevisionId == revisionId &&
                    _uiState.value.reflection?.revisionId == revisionId
                if (!isCurrentRequest) return@launch
                val candidates = if (append) {
                    _uiState.value.manualCandidates + visiblePage
                } else {
                    visiblePage
                }
                _uiState.value = _uiState.value.copy(
                    manualCandidates = candidates,
                    manualCandidatesHasMore = page.size > KnowledgeRepository.DEFAULT_MANUAL_LINK_PAGE_SIZE,
                    manualQuery = query,
                    isManualLoading = false
                )
            }.onFailure { error ->
                if (
                    requestGeneration == manualRequestGeneration &&
                    manualRevisionId == revisionId &&
                    _uiState.value.reflection?.revisionId == revisionId
                ) {
                    _uiState.value = _uiState.value.copy(
                        isManualLoading = false,
                        error = error.message
                    )
                }
            }
        }
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

    fun reviewPendingThemeLink(linkId: Long, accept: Boolean) {
        viewModelScope.launch {
            runCatching {
                knowledgeRepository.reviewPendingThemeLink(linkId, accept)
                loadLinked(_uiState.value.reflection)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun reviewPendingRelatedRecord(linkId: Long, accept: Boolean) {
        viewModelScope.launch {
            runCatching {
                knowledgeRepository.reviewPendingRelatedRecord(linkId, accept)
                loadLinked(_uiState.value.reflection)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun revise(newWording: String) {
        val reflection = _uiState.value.reflection ?: return
        if (_uiState.value.isRevising) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRevising = true, error = null)
            runCatching {
                val revised = reflectionRepository.revise(reflection.hypothesisId, newWording)
                _uiState.value = _uiState.value.copy(reflection = revised)
                loadLinked(revised)
                val revisions = reflectionRepository.getRevisionHistory(reflection.hypothesisId)
                _uiState.value = _uiState.value.copy(
                    revisions = revisions,
                    isRevising = false
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isRevising = false,
                    error = error.message ?: "Could not revise the conclusion."
                )
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
        val plan = _uiState.value.deletionPlan
        deleteEntry(
            EntryDeletionChoice(
                deleteOwnConclusion = includeConfirmedConclusion,
                unlinkIncomingEvidenceLinkIds = plan?.incomingEvidence.orEmpty().map { it.linkId }.toSet(),
                deleteDecisionIds = if (includeConfirmedConclusion) {
                    plan?.decisions.orEmpty().map { it.decisionId }.toSet()
                } else {
                    emptySet()
                }
            )
        )
    }

    fun deleteEntry(choice: EntryDeletionChoice) {
        if (_uiState.value.isDeleting) return

        viewModelScope.launch {
            val entry = _uiState.value.entry ?: return@launch
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)
            runCatching {
                entryRepository.deleteEntry(entry.id, choice)
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
