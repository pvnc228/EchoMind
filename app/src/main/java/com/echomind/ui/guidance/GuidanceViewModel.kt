package com.echomind.ui.guidance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.data.guidance.GuidanceFeedbackStore
import com.echomind.data.guidance.GuidanceRating
import com.echomind.data.remote.GuidancePreview
import com.echomind.data.repository.AiNetworkDisabledException
import com.echomind.data.repository.GuidanceRequestResult
import com.echomind.data.repository.StaleRemoteConsentException
import com.echomind.domain.model.GuidanceRefusalReason
import com.echomind.domain.usecase.GuidanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuidanceMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val sourceEntryIds: List<Long> = emptyList(),
    val rating: GuidanceRating? = null,
    val hasFeedback: Boolean = false
)

data class GuidanceUiState(
    val messages: List<GuidanceMessage> = emptyList(),
    val currentInput: String = "",
    val isLoading: Boolean = false,
    val refusal: String? = null,
    val pendingPreview: GuidancePreview? = null
)

@HiltViewModel
class GuidanceViewModel @Inject constructor(
    private val guidanceUseCase: GuidanceUseCase,
    private val feedbackStore: GuidanceFeedbackStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuidanceUiState())
    val uiState: StateFlow<GuidanceUiState> = _uiState.asStateFlow()

    fun onInputChanged(input: String) {
        _uiState.value = _uiState.value.copy(currentInput = input)
    }

    fun sendMessage() {
        val question = _uiState.value.currentInput.trim()
        if (question.isBlank() || _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(
            currentInput = "",
            isLoading = true,
            refusal = null
        )
        viewModelScope.launch {
            when (val result = guidanceUseCase.request(question)) {
                is GuidanceRequestResult.Ready -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pendingPreview = result.preview
                    )
                }
                is GuidanceRequestResult.Refused -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        refusal = refusalText(result.reason, result.focusedQuestion)
                    )
                }
                is GuidanceRequestResult.Failed -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        refusal = refusalFromError(result.error)
                    )
                }
            }
        }
    }

    fun approveRemoteRequest() {
        val preview = _uiState.value.pendingPreview ?: return
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, refusal = null)
        viewModelScope.launch {
            val result = guidanceUseCase.sendApproved(preview.requestId)
            result.fold(
                onSuccess = { outcome ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages +
                            GuidanceMessage(
                                id = "user-${preview.requestId}",
                                text = preview.question,
                                isUser = true
                            ) +
                            GuidanceMessage(
                                id = preview.requestId,
                                text = outcome.answer,
                                isUser = false,
                                sourceEntryIds = outcome.sourceEntryIds
                            ),
                        isLoading = false,
                        pendingPreview = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pendingPreview = null,
                        refusal = refusalFromError(error)
                    )
                }
            )
        }
    }

    fun cancelRemoteRequest() {
        val preview = _uiState.value.pendingPreview ?: return
        _uiState.value = _uiState.value.copy(pendingPreview = null, refusal = null)
        viewModelScope.launch {
            guidanceUseCase.cancel(preview.requestId)
        }
    }

    fun rateMessage(messageId: String, rating: GuidanceRating, outcome: String? = null) {
        viewModelScope.launch {
            feedbackStore.record(messageId, rating, outcome)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.map { message ->
                    if (message.id == messageId && !message.isUser) {
                        message.copy(rating = rating, hasFeedback = true)
                    } else {
                        message
                    }
                }
            )
        }
    }

    override fun onCleared() {
        _uiState.value.pendingPreview?.requestId?.let(guidanceUseCase::cancelNow)
        super.onCleared()
    }

    private fun refusalText(reason: GuidanceRefusalReason, focusedQuestion: String?): String = when (reason) {
        GuidanceRefusalReason.LOCAL_MODE -> "Guidance is unavailable while local mode is on."
        GuidanceRefusalReason.UNSAFE_PROMPT ->
            "EchoMind cannot diagnose or infer hidden motives. It can only reason from your confirmed conclusions."
        GuidanceRefusalReason.INSUFFICIENT_EVIDENCE ->
            focusedQuestion?.let { "Not enough confirmed evidence. $it" }
                ?: "Not enough confirmed evidence to give grounded guidance."
    }

    private fun refusalFromError(error: Throwable): String = when (error) {
        is AiNetworkDisabledException -> "Remote access is disabled while local mode is on."
        is StaleRemoteConsentException -> "This request is outdated. Review a new preview before sending."
        else -> "The guidance request failed. Nothing was retained."
    }
}
