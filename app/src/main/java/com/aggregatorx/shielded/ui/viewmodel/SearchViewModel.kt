package com.aggregatorx.shielded.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregatorx.shielded.data.model.PageDirection
import com.aggregatorx.shielded.data.model.ProviderEntity
import com.aggregatorx.shielded.data.model.ResultItem
import com.aggregatorx.shielded.data.repository.ShieldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val resultsByProvider: Map<String, List<ResultItem>> = emptyMap(),
    val providerPages: Map<String, Int> = emptyMap(),
    val isSearching: Boolean = false,
    val isPaused: Boolean = false,
    val activeTab: SearchTab = SearchTab.TOP,
    val error: String? = null
)

enum class SearchTab { TOP, MY_AI, TOKENS }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: ShieldRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(
        query = savedState["query"] ?: ""
    ))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    // Active discovery jobs — tracked so PAUSE can cancel them individually
    private val discoveryJobs = mutableMapOf<String, Job>()
    private val observerJobs  = mutableMapOf<String, Job>()

    val providers: StateFlow<List<ProviderEntity>> = repo.observeEnabledProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<ResultItem>> = repo.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tokens = repo.observeTokens()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Search ────────────────────────────────────────────────────────────────
    fun search(query: String) {
        if (query.isBlank()) return
        savedState["query"] = query
        _state.update { it.copy(query = query, isSearching = true, error = null, resultsByProvider = emptyMap()) }

        viewModelScope.launch {
            try {
                repo.search(query)
                // Attach observers for each enabled provider
                providers.value.forEach { attachObserver(it.name) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isSearching = false) }
            } finally {
                _state.update { it.copy(isSearching = false) }
            }
        }
    }

    // ── Pagination ────────────────────────────────────────────────────────────
    fun paginate(providerName: String, direction: PageDirection) {
        if (_state.value.isPaused) return
        val job = viewModelScope.launch {
            repo.paginate(providerName, direction, _state.value.query)
            val p = providers.value.find { it.name == providerName }
            if (p != null) {
                val newPage = when (direction) {
                    PageDirection.FORWARD -> (p.currentPage + 1)
                    PageDirection.BACK    -> (p.currentPage - 1).coerceAtLeast(1)
                    PageDirection.REFRESH -> p.currentPage
                }
                _state.update { it.copy(providerPages = it.providerPages + (providerName to newPage)) }
            }
        }
        discoveryJobs[providerName] = job
    }

    // ── Pause / Resume ────────────────────────────────────────────────────────
    fun togglePause() {
        val pausing = !_state.value.isPaused
        _state.update { it.copy(isPaused = pausing) }
        if (pausing) {
            // Cancel all active discovery coroutines — results feed stays static
            discoveryJobs.values.forEach { it.cancel() }
            discoveryJobs.clear()
        }
        // On resume, user must trigger a new search or paginate manually
    }

    // ── Panic Refresh ─────────────────────────────────────────────────────────
    fun panicRefresh() {
        // 1. Cancel everything
        discoveryJobs.values.forEach { it.cancel() }
        discoveryJobs.clear()
        observerJobs.values.forEach { it.cancel() }
        observerJobs.clear()

        // 2. Clear in-memory state
        _state.update { it.copy(
            resultsByProvider = emptyMap(),
            providerPages = emptyMap(),
            isSearching = false,
            isPaused = false,
            error = null
        )}

        // 3. Hint GC
        System.gc()

        // 4. Re-run last query if any
        val q = _state.value.query
        if (q.isNotBlank()) search(q)
    }

    // ── Favorites ─────────────────────────────────────────────────────────────
    fun toggleFavorite(item: ResultItem) {
        viewModelScope.launch { repo.toggleFavorite(item.id, item.isFavorite) }
    }

    // ── Tab ───────────────────────────────────────────────────────────────────
    fun setTab(tab: SearchTab) { _state.update { it.copy(activeTab = tab) } }

    // ── Token cleanup ─────────────────────────────────────────────────────────
    fun purgeTokens() { viewModelScope.launch { repo.purgeTokens() } }
    fun deleteToken(id: String) { viewModelScope.launch { repo.deleteToken(id) } }

    // ── Internal ──────────────────────────────────────────────────────────────
    private fun attachObserver(providerName: String) {
        if (observerJobs.containsKey(providerName)) return
        val job = viewModelScope.launch {
            repo.observeResults(providerName).collect { results ->
                _state.update { s ->
                    s.copy(resultsByProvider = s.resultsByProvider + (providerName to results))
                }
            }
        }
        observerJobs[providerName] = job
    }

    override fun onCleared() {
        super.onCleared()
        discoveryJobs.values.forEach { it.cancel() }
        observerJobs.values.forEach { it.cancel() }
    }
}
