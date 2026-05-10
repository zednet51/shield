package com.aggregatorx.shielded.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregatorx.shielded.data.model.ProviderEntity
import com.aggregatorx.shielded.data.repository.ShieldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProvidersUiState(
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val repo: ShieldRepository
) : ViewModel() {

    val providers: StateFlow<List<ProviderEntity>> = repo.observeAllProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _state = MutableStateFlow(ProvidersUiState())
    val state: StateFlow<ProvidersUiState> = _state.asStateFlow()

    fun toggleEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.setProviderEnabled(name, enabled)
            _state.update { it.copy(message = "Provider ${if (enabled) "enabled" else "disabled"}") }
        }
    }

    fun addProvider(provider: ProviderEntity) {
        viewModelScope.launch {
            try {
                repo.upsertProvider(provider)
                _state.update { it.copy(message = "Provider '${provider.name}' added") }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteProvider(name: String) {
        viewModelScope.launch {
            try {
                repo.deleteProvider(name)
                _state.update { it.copy(message = "Provider '$name' deleted") }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }
    fun clearError()   { _state.update { it.copy(error = null) } }
}
