@file:OptIn(ExperimentalFoundationApi::class)
package com.aurora.player.ui.library

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.player.data.MusicRepository
import com.aurora.player.model.Album
import com.aurora.player.model.Artist
import com.aurora.player.model.Song
import com.aurora.player.ui.start.StartScreen
import com.aurora.player.ui.theme.LocalCoverShape
import com.aurora.player.ui.theme.LocalListDensity
import com.aurora.player.ui.theme.coverShapeToCornerPercent
import com.aurora.player.ui.theme.listDensityToRowHeightDp
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.SongDetails
import com.aurora.player.util.buildMetadataUpdate
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
@Composable
fun LibraryScreen(
    hasAudioPermission: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    showStart: Boolean = false,
    onStartFinished: () -> Unit = {},
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
    // ETAPA 5: mantener pulsado una canción abre sus Detalles.
    var detailsSong by remember { mutableStateOf<Song?>(null) }

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
                        onDetails = { detailsSong = it },
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
    duplicateGroups: List<DuplicateGroup>,
    albums: List<Album>,
    onSongClick: (Song, List<Song>) -> Unit,
    onShowDuplicates: () -> Unit,
    onDetails: (Song) -> Unit,
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
                    onDetails = onDetails,
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
    onDetails: (Song) -> Unit,
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
    var editDisc by remember(song) { mutableStateOf(song.discNumber.toString()) }
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
                    editing = false
                    onLibraryChanged()
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
                    DetailRow("Formato", details?.mimeType ?: song.mimeType)
                    DetailRow("Bitrate", details?.bitrateKbps?.let { "$it kbps" })
                    DetailRow("Sample rate", details?.sampleRateHz?.let { "$it Hz" })
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
 * Portada real del álbum vía MediaStore, o la incrustada en el archivo,
 * o la inicial del título. La carga corre en IO; un fallo deja la
 * inicial, nunca se cuelga.
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
            } ?: loadEmbeddedCover(context, song.uri)
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
 * portada desde los metadatos del MediaItem, no desde un Song. Si
 * MediaStore no da portada, se prueba la incrustada en el archivo
 * ([songUri]); si tampoco hay, la inicial grande.
 */
@Composable
fun ArtworkImage(
    uri: android.net.Uri?,
    letter: String,
    size: Dp? = null,
    modifier: Modifier = Modifier,
    songUri: android.net.Uri? = null,
) {
    val context = LocalContext.current
    var bitmap by remember(uri, songUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, songUri) {
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.loadThumbnail(uri, Size(288, 288), null)
                } catch (_: Exception) {
                    null
                }
            } ?: songUri?.let { loadEmbeddedCover(context, it) }
        } else if (songUri != null) {
            bitmap = loadEmbeddedCover(context, songUri)
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
