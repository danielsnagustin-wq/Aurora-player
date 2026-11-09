package com.aurora.player.ui.library

import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.player.data.MusicRepository
import com.aurora.player.model.Album
import com.aurora.player.model.Artist
import com.aurora.player.model.Song
import com.aurora.player.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aurora Player — Biblioteca (Etapa 1 cableada, ETAPA 3 renovación).
 * La lista viene del LibraryViewModel (escaneo en IO). Tocar una
 * canción la reproduce (onSongClick). Cada fila lleva su portada real
 * de MediaStore; sin portada, la inicial del título, nunca la de otra
 * canción.
 *
 * ETAPA 3: pestañas Canciones / Artistas / Álbumes / Favoritos, orden
 * (título, artista, álbum, fecha, duración), favoritos con corazón
 * (persistentes en Room) y detección de duplicados (solo se muestran,
 * nada se borra).
 */
@Composable
fun LibraryScreen(
    hasAudioPermission: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val filtered by viewModel.filteredSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val artistFilter by viewModel.artistFilter.collectAsState()
    val albumFilter by viewModel.albumFilter.collectAsState()
    val idFilter by viewModel.idFilter.collectAsState()
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var showDuplicates by remember { mutableStateOf(false) }

    LaunchedEffect(hasAudioPermission) {
        viewModel.load(hasAudioPermission)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!hasAudioPermission) {
            Text(
                "Aurora Player necesita permiso para leer tus archivos de audio. Sin él no hay biblioteca que mostrar.",
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setQuery(it) },
            label = { Text("Buscar título, artista o álbum") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
        )
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Canciones") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Artistas") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Álbumes") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Favoritos") })
        }
        when (val state = uiState) {
            LibraryUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Cargando…", modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            LibraryUiState.Empty -> {
                Text(
                    "No hay canciones aquí todavía. Añade archivos de música al teléfono y vuelve a escanear.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            is LibraryUiState.Loaded -> {
                when (tab) {
                    0 -> SongsTab(
                        viewModel = viewModel,
                        songs = filtered,
                        favoriteIds = favoriteIds,
                        sortOrder = sortOrder,
                        artistFilter = artistFilter,
                        albumFilter = albumFilter,
                        idFilter = idFilter,
                        duplicateGroups = duplicateGroups,
                        albums = state.albums,
                        onSongClick = onSongClick,
                        onShowDuplicates = { showDuplicates = true },
                    )
                    1 -> ArtistsTab(
                        artists = state.artists,
                        onArtistClick = {
                            viewModel.filterByArtist(it.name)
                            tab = 0
                        },
                    )
                    2 -> AlbumsTab(
                        albums = state.albums,
                        songs = state.songs,
                        onAlbumClick = {
                            viewModel.filterByAlbum(it.id)
                            tab = 0
                        },
                    )
                    3 -> FavoritesTab(
                        songs = favorites,
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onSongClick = onSongClick,
                    )
                }
            }
        }
    }
    if (showDuplicates) {
        DuplicatesDialog(
            groups = duplicateGroups,
            onGroupClick = { group ->
                viewModel.filterByIds(group.songs.map { it.id }.toSet())
                showDuplicates = false
                tab = 0
            },
            onDismiss = { showDuplicates = false },
        )
    }
}

