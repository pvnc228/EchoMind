package com.echomind.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomind.domain.model.Entry
import com.echomind.domain.usecase.GetEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val entries: List<Entry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedCategory: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getEntriesUseCase: GetEntriesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadEntries()
    }

    fun loadEntries(category: String? = null) {
        viewModelScope.launch {
            val flow = if (category != null) {
                getEntriesUseCase.getEntriesByCategory(category)
            } else {
                getEntriesUseCase.getAllEntries()
            }
            flow.catch { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }.collect { entries ->
                _uiState.value = _uiState.value.copy(
                    entries = entries,
                    isLoading = false,
                    selectedCategory = category
                )
            }
        }
    }

    fun selectCategory(category: String?) {
        loadEntries(category)
    }
}
