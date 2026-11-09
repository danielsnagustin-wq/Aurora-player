package com.aurora.player.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.FavoriteEntity
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.model.Album
import com.aurora.player.model.Artist
import com.aurora.player.model.Song
import com.aurora.player.util.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aurora Player — ViewModel de la biblioteca.
 * El escaneo de MediaStore corre en Dispatchers.IO, nunca en el hilo
 * principal (con 1471 canciones el escaneo en el hilo de UI trababa
 * la app). La UI solo observa StateFlow.
 *
 * ETAPA 3 (renovación): pestañas Artistas/Álbumes, orden (título,
 * artista, álbum, fecha, duración), favoritos persistentes en Room
 * (tabla favorites, ya existe en v1) y detección de duplicados
 * (mismo título + artista + duración). Nada se borra: duplicados
 * solo se detectan y se muestran.
 */
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Loaded(
        val songs: List<Song>,
        val albums: List<Album>,
        val artists: List<Artist>,
    ) : LibraryUiState
    data object Empty : LibraryUiState
}

enum class LibrarySort(val label: String) {
    TITLE("Título"),
    ARTIST("Artista"),
    ALBUM("Álbum"),
    DATE_ADDED("Fecha añadida"),
    DURATION("Duración"),
    PATH("Ruta"),
}

/** Un grupo de canciones duplicadas (mismo título, artista y duración). */
data class DuplicateGroup(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val songs: List<Song>,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    private val playerStore = PlayerSettingsStore(application)
    private val db = Room.databaseBuilder(
        application,
        AuroraDatabase::class.java,
        "aurora-player.db",
    ).build()

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _sortOrder = MutableStateFlow(LibrarySort.TITLE)
    val sortOrder: StateFlow<LibrarySort> = _sortOrder

    /** Filtro por artista (pestaña Artistas) o por álbum (pestaña Álbumes). */
    private val _artistFilter = MutableStateFlow<String?>(null)
    val artistFilter: StateFlow<String?> = _artistFilter
    private val _albumFilter = MutableStateFlow<Long?>(null)
    val albumFilter: StateFlow<Long?> = _albumFilter

    /** Filtro por un grupo de duplicados (solo esas canciones). */
    private val _idFilter = MutableStateFlow<Set<Long>?>(null)
    val idFilter: StateFlow<Set<Long>?> = _idFilter

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _favoriteIds.value = db.libraryDao().favoriteIds().toSet()
        }
    }

    val filteredSongs: StateFlow<List<Song>> =
        combine(
            _uiState, _query, _sortOrder, _artistFilter, _albumFilter, _idFilter,
        ) { values ->
            // Forma de array (el overload de 6 flujos no resolvía en
            // este Kotlin: el compilador pedía la función de 1 parámetro).
            @Suppress("UNCHECKED_CAST")
            val state = values[0] as LibraryUiState
            val q = values[1] as String
            val sort = values[2] as LibrarySort
            val artist = values[3] as String?
            val albumId = values[4] as Long?
            val ids = values[5] as Set<Long>?
            var songs = (state as? LibraryUiState.Loaded)?.songs ?: emptyList()
            if (ids != null) songs = songs.filter { it.id in ids }
            if (artist != null) songs = songs.filter { it.artist == artist }
            if (albumId != null) songs = songs.filter { it.albumId == albumId }
            if (q.isNotBlank()) {
                songs = songs.filter {
                    it.title.contains(q, ignoreCase = true) ||
                        it.artist.contains(q, ignoreCase = true) ||
                        it.album.contains(q, ignoreCase = true)
                }
            }
            when (sort) {
                LibrarySort.TITLE -> songs.sortedBy { it.title.lowercase() }
                LibrarySort.ARTIST -> songs.sortedWith(
                    compareBy({ it.artist.lowercase() }, { it.title.lowercase() }),
                )
                LibrarySort.ALBUM -> songs.sortedWith(
                    compareBy({ it.album.lowercase() }, { it.title.lowercase() }),
                )
                LibrarySort.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
                LibrarySort.DURATION -> songs.sortedByDescending { it.durationMs }
                // Idea open source (Auxio/Gramophone): ordenar por ruta
                // o nombre de archivo, útil con carpetas organizadas.
                LibrarySort.PATH -> songs.sortedBy { it.path.lowercase() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> =
        combine(_uiState, _favoriteIds) { state, favs ->
            val songs = (state as? LibraryUiState.Loaded)?.songs ?: emptyList()
            songs.filter { it.id in favs }.sortedBy { it.title.lowercase() }
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
            val (songs, albums, artists) = try {
                withContext(Dispatchers.IO) {
                    // ETAPA 4: la duración mínima guardada en Ajustes sí se
                    // usa (antes loadSongs() siempre usaba 5 s fijos).
                    val minDurationMs = playerStore.settingsFlow.first().minDurationMs
                    val loadedSongs = repository.loadSongs(minDurationMs)
                    Triple(
                        loadedSongs,
                        repository.loadAlbums(loadedSongs),
                        repository.loadArtists(loadedSongs),
                    )
                }
            } catch (e: Exception) {
                // La biblioteca nunca se queda en "Cargando…" para
                // siempre: un fallo muestra el estado vacío.
                ErrorLog.log(getApplication(), "Cargar biblioteca", e)
                Triple(emptyList(), emptyList(), emptyList())
            }
            _duplicateGroups.value = songs
                .groupBy { Triple(it.title.lowercase(), it.artist.lowercase(), it.durationMs) }
                .values
                .filter { it.size > 1 }
                .map { DuplicateGroup(it.first().title, it.first().artist, it.first().durationMs, it) }
                .sortedByDescending { it.songs.size }
            _uiState.value =
                if (songs.isEmpty()) LibraryUiState.Empty
                else LibraryUiState.Loaded(songs, albums, artists)
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setSort(sort: LibrarySort) {
        _sortOrder.value = sort
    }

    fun filterByArtist(artist: String?) {
        _artistFilter.value = artist
        _albumFilter.value = null
        _idFilter.value = null
    }

    fun filterByAlbum(albumId: Long?) {
        _albumFilter.value = albumId
        _artistFilter.value = null
        _idFilter.value = null
    }

    fun filterByIds(ids: Set<Long>?) {
        _idFilter.value = ids
        _artistFilter.value = null
        _albumFilter.value = null
    }

    fun clearFilters() {
        _artistFilter.value = null
        _albumFilter.value = null
        _idFilter.value = null
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _favoriteIds.value
            if (songId in current) {
                db.libraryDao().removeFavorite(songId)
                _favoriteIds.value = current - songId
            } else {
                db.libraryDao().addFavorite(FavoriteEntity(songId))
                _favoriteIds.value = current + songId
            }
        }
    }

    /** Reproducciones y última escucha de una canción (ETAPA 5: Detalles). */
    suspend fun songStats(songId: Long): Pair<Int, Long?> = withContext(Dispatchers.IO) {
        db.libraryDao().playCountFor(songId) to db.libraryDao().lastPlayedAt(songId)
    }

    fun refresh() {
        loaded = false
        load(hasPermission)
    }
}
