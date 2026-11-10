package com.aurora.player.ui.settings

import androidx.compose.runtime.Composable
import com.aurora.player.model.PlayerSettings
import com.aurora.player.ui.design.AuroraSectionHeader
import com.aurora.player.ui.design.AuroraSwitchRow

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
    AuroraSectionHeader(
        title = "Gestos",
        description = "Lo que hace la portada del reproductor al tocarla.",
    )
    AuroraSwitchRow(
        title = "Deslizar la portada cambia de pista",
        checked = player.swipeToChangeTrack,
        onCheckedChange = { onSavePlayer(player.copy(swipeToChangeTrack = it)) },
    )
    AuroraSwitchRow(
        title = "Doble toque en la portada marca favorito",
        checked = player.doubleTapToFavorite,
        onCheckedChange = { onSavePlayer(player.copy(doubleTapToFavorite = it)) },
    )
    // Fase 1b: agitar para pasar a la siguiente (OFF por defecto)
    // y deslizar vertical sobre la portada para el volumen.
    AuroraSwitchRow(
        title = "Agitar el teléfono pasa a la siguiente",
        checked = player.shakeToNext,
        onCheckedChange = { onSavePlayer(player.copy(shakeToNext = it)) },
    )
    AuroraSwitchRow(
        title = "Deslizar vertical sobre la portada cambia el volumen",
        checked = player.volumeGesture,
        onCheckedChange = { onSavePlayer(player.copy(volumeGesture = it)) },
    )
}
