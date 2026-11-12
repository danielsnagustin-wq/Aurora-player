package com.aurora.player.ui.playlists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.FolderStore
import com.aurora.player.data.PlaylistEntity
import com.aurora.player.data.PlaylistSongEntity
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.model.Song
import com.aurora.player.model.isInExcludedFolder
import com.aurora.player.model.isInIncludedFolder
import com.aurora.player.util.ErrorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Aurora Player — ViewModel de playlists (Room, solo en el teléfono).
 * La base es la instancia única compartida (AuroraDatabase.getInstance);
 * antes cada ViewModel creaba la suya y los datos no se veían entre sí.
 * Todo corre en IO, nunca en el hilo de UI.
 */

/** Playlist inteligente: una regla sobre datos locales, no una lista fija. */
data class SmartPlaylist(
    val name: String,
    val description: String,
    val songs: List<Song>,
)

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AuroraDatabase.getInstance(application)
    private val dao = db.playlistDao()
    private val libraryDao = db.libraryDao()
    private val repository = MusicRepository(application)
    private val playerStore = PlayerSettingsStore(application)
    private val folderStore = FolderStore(application)

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists

    /** Playlists inteligentes (ETAPA 5): reglas sobre datos locales. */
    private val _smartPlaylists = MutableStateFlow<List<SmartPlaylist>>(emptyList())
    val smartPlaylists: StateFlow<List<SmartPlaylist>> = _smartPlaylists

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = _selectedId

    private val _selectedSongs = MutableStateFlow<List<Song>>(emptyList())
    val selectedSongs: StateFlow<List<Song>> = _selectedSongs

    private var loaded = false
    private var loadedPermission: Boolean? = null
    // RONDA 166: crear e importar hacen "existe? si no, inserta"
    // sobre el mismo nombre. Sin candado, dos toques seguidos
    // veían que no existía y creaban DOS playlists iguales (mismo
    // patrón que "Tu año" en la ronda 165).
    private val playlistMutex = Mutex()

    fun load(hasAudioPermission: Boolean) {
        // Refinamiento: si la primera carga fue sin permiso y el usuario
        // lo concede después, hay que volver a cargar (antes quedaba vacío).
        if (loaded && loadedPermission == hasAudioPermission) return
        loaded = true
        loadedPermission = hasAudioPermission
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Misma duración mínima que la biblioteca: el
                    // picker no muestra canciones que la biblioteca
                    // esconde (RONDA 2, 2026-11-09).
                    val minDurationMs = playerStore.settingsFlow.first().minDurationMs
                    // ESCANEAR (2026-11-11): las listas inteligentes
                    // y el selector de canciones usan la misma
                    // biblioteca que la pantalla principal (mismos
                    // filtros de carpeta), no todo MediaStore.
                    val excluded = folderStore.excludedFlow.first()
                    val included = folderStore.includedFlow.first()
                    _allSongs.value = if (hasAudioPermission) {
                        repository.loadSongs(minDurationMs)
                            .filter { !isInExcludedFolder(it.path, excluded) }
                            .filter { isInIncludedFolder(it.path, included) }
                    } else {
                        emptyList()
                    }
                    _playlists.value = dao.allPlaylists()
                // Playlists inteligentes: solo con datos reales locales.
                val songs = _allSongs.value
                val byId = songs.associateBy { it.id }
                val favorites = libraryDao.favoriteIds().toSet()
                val playedIds = libraryDao.playedSongIds().toSet()
                val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                _smartPlaylists.value = listOf(
                    SmartPlaylist(
                        "Más escuchadas",
                        "Tus canciones con más reproducciones (30 s o más)",
                        libraryDao.mostPlayed().take(25).mapNotNull { byId[it.songId] },
                    ),
                    SmartPlaylist(
                        "Añadidas recientemente",
                        "Lo último que llegó a tu biblioteca",
                        songs.sortedByDescending { it.dateAdded }.take(25),
                    ),
                    SmartPlaylist(
                        "Favoritas",
                        "Tus canciones con corazón",
                        songs.filter { it.id in favorites }.sortedBy { it.title.lowercase() },
                    ),
                    // Ideas open source (Auxio/Namida): lo que aún no
                    // has puesto y lo que llegó esta semana.
                    SmartPlaylist(
                        "Sin escuchar",
                        "Canciones que aún no has puesto nunca",
                        songs.filter { it.id !in playedIds }.take(25),
                    ),
                    SmartPlaylist(
                        "Esta semana",
                        "Añadidas en los últimos 7 días",
                        songs.filter { it.dateAdded >= weekAgo }
                            .sortedByDescending { it.dateAdded }.take(25),
                    ),
                ).filter { it.songs.isNotEmpty() }
                }
            } catch (e: Exception) {
                // Un fallo de la base de datos no deja las playlists
                // en blanco para siempre: se registra y se queda lo que había.
                ErrorLog.log(getApplication(), "Cargar playlists", e)
            }
        }
    }

    /** Vuelve a cargar al entrar a la pantalla: las inteligentes
     * ("Más escuchadas", "Sin escuchar", "Favoritas") se quedan viejas
     * si solo se cargan una vez por arranque. */
    fun refresh(hasAudioPermission: Boolean) {
        loaded = false
        load(hasAudioPermission)
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                playlistMutex.withLock {
                    // RONDA 166: mismo patrón que importM3u (ronda
                    // 53): si ya existe una con ese nombre se
                    // reutiliza, no se crea otra igual. Un doble
                    // toque en "Crear" ya no duplica la playlist.
                    dao.playlistByName(clean)?.id
                        ?: dao.insertPlaylist(PlaylistEntity(name = clean))
                }
                _playlists.value = dao.allPlaylists()
            } catch (e: Exception) {
                ErrorLog.log(getApplication(), "Crear playlist", e)
            }
        }
    }

    /**
     * RONDA 2 (2026-11-09): importar una lista M3U. El parser
     * (M3u.parse) existía pero NADIE lo llamaba: era código muerto.
     * Solo entran las pistas que existen en la biblioteca; el resto
     * se ignora y se dice cuántas entraron.
     */
    fun importM3u(name: String, songIds: List<Long>) {
        val clean = name.trim().ifEmpty { "Importada" }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = playlistMutex.withLock {
                    // Reimportar el mismo M3U actualiza la lista, no crea otra con
                    // el mismo nombre (ronda 53; mismo patrón que "Tu año").
                    dao.playlistByName(clean)?.id
                        ?: dao.insertPlaylist(PlaylistEntity(name = clean))
                }
                // RONDA 108: reimportar con 0 coincidencias también
                // actualiza: antes la lista vieja quedaba intacta aunque
                // el aviso decía que nada coincidió (y una lista nueva
                // quedaba vacía: incoherente). replaceSongs con lista
                // vacía la deja vacía, como dice el M3U.
                dao.replaceSongs(
                    id,
                    songIds.mapIndexed { index, songId ->
                        PlaylistSongEntity(playlistId = id, songId = songId, position = index)
                    },
                )
                _playlists.value = dao.allPlaylists()
            } catch (e: Exception) {
                ErrorLog.log(getApplication(), "Importar M3U", e)
            }
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.deletePlaylistWithSongs(id)
                _playlists.value = dao.allPlaylists()
                if (_selectedId.value == id) {
                    _selectedId.value = null
                    _selectedSongs.value = emptyList()
                }
            } catch (e: Exception) {
                ErrorLog.log(getApplication(), "Borrar playlist", e)
            }
        }
    }

    fun selectPlaylist(id: Long?) {
        _selectedId.value = id
        if (id == null) {
            _selectedSongs.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = dao.songsOf(id)
                val byId = _allSongs.value.associateBy { it.id }
                _selectedSongs.value = rows.mapNotNull { byId[it.songId] }
            } catch (e: Exception) {
                ErrorLog.log(getApplication(), "Cargar playlist", e)
            }
        }
    }

    fun setPlaylistSongs(id: Long, songIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // RONDA 174: el reemplazo también va bajo playlistMutex.
                // Antes solo crear/importar/añadir lo tomaban y un
                // setPlaylistSongs simultáneo (reordenar en Listas)
                // podía pisar un addSongToPlaylist en curso: el añadido
                // se perdía. Ningún llamador tiene ya el candado
                // (PlaylistsScreen:158, removeSongFromPlaylist), así que
                // no hay anidado.
                playlistMutex.withLock {
                    dao.replaceSongs(
                        id,
                        songIds.mapIndexed { index, songId ->
                            PlaylistSongEntity(playlistId = id, songId = songId, position = index)
                        },
                    )
                }
                val byId = _allSongs.value.associateBy { it.id }
                _selectedSongs.value = songIds.mapNotNull { byId[it] }
                _playlists.value = dao.allPlaylists()
            } catch (e: Exception) {
                ErrorLog.log(getApplication(), "Guardar playlist", e)
            }
        }
    }

    fun removeSongFromPlaylist(id: Long, songId: Long) {
        val current = _selectedSongs.value.map { it.id }.filter { it != songId }
        setPlaylistSongs(id, current)
    }

    /**
     * MENÚ ⋮ Stage 2 (2026-11-11): añade UNA canción al final de una
     * lista. Suspende para que el llamador ponga el Toast según el
     * resultado: true = añadida, false = ya estaba (no se duplica),
     * null = error. La escritura va bajo playlistMutex (ronda 166).
     */
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long): Boolean? {
        return try {
            playlistMutex.withLock {
                val current = dao.songsOf(playlistId).map { it.songId }
                if (songId in current) {
                    false
                } else {
                    dao.replaceSongs(
                        playlistId,
                        (current + songId).mapIndexed { index, id ->
                            PlaylistSongEntity(playlistId = playlistId, songId = id, position = index)
                        },
                    )
                    _playlists.value = dao.allPlaylists()
                    true
                }
            }
        } catch (e: Exception) {
            ErrorLog.log(getApplication(), "Añadir a la lista", e)
            null
        }
    }

    /**
     * MENÚ ⋮ Stage 2: crea la lista si no existe (reutiliza el nombre,
     * como createPlaylist) y añade la canción. El candado solo cubre
     * la creación: addSongToPlaylist toma el suyo aparte (un Mutex no
     * es reentrante y anidarlo se quedaría colgado).
     */
    suspend fun addSongToNewPlaylist(name: String, songId: Long): Boolean? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        return try {
            val id = playlistMutex.withLock {
                dao.playlistByName(clean)?.id ?: dao.insertPlaylist(PlaylistEntity(name = clean))
            }
            _playlists.value = dao.allPlaylists()
            addSongToPlaylist(id, songId)
        } catch (e: Exception) {
            ErrorLog.log(getApplication(), "Crear lista y añadir", e)
            null
        }
    }
}
