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
class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AuroraDatabase::class.java,
        "aurora-player.db",
    ).build()
    private val dao = db.playlistDao()
    private val repository = MusicRepository(application)

    private val _playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playlists: StateFlow<List<PlaylistEntity>> = _playlists

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = _selectedId

    private val _selectedSongs = MutableStateFlow<List<Song>>(emptyList())
    val selectedSongs: StateFlow<List<Song>> = _selectedSongs

    private var loaded = false

    fun load(hasAudioPermission: Boolean) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _allSongs.value = if (hasAudioPermission) repository.loadSongs() else emptyList()
                _playlists.value = dao.allPlaylists()
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
