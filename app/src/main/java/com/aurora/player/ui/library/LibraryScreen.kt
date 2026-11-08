package com.aurora.player.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.data.MusicRepository
import com.aurora.player.model.Song
import com.aurora.player.util.TimeFormat

/**
 * Aurora Player — Biblioteca (Etapa 1).
 * Lista real de MediaStore (canciones). Sin permiso, explica qué falta
 * y no muestra una lista vacía fingiendo que no hay música.
 * Artistas/Álbumes/Carpetas/Géneros: pestañas en Fase 1b; la base de
 * datos (MusicRepository.loadAlbums/loadArtists) ya existe.
 */
@Composable
fun LibraryScreen(hasAudioPermission: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val songs: List<Song> = remember(hasAudioPermission) {
        if (hasAudioPermission) MusicRepository(context).loadSongs() else emptyList()
    }
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (!hasAudioPermission) {
            Text(
                "Aurora Player necesita permiso para leer tus archivos de audio. Sin él no hay biblioteca que mostrar.",
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar título, artista o álbum") },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            singleLine = true,
        )
        Text(
            "${filtered.size} canciones",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (filtered.isEmpty()) {
            // Vacío elegante (PLAN §16): explica qué hacer, sin datos ficticios.
            Text(
                "No hay canciones aquí todavía. Añade archivos de música al teléfono y vuelve a escanear.",
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { song ->
                ListItem(
                    headlineContent = { Text(song.title) },
                    supportingContent = { Text("${song.artist} · ${song.album}") },
                    trailingContent = { Text(TimeFormat.formatDuration(song.durationMs)) },
                )
                Divider()
            }
        }
    }
}
