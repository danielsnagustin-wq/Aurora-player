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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
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
import com.aurora.player.data.DiscoveryStore
import com.aurora.player.data.NewReleaseItem
import com.aurora.player.data.SoundCloudStore
import com.aurora.player.data.SpotifyStore
import com.aurora.player.online.ChartsResult
import com.aurora.player.online.InnerTubeClient
import com.aurora.player.online.QuickPick
import com.aurora.player.online.SpotifyClient
import com.aurora.player.online.SpotifyUnauthorizedException
import com.aurora.player.online.StreamCacheStore
import com.aurora.player.online.fetchSoundCloudStream
import com.aurora.player.online.fetchSoundCloudWebClientId
import com.aurora.player.online.filterSpotifyReleasesByFollowed
import com.aurora.player.online.soundCloudArtwork500
import com.aurora.player.model.StreamQuality
import com.aurora.player.util.effectiveStreamQuality
import com.aurora.player.util.isWifiConnected
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
    // Ronda 7b: sesión de cookies caducada (401 sin token).
    onYtCookiesRejected: () -> Unit = {},
    // Calidad por conexión (copia BitChord, slice 3): una para
    // Wi-Fi y otra para datos móviles; se resuelve al pulsar.
    wifiStreamQuality: StreamQuality = StreamQuality.HIGH,
    mobileStreamQuality: StreamQuality = StreamQuality.MEDIUM,
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
                // RONDA CAZA 2026-11-13 (P1): el texto iba en el color
                // del acento y los acentos oscuros no se leían sobre
                // negro. Texto claro siempre, como los chips (n=828).
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text("Activar el modo online") }
        }
        return
    }

    val client = remember(ytMusicCookies, ytMusicToken) { InnerTubeClient(ytMusicCookies, ytMusicToken, onYtTokenRejected, onYtCookiesRejected) }
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
    // Ronda 33: el botón Reintentar de Para ti vuelve a lanzar la
    // carga sin salir de la pestaña.
    var chartsRetry by remember { mutableStateOf(0) }
    // Fase 2 — Fuentes: la sección de listas de Spotify se puede
    // ocultar (solo tiene sentido conectada) y el chip de YouTube
    // pone el foco en la búsqueda con el teclado abierto.
    var spotifySectionVisible by remember { mutableStateOf(true) }
    // RONDA CAZA 2026-11-13 (P2): igual que Spotify: el chip de
    // SoundCloud muestra u oculta sus lanzamientos (antes era un
    // "Próximamente" muerto aunque el inicio de sesión y el
    // descubrimiento de SoundCloud ya existían).
    var soundCloudSectionVisible by remember { mutableStateOf(true) }
    val searchFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var results by remember { mutableStateOf<List<OnlineTrack>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resolvingId by remember { mutableStateOf<String?>(null) }
    // Descubrimiento — Nuevos lanzamientos: lo último guardado es lo
    // que pinta el panel; el refresco lo vuelve a pedir a las cuentas
    // con sesión y guarda lo nuevo. Sin ninguna sesión no se muestra.
    val discoveryStore = remember { DiscoveryStore(context) }
    val savedReleases by discoveryStore.releasesFlow.collectAsState(initial = emptyList())
    val releasesUpdatedAt by discoveryStore.updatedAtFlow.collectAsState(initial = null)
    val spotifyStore = remember { SpotifyStore(context) }
    val spCookie by spotifyStore.cookieFlow.collectAsState(initial = "")
    val spotify = remember { SpotifyClient() }
    val soundCloudStore = remember { SoundCloudStore(context) }
    val scToken by soundCloudStore.tokenFlow.collectAsState(initial = "")
    val scConnected by soundCloudStore.connectedFlow.collectAsState(initial = false)
    var releasesLoading by remember { mutableStateOf(false) }
    var releasesLoaded by remember { mutableStateOf(false) }
    val hasAnyLogin = !ytMusicCookies.isNullOrBlank() || spotifyConnected || scConnected

    fun doSearch() {
        val q = query.trim()
        if (q.isBlank() || searching) return
        searching = true
        error = null
        results = null
        scope.launch {
            try {
                val found = client.search(q)
                // RONDA 90: null es FALLO (sin red, YouTube no
                // respondió, JSON inválido); lista vacía es "sin
                // resultados". Antes todo era lista vacía y sin
                // internet la pantalla decía "Sin resultados".
                if (found == null) {
                    error = "Sin conexión o YouTube no respondió. Revisa internet e inténtalo otra vez."
                    results = emptyList()
                } else {
                    results = found
                    if (found.isEmpty()) error = "Sin resultados para \"$q\"."
                }
            } catch (_: Exception) {
                error = "Sin conexión o YouTube no respondió. Revisa internet e inténtalo otra vez."
            } finally {
                searching = false
            }
        }
    }

    LaunchedEffect(onlineMode, chartsRetry) {
        if (!onlineMode || chartsLoaded) return@LaunchedEffect
        chartsLoaded = true
        chartsLoading = true
        // Ronda 25: sin try/catch, una excepción de red dejaba el
        // spinner girando para siempre (chartsLoading nunca bajaba)
        // y chartsLoaded en true impedía reintentarlo. Ahora el error
        // se muestra y se puede reintentar al volver a entrar.
        try {
            val loaded = client.getCharts()
            charts = loaded
            if (loaded.videoPlaylists.isEmpty() && loaded.artists.isEmpty()) {
                chartsError = "No se pudieron cargar los charts de YouTube Music."
            }
        } catch (e: Exception) {
            chartsError = "Sin conexión o YouTube Music no respondió. Revisa internet e inténtalo otra vez."
            chartsLoaded = false
        } finally {
            chartsLoading = false
        }
        // Ronda 33: las recomendaciones rápidas son opcionales — si
        // fallan, los charts se muestran igual y no se enseña un
        // error de red por algo que no impide escuchar.
        try {
            quickPicks = client.getQuickPicks()
        } catch (_: Exception) {
            quickPicks = emptyList()
        }
    }

    fun play(track: OnlineTrack) {
        if (resolvingId != null) return
        resolvingId = track.videoId
        error = null
        // Calidad por conexión (slice 3): se resuelve al pulsar,
        // según la red activa en ese momento, no al abrir la lista.
        val quality = effectiveStreamQuality(
            isWifiConnected(context),
            wifiStreamQuality,
            mobileStreamQuality,
        )
        scope.launch {
            try {
                // Caché (Fase 2): si ya sonó antes, suena del disco
                // sin gastar datos; si no, suena la URL y el audio se
                // baja en segundo plano para la próxima vez.
                cacheStore.cachedFile(track.videoId, quality)?.let {
                    onPlayTrack(track, it.toURI().toString())
                    return@launch
                }
                val stream = client.resolveStream(track.videoId, quality)
                if (stream != null) {
                    onPlayTrack(track, stream.url)
                    scope.launch {
                        cacheStore.download(track.videoId, stream.itag, stream.url, quality)
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

    // Descubrimiento — refresca los lanzamientos de las cuentas con
    // sesión (YouTube, Spotify, SoundCloud) y guarda lo nuevo. Cada
    // fuente va en su orden (lo más nuevo primero) y si una falla, las
    // demás llegan igual. Sin red, el panel enseña lo último guardado.
    fun refreshReleases() {
        if (releasesLoading) return
        releasesLoading = true
        scope.launch {
            try {
                val all = mutableListOf<NewReleaseItem>()
                if (!ytMusicCookies.isNullOrBlank()) {
                    // RONDA 2 (2026-11-13, P1, n=916, 1/2): getNewReleases
                    // devuelve null si YouTube no respondió (antes
                    // lista vacía, indistinguible de "sin estante"):
                    // un null NO tumba los demás lanzamientos.
                    client.getNewReleases()?.let { yt ->
                        all += yt.map {
                            NewReleaseItem(
                                source = "YouTube",
                                title = it.title,
                                artist = it.artist,
                                dateText = it.year?.toString(),
                                kind = it.kind,
                                imageUrl = it.thumbnailUrl,
                                browseId = it.browseId,
                                trackId = null,
                            )
                        }
                    }
                }
                if (spotifyConnected && spCookie.isNotBlank()) {
                    // RONDA CAZA 2026-11-13 (P1): un 401 a mitad de
                    // sesión reintenta una vez con otro token; si
                    // sigue 401, solo caen los lanzamientos de
                    // Spotify (YouTube y SoundCloud cargan igual y el
                    // panel no se queda vacío sin aviso).
                    try {
                        val mine = spotify.withFreshToken(spCookie) { token ->
                            val followed = spotify.fetchFollowedArtists(token)
                                ?: return@withFreshToken null
                            val fresh = spotify.fetchNewReleases(token)
                                ?: return@withFreshToken null
                            filterSpotifyReleasesByFollowed(
                                fresh,
                                followed.map { it.id }.toSet(),
                            )
                        }
                        if (mine != null) {
                            all += mine.map {
                                NewReleaseItem(
                                    source = "Spotify",
                                    title = it.name,
                                    artist = it.artistName,
                                    dateText = it.releaseDate,
                                    kind = null,
                                    imageUrl = it.imageUrl,
                                    browseId = null,
                                    trackId = null,
                                )
                            }
                        }
                    } catch (_: SpotifyUnauthorizedException) {
                        // Sesión caducada: la pantalla de Cuenta ya
                        // avisa; aquí solo se pierden los de Spotify.
                    }
                }
                if (scConnected && scToken.isNotBlank()) {
                    val clientId = fetchSoundCloudWebClientId()
                    if (clientId != null) {
                        val stream = fetchSoundCloudStream(clientId, scToken) ?: emptyList()
                        all += stream.take(10).map {
                            NewReleaseItem(
                                source = "SoundCloud",
                                title = it.title,
                                artist = it.artist,
                                dateText = null,
                                kind = null,
                                imageUrl = soundCloudArtwork500(it.artworkUrl),
                                browseId = null,
                                trackId = it.id,
                            )
                        }
                    }
                }
                if (all.isNotEmpty()) {
                    discoveryStore.save(all)
                }
            } catch (_: Exception) {
                // Sin red: el panel enseña lo último guardado.
            } finally {
                releasesLoading = false
            }
        }
    }

    // Tocar un lanzamiento: el álbum de YouTube abre sus canciones
    // como resultados; los demás buscan "artista - título" en YouTube
    // Music (el camino real que ya existe, como Para ti).
    fun openRelease(item: NewReleaseItem) {
        val browseId = item.browseId
        if (browseId != null) {
            scope.launch {
                try {
                    val tracks = client.getAlbumTracks(browseId)
                    if (tracks != null) {
                        results = tracks
                        error = if (tracks.isEmpty()) {
                            "YouTube no devolvió canciones para \"${item.title}\"."
                        } else {
                            null
                        }
                    } else {
                        error = "Sin conexión o YouTube no respondió."
                    }
                } catch (_: Exception) {
                    error = "Sin conexión o YouTube no respondió."
                }
            }
        } else {
            query = "${item.artist} - ${item.title}".trim()
            doSearch()
        }
    }

    LaunchedEffect(onlineMode) {
        if (!onlineMode || releasesLoaded || !hasAnyLogin) return@LaunchedEffect
        releasesLoaded = true
        refreshReleases()
    }

    // RONDA ARREGLOS 2026-11-13 (prueba en el teléfono): la raíz era
    // un Column sin scroll con un LazyColumn anidado al final: los
    // resultados, el error y el spinner quedaban fuera de la pantalla
    // (altura ~0) y la búsqueda parecía no hacer nada. Ahora TODO es
    // una sola LazyColumn: al buscar, los resultados se ven arriba y
    // el contenido de exploración se esconde; sin búsqueda se ve lo
    // de siempre (lanzamientos, listas de Spotify, Para ti).
    val list = results
    val showingResults = searching || list != null || error != null
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            SourcesRow(
                localCount = localCount,
                ytMusicConnected = !ytMusicCookies.isNullOrBlank(),
                spotifyConnected = spotifyConnected,
                spotifySectionVisible = spotifySectionVisible,
                soundCloudConnected = scConnected,
                soundCloudSectionVisible = soundCloudSectionVisible,
                onShowLocal = onShowLocal,
                onFocusSearch = {
                    searchFocus.requestFocus()
                    keyboardController?.show()
                },
                onToggleSpotify = { spotifySectionVisible = !spotifySectionVisible },
                onToggleSoundCloud = { soundCloudSectionVisible = !soundCloudSectionVisible },
            )
        }
        item {
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
        }
        item {
            Row(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                OutlinedButton(
                    onClick = { doSearch() },
                    enabled = query.isNotBlank() && !searching,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text("Buscar") }
                if (list != null || error != null) {
                    OutlinedButton(
                        onClick = { results = null; error = null },
                        modifier = Modifier.padding(start = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("Limpiar") }
                }
            }
        }
        // El error va justo debajo del botón: antes quedaba debajo de
        // "Para ti", fuera de la pantalla.
        error?.let { errorText ->
            item {
                Text(
                    errorText,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (searching) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        } else if (list != null) {
            // RONDA CAZA 2026-11-13 (P1): la clave era solo el videoId
            // y YouTube repite vídeos entre secciones: dos iguales
            // tumbaban la búsqueda (key already used). El índice la
            // hace única siempre.
            itemsIndexed(list, key = { index, it -> "$index|${it.videoId}" }) { _, track ->
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
        } else if (!showingResults) {
            item {
                Text(
                    "Busca una canción o un artista. Los resultados son de YouTube Music y suenan con el reproductor de siempre.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (!showingResults) {
            // Descubrimiento — Nuevos lanzamientos: solo con el modo
            // online y al menos una sesión (YouTube, Spotify o SoundCloud).
            if (onlineMode && hasAnyLogin) {
                item {
                    NewReleasesSection(
                        items = if (soundCloudSectionVisible) savedReleases
                        else savedReleases.filter { it.source != "SoundCloud" },
                        updatedAtMs = releasesUpdatedAt,
                        loading = releasesLoading,
                        onRefresh = { refreshReleases() },
                        onOpenRelease = { openRelease(it) },
                    )
                }
            }
            // Fase 2 — Spotify como BitChord: tus listas, importadas por
            // YouTube Music. Solo con la cookie conectada.
            if (spotifyConnected && spotifySectionVisible) {
                item {
                    SpotifyPlaylistsSection(
                        spotifyConnected = spotifyConnected,
                        onImportTracks = onImportOnlineTracks,
                        ytMusicCookies = ytMusicCookies,
                        ytMusicToken = ytMusicToken,
                        onYtTokenRejected = onYtTokenRejected,
                        wifiStreamQuality = wifiStreamQuality,
                        mobileStreamQuality = mobileStreamQuality,
                    )
                }
            }
            // Fase 2 — Para ti: charts de YouTube Music. Tocar una
            // lista o un artista busca su nombre en YouTube Music.
            item {
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
                    onRetry = {
                        chartsError = null
                        chartsLoaded = false
                        chartsRetry++
                    },
                )
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
