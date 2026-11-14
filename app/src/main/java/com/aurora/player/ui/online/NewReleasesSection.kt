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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.data.NewReleaseItem
import com.aurora.player.util.rememberRemoteBitmap

/** "hace X" para la última actualización del panel. Función pura. */
fun formatUpdatedAgo(updatedAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diffMin = ((nowMs - updatedAtMs) / 60_000L).coerceAtLeast(0L)
    return when {
        diffMin < 1L -> "Actualizado ahora mismo"
        diffMin < 60L -> "Actualizado hace $diffMin min"
        diffMin < 60L * 24L -> "Actualizado hace ${diffMin / 60} h"
        else -> "Actualizado hace ${diffMin / (60 * 24)} d"
    }
}

/**
 * Nuevos lanzamientos (Descubrimiento, 2026-11-13, tipo Spotify):
 * lo más nuevo de los artistas que sigues en tus cuentas con sesión
 * (YouTube, Spotify, SoundCloud), en una fila con portada. Tocar un
 * lanzamiento abre sus canciones. Sin ninguna sesión no se muestra:
 * quien lo llama decide eso, esta sección solo pinta lo que le dan.
 */
@Composable
fun NewReleasesSection(
    items: List<NewReleaseItem>,
    updatedAtMs: Long?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenRelease: (NewReleaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Nuevos lanzamientos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                updatedAtMs?.let {
                    Text(
                        formatUpdatedAgo(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualizar lanzamientos")
                }
            }
        }
        if (items.isEmpty() && !loading) {
            Text(
                "Todavía no hay lanzamientos nuevos de tus artistas. Pulsa actualizar para revisar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            return@Column
        }
        LazyRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            // RONDA CAZA 2026-11-13 (P1): la clave era
            // "Spotify:<título>" cuando browseId y trackId son nulos:
            // dos álbumes con el mismo título tumbaban la pestaña
            // Online (key already used). El índice la hace única
            // siempre; la lista se recrea entera en cada refresco.
            itemsIndexed(
                items,
                key = { index, it -> "$index:${it.source}:${it.browseId ?: it.trackId?.toString() ?: it.title}" },
            ) { _, item ->
                ReleaseCard(item = item, onClick = { onOpenRelease(item) })
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    item: NewReleaseItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(140.dp).clickable(onClick = onClick)
            .padding(start = 16.dp),
    ) {
        val bitmap = rememberRemoteBitmap(item.imageUrl)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null)
            }
        }
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            item.artist.ifBlank { item.source },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(item.kind, item.dateText).joinToString(" • ")
        Text(
            if (meta.isBlank()) item.source else "$meta • ${item.source}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
