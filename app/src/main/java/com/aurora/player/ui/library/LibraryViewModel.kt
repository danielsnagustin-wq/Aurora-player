package com.aurora.player.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.FavoriteEntity
import com.aurora.player.data.FolderStore
import com.aurora.player.data.LibraryPrefsStore
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.SongStatsRow
import com.aurora.player.model.Album
import com.aurora.player.model.Artist
import com.aurora.player.model.SearchCategory
import com.aurora.player.model.Song
import com.aurora.player.model.isInExcludedFolder
import com.aurora.player.model.matchesSearchQuery
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
    private val folderStore = FolderStore(application)
    private val libraryPrefs = LibraryPrefsStore(application)
    private val db = AuroraDatabase.getInstance(application)

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState

    // Ronda 31: si el escaneo falló, el mensaje para la pantalla
    // vacía. Null cuando el escaneo fue bien o no hay música de verdad.
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError

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

    /** Filtro por género (pestaña Géneros). */
    private val _genreFilter = MutableStateFlow<String?>(null)
    val genreFilter: StateFlow<String?> = _genreFilter

    /** SPEC 005 FR-005: ámbito de la búsqueda (Todo, Canciones, ...). */
    private val _searchCategory = MutableStateFlow(SearchCategory.ALL)
    val searchCategory: StateFlow<SearchCategory> = _searchCategory

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups

    // Fase 2 — Listas inteligentes: estadísticas por canción
    // (reproducciones y última escucha, de Room). Se cargan con la
    // biblioteca y se releen al abrir la pestaña Listas.
    private val _stats = MutableStateFlow<Map<Long, SongStatsRow>>(emptyMap())

    /**
     * Canciones con sus datos reales de uso: favorito, nº de
     * reproducciones y última escucha. Es lo que alimentan las
     * listas inteligentes (sin esto, "Más escuchadas" no sabría
     * nada). Sin estadísticas, la canción cuenta como 0 escuchas.
     */
    val enrichedSongs: StateFlow<List<Song>> =
        combine(_uiState, _favoriteIds, _stats) { state, favs, stats ->
            val songs = (state as? LibraryUiState.Loaded)?.songs ?: emptyList()
            songs.map { song ->
                val row = stats[song.id]
                song.copy(
                    isFavorite = song.id in favs,
                    playCount = row?.plays ?: 0,
                    lastPlayedAt = row?.lastPlayed,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Relee las estadísticas de Room (al abrir la pestaña Listas). */
    fun refreshStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _stats.value = db.libraryDao().statsPerSong().associateBy { it.songId }
        }
    }

    init {
        // Favoritos en vivo: el flujo de Room avisa de cualquier
        // cambio, venga de la biblioteca, del reproductor o de las
        // playlists. Antes se cargaban una sola vez y el corazón del
        // reproductor no se reflejaba en la biblioteca.
        viewModelScope.launch(Dispatchers.IO) {
            db.libraryDao().favoriteIdsFlow().collect { ids ->
                _favoriteIds.value = ids.toSet()
            }
        }
        // Ronda 19: el orden se pierde al recrear el ViewModel (antes
        // solo vivía en memoria). Se persiste en LibraryPrefsStore;
        // un nombre desconocido (versión vieja) cae a TITLE.
        viewModelScope.launch {
            libraryPrefs.sortFlow.collect { name ->
                _sortOrder.value = runCatching { LibrarySort.valueOf(name) }
                    .getOrDefault(LibrarySort.TITLE)
            }
        }
        // Ronda 17: estadísticas en vivo — las listas inteligentes
        // ("Más escuchadas", "Escuchadas recientemente") se
        // actualizaban solo al reabrir la pestaña Listas; ahora Room
        // avisa de cada escucha nueva y enrichedSongs se recalcula.
        viewModelScope.launch(Dispatchers.IO) {
            db.libraryDao().statsPerSongFlow().collect { rows ->
                _stats.value = rows.associateBy { it.songId }
            }
        }
        // SPEC 005 FR-006: si las carpetas excluidas cambian (desde
        // la pestaña Carpetas o desde Ajustes), recargar la
        // biblioteca con el nuevo conjunto. La primera emisión no
        // recarga: todavía no hay nada cargado.
        viewModelScope.launch {
            folderStore.excludedFlow.collect { excluded ->
                if (loaded && excluded != lastExcluded) refresh()
            }
        }
    }

    private var lastExcluded: Set<String> = emptySet()

    val filteredSongs: StateFlow<List<Song>> =
        combine(
            _uiState, _query, _sortOrder, _artistFilter, _albumFilter, _idFilter, _genreFilter,
            _searchCategory,
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
            val genre = values[6] as String?
            val category = values[7] as SearchCategory
            var songs = (state as? LibraryUiState.Loaded)?.songs ?: emptyList()
            if (ids != null) songs = songs.filter { it.id in ids }
            if (artist != null) songs = songs.filter { it.artist == artist }
            if (albumId != null) songs = songs.filter { it.albumId == albumId }
            if (genre != null) {
                // RONDA 4 (2026-11-09): la pestaña agrupa el género
                // en blanco como "Sin género", pero el filtro solo
                // trataba el null: esas canciones no aparecían al
                // tocar "Sin género".
                songs = songs.filter {
                    (it.genre?.takeIf { g -> g.isNotBlank() } ?: "Sin género") == genre
                }
            }
            if (q.isNotBlank()) {
                songs = songs.filter {
                    matchesSearchQuery(it.title, it.artist, it.album, it.genre, q, category)
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
            _scanError.value = null
            _uiState.value = LibraryUiState.Empty
            // RONDA 87: sin esto, al conceder el permiso otra vez
            // load(true) se topaba con loaded=true y volvía sin
            // escanear: la biblioteca se quedaba vacía hasta pulsar
            // "Volver a escanear" a mano.
            loaded = false
            return
        }
        if (loaded) return
        loaded = true
        _uiState.value = LibraryUiState.Loading
        viewModelScope.launch {
            // SPEC 005 FR-006: leer las carpetas excluidas antes del
            // escaneo, para filtrarlas fuera de todo.
            val excluded = folderStore.excludedFlow.first()
            lastExcluded = excluded
            var loadFailed = false
            val (songs, albums, artists) = try {
                withContext(Dispatchers.IO) {
                    // ETAPA 4: la duración mínima guardada en Ajustes sí se
                    // usa (antes loadSongs() siempre usaba 5 s fijos).
                    val minDurationMs = playerStore.settingsFlow.first().minDurationMs
                    // SPEC 005 FR-006: las carpetas excluidas no entran
                    // en ninguna pestaña, ni en la búsqueda, ni en los
                    // duplicados, ni en la cola (la cola guardada las
                    // pierde al restaurar, porque ya no están aquí).
                    val loadedSongs = repository.loadSongs(minDurationMs)
                        .filter { !isInExcludedFolder(it.path, excluded) }
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
                loadFailed = true
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
            _scanError.value = repository.lastLoadError
                // RONDA 54: si falló otra cosa del try (leer ajustes,
                // agrupar álbumes/artistas), lastLoadError no lo cubre
                // y la pantalla diría "biblioteca vacía" en vez de avisar.
                ?: if (loadFailed) "No se pudo escanear la biblioteca. Revisa el permiso de música e inténtalo otra vez." else null
            // RONDA 87: esta consulta quedaba fuera del try de carga:
            // si fallaba, la corrutina moría con la excepción y la
            // app se cerraba con la biblioteca ya cargada. Ahora se
            // queda sin estadísticas, que es lo que toca.
            _stats.value = try {
                withContext(Dispatchers.IO) {
                    db.libraryDao().statsPerSong().associateBy { it.songId }
                }
            } catch (e: Exception) {
                ErrorLog.log(getApplication(), "Estadísticas de biblioteca", e)
                emptyMap()
            }
        }
    }

    /** SPEC 005 FR-006: excluir una carpeta (sale de toda la biblioteca). */
    fun excludeFolder(path: String) {
        viewModelScope.launch { folderStore.exclude(path) }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** SPEC 005 FR-005: cambiar el ámbito de la búsqueda. */
    fun setSearchCategory(category: SearchCategory) {
        _searchCategory.value = category
    }

    /**
     * SPEC 005 FR-005: mantener pulsado un ámbito lo aísla: busca
     * solo ahí y quita cualquier otro filtro (artista, álbum,
     * género, duplicados).
     */
    fun isolateSearchCategory(category: SearchCategory) {
        _searchCategory.value = category
        _artistFilter.value = null
        _albumFilter.value = null
        _idFilter.value = null
        _genreFilter.value = null
    }

    fun setSort(sort: LibrarySort) {
        _sortOrder.value = sort
        viewModelScope.launch { libraryPrefs.saveSort(sort.name) }
    }

    fun filterByArtist(artist: String?) {
        _artistFilter.value = artist
        _albumFilter.value = null
        _idFilter.value = null
        _genreFilter.value = null
    }

    fun filterByAlbum(albumId: Long?) {
        _albumFilter.value = albumId
        _artistFilter.value = null
        _idFilter.value = null
        _genreFilter.value = null
    }

    fun filterByIds(ids: Set<Long>?) {
        _idFilter.value = ids
        _artistFilter.value = null
        _albumFilter.value = null
        _genreFilter.value = null
    }

    fun filterByGenre(genre: String?) {
        _genreFilter.value = genre
        _artistFilter.value = null
        _albumFilter.value = null
        _idFilter.value = null
    }

    fun clearFilters() {
        _artistFilter.value = null
        _albumFilter.value = null
        _idFilter.value = null
        _genreFilter.value = null
    }

    fun toggleFavorite(songId: Long) {
        // El estado se actualiza solo por el flujo de favoritos: no se
        // toca _favoriteIds aquí para no pelear con Room.
        viewModelScope.launch(Dispatchers.IO) {
            if (songId in _favoriteIds.value) {
                db.libraryDao().removeFavorite(songId)
            } else {
                db.libraryDao().addFavorite(FavoriteEntity(songId))
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
