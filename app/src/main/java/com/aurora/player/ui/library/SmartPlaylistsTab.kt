package com.aurora.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.aurora.player.library.SmartPlaylistKind
import com.aurora.player.library.smartPlaylistSongs
import com.aurora.player.model.Song

/**
 * Fase 2 — Listas inteligentes (pestaña "Listas").
 * Cinco listas que se calculan solas desde los datos reales de
 * uso (favoritos, reproducciones, fecha añadida, última escucha).
 * Tocar una lista la pone a sonar desde la primera canción; el
 * botón de mezclar la suena en aleatorio. Una lista vacía se
 * muestra atenuada y no se puede tocar: no hay nada que sonar.
 */
@Composable
fun SmartPlaylistsTab(
    songs: List<Song>,
    onSongClick: (Song, List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) {
        Text(
            "No hay canciones todavía. Añade archivos de música al teléfono y las listas se llenarán solas.",
            modifier = modifier.padding(16.dp),
        )
        return
    }
    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (kind in SmartPlaylistKind.entries) {
            val list = remember(songs, kind) { smartPlaylistSongs(kind, songs) }
            SmartPlaylistRow(
                kind = kind,
                count = list.size,
                onPlay = { if (list.isNotEmpty()) onSongClick(list.first(), list) },
                onShuffle = { if (list.isNotEmpty()) onShuffleAll(list) },
            )
        }
    }
}

private fun smartPlaylistDescription(kind: SmartPlaylistKind): String = when (kind) {
    SmartPlaylistKind.MOST_PLAYED -> "Las que más has escuchado"
    SmartPlaylistKind.FAVORITES -> "Tus canciones con corazón"
    SmartPlaylistKind.RECENTLY_ADDED -> "Lo último que llegó al teléfono"
    SmartPlaylistKind.NEVER_PLAYED -> "Todavía no las has escuchado"
    SmartPlaylistKind.RECENTLY_PLAYED -> "Lo último que sonó"
}

@Composable
private fun SmartPlaylistRow(
    kind: SmartPlaylistKind,
    count: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val empty = count == 0
    Card(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (empty) 0.45f else 1f)
            .clickable(enabled = !empty) { onPlay() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(kind.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    smartPlaylistDescription(kind),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (empty) "Vacía por ahora" else "$count canciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShuffle, enabled = !empty) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Mezclar ${kind.title}")
            }
            IconButton(onClick = onPlay, enabled = !empty) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Sonar ${kind.title}")
            }
        }
    }
}
