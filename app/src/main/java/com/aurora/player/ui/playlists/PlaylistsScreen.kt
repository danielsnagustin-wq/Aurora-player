package com.aurora.player.ui.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Aurora Player — Playlists (Etapa 1, base).
 * CRUD real con Room (AuroraDatabase.PlaylistDao) + export M3U
 * (util/M3u) en el siguiente paso de la Etapa 1. Import M3U en Etapa 1;
 * XSPF/WPL/PLS en Fase 1b. Favoritos = playlist especial en Room.
 */
@Composable
fun PlaylistsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Playlists")
        Text("Crea tu primera playlist. En esta Etapa 1 las playlists se guardan en el teléfono y se pueden exportar como M3U.")
    }
}
