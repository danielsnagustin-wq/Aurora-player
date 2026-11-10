package com.aurora.player.ui.settings

import androidx.compose.runtime.Composable
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.KaraokeMode
import com.aurora.player.ui.design.AuroraOptionChips
import com.aurora.player.ui.design.AuroraSectionHeader
import com.aurora.player.ui.design.AuroraSwitchRow

/**
 * Sección Reproducción, partida de SettingsScreen (2026-11-09,
 * paso estructural 3): pausar al quitar auriculares, reproducir al
 * conectar, reanudar la cola, saltar silencios y radio local. Todo
 * son interruptores sobre PlayerSettings.
 */
@Composable
fun SettingsPlaybackSection(
    player: PlayerSettings,
    onSavePlayer: (PlayerSettings) -> Unit,
) {
    // Fase 2 — modos de karaoke (Apple Music): cuándo se colorean
    // las palabras de la letra. "Siempre" lo lleva también a la
    // vista previa del reproductor; "Solo a pantalla completa" es
    // como se ve hoy; "Apagado" deja solo el color de la línea.
    AuroraSectionHeader(
        title = "Karaoke por palabra",
        description = "Cuándo se colorean las palabras de la letra que suena.",
    )
    AuroraOptionChips(
        options = KaraokeMode.entries.map { it.name to it.label },
        selected = player.karaokeMode.name,
        onSelect = { onSavePlayer(player.copy(karaokeMode = KaraokeMode.valueOf(it))) },
    )
    AuroraSwitchRow(
        title = "Pausar al quitar los auriculares",
        checked = player.autoPauseOnDisconnect,
        onCheckedChange = { onSavePlayer(player.copy(autoPauseOnDisconnect = it)) },
    )
    // Fase 1b: reproducir al conectar auriculares/BT (OFF por
    // defecto). Reanuda si hay una canción cargada en pausa; sin
    // canción no hace nada y si la app se cerró del todo, tampoco.
    AuroraSwitchRow(
        title = "Reproducir al conectar auriculares o Bluetooth",
        checked = player.autoplayOnConnect,
        onCheckedChange = { onSavePlayer(player.copy(autoplayOnConnect = it)) },
    )
    AuroraSwitchRow(
        title = "Reanudar la cola al abrir la app",
        checked = player.resumeWhereLeft,
        onCheckedChange = { onSavePlayer(player.copy(resumeWhereLeft = it)) },
    )
    // RONDA 6: saltar silencio (idea de Metrolist) y radio local.
    AuroraSwitchRow(
        title = "Saltar silencios largos",
        checked = player.skipSilence,
        onCheckedChange = { onSavePlayer(player.copy(skipSilence = it)) },
        description = "Salta los silencios de más de 2 segundos: al final de la pista pasa a la siguiente; en mitad, avanza 3 s.",
    )
    // Fase 2 — Auto-DJ: la "Radio local" de la RONDA 6, con su
    // nombre de siempre en las apps de referencia. El ajuste es el
    // mismo (localRadio): sigue sonando al acabar la cola, ahora
    // eligiendo con el Auto-DJ (parecidas, sin repetir).
    AuroraSwitchRow(
        title = "Auto-DJ",
        checked = player.localRadio,
        onCheckedChange = { onSavePlayer(player.copy(localRadio = it)) },
        description = "Cuando se acaba la cola y la repetición está apagada, sigue sonando con canciones parecidas de tu biblioteca: mismo artista, género o álbum; nunca repite.",
    )
}
