package com.aurora.player.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.player.data.MusicRepository
import com.aurora.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aurora Player — ViewModel de la biblioteca.
 * El escaneo de MediaStore corre en Dispatchers.IO, nunca en el hilo
 * principal (con 1471 canciones el escaneo en el hilo de UI trababa
 * la app). La UI solo observa StateFlow.
 */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Loaded(val songs: List<Song>) : LibraryUiState
    data object Empty : LibraryUiState
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val filteredSongs: StateFlow<List<Song>> =
        combine(_uiState, _query) { state, q ->
            val songs = (state as? LibraryUiState.Loaded)?.songs ?: emptyList()
            if (q.isBlank()) {
                songs
            } else {
                songs.filter {
                    it.title.contains(q, ignoreCase = true) ||
                        it.artist.contains(q, ignoreCase = true) ||
                        it.album.contains(q, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var hasPermission = false
    private var loaded = false

    fun load(hasAudioPermission: Boolean) {
        hasPermission = hasAudioPermission
        if (!hasAudioPermission) {
            _uiState.value = LibraryUiState.Empty
            return
        }
        if (loaded) return
        loaded = true
        _uiState.value = LibraryUiState.Loading
        viewModelScope.launch {
            val songs = withContext(Dispatchers.IO) { repository.loadSongs() }
            _uiState.value =
                if (songs.isEmpty()) LibraryUiState.Empty else LibraryUiState.Loaded(songs)
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun refresh() {
        loaded = false
        load(hasPermission)
    }
}
