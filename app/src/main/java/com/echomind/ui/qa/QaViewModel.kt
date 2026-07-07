package com.echomind.ui.qa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.domain.usecase.AskQuestionUseCase
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
    val error: String? = null
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

        val userMessage = ChatMessage(text = question, isUser = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            currentInput = "",
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            val result = askQuestionUseCase(question)
            result.fold(
                onSuccess = { qaResult ->
                    val aiMessage = ChatMessage(
                        text = qaResult.answer,
                        isUser = false,
                        sourceEntryIds = qaResult.sourceEntryIds
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + aiMessage,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to get answer"
                    )
                }
            )
        }
    }
}
