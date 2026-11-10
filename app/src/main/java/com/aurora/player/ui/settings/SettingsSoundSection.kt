package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.audio.directModeStatusLine
import com.aurora.player.audio.isDirectMode
import com.aurora.player.model.PlayerSettings
import com.aurora.player.ui.design.AuroraOptionChips
import com.aurora.player.ui.design.AuroraSectionHeader
import com.aurora.player.ui.design.AuroraSliderRow
import com.aurora.player.ui.design.AuroraSwitchRow
import com.aurora.player.ui.theme.auroraButtonColors

/**
 * Sección Motor de sonido, partida de SettingsScreen (2026-11-09,
 * paso estructural 3b): ecualizador, crossfade, balance y
 * ReplayGain. Los deslizadores guardan al soltar la barra, no a
 * cada paso del arrastre. Usa FlowRow: lleva su propio
 * @OptIn(ExperimentalLayoutApi::class) — lección del #233, el
 * opt-in no viaja solo al extraer.
 */
@Composable
fun SettingsSoundSection(
    player: PlayerSettings,
    onSavePlayer: (PlayerSettings) -> Unit,
    onOpenEq: () -> Unit,
) {
    // ETAPA 4: reproducción real (crossfade con efecto, auto-pausa y
    // reanudar la cola). Todo se guarda al tocar, como el tema.
    AuroraSectionHeader(
        title = "Reproducción",
        description = "Cómo suena y qué pasa al quitar los auriculares o al reabrir la app.",
    )
    Button(
        onClick = onOpenEq,
        colors = auroraButtonColors(),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text("Abrir ecualizador")
    }
    // El crossfade se guarda al soltar la barra, no a cada paso del
    // arrastre (antes escribía en DataStore decenas de veces por
    // arrastre y el servicio reaccionaba a cada una).
    var crossfade by remember(player.crossfadeSeconds) {
        mutableStateOf(player.crossfadeSeconds.toFloat())
    }
    AuroraSliderRow(
        label = "Crossfade: ${crossfade.toInt()} s (0 = apagado)",
        value = crossfade,
        onValueChange = { crossfade = it },
        onValueChangeFinished = {
            onSavePlayer(player.copy(crossfadeSeconds = crossfade.toInt()))
        },
        valueRange = 0f..12f,
        steps = 11,
    )
    // Fase 1b: no solapar dentro del mismo álbum (los discos
    // continuos se rompen si se solapan dos pistas pegadas).
    AuroraSwitchRow(
        title = "No solapar dentro del mismo álbum",
        checked = player.crossfadeGaplessAlbumException,
        onCheckedChange = { onSavePlayer(player.copy(crossfadeGaplessAlbumException = it)) },
    )
    // SPEC 005 FR-001: balance izquierda/derecha. Se guarda al
    // soltar la barra, como el crossfade.
    var balance by remember(player.channelBalance) {
        mutableStateOf(player.channelBalance)
    }
    AuroraSliderRow(
        label = "Balance: " + when {
            balance == 0f -> "centro"
            balance < 0f -> "izquierda ${(-balance * 100).toInt()}%"
            else -> "derecha ${(balance * 100).toInt()}%"
        },
        value = balance,
        onValueChange = { balance = it },
        onValueChangeFinished = {
            onSavePlayer(player.copy(channelBalance = balance))
        },
        valueRange = -1f..1f,
        steps = 19,
    )
    // RONDA 6: ReplayGain de verdad (antes el ajuste existía en
    // el modelo pero nada lo aplicaba). Usa la etiqueta ReplayGain
    // del archivo: sin etiqueta, no cambia el volumen.
    AuroraSectionHeader(
        title = "ReplayGain (normalización de volumen)",
        description = "Iguala el volumen entre canciones con la etiqueta ReplayGain del archivo. Sin etiqueta, no cambia nada.",
    )
    AuroraOptionChips(
        options = listOf(
            "off" to "Apagado",
            "track" to "Por canción",
            "album" to "Por álbum",
            "smart" to "Inteligente",
        ),
        selected = player.replayGainMode,
        onSelect = { onSavePlayer(player.copy(replayGainMode = it)) },
    )
    if (player.replayGainMode != "off") {
        var preamp by remember(player.replayGainPreAmpDb) {
            mutableStateOf(player.replayGainPreAmpDb)
        }
        AuroraSliderRow(
            label = "Preamp: ${if (preamp >= 0) "+" else ""}${preamp.toInt()} dB",
            value = preamp,
            onValueChange = { preamp = it },
            onValueChangeFinished = {
                onSavePlayer(player.copy(replayGainPreAmpDb = preamp))
            },
            valueRange = -12f..12f,
            steps = 23,
        )
    }
    // Fase 2, Salida Directa: la app no toca el audio. Sin formato
    // de pista a mano aquí, la línea es la genérica honesta.
    AuroraSectionHeader(
        title = "Salida de audio",
        description = "Directa apaga ReplayGain, silencio, balance, EQ del sistema y crossfade, y deja el volumen a 1.0.",
    )
    AuroraOptionChips(
        options = listOf(
            "standard" to "Estándar",
            "direct" to "Directa",
        ),
        selected = player.audioOutputMode,
        onSelect = { onSavePlayer(player.copy(audioOutputMode = it)) },
    )
    Text(
        directModeStatusLine(isDirectMode(player.audioOutputMode), null, null),
        modifier = Modifier.padding(top = 8.dp),
    )
}
