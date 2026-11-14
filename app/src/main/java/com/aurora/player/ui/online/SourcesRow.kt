package com.aurora.player.ui.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Fuentes (Fase 2, 2026-11-10, como BitChord): de dónde sale la
 * música. Cada chip muestra su estado real y hace algo real:
 * Local abre la pestaña Canciones; YouTube Music pone el foco en
 * la búsqueda; Spotify muestra u oculta tus listas (solo conectado);
 * SoundCloud muestra u oculta sus lanzamientos (solo conectado:
 * el inicio de sesión y el descubrimiento ya existen).
 */
@Composable
fun SourcesRow(
    localCount: Int,
    ytMusicConnected: Boolean,
    spotifyConnected: Boolean,
    spotifySectionVisible: Boolean,
    soundCloudConnected: Boolean,
    soundCloudSectionVisible: Boolean,
    onShowLocal: () -> Unit,
    onFocusSearch: () -> Unit,
    onToggleSpotify: () -> Unit,
    onToggleSoundCloud: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceChip(
            label = "Local · $localCount",
            onClick = onShowLocal,
        )
        SourceChip(
            label = if (ytMusicConnected) "YouTube Music · Conectado"
            else "YouTube Music · Anónimo",
            onClick = onFocusSearch,
        )
        SourceChip(
            label = if (spotifyConnected) {
                if (spotifySectionVisible) "Spotify · Listas visibles"
                else "Spotify · Listas ocultas"
            } else "Spotify · No conectado",
            onClick = if (spotifyConnected) onToggleSpotify else null,
        )
        SourceChip(
            label = if (soundCloudConnected) {
                if (soundCloudSectionVisible) "SoundCloud · Lanzamientos visibles"
                else "SoundCloud · Lanzamientos ocultos"
            } else "SoundCloud · No conectado",
            onClick = if (soundCloudConnected) onToggleSoundCloud else null,
        )
    }
}

@Composable
private fun SourceChip(
    label: String,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
