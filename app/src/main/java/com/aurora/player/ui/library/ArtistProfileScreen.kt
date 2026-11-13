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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.aurora.player.online.ArtistPage
import com.aurora.player.online.ArtistRelease
import com.aurora.player.online.ArtistVideo
import com.aurora.player.online.InnerTubeClient
import com.aurora.player.online.OnlineTrack
import com.aurora.player.util.TimeFormat
import com.aurora.player.util.effectiveStreamQuality
import com.aurora.player.util.isWifiConnected
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var resolvingId by remember { mutableStateOf<String?>(null) }
    // R8b: página del artista en YouTube Music (novedades, álbumes,
    // sencillos y vídeos reales). Carga independiente de la búsqueda.
    var artistPage by remember { mutableStateOf<ArtistPage?>(null) }
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

    LaunchedEffect(artistName, onlineMode) {
        if (onlineMode) {
            searchOnline()
            loadArtistPage()
        }
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

        // R8a: secciones tipo Spotify, cada una solo si tiene datos reales.
        // Las claves llevan prefijo porque la misma canción sale en varias
        // secciones y la clave debe ser única en toda la lista.
        if (topSongs.isNotEmpty()) {
            item {
                SectionTitle("Más escuchadas")
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
        if (albumReleases.isNotEmpty()) {
            item {
                SectionTitle("Álbumes")
            }
            items(albumReleases, key = { it.id }) { album ->
                ProfileAlbumRow(album, albumSubtitle(album))
            }
        }
        if (singleEpReleases.isNotEmpty()) {
            item {
                SectionTitle("Sencillos y EP")
            }
            items(singleEpReleases, key = { it.id }) { album ->
                ProfileAlbumRow(album, releaseSubtitle(album))
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
                    "En YouTube Music",
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

            // R8b: secciones de la página del artista en YouTube Music.
            // Cada una sale solo si trae datos reales (R-17); si la
            // página falla, error con Reintentar (R-27).
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
            val page = artistPage
            if (page != null) {
                if (page.topSongs.isNotEmpty()) {
                    item {
                        SectionTitle("Canciones principales")
                    }
                    items(page.topSongs, key = { "top-online:${it.videoId}" }) { track ->
                        OnlineTrackRow(track = track, onPlay = { playOnline(track) })
                    }
                }
                val singles = expandedSingles ?: page.singles
                if (singles.isNotEmpty()) {
                    item {
                        SectionTitle("Nuevos lanzamientos")
                    }
                    itemsIndexed(singles, key = { index, release ->
                        "new:$index:${release.browseId ?: release.title}"
                    }) { _, release ->
                        ReleaseRow(
                            release = release,
                            onClick = if (release.browseId != null) {
                                { loadAlbumTracks(release) }
                            } else {
                                null
                            },
                        )
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
                val albums = expandedAlbums ?: page.albums
                if (albums.isNotEmpty()) {
                    item {
                        SectionTitle("Álbumes")
                    }
                    itemsIndexed(albums, key = { index, release ->
                        "alb:$index:${release.browseId ?: release.title}"
                    }) { _, release ->
                        ReleaseRow(
                            release = release,
                            onClick = if (release.browseId != null) {
                                { loadAlbumTracks(release) }
                            } else {
                                null
                            },
                        )
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
                if (page.videos.isNotEmpty()) {
                    item {
                        SectionTitle("Vídeos")
                    }
                    items(page.videos, key = { "vid:${it.videoId}" }) { video ->
                        VideoRow(
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

/** Fila de álbum/EP/sencillo del perfil: solo información, no es un control (R-26). */
@Composable
private fun ProfileAlbumRow(album: Album, subtitle: String) {
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

/** Fila de lanzamiento online (álbum, sencillo o EP): título y tipo con año real. */
@Composable
private fun ReleaseRow(release: ArtistRelease, onClick: (() -> Unit)?) {
    // Solo clicable cuando abre sus canciones (browseId real); sin
    // browseId es información, no un control (R-26).
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
            .heightIn(min = 52.dp)
            .then(clickableModifier)
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

/** Fila de vídeo online: título y subtítulo real (vistas). */
@Composable
private fun VideoRow(video: ArtistVideo, onPlay: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onPlay() }
            .semantics {
                role = Role.Button
                contentDescription = "Reproducir ${video.title}"
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
                video.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            if (video.subtitle != null) {
                Text(
                    video.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Álbum · 2025", "Sencillo · 2026" o solo el tipo cuando no hay año. */
private fun onlineReleaseSubtitle(release: ArtistRelease): String {
    val kind = release.kind ?: "Lanzamiento"
    return if (release.year != null) "$kind · ${release.year}" else kind
}
