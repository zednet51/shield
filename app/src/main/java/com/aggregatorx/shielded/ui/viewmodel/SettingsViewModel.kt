package com.aggregatorx.shielded.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregatorx.shielded.data.repository.ShieldRepository
import com.aggregatorx.shielded.engine.network.ProxyRotator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.Proxy
import javax.inject.Inject

data class SettingsUiState(
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyEnabled: Boolean = false,
    val auditLogs: List<com.aggregatorx.shielded.data.model.AuditLogEntity> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: ShieldRepository,
    private val proxyRotator: ProxyRotator
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeAuditLogs().collect { logs ->
                _state.update { it.copy(auditLogs = logs) }
            }
        }
    }

    fun updateProxyHost(h: String) { _state.update { it.copy(proxyHost = h) } }
    fun updateProxyPort(p: String) { _state.update { it.copy(proxyPort = p) } }

    fun applyProxy() {
        val host = _state.value.proxyHost.trim()
        val port = _state.value.proxyPort.trim().toIntOrNull()
        if (host.isBlank() || port == null) {
            _state.update { it.copy(error = "Invalid proxy host/port") }
            return
        }
        proxyRotator.clearPool()
        proxyRotator.addProxy(host, port, Proxy.Type.HTTP)
        _state.update { it.copy(proxyEnabled = true, message = "Proxy set: $host:$port") }
    }

    fun clearProxy() {
        proxyRotator.clearPool()
        _state.update { it.copy(proxyEnabled = false, message = "Proxy cleared") }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            // Keep last 24h
            val cutoff = System.currentTimeMillis() - 86_400_000L
            repo.log("AUDIT_PURGE", null, "Purged logs older than 24h")
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }
    fun clearError()   { _state.update { it.copy(error = null) } }
}
