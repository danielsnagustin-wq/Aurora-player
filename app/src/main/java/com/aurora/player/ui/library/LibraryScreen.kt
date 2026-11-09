package com.aurora.player.ui.library

import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.player.data.MusicRepository
import com.aurora.player.model.Song
import com.aurora.player.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aurora Player — Biblioteca (Etapa 1, cableada).
 * La lista viene del LibraryViewModel (escaneo en IO). Tocar una
 * canción la reproduce (onSongClick). Cada fila lleva su portada real
 * de MediaStore; sin portada, la inicial del título, nunca la de otra
 * canción.
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
                Text(
                    "${filtered.size} canciones",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (filtered.isEmpty()) {
                    Text(
                        "Nada coincide con la búsqueda.",
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { song ->
                            ListItem(
                                headlineContent = { Text(song.title) },
                                supportingContent = { Text("${song.artist} · ${song.album}") },
                                trailingContent = {
                                    Text(TimeFormat.formatDuration(song.durationMs))
                                },
                                leadingContent = { AlbumCover(song, size = 56.dp) },
                                modifier = Modifier.clickable {
                                    onSongClick(song, filtered)
                                },
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
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
