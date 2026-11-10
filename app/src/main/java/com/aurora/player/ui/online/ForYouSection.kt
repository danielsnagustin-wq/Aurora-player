package com.aurora.player.ui.online

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.aurora.player.online.ChartArtist
import com.aurora.player.online.ChartPlaylist
import com.aurora.player.online.ChartsResult
import com.aurora.player.util.rememberRemoteBitmap

/**
 * Para ti (Fase 2, 2026-11-10): los charts de YouTube Music —
 * listas de vídeos top y artistas top, globales. Tocar una lista o
 * un artista busca su nombre en YouTube Music (las listas de charts
 * no se pueden abrir por pista sin otro endpoint: la búsqueda es el
 * camino real que ya existe). Sin charts, se dice claro.
 */
@Composable
fun ForYouSection(
    charts: ChartsResult?,
    loading: Boolean,
    error: String?,
    onSearchQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "Para ti",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        if (loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            return
        }
        val result = charts ?: return
        if (result.videoPlaylists.isEmpty() && result.artists.isEmpty()) {
            Text(
                "YouTube Music no devolvió los charts ahora mismo. Prueba más tarde.",
                modifier = Modifier.padding(bottom = 8.dp),
            )
            return
        }
        if (result.videoPlaylists.isNotEmpty()) {
            Text(
                "Top vídeos",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            for (playlist in result.videoPlaylists) {
                ChartPlaylistRow(playlist, onClick = { onSearchQuery(playlist.title) })
            }
        }
        if (result.artists.isNotEmpty()) {
            Text(
                "Artistas top",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            for (artist in result.artists) {
                ChartArtistRow(artist, onClick = { onSearchQuery(artist.title) })
            }
        }
    }
}

@Composable
private fun ChartPlaylistRow(
    playlist: ChartPlaylist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = rememberRemoteBitmap(playlist.thumbnailUrl)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(playlist.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "Lista de YouTube Music",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartArtistRow(
    artist: ChartArtist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = rememberRemoteBitmap(artist.thumbnailUrl)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(artist.title, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    artist.subscribers?.let { "$it suscriptores" },
                    when (artist.trend) {
                        "sube" -> "Sube"
                        "baja" -> "Baja"
                        "igual" -> "Igual"
                        else -> null
                    },
                ).joinToString(" • ").ifBlank { "Artista" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        artist.rank?.let {
            Text(
                "#$it",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
