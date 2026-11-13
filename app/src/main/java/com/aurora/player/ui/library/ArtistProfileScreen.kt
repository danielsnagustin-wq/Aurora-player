package com.aurora.player.ui.library

// Perfil de artista (rediseño, diseño ganador Codex).
// Design Read: perfil de artista para quien escucha a un artista y quiere
// sus canciones y álbumes, en estilo AMOLED propio sobrio, dial ENERGY 1 / RHYTHM 1 / MOTION 1.
// Motivo de cada decisión (R-31):
// - Fondo negro y texto plata: identidad Aurora, no decoración.
// - El nombre del artista es el punto focal; el resto cede.
// - "Reproducir todo" arriba: nombra la acción real (R-15) y es la principal.
// - Dos secciones separadas ("En tu biblioteca" / "En YouTube Music"): el usuario
//   pidió distinguir lo que tiene de todo lo del artista (offline tuyo, online todo).
// - Solo cifras reales (recuentos de la biblioteca y de la búsqueda); sin dato, sin número (R-17).
// - Sin portadas inventadas: las filas locales usan AlbumCover real; los resultados
//   online llevan icono de nota (R-38). Las filas de álbum son información, no controles (R-26).

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.model.Album
import com.aurora.player.model.Song
import com.aurora.player.model.StreamQuality
import com.aurora.player.online.InnerTubeClient
import com.aurora.player.online.OnlineTrack
import com.aurora.player.util.TimeFormat
import com.aurora.player.util.effectiveStreamQuality
import com.aurora.player.util.isWifiConnected
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ArtistProfileScreen(
    artistName: String,
    songs: List<Song>,
    albums: List<Album>,
    onlineMode: Boolean,
    ytMusicCookies: String = "",
    ytMusicToken: String = "",
    wifiStreamQuality: StreamQuality = StreamQuality.HIGH,
    mobileStreamQuality: StreamQuality = StreamQuality.MEDIUM,
    onYtTokenRejected: (String) -> Unit = {},
    onYtCookiesRejected: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onPlayOnlineTrack: (OnlineTrack, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(ytMusicCookies, ytMusicToken) {
        InnerTubeClient(ytMusicCookies, ytMusicToken, onYtTokenRejected, onYtCookiesRejected)
    }
    var onlineResults by remember { mutableStateOf<List<OnlineTrack>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var resolvingId by remember { mutableStateOf<String?>(null) }

    fun searchOnline() {
        if (!onlineMode || searching) return
        searching = true
        searchError = null
        onlineResults = null
        scope.launch {
            try {
                val found = client.search(artistName)
                if (found == null) {
                    searchError = "Sin conexión o YouTube no respondió. Revisa internet e inténtalo otra vez."
                    onlineResults = emptyList()
                } else {
                    onlineResults = found
                    if (found.isEmpty()) searchError = "Sin resultados para \"$artistName\"."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                searchError = "Sin conexión o YouTube no respondió."
                onlineResults = emptyList()
            } finally {
                searching = false
            }
        }
    }

    fun playOnline(track: OnlineTrack) {
        if (resolvingId != null) return
        resolvingId = track.videoId
        searchError = null
        val quality = effectiveStreamQuality(
            isWifiConnected(context),
            wifiStreamQuality,
            mobileStreamQuality,
        )
        scope.launch {
            try {
                val stream = client.resolveStream(track.videoId, quality)
                if (stream != null) {
                    onPlayOnlineTrack(track, stream.url)
                } else {
                    searchError = "YouTube no devolvió audio para \"${track.title}\". Prueba con otro resultado."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                searchError = "Sin conexión o YouTube no respondió."
            } finally {
                resolvingId = null
            }
        }
    }

    LaunchedEffect(artistName, onlineMode) {
        if (onlineMode) searchOnline()
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Volver",
                    )
                }
                Text(
                    artistName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "${songs.size} canciones · ${albums.size} álbumes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            Button(
                onClick = onPlayAll,
                enabled = songs.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Reproducir todo", modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(
                "En tu biblioteca",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (songs.isEmpty() && albums.isEmpty()) {
            item {
                Text(
                    "No tienes canciones de este artista en tu biblioteca.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(songs, key = { it.id }) { song ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable { onSongClick(song, songs) }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumCover(song, size = 52.dp)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            song.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "${song.album} · ${TimeFormat.formatDuration(song.durationMs)}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(albums, key = { it.id }) { album ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(52.dp).padding(14.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            album.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            if (album.year != null) {
                                "${album.songCount} canciones · ${album.year}"
                            } else {
                                "${album.songCount} canciones"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (onlineMode) {
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "En YouTube Music",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (searching) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            "Buscando en YouTube Music…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            if (searchError != null) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            searchError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { searchOnline() },
                            modifier = Modifier.height(48.dp).padding(top = 8.dp),
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            val results = onlineResults
            if (results != null && results.isNotEmpty()) {
                items(results, key = { it.videoId }) { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { playOnline(track) }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Reproducir ${track.title}"
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(52.dp).padding(14.dp),
                        )
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                track.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                track.artist +
                                    if (track.durationSec != null) {
                                        " · ${TimeFormat.formatDuration(track.durationSec * 1_000L)}"
                                    } else {
                                        ""
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
