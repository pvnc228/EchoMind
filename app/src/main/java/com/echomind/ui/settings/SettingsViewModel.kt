package com.echomind.ui.settings

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.export.ExportManager
import com.echomind.data.export.MAX_RESTORE_ARCHIVE_BYTES
import com.echomind.data.export.RestorePreview
import com.echomind.data.export.RestoreScope
import com.echomind.data.repository.KnowledgeRepository
import com.echomind.data.repository.EntryRepository
import com.echomind.data.remote.BaseUrlProvider
import com.echomind.data.remote.CredentialsProvider
import com.echomind.data.settings.SettingsStore
import com.echomind.data.local.entity.HomeCardDispositionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import com.echomind.di.IoDispatcher
import javax.inject.Inject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

import com.echomind.domain.model.TranscriptionEngine

data class SettingsUiState(
    val apiEndpoint: String = "http://localhost:1234",
    val apiKey: String = "",
    val localMode: Boolean = true,
    val transcriptionEngine: TranscriptionEngine = TranscriptionEngine.ON_DEVICE,
    val exportState: ExportState = ExportState.Idle,
    val restoreState: RestoreState = RestoreState.Idle,
    val showEndpointWarning: Boolean = false,
    val dismissedCards: List<HomeCardDispositionEntity> = emptyList(),
    val pendingAudioCleanupCount: Int = 0
)


sealed interface ExportState {
    data object Idle : ExportState
    data object InProgress : ExportState
    data class Success(val uri: Uri) : ExportState
    data class Error(val message: String) : ExportState
}

sealed interface RestoreState {
    data object Idle : RestoreState
    data object InProgress : RestoreState
    data class PreviewReady(val preview: RestorePreview) : RestoreState
    data object Success : RestoreState
    data class Error(val message: String) : RestoreState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val baseUrlProvider: BaseUrlProvider,
    private val exportManager: ExportManager,
    private val credentialsProvider: CredentialsProvider,
    private val settingsStore: SettingsStore,
    private val knowledgeRepository: KnowledgeRepository,
    private val entryRepository: EntryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var stagedRestoreFile: File? = null
    private var pendingRestoreRootIds: Set<Long>? = null
    private val restoreOperationGeneration = AtomicLong(0L)

    init {
        viewModelScope.launch {
            val settings = settingsStore.load()
            baseUrlProvider.updateUrl(settings.apiEndpoint)
            val dispositions = runCatching { knowledgeRepository.getCardDispositions() }.getOrDefault(emptyList())
            val pendingAudioCleanupCount = runCatching {
                entryRepository.getPendingAudioCleanupCount()
            }.getOrDefault(0)
            _uiState.value = SettingsUiState(
                apiEndpoint = settings.apiEndpoint,
                apiKey = credentialsProvider.apiKey,
                localMode = settings.localMode,
                transcriptionEngine = settings.transcriptionEngine,
                dismissedCards = dispositions.filter { it.dismissedAt != null },
                pendingAudioCleanupCount = pendingAudioCleanupCount
            )
        }
    }

    fun updateTranscriptionEngine(engine: TranscriptionEngine) {
        _uiState.value = _uiState.value.copy(transcriptionEngine = engine)
        viewModelScope.launch(ioDispatcher) {
            settingsStore.setTranscriptionEngine(engine)
        }
    }

    fun applyGeminiPreset() {
        updateApiEndpoint("https://generativelanguage.googleapis.com/v1beta/openai/")
        updateTranscriptionEngine(TranscriptionEngine.GEMINI)
    }


    fun updateApiEndpoint(endpoint: String) {

        val isNonLocal = endpoint.contains("://") &&
            !endpoint.contains("localhost") &&
            !endpoint.contains("127.0.0.1") &&
            !endpoint.contains("10.0.2.2")
        _uiState.value = _uiState.value.copy(
            apiEndpoint = endpoint,
            showEndpointWarning = isNonLocal
        )
        baseUrlProvider.updateUrl(endpoint)
        viewModelScope.launch(ioDispatcher) {
            settingsStore.setApiEndpoint(endpoint)
        }
    }