@Composable
private fun SongsTab(
    viewModel: LibraryViewModel,
    songs: List<Song>,
    favoriteIds: Set<Long>,
    sortOrder: LibrarySort,
    artistFilter: String?,
    albumFilter: Long?,
    idFilter: Set<Long>?,
    duplicateGroups: List<DuplicateGroup>,
    albums: List<Album>,
    onSongClick: (Song, List<Song>) -> Unit,
    onShowDuplicates: () -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${songs.size} canciones")
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (duplicateGroups.isNotEmpty()) {
                TextButton(onClick = onShowDuplicates) {
                    Text("Duplicados (${duplicateGroups.size})")
                }
            }
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = "Ordenar")
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                ) {
                    LibrarySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (sort == sortOrder) "✓ ${sort.label}" else sort.label,
                                )
                            },
                            onClick = {
                                viewModel.setSort(sort)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
    val filterLabel = when {
        idFilter != null -> "Duplicados"
        artistFilter != null -> "Artista: $artistFilter"
        albumFilter != null -> "Álbum: ${albums.firstOrNull { it.id == albumFilter }?.name ?: ""}"
        else -> null
    }
    if (filterLabel != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                filterLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.clearFilters() }) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar filtro")
            }
        }
    }
    if (songs.isEmpty()) {
        Text(
            "Nada coincide con la búsqueda.",
            modifier = Modifier.padding(16.dp),
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isFavorite = song.id in favoriteIds,
                    onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                    onClick = { onSongClick(song, songs) },
                )
                Divider()
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
) {
    if (artists.isEmpty()) {
        Text("No hay artistas.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.id }) { artist ->
            ListItem(
                headlineContent = {
                    Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        "${artist.songCount} canciones · ${artist.albumCount} álbumes",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { onArtistClick(artist) },
            )
            Divider()
        }
    }
}

@Composable
private fun AlbumsTab(
    albums: List<Album>,
    songs: List<Song>,
    onAlbumClick: (Album) -> Unit,
) {
    if (albums.isEmpty()) {
        Text("No hay álbumes.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(albums, key = { it.id }) { album ->
            val coverSong = songs.firstOrNull { it.albumId == album.id }
            ListItem(
                headlineContent = {
                    Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        "${album.artist} · ${album.songCount} canciones",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = {
                    if (coverSong != null) AlbumCover(coverSong, size = 56.dp)
                },
                modifier = Modifier.clickable { onAlbumClick(album) },
            )
            Divider()
        }
    }
}

@Composable
private fun FavoritesTab(
    songs: List<Song>,
    favoriteIds: Set<Long>,
    onToggleFavorite: (Long) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
) {
    if (songs.isEmpty()) {
        Text(
            "Aún no tienes favoritos. Toca el corazón de una canción para guardarla aquí.",
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isFavorite = song.id in favoriteIds,
                onToggleFavorite = { onToggleFavorite(song.id) },
                onClick = { onSongClick(song, songs) },
            )
            Divider()
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                "${song.artist} · ${song.album}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    TimeFormat.formatDuration(song.durationMs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = { AlbumCover(song, size = 56.dp) },
        modifier = Modifier.clickable { onClick() },
    )
}

@Composable
private fun DuplicatesDialog(
    groups: List<DuplicateGroup>,
    onGroupClick: (DuplicateGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        title = { Text("Canciones duplicadas") },
        text = {
            Column {
                Text(
                    "Mismo título, artista y duración. Toca un grupo para ver sus copias. Nada se borra desde aquí.",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    items(groups) { group ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    group.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    "${group.artist} · ${group.songs.size} copias · ${TimeFormat.formatDuration(group.durationMs)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.clickable { onGroupClick(group) },
                        )
                        Divider()
                    }
                }
            }
        },
    )
}

/** Caché pequeña de portadas en memoria (32 álbumes como mucho). */
private object AlbumArtCache {
    private val cache = object : LruCache<Long, Bitmap>(32) {
        override fun sizeOf(key: Long, value: Bitmap): Int = 1
    }

    fun get(albumId: Long): Bitmap? = cache.get(albumId)

    fun put(albumId: Long, bitmap: Bitmap) {
        cache.put(albumId, bitmap)
    }
}

/**
 * Portada real del álbum vía MediaStore, o la inicial del título.
 * La carga corre en IO; un fallo deja la inicial, nunca se cuelga.
 */
@Composable
fun AlbumCover(song: Song, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(song.albumId) { mutableStateOf(AlbumArtCache.get(song.albumId)) }
    LaunchedEffect(song.albumId) {
        if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val uri = MusicRepository(context).albumArtUri(song.albumId)
                    context.contentResolver.loadThumbnail(uri, Size(144, 144), null)
                } catch (_: Exception) {
                    null
                }
            }
            if (loaded != null) {
                AlbumArtCache.put(song.albumId, loaded)
                bitmap = loaded
            }
        }
    }
    ArtworkImage(
        bitmap = bitmap,
        letter = song.title.take(1).uppercase(),
        size = size,
        modifier = modifier,
    )
}

/**
 * Imagen de portada (bitmap ya cargado) o la inicial del título.
 * La usa también la mini-barra y el reproductor, que reciben la
 * portada desde los metadatos del MediaItem, no desde un Song.
 */
@Composable
fun ArtworkImage(
    uri: android.net.Uri?,
    letter: String,
    size: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(288, 288), null)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    ArtworkImage(bitmap = bitmap, letter = letter, size = size, modifier = modifier)
}

@Composable
private fun ArtworkImage(
    bitmap: Bitmap?,
    letter: String,
    size: Dp?,
    modifier: Modifier = Modifier,
) {
    val sized = if (size != null) modifier.size(size) else modifier
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = sized.clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = sized
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
