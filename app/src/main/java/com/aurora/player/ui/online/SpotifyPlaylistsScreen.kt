package com.aurora.player.ui.online

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.data.DownloadRecord
import com.aurora.player.data.DownloadStatus
import com.aurora.player.data.DownloadStore
import com.aurora.player.data.SpotifyStore
import com.aurora.player.util.effectiveStreamQuality
import com.aurora.player.util.isWifiConnected
import com.aurora.player.util.rememberRemoteBitmap
import com.aurora.player.online.InnerTubeClient
import com.aurora.player.model.StreamQuality
import com.aurora.player.online.OnlineTrack
import com.aurora.player.online.SpotifyClient
import com.aurora.player.online.SpotifyUnauthorizedException
import com.aurora.player.online.StreamCacheStore
import com.aurora.player.online.parseSpotifyPlaylistId
import com.aurora.player.online.SpotifyPlaylist
import com.aurora.player.online.SpotifyTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

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
/**
 * Slice 4 paso 7: marca de descarga en listas importadas. El
 * registro de descargas (DownloadStore) guarda el título y el
 * artista de YouTube; la lista de Spotify trae los suyos. La
 * comparación normaliza ambos lados (minúsculas, sin acentos,
 * espacios colapsados). RONDA 2 (2026-11-13, P2): la regla era
 * demasiado laxa — un título que CONTENGA al otro ("Love" en
 * "Lovely") marcaba, y un artista en blanco en cualquier lado
 * contaba como coincidencia. Ahora el título es exacto o con
 * sufijo de YouTube (" (Official Video)", " - Remastered") y el
 * artista tiene que venir en los dos lados y coincidir exacto:
 * sin artista no hay marca. Solo cuentan las descargas READY
 * (lista permanente).
 */
internal fun normalizeDownloadTitle(value: String): String {
    val noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return noAccents.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
}

internal fun isTrackDownloaded(
    trackName: String,
    trackArtist: String,
    records: List<DownloadRecord>,
): Boolean {
    val name = normalizeDownloadTitle(trackName)
    val artist = normalizeDownloadTitle(trackArtist)
    if (name.isEmpty() || artist.isEmpty()) return false
    return records.any { record ->
        if (record.status != DownloadStatus.READY) return@any false
        val recordName = normalizeDownloadTitle(record.title)
        val recordArtist = normalizeDownloadTitle(record.artist)
        if (recordArtist.isEmpty()) return@any false
        val titleMatches = recordName == name ||
            recordName.startsWith("$name (") ||
            recordName.startsWith("$name -")
        titleMatches && recordArtist == artist
    }
}

