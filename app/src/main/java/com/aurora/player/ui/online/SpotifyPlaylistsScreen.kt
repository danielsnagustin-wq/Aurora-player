package com.aurora.player.ui.online

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.data.SpotifyStore
import com.aurora.player.online.InnerTubeClient
import com.aurora.player.online.OnlineTrack
import com.aurora.player.online.SpotifyClient
import com.aurora.player.online.SpotifyPlaylist
import com.aurora.player.online.SpotifyTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Spotify como BitChord — tus listas en la pestaña Online
 * (Fase 2, usuario 2026-11-09). Con la cookie sp_dc conectada
 * (Ajustes → Red → Spotify), carga TUS listas de Spotify y las
 * importa buscando cada canción en YouTube Music, como BitChord:
 * la lista suena por YouTube, nunca por Spotify.
 *
 * La importación es canción por canción y se ve el progreso
 * ("Buscando 12/50…"); las que no tienen coincidencia en YouTube
 * se cuentan al final, no se inventan. Las pistas online no se
 * guardan como cola con nombre (el esquema solo guarda canciones
 * locales): van a la cola de reproducción.
 */
@Composable
fun SpotifyPlaylistsSection(
    spotifyConnected: Boolean,
    onImportTracks: (List<Pair<OnlineTrack, String>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!spotifyConnected) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spotifyStore = remember { SpotifyStore(context) }
    val cookie by spotifyStore.cookieFlow.collectAsState(initial = "")
    val spotify = remember { SpotifyClient() }
    val tube = remember { InnerTubeClient() }

    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>?>(null) }
    var loadingPlaylists by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var tracks by remember { mutableStateOf<List<SpotifyTrack>?>(null) }
    var loadingTracks by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf("") }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun loadPlaylists() {
        if (loadingPlaylists) return
        loadingPlaylists = true
        message = null
        scope.launch {
            try {
                val token = spotify.fetchAccessToken(cookie)
                if (token == null) {
                    message = "La cookie de Spotify caducó o no sirve. Vuelve a conectarla en Ajustes → Red → Spotify."
                } else {
                    val found = spotify.fetchPlaylists(token)
                    playlists = found
                    if (found.isEmpty()) message = "No tienes listas en Spotify."
                }
            } catch (_: Exception) {
                message = "Sin conexión o Spotify no respondió."
            } finally {
                loadingPlaylists = false
            }
        }
    }

    fun loadTracks(playlist: SpotifyPlaylist) {
        if (loadingTracks) return
        selected = playlist
        tracks = null
        loadingTracks = true
        message = null
        scope.launch {
            try {
                val token = spotify.fetchAccessToken(cookie)
                if (token == null) {
                    message = "La cookie de Spotify caducó o no sirve. Vuelve a conectarla en Ajustes → Red → Spotify."
                } else {
                    val found = spotify.fetchPlaylistTracks(token, playlist.id)
                    tracks = found
                    if (found.isEmpty()) message = "Esa lista está vacía."
                }
            } catch (_: Exception) {
                message = "Sin conexión o Spotify no respondió."
            } finally {
                loadingTracks = false
            }
        }
    }

    fun importTracks() {
        val list = tracks ?: return
        if (importing) return
        importing = true
        importProgress = "Buscando 0/${list.size}…"
        message = null
        importJob = scope.launch {
            try {
                val matched = mutableListOf<Pair<OnlineTrack, String>>()
                var missing = 0
                for ((index, track) in list.withIndex()) {
                    importProgress = "Buscando ${index + 1}/${list.size}…"
                    val found = try {
                        tube.search("${track.artist} ${track.name}")
                    } catch (_: Exception) {
                        emptyList()
                    }.firstOrNull()
                    val url = found?.let {
                        try {
                            tube.resolveStreamUrl(it.videoId)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (found != null && url != null) {
                        matched.add(found to url)
                    } else {
                        missing++
                    }
                }
                if (matched.isEmpty()) {
                    message = "Ninguna canción tuvo coincidencia en YouTube Music."
                } else {
                    onImportTracks(matched)
                    message = "Importadas ${matched.size} de ${list.size}" +
                        if (missing > 0) " ($missing sin coincidencia en YouTube)." else "."
                }
            } finally {
                importing = false
                importProgress = ""
                importJob = null
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Tus listas de Spotify", modifier = Modifier.padding(top = 8.dp))
        if (playlists == null) {
            OutlinedButton(
                onClick = { loadPlaylists() },
                enabled = !loadingPlaylists,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(if (loadingPlaylists) "Cargando…" else "Ver mis listas") }
        } else if (selected == null) {
            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(playlists.orEmpty(), key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.trackCount} canciones") },
                        modifier = Modifier.clickable { loadTracks(playlist) },
                    )
                }
            }
        } else {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { selected = null; tracks = null }) {
                    Text("← Listas")
                }
                OutlinedButton(
                    onClick = { importTracks() },
                    enabled = tracks != null && !loadingTracks && !importing,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Importar y reproducir") }
                if (importing) {
                    OutlinedButton(
                        onClick = { importJob?.cancel() },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Cancelar") }
                }
            }
            Text(selected?.name.orEmpty(), modifier = Modifier.padding(top = 8.dp))
            if (loadingTracks) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            } else if (importing) {
                Text(importProgress, modifier = Modifier.padding(top = 8.dp))
            } else {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(tracks.orEmpty()) { track ->
                        ListItem(
                            headlineContent = { Text(track.name) },
                            supportingContent = { Text(track.artist) },
                        )
                    }
                }
            }
        }
        message?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        }
    }
}
