package com.aurora.player.ui.online

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aurora.player.online.ChartsResult
import com.aurora.player.online.InnerTubeClient
import com.aurora.player.online.QuickPick
import com.aurora.player.online.StreamCacheStore
import com.aurora.player.model.StreamQuality
import com.aurora.player.online.OnlineTrack
import com.aurora.player.online.ResolvedVideo
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
    onPlayVideo: (OnlineTrack, ResolvedVideo) -> Unit = { _, _ -> },
    spotifyConnected: Boolean = false,
    onImportOnlineTracks: (List<Pair<OnlineTrack, String>>) -> Unit = {},
    ytMusicCookies: String? = null,
    ytMusicToken: String? = null,
    onYtTokenRejected: (String) -> Unit = {},
    streamQuality: StreamQuality = StreamQuality.HIGH,
    // Fase 2 — Fuentes: cuántas canciones locales hay y qué hacer
    // al tocar el chip Local (volver a la pestaña Canciones).
    localCount: Int = 0,
    onShowLocal: () -> Unit = {},
    // Ronda 7 (Gemini): abrir Ajustes directo en Red.
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!onlineMode) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text("El modo online está apagado.")
            Text(
                "Activa el Modo online en Ajustes → Modo online para buscar y escuchar de YouTube. Apagado, la app no toca la red.",
                modifier = Modifier.padding(top = 8.dp),
            )
            // Ronda 7 (Gemini): el texto decía dónde encenderlo
            // pero no había ningún botón para ir.
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Activar el modo online") }
        }
        return
    }

    val client = remember(ytMusicCookies, ytMusicToken) { InnerTubeClient(ytMusicCookies, ytMusicToken, onYtTokenRejected) }
    val context = LocalContext.current
    val cacheStore = remember { StreamCacheStore(context) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    // Fase 2 — Para ti: los charts se cargan una sola vez por
    // entrada a la pestaña; si YouTube no responde, se dice claro.
    var charts by remember { mutableStateOf<ChartsResult?>(null) }
    var quickPicks by remember { mutableStateOf<List<QuickPick>>(emptyList()) }
    var chartsLoading by remember { mutableStateOf(false) }
    var chartsError by remember { mutableStateOf<String?>(null) }
    var chartsLoaded by remember { mutableStateOf(false) }
    // Fase 2 — Fuentes: la sección de listas de Spotify se puede
    // ocultar (solo tiene sentido conectada) y el chip de YouTube
    // pone el foco en la búsqueda con el teclado abierto.
    var spotifySectionVisible by remember { mutableStateOf(true) }
    val searchFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
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

    LaunchedEffect(onlineMode) {
        if (!onlineMode || chartsLoaded) return@LaunchedEffect
        chartsLoaded = true
        chartsLoading = true
        val loaded = client.getCharts()
        charts = loaded
        chartsLoading = false
        if (loaded.videoPlaylists.isEmpty() && loaded.artists.isEmpty()) {
            chartsError = "No se pudieron cargar los charts de YouTube Music."
        }
        quickPicks = client.getQuickPicks()
    }

    fun play(track: OnlineTrack) {
        if (resolvingId != null) return
        resolvingId = track.videoId
        error = null
        scope.launch {
            try {
                // Caché (Fase 2): si ya sonó antes, suena del disco
                // sin gastar datos; si no, suena la URL y el audio se
                // baja en segundo plano para la próxima vez.
                cacheStore.cachedFile(track.videoId, streamQuality)?.let {
                    onPlayTrack(track, it.toURI().toString())
                    return@launch
                }
                val stream = client.resolveStream(track.videoId, streamQuality)
                if (stream != null) {
                    onPlayTrack(track, stream.url)
                    scope.launch {
                        cacheStore.download(track.videoId, stream.itag, stream.url, streamQuality)
                    }
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

    // Fase 2 app — YouTube con vídeo: resuelve el progresivo con
    // audio y lo entrega ya resuelto a quien reproduce el vídeo.
    fun watchVideo(track: OnlineTrack) {
        if (resolvingId != null) return
        resolvingId = track.videoId
        error = null
        scope.launch {
            try {
                val video = client.resolveVideo(track.videoId)
                if (video != null) {
                    onPlayVideo(track, video)
                } else {
                    error = "YouTube no devolvió vídeo para \"${track.title}\". Prueba con otro resultado."
                }
            } catch (_: Exception) {
                error = "Sin conexión o YouTube no respondió."
            } finally {
                resolvingId = null
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SourcesRow(
            localCount = localCount,
            ytMusicConnected = ytMusicCookies != null,
            spotifyConnected = spotifyConnected,
            spotifySectionVisible = spotifySectionVisible,
            onShowLocal = onShowLocal,
            onFocusSearch = {
                searchFocus.requestFocus()
                keyboardController?.show()
            },
            onToggleSpotify = { spotifySectionVisible = !spotifySectionVisible },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar en YouTube Music") },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
                .focusRequester(searchFocus),
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
        if (spotifyConnected && spotifySectionVisible) {
            SpotifyPlaylistsSection(
                spotifyConnected = spotifyConnected,
                onImportTracks = onImportOnlineTracks,
                ytMusicCookies = ytMusicCookies,
                ytMusicToken = ytMusicToken,
                onYtTokenRejected = onYtTokenRejected,
                streamQuality = streamQuality,
            )
        }
        // Fase 2 — Para ti: charts de YouTube Music. Tocar una
        // lista o un artista busca su nombre en YouTube Music.
        ForYouSection(
            charts = charts,
            loading = chartsLoading,
            error = chartsError,
            onSearchQuery = { q ->
                query = q
                doSearch()
            },
            quickPicks = quickPicks,
            onPlayQuickPick = { pick ->
                play(
                    OnlineTrack(
                        videoId = pick.videoId,
                        title = pick.title,
                        artist = pick.artist,
                        album = null,
                        durationSec = null,
                        thumbnailUrl = pick.thumbnailUrl,
                    ),
                )
            },
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (resolvingId == track.videoId) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    IconButton(onClick = { watchVideo(track) }) {
                                        Icon(
                                            Icons.Filled.OndemandVideo,
                                            contentDescription = "Ver vídeo",
                                        )
                                    }
                                }
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