@Composable
fun SpotifyPlaylistsSection(
    spotifyConnected: Boolean,
    onImportTracks: (List<Pair<OnlineTrack, String>>) -> Unit,
    ytMusicCookies: String? = null,
    ytMusicToken: String? = null,
    onYtTokenRejected: (String) -> Unit = {},
    // Calidad por conexión (copia BitChord, slice 3): una para
    // Wi-Fi y otra para datos móviles; se resuelve al importar.
    wifiStreamQuality: StreamQuality = StreamQuality.HIGH,
    mobileStreamQuality: StreamQuality = StreamQuality.MEDIUM,
    modifier: Modifier = Modifier,
) {
    if (!spotifyConnected) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spotifyStore = remember { SpotifyStore(context) }
    val cookie by spotifyStore.cookieFlow.collectAsState(initial = "")
    val spotify = remember { SpotifyClient() }
    val cacheStore = remember { StreamCacheStore(context) }
    // Slice 4 paso 7: registro de descargas permanentes. La marca
    // de "descargada" en la lista y el salto de la re-descarga se
    // derivan de aquí: nada se guarda en la canción ni en la lista.
    val downloadStore = remember { DownloadStore(context) }
    val downloads by downloadStore.recordsFlow.collectAsState(initial = emptyList())
    val downloadedByVideoId = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.READY && it.uri != null }
            .associateBy { it.videoId }
    }
    val tube = remember(ytMusicCookies, ytMusicToken) { InnerTubeClient(ytMusicCookies, ytMusicToken, onYtTokenRejected) }

    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>?>(null) }
    var loadingPlaylists by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var tracks by remember { mutableStateOf<List<SpotifyTrack>?>(null) }
    var loadingTracks by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf("") }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var linkText by remember { mutableStateOf("") }

    fun loadPlaylists() {
        if (loadingPlaylists) return
        loadingPlaylists = true
        message = null
        scope.launch {
            try {
                // RONDA CAZA 2026-11-13 (P1): withFreshToken pide otro
                // token una vez si api.spotify.com devuelve 401 a mitad
                // de sesión; si sigue 401, la sesión caducó (no es
                // "sin conexión"). Null es solo fallo de red.
                val found = spotify.withFreshToken(cookie) { token ->
                    spotify.fetchPlaylists(token)
                }
                if (found == null) {
                    message = "Sin conexión o Spotify no respondió."
                } else {
                    playlists = found
                    if (found.isEmpty()) message = "No tienes listas en Spotify."
                }
            } catch (_: SpotifyUnauthorizedException) {
                message = "Tu sesión de Spotify caducó. Vuelve a conectarla en Ajustes → Red → Spotify."
            } catch (e: CancellationException) {
                // RONDA 2 (2026-11-13, P2): cancelar la carga no es
                // "sin conexión": se relanza.
                throw e
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
                // RONDA CAZA 2026-11-13 (P1): igual que las listas,
                // reintento único con otro token ante 401; si sigue
                // 401, la sesión caducó.
                val found = spotify.withFreshToken(cookie) { token ->
                    spotify.fetchPlaylistTracks(token, playlist.id)
                }
                if (found == null) {
                    message = "Sin conexión o Spotify no respondió."
                } else {
                    tracks = found
                    if (found.isEmpty()) message = "Esa lista está vacía."
                }
            } catch (_: SpotifyUnauthorizedException) {
                message = "Tu sesión de Spotify caducó. Vuelve a conectarla en Ajustes → Red → Spotify."
            } catch (e: CancellationException) {
                // RONDA 2 (2026-11-13, P2): cancelar la carga no es
                // "sin conexión": se relanza.
                throw e
            } catch (_: Exception) {
                message = "Sin conexión o Spotify no respondió."
            } finally {
                loadingTracks = false
            }
        }
    }

    // Importar por enlace publico (Fase 2, 2026-11-10): la
    // lista que no es tuya no sale en tus listas; con su
    // enlace se trae igual y se importa por YouTube Music.
    fun importByLink() {
        val id = parseSpotifyPlaylistId(linkText)
        if (id == null) {
            message = "Ese enlace no es una lista de Spotify."
            return
        }
        linkText = ""
        loadTracks(SpotifyPlaylist(id, "Lista por enlace", 0, null))
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
                // Ronda 34: una búsqueda que falla por la red no es
                // una canción sin coincidencia; se cuenta aparte.
                var searchErrors = 0
                for ((index, track) in list.withIndex()) {
                    importProgress = "Buscando ${index + 1}/${list.size}…"
                    // RONDA 90: search() devuelve null si FALLÓ; una
                    // lista vacía es "sin coincidencia". Antes el
                    // fallo también era lista vacía y searchErrors
                    // nunca se contaba (ronda 34, sin efecto).
                    val searched = try {
                        tube.search("${track.artist} ${track.name}")
                    } catch (e: CancellationException) {
                        // RONDA 2 (2026-11-13, P2): cancelar la
                        // importación no es un fallo de búsqueda: se
                        // relanza en vez de seguir con la siguiente.
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (searched == null) searchErrors++
                    val found = searched?.firstOrNull()
                    val url = found?.let {
                        // Slice 4 paso 7: ya descargada permanente:
                        // suena el archivo local; no se resuelve el
                        // stream ni se baja a la caché otra vez. Al
                        // reimportar la lista, las descargadas no se
                        // vuelven a bajar.
                        downloadedByVideoId[it.videoId]?.uri ?: run {
                            // Calidad por conexión (slice 3): se
                            // resuelve por pista, según la red activa
                            // al importarla.
                            val quality = effectiveStreamQuality(
                                isWifiConnected(context),
                                wifiStreamQuality,
                                mobileStreamQuality,
                            )
                            cacheStore.cachedFile(it.videoId, quality)?.toURI()?.toString()
                                ?: try {
                                    val stream = tube.resolveStream(it.videoId, quality)
                                    stream?.also { resolved ->
                                        scope.launch {
                                            cacheStore.download(it.videoId, resolved.itag, resolved.url, quality)
                                        }
                                    }?.url
                                } catch (e: CancellationException) {
                                    // RONDA 2 (2026-11-13, P2): igual
                                    // que la búsqueda: la cancelación
                                    // se relanza, no se traga.
                                    throw e
                                } catch (_: Exception) {
                                    null
                                }
                        }
                    }
                    if (found != null && url != null) {
                        matched.add(found to url)
                    } else {
                        missing++
                    }
                }
                if (matched.isEmpty()) {
                    message = if (searchErrors == list.size) {
                        "Sin conexión o YouTube no respondió al buscar las canciones."
                    } else {
                        "Ninguna canción tuvo coincidencia en YouTube Music."
                    }
                } else {
                    onImportTracks(matched)
                    message = "Importadas ${matched.size} de ${list.size}" +
                        if (missing > 0) " ($missing sin coincidencia en YouTube" +
                            if (searchErrors > 0) ", $searchErrors fallaron por la red)." else ")." else "."
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
                    // Portada de la lista (2026-11-10, como BitChord):
                    // la imagen viene en el JSON de Spotify.
                    val cover = rememberRemoteBitmap(playlist.imageUrl)
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.trackCount} canciones") },
                        leadingContent = {
                            if (cover != null) {
                                Image(
                                    bitmap = cover.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                                }
                            }
                        },
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
                    // Ronda 5 (fluidez): sin clave estable, al cargar
                    // la lista Compose recreaba las filas y se notaba
                    // el tirón. SpotifyTrack no tiene id: índice +
                    // nombre + artista (la lista se carga una vez).
                    itemsIndexed(
                        tracks.orEmpty(),
                        key = { index, track -> "$index|${track.name}|${track.artist}" },
                    ) { _, track ->
                        ListItem(
                            headlineContent = { Text(track.name) },
                            supportingContent = { Text(track.artist) },
                            // Slice 4 paso 7: marca de descarga en la
                            // lista (derivada del registro; la canción
                            // no guarda nada).
                            trailingContent = {
                                if (isTrackDownloaded(track.name, track.artist, downloads)) {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = "Descargada",
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        if (selected == null) {
            Text(
                "O pega el enlace de una lista publica",
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = { Text("Enlace de Spotify") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { importByLink() },
                    enabled = linkText.isNotBlank() && !loadingTracks,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Importar") }
            }
        }
        message?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        }
    }
}
