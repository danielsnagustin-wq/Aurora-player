@file:OptIn(ExperimentalFoundationApi::class)
package com.aurora.player.ui.library

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.player.data.LibraryPrefsStore
import com.aurora.player.data.MusicRepository
import com.aurora.player.model.Album
import com.aurora.player.model.Artist
import com.aurora.player.model.FolderEntry
import com.aurora.player.model.ListDensity
import com.aurora.player.model.SearchCategory
import com.aurora.player.model.Song
import com.aurora.player.model.childFolders
import com.aurora.player.model.commonFolderPrefix
import com.aurora.player.model.isDirectlyInFolder
import com.aurora.player.model.parentPath
import com.aurora.player.online.OnlineTrack
import com.aurora.player.ui.online.OnlineScreen
import com.aurora.player.ui.start.StartScreen
import com.aurora.player.ui.theme.LocalAlbumGrid
import com.aurora.player.ui.theme.LocalCardStyle
import com.aurora.player.ui.theme.LocalCornerRadiusDp
import com.aurora.player.ui.theme.LocalCoverShape
import com.aurora.player.ui.theme.LocalListDensity
import com.aurora.player.ui.theme.cardBackground
import com.aurora.player.ui.theme.cardBorder
import com.aurora.player.ui.theme.cornerRadiusShape
import com.aurora.player.ui.theme.coverShapeToCornerPercent
import com.aurora.player.ui.theme.listDensityToRowHeightDp
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.SongDetails
import com.aurora.player.util.StartupTimer
import com.aurora.player.util.buildMetadataUpdate
import com.aurora.player.util.codecDisplayName
import com.aurora.player.util.loadEmbeddedCover
import com.aurora.player.util.TimeFormat
import com.aurora.player.util.loadSongDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
/**
 * Pestañas del inicio, en orden. Los índices 0..6 son estables: se
 * guardan en LibraryPrefsStore (visibles + última abierta).
 */
private val LIBRARY_TABS = listOf(
    "Canciones", "Artistas", "Álbumes", "Favoritos", "Géneros", "Carpetas", "Online",
)