    fun dismissEndpointWarning() {
        _uiState.value = _uiState.value.copy(showEndpointWarning = false)
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key)
        credentialsProvider.updateApiKey(key)
    }

    fun toggleLocalMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(localMode = enabled)
        settingsStore.updateLocalMode(enabled)
        viewModelScope.launch {
            settingsStore.persistLocalMode(enabled)
        }
    }

    fun exportData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(exportState = ExportState.InProgress)
            val result = exportManager.exportToZip()
            result.fold(
                onSuccess = { file ->
                    val uri = FileProvider.getUriForFile(
                        getApplication(),
                        "${getApplication<Application>().packageName}.fileprovider",
                        file
                    )
                    _uiState.value = _uiState.value.copy(exportState = ExportState.Success(uri))
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        exportState = ExportState.Error(e.message ?: "Export failed")
                    )
                }
            )
        }
    }

    fun clearExportState() {
        _uiState.value = _uiState.value.copy(exportState = ExportState.Idle)
    }

    fun restoreData(uri: Uri) {
        val generation = restoreOperationGeneration.incrementAndGet()
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = _uiState.value.copy(restoreState = RestoreState.InProgress)
            stagedRestoreFile?.delete()
            stagedRestoreFile = null
            pendingRestoreRootIds = null
            val staged = runCatching {
                File.createTempFile(
                    "restore_",
                    ".zip",
                    getApplication<Application>().cacheDir
                )
            }.getOrElse { error ->
                if (generation == restoreOperationGeneration.get()) {
                    _uiState.value = _uiState.value.copy(
                        restoreState = RestoreState.Error(error.message ?: "Restore failed")
                    )
                }
                return@launch
            }
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open restore archive." }
                    staged.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        var count = input.read(buffer)
                        while (count >= 0) {
                            if (count > 0) {
                                total += count
                                require(total <= MAX_RESTORE_ARCHIVE_BYTES) {
                                    "Restore archive is too large."
                                }
                                output.write(buffer, 0, count)
                            }
                            count = input.read(buffer)
                        }
                    }
                }
                exportManager.previewRestore(staged, RestoreScope.All).getOrThrow()
            }.fold(
                onSuccess = { preview ->
                    if (generation == restoreOperationGeneration.get()) {
                        stagedRestoreFile = staged
                        pendingRestoreRootIds = preview.rootRawRecordIds.toSet()
                        _uiState.value = _uiState.value.copy(
                            restoreState = RestoreState.PreviewReady(preview)
                        )
                    } else {
                        staged.delete()
                    }
                },
                onFailure = { error ->
                    staged.delete()
                    if (generation == restoreOperationGeneration.get()) {
                        stagedRestoreFile = null
                        pendingRestoreRootIds = null
                        _uiState.value = _uiState.value.copy(
                            restoreState = RestoreState.Error(error.message ?: "Restore failed")
                        )
                    }
                }
            )
        }
    }

    fun toggleRestoreRoot(rawRecordId: Long, selected: Boolean) {
        val current = _uiState.value.restoreState as? RestoreState.PreviewReady ?: return
        if (rawRecordId !in current.preview.availableRoots.map { it.rawRecordId }) return
        val selectedIds = (pendingRestoreRootIds ?: current.preview.rootRawRecordIds).toMutableSet()
        if (selected) {
            selectedIds += rawRecordId
        } else {
            if (selectedIds.size == 1 && rawRecordId in selectedIds) return
            selectedIds -= rawRecordId
        }
        pendingRestoreRootIds = selectedIds
        previewSelectedRestore(selectedIds)
    }

    fun restoreSelectedData() {
        val current = _uiState.value.restoreState as? RestoreState.PreviewReady ?: return
        val selectedIds = pendingRestoreRootIds ?: current.preview.rootRawRecordIds.toSet()
        restoreStaged(RestoreScope.SelectedRawRecords(selectedIds))
    }

    fun mergeAllData() {
        restoreStaged(RestoreScope.All)
    }

    fun cancelRestorePreview() {
        stagedRestoreFile?.delete()
        stagedRestoreFile = null
        pendingRestoreRootIds = null
        _uiState.value = _uiState.value.copy(restoreState = RestoreState.Idle)
    }

    private fun previewSelectedRestore(selectedIds: Set<Long>) {
        val staged = stagedRestoreFile ?: return
        val generation = restoreOperationGeneration.incrementAndGet()
        viewModelScope.launch(ioDispatcher) {
            exportManager.previewRestore(
                staged,
                RestoreScope.SelectedRawRecords(selectedIds)
            ).fold(
                onSuccess = { preview ->
                    if (generation == restoreOperationGeneration.get() && staged == stagedRestoreFile) {
                        pendingRestoreRootIds = preview.rootRawRecordIds.toSet()
                        _uiState.value = _uiState.value.copy(
                            restoreState = RestoreState.PreviewReady(preview)
                        )
                    }
                },
                onFailure = { error ->
                    if (generation == restoreOperationGeneration.get() && staged == stagedRestoreFile) {
                        staged.delete()
                        stagedRestoreFile = null
                        pendingRestoreRootIds = null
                        _uiState.value = _uiState.value.copy(
                            restoreState = RestoreState.Error(error.message ?: "Restore preview failed")
                        )
                    }
                }
            )
        }
    }

    private fun restoreStaged(scope: RestoreScope) {
        val staged = stagedRestoreFile ?: return
        val generation = restoreOperationGeneration.incrementAndGet()
        viewModelScope.launch(ioDispatcher) {
            _uiState.value = _uiState.value.copy(restoreState = RestoreState.InProgress)
            exportManager.restoreFromZip(staged, scope).fold(
                onSuccess = {
                    if (generation == restoreOperationGeneration.get() && staged == stagedRestoreFile) {
                        staged.delete()
                        stagedRestoreFile = null
                        pendingRestoreRootIds = null
                        _uiState.value = _uiState.value.copy(restoreState = RestoreState.Success)
                    }
                },
                onFailure = { error ->
                    if (generation == restoreOperationGeneration.get() && staged == stagedRestoreFile) {
                        staged.delete()
                        stagedRestoreFile = null
                        pendingRestoreRootIds = null
                        _uiState.value = _uiState.value.copy(
                            restoreState = RestoreState.Error(error.message ?: "Restore failed")
                        )
                    }
                }
            )
        }
    }

    fun clearRestoreState() {
        restoreOperationGeneration.incrementAndGet()
        stagedRestoreFile?.delete()
        stagedRestoreFile = null
        pendingRestoreRootIds = null
        _uiState.value = _uiState.value.copy(restoreState = RestoreState.Idle)
    }

    override fun onCleared() {
        restoreOperationGeneration.incrementAndGet()
        stagedRestoreFile?.delete()
        stagedRestoreFile = null
        pendingRestoreRootIds = null
        super.onCleared()
    }

    fun restoreCard(cardKey: String) {
        viewModelScope.launch {
            knowledgeRepository.restoreCard(cardKey)
            _uiState.value = _uiState.value.copy(
                dismissedCards = _uiState.value.dismissedCards.filterNot { it.cardKey == cardKey }
            )
        }
    }
}
