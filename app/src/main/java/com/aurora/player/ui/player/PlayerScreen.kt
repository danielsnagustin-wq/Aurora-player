package com.aurora.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aurora.player.model.Song
import com.aurora.player.util.TimeFormat

/**
 * Aurora Player — reproductor a pantalla completa, pieza central premium (PLAN §16).
 * Estructura: portada GRANDE arriba, título/artista con jerarquía clara,
 * barra de progreso, controles grandes (play central), cola visible debajo.
 * Letras sincronizadas elegantes + visualizador sutil: Fase 1b (apagados
 * por defecto, no se fingen aquí).
 *
 * Cableado real con MediaController en el siguiente paso de la Etapa 1:
 * los botones de esta pantalla hoy son estructura visual; su onClick se
 * conecta al MediaController del PlaybackService — no se finge estado
 * con variables locales. Ver MANIFIESTO.md.
 */
@Composable
fun PlayerScreen(song: Song?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (song == null) {
            // Vacío elegante (PLAN §16): explica qué hacer, sin datos ficticios.
            Spacer(Modifier.height(64.dp))
            Text("No suena nada todavía", style = MaterialTheme.typography.titleLarge)
            Text(
                "Elige una canción en Biblioteca y sonará aquí.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }
        // Portada grande (24 dp de radio, sin bordes toscos).
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                song.title.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(song.title, style = MaterialTheme.typography.titleLarge)
        Text(
            "${song.artist} · ${song.album}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            TimeFormat.formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Barra de progreso (estructura; el progreso real llega con MediaController).
        Box(
            Modifier.fillMaxWidth().height(4.dp).padding(top = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // Controles grandes: play central. onClick reales en el cableado MediaController.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { /* TODO Etapa 1: MediaController.seekToPrevious() */ }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { /* TODO Etapa 1: MediaController play/pause */ }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", modifier = Modifier.size(56.dp))
            }
            IconButton(onClick = { /* TODO Etapa 1: MediaController.seekToNext() */ }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(36.dp))
            }
        }
        Text(
            "Cola: a continuación (Fase 1b: reordenar y ver qué suena después)",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
