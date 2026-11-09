package com.aurora.player.ui.playlists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.PlaylistEntity
import com.aurora.player.data.PlaylistSongEntity
import com.aurora.player.data.MusicRepository
import com.aurora.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aurora Player — ViewModel de playlists (Room, solo en el teléfono).
 * La base se crea aquí (Room.databaseBuilder); el DAO ya existía pero
 * nadie lo instanciaba. Todo corre en IO, nunca en el hilo de UI.
 */

/** Playlist inteligente: una regla sobre datos locales, no una lista fija. */
data class SmartPlaylist(
    val name: String,
    val description: String,
    val songs: List<Song>,
)

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AuroraDatabase::class.java,
        "aurora-player.db",
    ).build()
    private val dao = db.playlistDao()
    private val libraryDao = db.libraryDao()
    private val repository = MusicRepository(application)

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

    fun load(hasAudioPermission: Boolean) {
        // Refinamiento: si la primera carga fue sin permiso y el usuario
        // lo concede después, hay que volver a cargar (antes quedaba vacío).
        if (loaded && loadedPermission == hasAudioPermission) return
        loaded = true
        loadedPermission = hasAudioPermission
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _allSongs.value = if (hasAudioPermission) repository.loadSongs() else emptyList()
                _playlists.value = dao.allPlaylists()
                // Playlists inteligentes: solo con datos reales locales.
                val songs = _allSongs.value
                val byId = songs.associateBy { it.id }
                val favorites = libraryDao.favoriteIds().toSet()
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
                ).filter { it.songs.isNotEmpty() }
            }
        }
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertPlaylist(PlaylistEntity(name = clean))
            _playlists.value = dao.allPlaylists()
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearSongs(id)
            dao.deletePlaylist(id)
            _playlists.value = dao.allPlaylists()
            if (_selectedId.value == id) {
                _selectedId.value = null
                _selectedSongs.value = emptyList()
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
            val rows = dao.songsOf(id)
            val byId = _allSongs.value.associateBy { it.id }
            _selectedSongs.value = rows.mapNotNull { byId[it.songId] }
        }
    }

    fun setPlaylistSongs(id: Long, songIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearSongs(id)
            dao.setSongs(songIds.mapIndexed { index, songId ->
                PlaylistSongEntity(playlistId = id, songId = songId, position = index)
            })
            val byId = _allSongs.value.associateBy { it.id }
            _selectedSongs.value = songIds.mapNotNull { byId[it] }
            _playlists.value = dao.allPlaylists()
        }
    }

    fun removeSongFromPlaylist(id: Long, songId: Long) {
        val current = _selectedSongs.value.map { it.id }.filter { it != songId }
        setPlaylistSongs(id, current)
    }
}
