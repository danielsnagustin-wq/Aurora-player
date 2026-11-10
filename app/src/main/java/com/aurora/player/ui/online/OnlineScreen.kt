package com.aurora.player.ui.online

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aurora.player.online.InnerTubeClient
import com.aurora.player.online.OnlineTrack
import kotlinx.coroutines.launch

/**
 * RONDA 7 (002-online-youtube): pestaña Online — buscar y escuchar de
 * YouTube Music. La búsqueda va a InnerTube (WEB_REMIX); al tocar un
 * resultado se resuelve su URL de audio directa (escalera de clientes)
 * y se entrega ya resuelta a quien reproduce. Si YouTube no devuelve
 * audio, se dice claro: nunca se finge que suena.
 * Sin modo online, la pestaña solo explica dónde se enciende.
 */
@Composable
fun OnlineScreen(
    onlineMode: Boolean,
    onPlayTrack: (OnlineTrack, String) -> Unit,
    spotifyConnected: Boolean = false,
    onImportOnlineTracks: (List<Pair<OnlineTrack, String>>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!onlineMode) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text("El modo online está apagado.")
            Text(
                "Activa el Modo online en Ajustes → Modo online para buscar y escuchar de YouTube. Apagado, la app no toca la red.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }

    val client = remember { InnerTubeClient() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OnlineTrack>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resolvingId by remember { mutableStateOf<String?>(null) }

    fun doSearch() {
        val q = query.trim()
        if (q.isBlank() || searching) return
        searching = true
        error = null
        results = null
        scope.launch {
            try {
                val found = client.search(q)
                results = found
                if (found.isEmpty()) error = "Sin resultados para \"$q\"."
            } catch (_: Exception) {
                error = "Sin conexión o YouTube no respondió. Revisa internet e inténtalo otra vez."
            } finally {
                searching = false
            }
        }
    }

    fun play(track: OnlineTrack) {
        if (resolvingId != null) return
        resolvingId = track.videoId
        error = null
        scope.launch {
            try {
                val url = client.resolveStreamUrl(track.videoId)
                if (url != null) {
                    onPlayTrack(track, url)
                } else {
                    error = "YouTube no devolvió audio para \"${track.title}\". Prueba con otro resultado."
                }
            } catch (_: Exception) {
                error = "Sin conexión o YouTube no respondió."
            } finally {
                resolvingId = null
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar en YouTube Music") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
        )
        OutlinedButton(
            onClick = { doSearch() },
            enabled = query.isNotBlank() && !searching,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        ) { Text("Buscar") }
        // Fase 2 — Spotify como BitChord: tus listas, importadas por
        // YouTube Music. Solo con la cookie conectada.
        SpotifyPlaylistsSection(
            spotifyConnected = spotifyConnected,
            onImportTracks = onImportOnlineTracks,
        )
        if (searching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Column
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
        val list = results
        if (list == null && error == null) {
            Text(
                "Busca una canción o un artista. Los resultados son de YouTube Music y suenan con el reproductor de siempre.",
                modifier = Modifier.padding(16.dp),
            )
        } else if (list != null) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.videoId }) { track ->
                    val subtitle = listOfNotNull(
                        track.artist.takeIf { it.isNotBlank() },
                        track.album,
                        track.durationSec?.let { formatOnlineDuration(it) },
                    ).joinToString(" • ")
                    ListItem(
                        headlineContent = { Text(track.title) },
                        supportingContent = { Text(subtitle) },
                        trailingContent = {
                            if (resolvingId == track.videoId) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        },
                        modifier = Modifier.clickable { play(track) },
                    )
                }
            }
        }
    }
}

/** Segundos → "3:24" o "1:02:03". Función pura, sin Android. */
fun formatOnlineDuration(totalSec: Long): String {
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
