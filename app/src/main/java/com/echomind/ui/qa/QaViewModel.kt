package com.echomind.ui.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.repository.AiNetworkDisabledException
import com.echomind.data.repository.NoConfirmedContextException
import com.echomind.data.repository.RemoteApprovalRequiredException
import com.echomind.data.repository.StaleRemoteConsentException
import com.echomind.domain.usecase.AskQuestionUseCase
import com.echomind.data.remote.RemoteQuestionPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val sourceEntryIds: List<Long> = emptyList()
)

data class QaUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentInput: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val pendingPreview: RemoteQuestionPreview? = null
)

@HiltViewModel
class QaViewModel @Inject constructor(
    private val askQuestionUseCase: AskQuestionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QaUiState())
    val uiState: StateFlow<QaUiState> = _uiState.asStateFlow()

    fun onInputChanged(input: String) {
        _uiState.value = _uiState.value.copy(currentInput = input)
    }

    fun sendMessage() {
        val question = _uiState.value.currentInput.trim()
        if (question.isBlank() || _uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(
            currentInput = "",
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            val result = askQuestionUseCase.preview(question)
            result.fold(
                onSuccess = { preview ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pendingPreview = preview
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = userFacingError(e)
                    )
                }
            )
        }
    }

    fun approveRemoteRequest() {
        val preview = _uiState.value.pendingPreview ?: return
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = askQuestionUseCase.sendApproved(preview.requestId)
            result.fold(
                onSuccess = { qaResult ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages +
                            ChatMessage(text = preview.question, isUser = true) +
                            ChatMessage(
                                text = qaResult.answer,
                                isUser = false,
                                sourceEntryIds = qaResult.sourceEntryIds
                            ),
                        isLoading = false,
                        pendingPreview = null
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pendingPreview = null,
                        error = userFacingError(e)
                    )
                }
            )
        }
    }

    fun cancelRemoteRequest() {
        val preview = _uiState.value.pendingPreview ?: return
        _uiState.value = _uiState.value.copy(pendingPreview = null, error = null)
        viewModelScope.launch {
            askQuestionUseCase.cancel(preview.requestId)
        }
    }

    override fun onCleared() {
        _uiState.value.pendingPreview?.requestId?.let(askQuestionUseCase::cancelNow)
        super.onCleared()
    }

    private fun userFacingError(error: Throwable): String = when (error) {
        is AiNetworkDisabledException -> "Remote access is disabled while local mode is on."
        is NoConfirmedContextException -> "No confirmed conclusions match this question. Nothing was sent."
        is RemoteApprovalRequiredException -> "Review and approve the exact request before sending it."
        is StaleRemoteConsentException -> "This request is outdated. Review a new preview before sending."
        else -> "The remote request failed. Nothing was retained; review and approve a new request."
    }
}
