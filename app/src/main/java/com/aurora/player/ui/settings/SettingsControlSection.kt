package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.model.PlayerSettings

/**
 * Sección Control, partida de SettingsScreen (2026-11-09, paso
 * estructural 3): los gestos del reproductor, cada uno con su
 * interruptor real (antes vivían muertos en el modelo).
 */
@Composable
fun SettingsControlSection(
    player: PlayerSettings,
    onSavePlayer: (PlayerSettings) -> Unit,
) {
    // AUDITORÍA (2026-11-09): los gestos del reproductor existían
    // pero no se podían apagar; sus ajustes vivían muertos en el
    // modelo. Ahora cada gesto tiene su interruptor real.
    SettingsGroupTitle("Gestos")
    SettingsGroupDescription("Lo que hace la portada del reproductor al tocarla.")
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Deslizar la portada cambia de pista", modifier = Modifier.weight(1f))
        Switch(
            checked = player.swipeToChangeTrack,
            onCheckedChange = { onSavePlayer(player.copy(swipeToChangeTrack = it)) },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Doble toque en la portada marca favorito", modifier = Modifier.weight(1f))
        Switch(
            checked = player.doubleTapToFavorite,
            onCheckedChange = { onSavePlayer(player.copy(doubleTapToFavorite = it)) },
        )
    }
    // Fase 1b: agitar para pasar a la siguiente (OFF por defecto)
    // y deslizar vertical sobre la portada para el volumen.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Agitar el teléfono pasa a la siguiente", modifier = Modifier.weight(1f))
        Switch(
            checked = player.shakeToNext,
            onCheckedChange = { onSavePlayer(player.copy(shakeToNext = it)) },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Deslizar vertical sobre la portada cambia el volumen", modifier = Modifier.weight(1f))
        Switch(
            checked = player.volumeGesture,
            onCheckedChange = { onSavePlayer(player.copy(volumeGesture = it)) },
        )
    }
}