@Composable
fun LibraryScreen(
    hasAudioPermission: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    // RONDA 2 (2026-11-09): "Mezclar todo" no existía: todos los
    // reproductores open source lo tienen. Mezcla lo que se ve
    // (búsqueda/filtro incluidos) y suena en aleatorio.
    onShuffleAll: (List<Song>) -> Unit = {},
    // 002-online-youtube: pestaña Online (YouTube). Apagada, la
    // pestaña solo explica dónde se enciende el modo online.
    onlineMode: Boolean = false,
    onPlayOnlineTrack: (OnlineTrack, String) -> Unit = { _, _ -> },
    // Fase 2 — Spotify como BitChord: tus listas en la pestaña Online.
    spotifyConnected: Boolean = false,
    onImportOnlineTracks: (List<Pair<OnlineTrack, String>>) -> Unit = {},
    showStart: Boolean = false,
    onStartFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val searchCategory by viewModel.searchCategory.collectAsState()
    val filtered by viewModel.filteredSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val artistFilter by viewModel.artistFilter.collectAsState()
    val albumFilter by viewModel.albumFilter.collectAsState()
    val idFilter by viewModel.idFilter.collectAsState()
    val genreFilter by viewModel.genreFilter.collectAsState()
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    // RONDA 2 (2026-11-09): inicio personalizable. Qué pestañas se
    // ven, cuál fue la última abierta y qué tamaño tienen las
    // portadas de las filas. Todo guardado en LibraryPrefsStore.
    val context = LocalContext.current
    val libraryPrefs = remember { LibraryPrefsStore(context) }
    val visibleTabs by libraryPrefs.visibleTabsFlow.collectAsState(
        initial = LibraryPrefsStore.ALL_TABS,
    )
    val lastTab by libraryPrefs.lastTabFlow.collectAsState(initial = 0)
    val rowCoverDp by libraryPrefs.rowCoverSizeFlow.collectAsState(
        initial = LibraryPrefsStore.DEFAULT_ROW_COVER_DP,
    )
    var tab by remember(lastTab) {
        mutableStateOf(lastTab.takeIf { it in visibleTabs } ?: visibleTabs.firstOrNull() ?: 0)
    }
    LaunchedEffect(tab) { libraryPrefs.saveLastTab(tab) }
    LaunchedEffect(visibleTabs) {
        if (tab !in visibleTabs) tab = visibleTabs.firstOrNull() ?: 0
    }
    var showDuplicates by remember { mutableStateOf(false) }
    // ETAPA 5: mantener pulsado una canción abre sus Detalles.
    var detailsSong by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(hasAudioPermission) {
        viewModel.load(hasAudioPermission)
    }

    // RENDIMIENTO (spec 004): marca el primer dibujo real de la
    // biblioteca (después de la animación de inicio, si la hay).
    // StartupTimer solo guarda la primera marca.
    LaunchedEffect(showStart) {
        if (!showStart) StartupTimer.markFirstFrame()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!hasAudioPermission) {
            Text(
                "Aurora Player necesita permiso para leer tus archivos de audio. Sin él no hay biblioteca que mostrar.",
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        // Inicio propio: animación de Aurora (se disuelve en polvo
        // y el viento se lo lleva) a pantalla completa, una sola vez
        // por arranque. Tocar salta la animación.
        if (showStart) {
            StartScreen(onFinished = onStartFinished)
            return@Column
        }
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setQuery(it) },
            label = { Text("Buscar en tu biblioteca") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
        )
        // SPEC 005 FR-005: ámbito de la búsqueda. Solo se muestra
        // con texto, para que nunca sea un adorno: tocar cambia
        // dónde busca; mantener pulsado aísla ese ámbito y quita
        // los demás filtros.
        if (query.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (category in SearchCategory.entries) {
                    val selected = category == searchCategory
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .combinedClickable(
                                onClick = { viewModel.setSearchCategory(category) },
                                onLongClick = { viewModel.isolateSearchCategory(category) },
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            category.label,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // 7 pestañas con Online: TabRow las aplastaría; con scroll
        // caben todas sin recortar los nombres. Solo se muestran las
        // visibles (Ajustes > Biblioteca > Pestañas visibles).
        val visibleList = LIBRARY_TABS.withIndex().filter { it.index in visibleTabs }
        ScrollableTabRow(
            selectedTabIndex = visibleList.indexOfFirst { it.index == tab }.coerceAtLeast(0),
            edgePadding = 0.dp,
        ) {
            for ((index, label) in visibleList) {
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        // 002-online-youtube: la pestaña Online no depende de la
        // biblioteca local (funciona hasta sin canciones en el teléfono).
        if (tab == 6) {
            OnlineScreen(
                onlineMode = onlineMode,
                onPlayTrack = onPlayOnlineTrack,
                spotifyConnected = spotifyConnected,
                onImportOnlineTracks = onImportOnlineTracks,
                modifier = Modifier.fillMaxSize(),
            )
        } else when (val state = uiState) {
            LibraryUiState.Loading -> {
                // Si la biblioteca tarda más que la animación de
                // inicio, un indicador simple hasta que aparezca.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            LibraryUiState.Empty -> {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("No hay canciones aquí todavía. Añade archivos de música al teléfono y vuelve a escanear.")
                    // AUDITORÍA (2026-11-09): el texto decía "vuelve a
                    // escanear" pero NO HABÍA ningún botón para hacerlo.
                    OutlinedButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Volver a escanear") }
                }
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
                        genreFilter = genreFilter,
                        duplicateGroups = duplicateGroups,
                        albums = state.albums,
                        onSongClick = onSongClick,
                        onShowDuplicates = { showDuplicates = true },
                        onDetails = { detailsSong = it },
                        onShuffleAll = onShuffleAll,
                        coverSizeDp = rowCoverDp,
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
                        onDetails = { detailsSong = it },
                        coverSizeDp = rowCoverDp,
                    )
                    4 -> GenresTab(
                        songs = state.songs,
                        onGenreClick = {
                            viewModel.filterByGenre(it)
                            tab = 0
                        },
                    )
                    5 -> FoldersTab(
                        songs = state.songs,
                        favoriteIds = favoriteIds,
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onSongClick = onSongClick,
                        onDetails = { detailsSong = it },
                        onExcludeFolder = { viewModel.excludeFolder(it) },
                        coverSizeDp = rowCoverDp,
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
    detailsSong?.let { song ->
        SongDetailsDialog(
            song = song,
            viewModel = viewModel,
            onDismiss = { detailsSong = null },
            onLibraryChanged = { viewModel.refresh() },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
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
    genreFilter: String?,
    duplicateGroups: List<DuplicateGroup>,
    albums: List<Album>,
    onSongClick: (Song, List<Song>) -> Unit,
    onShowDuplicates: () -> Unit,
    onDetails: (Song) -> Unit,
    onShuffleAll: (List<Song>) -> Unit = {},
    coverSizeDp: Int = LibraryPrefsStore.DEFAULT_ROW_COVER_DP,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${songs.size} canciones")
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (songs.isNotEmpty()) {
                TextButton(onClick = { onShuffleAll(songs) }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Mezclar todo", modifier = Modifier.padding(start = 4.dp))
                }
            }
            if (duplicateGroups.isNotEmpty()) {
                TextButton(onClick = onShowDuplicates) {
                    Text("Duplicados (${duplicateGroups.size})")
                }
            }
            // Volver a escanear: música nueva en el teléfono sin
            // tener que reiniciar la app.
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Volver a escanear")
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
        genreFilter != null -> "Género: $genreFilter"
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
                    onDetails = onDetails,
                    coverSizeDp = coverSizeDp,
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
        // La clave es el nombre, no el id: el id es un hashCode del
        // nombre y dos artistas distintos podrían colisionar y tumbar
        // la lista ("key was already used").
        items(artists, key = { it.name }) { artist ->
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
    // DISEÑO PREMIUM (spec 003): cuadrícula de 2 columnas cuando el
    // interruptor está encendido; lista cuando está apagado.
    if (LocalAlbumGrid.current) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(albums, key = { it.id }) { album ->
                val coverSong = songs.firstOrNull { it.albumId == album.id }
                Surface(
                    color = cardBackground(
                        LocalCardStyle.current,
                        MaterialTheme.colorScheme.surface,
                    ),
                    shape = cornerRadiusShape(LocalCornerRadiusDp.current),
                    border = cardBorder(
                        LocalCardStyle.current,
                        MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.clickable { onAlbumClick(album) },
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (coverSong != null) AlbumCover(coverSong, size = 160.dp)
                        Text(
                            album.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "${album.artist} · ${album.songCount} canciones",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
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
private fun GenresTab(
    songs: List<Song>,
    onGenreClick: (String) -> Unit,
) {
    // RONDA 2 (2026-11-09): la tabla de paridad open source pedía
    // géneros en la Etapa 1 y no había pestaña. El género viene de
    // MediaStore; sin género, "Sin género".
    val genres = songs.groupBy { it.genre?.takeIf { g -> g.isNotBlank() } ?: "Sin género" }
        .map { (name, list) -> name to list.size }
        .sortedBy { it.first.lowercase() }
    if (genres.isEmpty()) {
        Text("No hay géneros.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(genres, key = { it.first }) { (name, count) ->
            // DISEÑO PREMIUM (spec 003): cada género es una tarjeta
            // con el estilo y el radio elegidos en Ajustes.
            Surface(
                color = cardBackground(
                    LocalCardStyle.current,
                    MaterialTheme.colorScheme.surface,
                ),
                shape = cornerRadiusShape(LocalCornerRadiusDp.current),
                border = cardBorder(
                    LocalCardStyle.current,
                    MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                ListItem(
                    headlineContent = {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            "$count canciones",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.clickable { onGenreClick(name) },
                )
            }
        }
    }
}

/**
 * SPEC 005 FR-006: pestaña Carpetas. Navegación jerárquica por la
 * ruta real de los archivos: la raíz es la carpeta común de todas
 * las canciones; tocar una carpeta baja a ella, "Atrás" sube.
 * Mantener pulsada una carpeta la excluye de toda la biblioteca
 * (con confirmación); se vuelve a incluir en Ajustes.
 */
@Composable
private fun FoldersTab(
    songs: List<Song>,
    favoriteIds: Set<Long>,
    onToggleFavorite: (Long) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onDetails: (Song) -> Unit,
    onExcludeFolder: (String) -> Unit,
    coverSizeDp: Int = LibraryPrefsStore.DEFAULT_ROW_COVER_DP,
) {
    if (songs.isEmpty()) {
        Text(
            "No hay canciones aquí todavía.",
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    val root = remember(songs) {
        commonFolderPrefix(songs.map { it.path }).ifEmpty { "/" }
    }
    var current by remember(songs) { mutableStateOf(root) }
    var excludeTarget by remember { mutableStateOf<FolderEntry?>(null) }
    val folders = remember(songs, current) {
        childFolders(songs.map { it.path }, current)
    }
    val here = remember(songs, current) {
        songs.filter { isDirectlyInFolder(it.path, current) }
            .sortedBy { it.title.lowercase() }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (current != root) {
                OutlinedButton(onClick = { current = parentPath(current) }) {
                    Text("↑ Atrás")
                }
            }
            Column {
                Text(
                    if (current == root) "Todas las carpetas" else current.substringAfterLast('/'),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(folders, key = { it.path }) { folder ->
                ListItem(
                    headlineContent = { Text(folder.name) },
                    supportingContent = {
                        Text(
                            "${folder.songCount} canciones",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { current = folder.path },
                        onLongClick = { excludeTarget = folder },
                    ),
                )
                Divider()
            }
            items(here, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isFavorite = song.id in favoriteIds,
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onClick = { onSongClick(song, here) },
                    onDetails = onDetails,
                    coverSizeDp = coverSizeDp,
                )
                Divider()
            }
        }
    }
    if (excludeTarget != null) {
        val folder = excludeTarget!!
        AlertDialog(
            onDismissRequest = { excludeTarget = null },
            title = { Text("Excluir carpeta") },
            text = {
                Text(
                    "«${folder.name}» y sus ${folder.songCount} canciones saldrán de todas las pestañas, " +
                        "de la búsqueda y de la cola. Puedes volver a incluirla en Ajustes, en Carpetas excluidas.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    excludeTarget = null
                    onExcludeFolder(folder.path)
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { excludeTarget = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun FavoritesTab(
    songs: List<Song>,
    favoriteIds: Set<Long>,
    onToggleFavorite: (Long) -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onDetails: (Song) -> Unit,
    coverSizeDp: Int = LibraryPrefsStore.DEFAULT_ROW_COVER_DP,
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
                onDetails = onDetails,
                coverSizeDp = coverSizeDp,
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
    onDetails: (Song) -> Unit = {},
    coverSizeDp: Int = LibraryPrefsStore.DEFAULT_ROW_COVER_DP,
) {
    // AUDITORÍA (2026-11-09): la densidad "Compacta" no hacía nada:
    // el ListItem de Material 3 impone su propia altura mínima (72 dp
    // con dos líneas) y el heightIn(52) de fuera no lo encogía. Ahora
    // compacta es una fila propia de 52 dp; cómoda sigue siendo ListItem.
    // RECONSTRUCCIÓN (2026-11-10, Fase 2): fila limpia estilo
    // Spotify/Plexamp, sin tarjeta por canción. La tarjeta por fila
    // llenaba la pantalla de bordes y se veía cargada, no premium.
    // El estilo de tarjetas y el radio siguen viviendo en la
    // mini-barra, las cabeceras y los diálogos.
    if (LocalListDensity.current == ListDensity.COMPACT) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .height(52.dp)
                .combinedClickable(onClick = onClick, onLongClick = { onDetails(song) })
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumCover(song, size = coverSizeDp.dp)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${song.artist} · ${song.album}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
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
        leadingContent = { AlbumCover(song, size = coverSizeDp.dp) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .heightIn(min = listDensityToRowHeightDp(LocalListDensity.current).dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onDetails(song) },
            ),
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

/**
 * Detalles reales de una canción (ETAPA 5): todos los campos que
 * existen, "—" donde no hay dato, nunca inventado. "Editar" cambia
 * todos los metadatos en MediaStore (en Android 10+ el sistema pide
 * permiso al usuario con su propio diálogo).
 */
@Composable
private fun SongDetailsDialog(
    song: Song,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
    onLibraryChanged: () -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember(song) { mutableStateOf<SongDetails?>(null) }
    var playCount by remember(song) { mutableStateOf<Int?>(null) }
    var lastPlayed by remember(song) { mutableStateOf<Long?>(null) }
    var editing by remember(song) { mutableStateOf(false) }
    var editTitle by remember(song) { mutableStateOf(song.title) }
    var editArtist by remember(song) { mutableStateOf(song.artist) }
    var editAlbumArtist by remember(song) { mutableStateOf(song.albumArtist ?: "") }
    var editAlbum by remember(song) { mutableStateOf(song.album) }
    var editGenre by remember(song) { mutableStateOf(song.genre ?: "") }
    var editComposer by remember(song) { mutableStateOf(song.composer ?: "") }
    var editYear by remember(song) { mutableStateOf(song.year?.toString() ?: "") }
    var editTrack by remember(song) {
        mutableStateOf(if (song.trackNumber > 0) song.trackNumber.toString() else "")
    }
    var editDisc by remember(song) {
        // RONDA 4 (2026-11-09): con disco 0 el campo mostraba "0".
        mutableStateOf(if (song.discNumber > 0) song.discNumber.toString() else "")
    }
    var saveError by remember { mutableStateOf<String?>(null) }
    // El sistema pide permiso para editar metadatos (Android 10+):
    // se guarda la petición y se lanza desde un efecto, para no
    // crear una referencia circular entre save() y el launcher.
    var pendingConsent by remember { mutableStateOf<IntentSenderRequest?>(null) }

    LaunchedEffect(song) {
        details = loadSongDetails(context, song)
        val (count, last) = viewModel.songStats(song.id)
        playCount = count
        lastPlayed = last
    }

    val save: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val updates = buildMetadataUpdate(
                editTitle, editArtist, editAlbumArtist, editAlbum,
                editGenre, editComposer, editYear, editTrack, editDisc,
                api30 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            )
            val values = ContentValues().apply {
                for ((key, value) in updates) {
                    when (value) {
                        is String -> put(key, value)
                        is Int -> put(key, value)
                    }
                }
            }
            try {
                context.contentResolver.update(song.uri, values, null, null)
                withContext(Dispatchers.Main) {
                    // RONDA 4 (2026-11-09): antes el diálogo se
                    // quedaba abierto mostrando los datos VIEJOS (el
                    // Song del diálogo no cambia al refrescar la
                    // biblioteca). Al guardar bien, se cierra y la
                    // lista muestra los valores nuevos.
                    onLibraryChanged()
                    onDismiss()
                }
            } catch (e: RecoverableSecurityException) {
                val request = IntentSenderRequest.Builder(
                    e.userAction.actionIntent.intentSender,
                ).build()
                withContext(Dispatchers.Main) { pendingConsent = request }
            } catch (e: Exception) {
                ErrorLog.log(context, "Editar metadatos", e)
                withContext(Dispatchers.Main) { saveError = "No se pudo guardar el cambio." }
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) save()
    }

    LaunchedEffect(pendingConsent) {
        pendingConsent?.let {
            consentLauncher.launch(it)
            pendingConsent = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = {
            if (!editing) TextButton(onClick = { editing = true }) { Text("Editar") }
        },
        title = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (editing) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artista") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = editAlbumArtist,
                        onValueChange = { editAlbumArtist = it },
                        label = { Text("Artista del álbum") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = editAlbum,
                        onValueChange = { editAlbum = it },
                        label = { Text("Álbum") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = editGenre,
                        onValueChange = { editGenre = it },
                        label = { Text("Género") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = editComposer,
                        onValueChange = { editComposer = it },
                        label = { Text("Compositor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = editYear,
                        onValueChange = { editYear = it },
                        label = { Text("Año") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = editTrack,
                        onValueChange = { editTrack = it },
                        label = { Text("Pista") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = editDisc,
                        onValueChange = { editDisc = it },
                        label = { Text("Disco") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    TextButton(onClick = { save() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Guardar cambios")
                    }
                    saveError?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                } else {
                    DetailRow("Artista", song.artist)
                    DetailRow("Artista del álbum", song.albumArtist)
                    DetailRow("Álbum", song.album)
                    DetailRow("Año", song.year?.toString())
                    DetailRow("Género", details?.genre ?: song.genre)
                    DetailRow("Compositor", details?.composer ?: song.composer)
                    DetailRow("Pista", if (song.trackNumber > 0) song.trackNumber.toString() else null)
                    DetailRow("Disco", if (song.discNumber > 1) song.discNumber.toString() else null)
                    DetailRow("Formato", codecDisplayName(details?.mimeType ?: song.mimeType, song.path))
                    DetailRow("Bitrate", details?.bitrateKbps?.let { "$it kbps" })
                    DetailRow("Sample rate", details?.sampleRateHz?.let { "$it Hz" })
                    DetailRow(
                        "Canales",
                        details?.channelCount?.let {
                            when (it) {
                                1 -> "Mono"
                                2 -> "Estéreo"
                                else -> "$it canales"
                            }
                        },
                    )
                    DetailRow("Duración", TimeFormat.formatDuration(song.durationMs))
                    DetailRow("Tamaño", formatSize(song.sizeBytes))
                    DetailRow("Añadida", formatDate(song.dateAdded))
                    DetailRow("Reproducciones", playCount?.toString())
                    DetailRow("Última escucha", lastPlayed?.let { formatDate(it) })
                    DetailRow("Ruta", song.path)
                    // Ruta de audio (idea de Namida): lo que el archivo
                    // trae y lo que el sistema saca de verdad. Android
                    // remuestrea la salida a la frecuencia del sistema.
                    val audioManager = context.getSystemService(
                        android.media.AudioManager::class.java,
                    )
                    DetailRow(
                        "Salida del sistema",
                        audioManager?.getProperty(
                            android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE,
                        )?.let { "$it Hz" },
                    )
                    DetailRow(
                        "Búfer de salida",
                        audioManager?.getProperty(
                            android.media.AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER,
                        )?.let { "$it muestras" },
                    )
                    TextButton(onClick = { onPlayNext(song); onDismiss() }) {
                        Text("Reproducir a continuación")
                    }
                    TextButton(onClick = { onAddToQueue(song); onDismiss() }) {
                        Text("Añadir a la cola")
                    }
                    // RONDA 2 (2026-11-09): compartir el archivo de
                    // audio (Retro/Namida lo tienen). Se envía la Uri
                    // de MediaStore con permiso de lectura temporal.
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = song.mimeType.ifBlank { "audio/*" }
                            putExtra(Intent.EXTRA_STREAM, song.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(intent, "Compartir ${song.title}"),
                        )
                    }) {
                        Text("Compartir archivo")
                    }
                }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value ?: "—",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}

private fun formatDate(millis: Long): String =
    if (millis <= 0) "—"
    else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))

/** Caché pequeña de portadas en memoria (16 MB como mucho).
 * AUDITORÍA (2026-11-09): antes se contaban 32 entradas sin mirar
 * los bytes: 32 portadas grandes podían ser cientos de MB.
 * Asistente de configuración (2026-11-09): el usuario puede
 * apagarla (cada fila recarga su portada) y vaciarla. */
internal object AlbumArtCache {
    private const val MAX_KB = 16 * 1024
    private val cache = object : LruCache<Long, Bitmap>(MAX_KB) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(albumId: Long): Bitmap? = cache.get(albumId)

    fun put(albumId: Long, bitmap: Bitmap) {
        cache.put(albumId, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}

/**
 * Portada real del álbum vía MediaStore, o la incrustada en el archivo,
 * o la inicial del título. La carga corre en IO; un fallo deja la
 * inicial, nunca se cuelga.
 */
@Composable
fun AlbumCover(song: Song, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Asistente de configuración (2026-11-09): si la caché de
    // imágenes está apagada, cada fila recarga su portada desde
    // MediaStore y no se guarda nada en memoria.
    val libraryPrefs = remember { LibraryPrefsStore(context) }
    val useCache by libraryPrefs.imageCacheEnabledFlow.collectAsState(initial = true)
    var bitmap by remember(song.albumId, useCache) {
        mutableStateOf(if (useCache) AlbumArtCache.get(song.albumId) else null)
    }
    LaunchedEffect(song.albumId, useCache) {
        if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val uri = MusicRepository(context).albumArtUri(song.albumId)
                    context.contentResolver.loadThumbnail(uri, Size(144, 144), null)
                } catch (_: Exception) {
                    null
                }
            } ?: loadEmbeddedCover(context, song.uri)
            if (loaded != null) {
                if (useCache) AlbumArtCache.put(song.albumId, loaded)
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
 * portada desde los metadatos del MediaItem, no desde un Song. Si
 * MediaStore no da portada, se prueba la incrustada en el archivo
 * ([songUri]); si tampoco hay, la inicial grande.
 */
/**
 * Carga la portada (MediaStore o incrustada en el archivo) y la
 * devuelve como Bitmap. La usan ArtworkImage y el reproductor, que
 * necesita el bitmap para el fondo y el color dinámico (spec 003).
 * Un fallo deja null, nunca se cuelga.
 */
@Composable
fun rememberArtworkBitmap(
    uri: android.net.Uri?,
    songUri: android.net.Uri? = null,
    // RONDA PULIDO (2026-11-09): la portada del reproductor se
    // veía borrosa porque se cargaba a 288 px y se estiraba a
    // todo el ancho. El reproductor pide 1024; la lista sigue a 288.
    sizePx: Int = 288,
): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri, songUri, sizePx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, songUri, sizePx) {
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
                } catch (_: Exception) {
                    null
                }
            } ?: songUri?.let { loadEmbeddedCover(context, it, maxPx = sizePx) }
        } else if (songUri != null) {
            bitmap = loadEmbeddedCover(context, songUri, maxPx = sizePx)
        }
    }
    return bitmap
}

@Composable
fun ArtworkImage(
    uri: android.net.Uri?,
    letter: String,
    size: Dp? = null,
    modifier: Modifier = Modifier,
    songUri: android.net.Uri? = null,
    sizePx: Int = 288,
) {
    ArtworkImage(
        bitmap = rememberArtworkBitmap(uri, songUri, sizePx = sizePx),
        letter = letter,
        size = size,
        modifier = modifier,
    )
}

@Composable
private fun ArtworkImage(
    bitmap: Bitmap?,
    letter: String,
    size: Dp?,
    modifier: Modifier = Modifier,
) {
    val sized = if (size != null) modifier.size(size) else modifier
    // ETAPA 6: la forma de portada elegida en Ajustes (cuadrada,
    // redondeada o circular) se aplica aquí, en la lista, la mini-barra
    // y el reproductor: es el mismo componente en los tres sitios.
    val corner = RoundedCornerShape(coverShapeToCornerPercent(LocalCoverShape.current))
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = sized.clip(corner),
        )
    } else {
        // Inicial GRANDE sobre el color de acento suave: antes era una
        // letra pequeña gris sobre fondo oscuro, casi invisible y fea.
        BoxWithConstraints(
            modifier = sized
                .clip(corner)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = (maxWidth.value * 0.38f).sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
