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
// - R8a (2026-11-12): secciones tipo Spotify con datos reales de la biblioteca:
//   "Más escuchadas" (reproducciones reales), "Lo más reciente" (fecha de
//   agregado real), "Álbumes" y "Sencillos y EP" (clasificados por nº real de
//   canciones: 1 = sencillo, 2-7 = EP, 8 o más = álbum). Sin datos, no sale (R-17).
// - Solo cifras reales (recuentos de la biblioteca y de la búsqueda); sin dato, sin número (R-17).
// - Sin portadas inventadas: las filas locales usan AlbumCover real; los resultados
//   online llevan icono de nota (R-38). Las filas de álbum son información, no controles (R-26).
// - R8c (2026-11-12): layout como la captura de BitChord (estructura, no
//   colores: nada de rojo, identidad AMOLED nuestra). Banner con la mejor
//   portada real del artista (o fondo #17171B sin imagen), acciones
//   Reproducir/Aleatorio reales, tarjeta de sencillo reciente, Canciones
//   principales (online + local con origen distinto), Álbumes en cuadrícula
//   de 2 columnas, Singles y EPs y Vídeos en horizontal, resto local y
//   búsqueda online al final ("Más en YouTube Music"). Sin botón de
//   favorito: no existe store de artistas favoritos y un control sin
//   efecto real es un defecto (R-26).

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.aurora.player.online.ArtistPage
import com.aurora.player.online.ArtistRelease
import com.aurora.player.online.ArtistVideo
import com.aurora.player.online.InnerTubeClient
import com.aurora.player.online.OnlineTrack
import com.aurora.player.util.TimeFormat
import com.aurora.player.util.effectiveStreamQuality
import com.aurora.player.util.isWifiConnected
import com.aurora.player.util.rememberRemoteBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.aurora.player.online.searchItunes
import java.io.IOException

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
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var resolvingId by remember { mutableStateOf<String?>(null) }
    // R8b: página del artista en YouTube Music (novedades, álbumes,
    // sencillos y vídeos reales). Carga independiente de la búsqueda.
    var artistPage by remember { mutableStateOf<ArtistPage?>(null) }
    var itunesCoverUrl by remember { mutableStateOf<String?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var pageJob by remember { mutableStateOf<Job?>(null) }
    // Canciones de un álbum online abierto con un toque.
    var albumTracks by remember { mutableStateOf<Pair<String, List<OnlineTrack>>?>(null) }
    var albumTracksError by remember { mutableStateOf<String?>(null) }
    var albumTracksJob by remember { mutableStateOf<Job?>(null) }
    // Listas completas tras "Ver todo" (null = solo lo de la página).
    var expandedSingles by remember { mutableStateOf<List<ArtistRelease>?>(null) }
    var expandedAlbums by remember { mutableStateOf<List<ArtistRelease>?>(null) }
    var moreError by remember { mutableStateOf<String?>(null) }
    // La carga se deriva del estado, no de un flag: un flag puesto en
    // `finally` lo podía apagar la búsqueda anterior cancelada mientras
    // la nueva seguía en vuelo (carrera A3-1, auditoría 2026-11-12).
    val searchingOnline = onlineMode && onlineResults == null && searchError == null
    val loadingPage = onlineMode && artistPage == null && pageError == null

    // R8a: secciones tipo Spotify con datos reales de la biblioteca.
    // "Más escuchadas" por reproducciones reales (30 s por escucha);
    // "Lo más reciente" por fecha de agregado real; álbumes, sencillos
    // y EP clasificados por nº real de canciones (1 = sencillo,
    // 2-7 = EP, 8 o más = álbum). Sin datos, la sección no sale (R-17).
    val topSongs = remember(songs) {
        songs.filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }.take(5)
    }
    val recentSongs = remember(songs) {
        songs.filter { it.dateAdded > 0 }
            .sortedByDescending { it.dateAdded }.take(5)
    }
    val albumReleases = remember(albums) { albums.filter { it.songCount >= 8 } }
    val singleEpReleases = remember(albums) { albums.filter { it.songCount < 8 } }

    // R8c: portada del banner (la mejor thumbnail real: primer álbum o
    // sencillo online con portada, o la del primer vídeo; sin ninguna,
    // null y el banner queda en #17171B). Sencillo destacado: el
    // primero de la lista (el más nuevo).
    val bannerCoverUrl = remember(artistPage) {
        val page = artistPage
        page?.albums?.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
            ?: page?.singles?.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
            ?: page?.videos?.firstOrNull { it.thumbnailUrl != null }?.thumbnailUrl
    }
    val featuredSingle = remember(artistPage, expandedSingles) {
        (expandedSingles ?: artistPage?.singles)?.firstOrNull()
    }

    // R8c: tarjetas de la cuadrícula de álbumes (locales + online, en
    // parejas) y de la fila de sencillos y EP. Las locales son
    // información (sin tap); las online guardan su release para abrir
    // sus canciones cuando tienen browseId. Sin remember con funs:
    // el tap se resuelve al dibujar, no aquí.
    val albumCardRows = remember(albumReleases, artistPage, expandedAlbums, songs) {
        val online = expandedAlbums ?: artistPage?.albums.orEmpty()
        val local = albumReleases.map { album ->
            ReleaseCardData(
                key = "alb-local:${album.id}",
                title = album.name,
                subtitle = albumSubtitle(album),
                coverSong = songs.firstOrNull { it.album == album.name },
                thumbnailUrl = null,
                release = null,
            )
        }
        val remote = online.mapIndexed { index, release ->
            ReleaseCardData(
                key = "alb:$index:${release.browseId ?: release.title}",
                title = release.title,
                subtitle = onlineReleaseSubtitle(release),
                coverSong = null,
                thumbnailUrl = release.thumbnailUrl,
                release = release,
            )
        }
        (local + remote).chunked(2)
    }
    val singlesCards = remember(singleEpReleases, artistPage, expandedSingles, songs) {
        val online = expandedSingles ?: artistPage?.singles.orEmpty()
        val local = singleEpReleases.map { album ->
            ReleaseCardData(
                key = "sloc:${album.id}",
                title = album.name,
                subtitle = releaseSubtitle(album),
                coverSong = songs.firstOrNull { it.album == album.name },
                thumbnailUrl = null,
                release = null,
            )
        }
        val remote = online.mapIndexed { index, release ->
            ReleaseCardData(
                key = "son:$index:${release.browseId ?: release.title}",
                title = release.title,
                subtitle = onlineReleaseSubtitle(release),
                coverSong = null,
                thumbnailUrl = release.thumbnailUrl,
                release = release,
            )
        }
        local + remote
    }

    fun searchOnline() {
        if (!onlineMode) return
        // Cancelar la búsqueda anterior: sin esto, abrir el perfil de
        // otro artista mientras una búsqueda seguía en vuelo dejaba los
        // resultados del artista anterior en pantalla (carrera A3-1).
        searchJob?.cancel()
        searchError = null
        onlineResults = null
        searchJob = scope.launch {
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
            }
        }
    }

    fun loadArtistPage() {
        if (!onlineMode) return
        pageJob?.cancel()
        pageError = null
        artistPage = null
        albumTracks = null
        albumTracksError = null
        expandedSingles = null
        expandedAlbums = null
        moreError = null
        pageJob = scope.launch {
            try {
                val channelId = client.findArtistChannelId(artistName)
                if (channelId == null) {
                    pageError = "No se encontró la página de \"$artistName\" en YouTube Music."
                    return@launch
                }
                val page = client.browseArtist(channelId)
                if (page == null) {
                    pageError = "La página del artista no respondió. Revisa internet e inténtalo otra vez."
                } else {
                    artistPage = page
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                pageError = "Sin conexión o YouTube no respondió."
            }
        }
    }

    fun loadAlbumTracks(release: ArtistRelease) {
        val browseId = release.browseId ?: return
        albumTracksJob?.cancel()
        albumTracksError = null
        albumTracks = null
        albumTracksJob = scope.launch {
            try {
                val tracks = client.getAlbumTracks(browseId)
                if (tracks == null) {
                    albumTracksError = "No se pudieron cargar las canciones de \"${release.title}\"."
                } else if (tracks.isEmpty()) {
                    albumTracksError = "YouTube no devolvió canciones para \"${release.title}\"."
                } else {
                    albumTracks = release.title to tracks
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                albumTracksError = "Sin conexión o YouTube no respondió."
            }
        }
    }

    fun loadMoreReleases(isSingles: Boolean) {
        val page = artistPage ?: return
        val browseId = if (isSingles) page.singlesBrowseId else page.albumsBrowseId
        val params = if (isSingles) page.singlesParams else page.albumsParams
        if (browseId == null) return
        moreError = null
        pageJob = scope.launch {
            try {
                val releases = client.getArtistReleases(browseId, params)
                if (releases.isEmpty()) {
                    moreError = "No se pudo cargar la lista completa."
                } else if (isSingles) {
                    expandedSingles = releases
                } else {
                    expandedAlbums = releases
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                moreError = "Sin conexión o YouTube no respondió."
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

    // R8c: acciones del banner. Reproducir: todo lo local primero; sin
    // local, la primera canción principal online. Aleatorio: baraja lo
    // local y suena desde una al azar; sin local, una principal online
    // al azar. Sin local ni online, los botones se desactivan (R-26).
    fun playAllArtist() {
        if (songs.isNotEmpty()) {
            onPlayAll()
            return
        }
        val first = artistPage?.topSongs?.firstOrNull() ?: return
        playOnline(first)
    }

    fun playRandomArtist() {
        if (songs.isNotEmpty()) {
            val shuffled = songs.shuffled()
            onSongClick(shuffled.first(), shuffled)
            return
        }
        val top = artistPage?.topSongs
        if (!top.isNullOrEmpty()) playOnline(top.random())
    }

    LaunchedEffect(artistName, onlineMode) {
        if (onlineMode) {
            searchOnline()
            loadArtistPage()
        }
    }

    LaunchedEffect(artistName, onlineMode, bannerCoverUrl) {
        if (!onlineMode || bannerCoverUrl != null) {
            itunesCoverUrl = null
            return@LaunchedEffect
        }
        try {
            val res = searchItunes(artistName, limit = 1)
            itunesCoverUrl = res?.firstOrNull {
                it.artist.contains(artistName, ignoreCase = true) ||
                artistName.contains(it.artist, ignoreCase = true)
            }?.artworkUrl600
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: IOException) {
            itunesCoverUrl = null
        }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        // R8c: Atrás queda arriba del banner; el nombre va sobre la
        // portada y las acciones debajo, con los recuentos reales.
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
            }
        }
        item {
            ArtistBanner(artistName = artistName, coverUrl = bannerCoverUrl ?: itunesCoverUrl)
        }
        item {
            Text(
                "${songs.size} canciones · ${albums.size} álbumes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hasSomethingToPlay = songs.isNotEmpty() ||
                    artistPage?.topSongs?.isNullOrEmpty() == false
                Button(
                    onClick = { playAllArtist() },
                    enabled = hasSomethingToPlay,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Reproducir", modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { playRandomArtist() },
                    enabled = hasSomethingToPlay,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Text("Aleatorio", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // R8a/R8c: secciones locales. "Más escuchadas" ya no va suelta:
        // sus canciones locales viven dentro de "Canciones principales",
        // junto a las online y con el origen marcado.
        // R8c: "Lo más reciente" y "En tu biblioteca" van después de
        // las secciones online (se reinsertan tras "Vídeos").
        // R8c: "En tu biblioteca" se reinsertan tras "Vídeos".

        if (onlineMode) {
            // R8c: la carga y el error de la página van justo tras las
            // acciones: si la página falla, se ve por qué no hay nada
            // online. La búsqueda queda al final ("Más en YouTube Music").
            if (loadingPage) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            "Cargando la página del artista…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            if (pageError != null) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            pageError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { loadArtistPage() },
                            modifier = Modifier.height(48.dp).padding(top = 8.dp),
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }

            // R8b: secciones de la página del artista en YouTube Music.
            // Cada una sale solo si trae datos reales (R-17); si la
            // página falla, error con Reintentar (R-27).
            val page = artistPage
            if (page != null) {
                // R8c: tarjeta del sencillo más reciente (solo si hay
                // singles online; tap abre sus canciones con browseId).
                val single = featuredSingle
                if (single != null) {
                    item {
                        FeaturedSingleCard(
                            release = single,
                            onClick = if (single.browseId != null) {
                                { loadAlbumTracks(single) }
                            } else {
                                null
                            },
                        )
                    }
                }
                // R8c: "Canciones principales" junta online y local,
                // cada una con su origen marcado (no se mezclan sin
                // decirlo). Solo sale si alguna trae datos.
                if (page.topSongs.isNotEmpty() || topSongs.isNotEmpty()) {
                    item {
                        SectionTitle("Canciones principales")
                    }
                    if (page.topSongs.isNotEmpty()) {
                        item {
                            Text(
                                "En YouTube Music",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(page.topSongs, key = { "top-online:${it.videoId}" }) { track ->
                            OnlineTrackRow(track = track, onPlay = { playOnline(track) })
                        }
                    }
                    if (topSongs.isNotEmpty()) {
                        item {
                            Text(
                                "En tu biblioteca",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(topSongs, key = { "top:${it.id}" }) { song ->
                            ProfileSongRow(
                                song = song,
                                queue = songs,
                                onSongClick = onSongClick,
                                subtitle = "${song.album} · ${TimeFormat.formatDuration(song.durationMs)} · ${song.playCount} reproducciones",
                            )
                        }
                    }
                }
                // R8c: "Álbumes" en cuadrícula de 2 columnas, locales y
                // online juntos. Las tarjetas locales usan la portada
                // real de su primera canción; las online, su thumbnail.
                if (albumCardRows.isNotEmpty()) {
                    item {
                        SectionTitle("Álbumes")
                    }
                    items(albumCardRows, key = { row -> "albgrid:${row.first().key}" }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            ReleaseCard(
                                data = row[0],
                                onOpenTracks = { release -> loadAlbumTracks(release) },
                                modifier = Modifier.weight(1f),
                            )
                            if (row.size > 1) {
                                Spacer(modifier = Modifier.width(8.dp))
                                ReleaseCard(
                                    data = row[1],
                                    onOpenTracks = { release -> loadAlbumTracks(release) },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (expandedAlbums == null && page.albumsBrowseId != null) {
                        item {
                            Button(
                                onClick = { loadMoreReleases(false) },
                                modifier = Modifier.fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text("Ver todo")
                            }
                        }
                    }
                }
                // R8c: "Singles y EPs" en horizontal (locales + online),
                // con "Ver todo" para la lista online completa.
                if (singlesCards.isNotEmpty()) {
                    item {
                        SectionTitle("Singles y EPs")
                    }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(singlesCards, key = { it.key }) { data ->
                                ReleaseCard(
                                    data = data,
                                    onOpenTracks = { release -> loadAlbumTracks(release) },
                                    modifier = Modifier.width(140.dp),
                                )
                            }
                        }
                    }
                    if (expandedSingles == null && page.singlesBrowseId != null) {
                        item {
                            Button(
                                onClick = { loadMoreReleases(true) },
                                modifier = Modifier.fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text("Ver todo")
                            }
                        }
                    }
                }
                if (moreError != null) {
                    item {
                        Text(
                            moreError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                val openAlbum = albumTracks
                if (openAlbum != null) {
                    item {
                        SectionTitle("Canciones de ${openAlbum.first}")
                    }
                    items(openAlbum.second, key = { "at:${it.videoId}" }) { track ->
                        OnlineTrackRow(track = track, onPlay = { playOnline(track) })
                    }
                }
                if (albumTracksError != null) {
                    item {
                        Text(
                            albumTracksError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                // R8c: "Vídeos" en horizontal, miniatura 16:9 real; el
                // tap reproduce su audio por el camino existente.
                if (page.videos.isNotEmpty()) {
                    item {
                        SectionTitle("Vídeos")
                    }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(page.videos, key = { "vid:${it.videoId}" }) { video ->
                                VideoCard(
                                    video = video,
                                    onPlay = {
                                        playOnline(
                                            OnlineTrack(
                                                videoId = video.videoId,
                                                title = video.title,
                                                artist = artistName,
                                                album = null,
                                                durationSec = null,
                                                thumbnailUrl = video.thumbnailUrl,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // R8c: resto local (siempre, con o sin online) y búsqueda online
        // al final ("Más en YouTube Music").
        if (recentSongs.isNotEmpty()) {
            item {
                SectionTitle("Lo más reciente")
            }
            items(recentSongs, key = { "recent:${it.id}" }) { song ->
                ProfileSongRow(
                    song = song,
                    queue = songs,
                    onSongClick = onSongClick,
                    subtitle = "${song.album} · ${TimeFormat.formatDuration(song.durationMs)}",
                )
            }
        }
        item {
            SectionTitle("En tu biblioteca")
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
            items(songs, key = { "all:${it.id}" }) { song ->
                ProfileSongRow(
                    song = song,
                    queue = songs,
                    onSongClick = onSongClick,
                    subtitle = "${song.album} · ${TimeFormat.formatDuration(song.durationMs)}",
                )
            }
        }
        if (onlineMode) {
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "Más en YouTube Music",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (searchingOnline) {
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
                    OnlineTrackRow(track = track, onPlay = { playOnline(track) })
                }
            }
        }
    }
}

/** Título de sección del perfil (mismo estilo en todas las secciones). */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Fila de canción del perfil: portada real, título y subtítulo. */
@Composable
private fun ProfileSongRow(
    song: Song,
    queue: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onSongClick(song, queue) }
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
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "N canciones · año" o "N canciones" cuando no hay año. */
private fun albumSubtitle(album: Album): String =
    if (album.year != null) "${album.songCount} canciones · ${album.year}"
    else "${album.songCount} canciones"

/** "Sencillo" (1 canción) o "EP · N canciones", con el año si lo hay. */
private fun releaseSubtitle(album: Album): String {
    val kind = if (album.songCount <= 1) "Sencillo" else "EP"
    val base = if (album.songCount <= 1) kind else "$kind · ${album.songCount} canciones"
    return if (album.year != null) "$base · ${album.year}" else base
}

/** Fila de canción online: título, artista y duración real. */
@Composable
private fun OnlineTrackRow(track: OnlineTrack, onPlay: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onPlay() }
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

/** "Álbum · 2025", "Sencillo · 2026" o solo el tipo cuando no hay año. */
private fun onlineReleaseSubtitle(release: ArtistRelease): String {
    val kind = release.kind ?: "Lanzamiento"
    return if (release.year != null) "$kind · ${release.year}" else kind
}

/** Datos de una tarjeta de lanzamiento (álbum, sencillo o EP). */
private data class ReleaseCardData(
    val key: String,
    val title: String,
    val subtitle: String,
    val coverSong: Song?,
    val thumbnailUrl: String?,
    val release: ArtistRelease?,
)

/** Banner del perfil: mejor portada real del artista o fondo #17171B. */
@Composable
private fun ArtistBanner(artistName: String, coverUrl: String?) {
    val bitmap = rememberRemoteBitmap(coverUrl)
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp)
            .background(Color(0xFF17171B)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Velo plano para que el nombre se lea sobre cualquier portada (R-25).
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        Text(
            artistName,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )
    }
}

/** Tarjeta del sencillo más reciente: portada 64 dp + tipo, año y título. */
@Composable
private fun FeaturedSingleCard(release: ArtistRelease, onClick: (() -> Unit)?) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
            .semantics {
                role = Role.Button
                contentDescription = "Ver canciones de ${release.title}"
            }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = rememberRemoteBitmap(release.thumbnailUrl)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                "Sencillo reciente",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                release.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                onlineReleaseSubtitle(release),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tarjeta de lanzamiento para la cuadrícula y las filas horizontales. */
@Composable
private fun ReleaseCard(
    data: ReleaseCardData,
    onOpenTracks: (ArtistRelease) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Solo clicable cuando abre sus canciones (browseId real); sin
    // browseId es información, no un control (R-26).
    val release = data.release
    val clickableModifier = if (release?.browseId != null) {
        Modifier.clickable { onOpenTracks(release) }
            .semantics {
                role = Role.Button
                contentDescription = "Ver canciones de ${data.title}"
            }
    } else {
        Modifier
    }
    Column(
        modifier = modifier.then(clickableModifier),
    ) {
        val localBitmap = if (data.coverSong != null) {
            rememberArtworkBitmap(data.coverSong.uri)
        } else {
            null
        }
        val remoteBitmap = rememberRemoteBitmap(data.thumbnailUrl)
        val bitmap = localBitmap ?: remoteBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
            }
        }
        Text(
            data.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            data.subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tarjeta de vídeo: miniatura 16:9 real + título; tap reproduce el audio. */
@Composable
private fun VideoCard(video: ArtistVideo, onPlay: () -> Unit) {
    Column(
        modifier = Modifier.width(160.dp)
            .clickable { onPlay() }
            .semantics {
                role = Role.Button
                contentDescription = "Reproducir ${video.title}"
            },
    ) {
        val bitmap = rememberRemoteBitmap(video.thumbnailUrl)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
            }
        }
        Text(
            video.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
