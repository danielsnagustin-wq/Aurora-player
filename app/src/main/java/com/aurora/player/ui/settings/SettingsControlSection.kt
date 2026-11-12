package com.aurora.player.ui.settings

import androidx.compose.runtime.Composable
import com.aurora.player.model.GestureAction
import com.aurora.player.model.PlayerSettings
import com.aurora.player.ui.design.AuroraOptionChips
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
        title = "Doble toque en la portada",
        checked = player.doubleTapToFavorite,
        onCheckedChange = { onSavePlayer(player.copy(doubleTapToFavorite = it)) },
        description = "Qué hace el doble toque sobre la portada.",
    )
    // Fase 2 — gestos configurables: qué hace el doble toque.
    // Solo se muestra con el gesto encendido, para no ser un adorno.
    if (player.doubleTapToFavorite) {
        AuroraOptionChips(
            options = GestureAction.entries.map { it.name to it.label },
            selected = player.doubleTapAction,
            onSelect = { onSavePlayer(player.copy(doubleTapAction = it)) },
        )
    }
    // Fase 1b: agitar para pasar a la siguiente (OFF por defecto)
    // y deslizar vertical sobre la portada para el volumen.
    AuroraSwitchRow(
        title = "Agitar el teléfono",
        checked = player.shakeToNext,
        onCheckedChange = { onSavePlayer(player.copy(shakeToNext = it)) },
        description = "Qué hace agitar el teléfono mientras suena.",
    )
    // Fase 2 — gestos configurables: qué hace agitar.
    if (player.shakeToNext) {
        AuroraOptionChips(
            options = GestureAction.entries.map { it.name to it.label },
            selected = player.shakeAction,
            onSelect = { onSavePlayer(player.copy(shakeAction = it)) },
        )
    }
}
