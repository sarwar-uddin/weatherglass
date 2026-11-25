package com.github.sarwaruddin.weatherglass.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.sarwaruddin.weatherglass.data.Bundle
import com.github.sarwaruddin.weatherglass.data.Place
import com.github.sarwaruddin.weatherglass.data.Repo
import com.github.sarwaruddin.weatherglass.data.SearchHit
import com.github.sarwaruddin.weatherglass.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ErrKind { NETWORK, OFFLINE, LOCATION }

data class UiState(
    val places: List<Place> = emptyList(),
    val selected: Int = 0,
    val bundle: Bundle? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** What kind of failure [error] describes, so the UI can offer the right way out. */
    val errKind: ErrKind? = null,
    val search: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val locating: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var searchJob: Job? = null

    init {
        val places = repo.places()
        val sel = places.indexOfFirst { it.key == repo.selectedKey() }.coerceAtLeast(0)
        _state.value = UiState(places = places, selected = sel)
        show(sel, force = false)
    }

    private val place get() = _state.value.places.getOrNull(_state.value.selected)

    fun select(i: Int) {
        val p = _state.value.places.getOrNull(i) ?: return
        repo.setSelectedKey(p.key)
        _state.update { it.copy(selected = i) }
        show(i, force = false)
    }

    fun refresh() = show(_state.value.selected, force = true)

    /** Called on resume: refresh quietly if the data went stale. */
    fun resume() = show(_state.value.selected, force = false)

    private fun show(i: Int, force: Boolean) {
        val p = _state.value.places.getOrNull(i) ?: return
        refreshJob?.cancel()
        _state.update { it.copy(refreshing = true, error = null, errKind = null) }
        refreshJob = viewModelScope.launch {
            // Show the saved copy first (parsing ~1 MB, so off the main thread).
            val cached = withContext(Dispatchers.IO) { repo.cached(p) }
            _state.update { it.copy(bundle = cached) }
            try {
                repo.refresh(p, force) { b -> if (place?.key == p.key) _state.update { it.copy(bundle = b) } }
                // Home-screen widgets redraw from the fresh cache (no network).
                runCatching { WidgetUpdater.updateAll(getApplication()) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    val saved = it.bundle?.forecast != null
                    it.copy(
                        error = if (saved) "Can't reach Open-Meteo. Showing saved data." else "Can't reach Open-Meteo.",
                        errKind = if (saved) ErrKind.OFFLINE else ErrKind.NETWORK,
                    )
                }
            } finally {
                // A superseded job must not clear the flag for the one that replaced it.
                if (refreshJob === coroutineContext[Job]) _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun search(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 2) { _state.update { it.copy(search = emptyList(), searching = false) }; return }
        searchJob = viewModelScope.launch {
            delay(300) // debounce typing
            _state.update { it.copy(searching = true) }
            val hits = runCatching { repo.search(q.trim()) }.getOrDefault(emptyList())
            _state.update { it.copy(search = hits, searching = false) }
        }
    }

    fun add(h: SearchHit) {
        val p = Place(h.name, listOf(h.region, h.country).filter { it.isNotBlank() }.joinToString(", "), h.lat, h.lng)
        val list = _state.value.places.filterNot { it.key == p.key } + p
        repo.savePlaces(list)
        _state.update { it.copy(places = list, search = emptyList()) }
        select(list.lastIndex)
    }

    fun remove(i: Int) {
        val list = _state.value.places.toMutableList()
        if (list.size <= 1 || i !in list.indices) return
        list.removeAt(i)
        repo.savePlaces(list)
        val sel = _state.value.selected.let { if (it >= list.size) list.lastIndex else if (it > i) it - 1 else it }
        _state.update { it.copy(places = list) }
        select(sel)
    }

    /** Permission already granted by the activity. Puts the GPS place first. */
    fun locate() {
        viewModelScope.launch {
            _state.update { it.copy(locating = true) }
            val p = repo.locate()
            _state.update { it.copy(locating = false) }
            if (p == null) {
                _state.update { it.copy(error = "No location fix. Try again, or pick a city.", errKind = ErrKind.LOCATION) }
                return@launch
            }
            val list = listOf(p) + _state.value.places.filterNot { it.gps }
            repo.savePlaces(list)
            _state.update { it.copy(places = list) }
            repo.setSelectedKey(p.key)
            _state.update { it.copy(selected = 0) }
            show(0, force = true) // new coordinates: the shared "gps" cache is stale
        }
    }
}
