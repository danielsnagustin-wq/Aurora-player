package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.model.PlayerSettings
import com.aurora.player.ui.theme.auroraButtonColors

/**
 * Sección Motor de sonido, partida de SettingsScreen (2026-11-09,
 * paso estructural 3b): ecualizador, crossfade, balance y
 * ReplayGain. Los deslizadores guardan al soltar la barra, no a
 * cada paso del arrastre. Usa FlowRow: lleva su propio
 * @OptIn(ExperimentalLayoutApi::class) — lección del #233, el
 * opt-in no viaja solo al extraer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSoundSection(
    player: PlayerSettings,
    onSavePlayer: (PlayerSettings) -> Unit,
    onOpenEq: () -> Unit,
) {
    // ETAPA 4: reproducción real (crossfade con efecto, auto-pausa y
    // reanudar la cola). Todo se guarda al tocar, como el tema.
    SettingsGroupTitle("Reproducción")
    SettingsGroupDescription("Cómo suena y qué pasa al quitar los auriculares o al reabrir la app.")
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
    Text(
        "Crossfade: ${crossfade.toInt()} s (0 = apagado)",
        modifier = Modifier.padding(top = 8.dp),
    )
    Slider(
        value = crossfade,
        onValueChange = { crossfade = it },
        onValueChangeFinished = {
            onSavePlayer(player.copy(crossfadeSeconds = crossfade.toInt()))
        },
        valueRange = 0f..12f,
        steps = 11,
        modifier = Modifier.fillMaxWidth(),
    )
    // Fase 1b: no solapar dentro del mismo álbum (los discos
    // continuos se rompen si se solapan dos pistas pegadas).
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("No solapar dentro del mismo álbum", modifier = Modifier.weight(1f))
        Switch(
            checked = player.crossfadeGaplessAlbumException,
            onCheckedChange = { onSavePlayer(player.copy(crossfadeGaplessAlbumException = it)) },
        )
    }
    // SPEC 005 FR-001: balance izquierda/derecha. Se guarda al
    // soltar la barra, como el crossfade.
    var balance by remember(player.channelBalance) {
        mutableStateOf(player.channelBalance)
    }
    Text(
        "Balance: " + when {
            balance == 0f -> "centro"
            balance < 0f -> "izquierda ${(-balance * 100).toInt()}%"
            else -> "derecha ${(balance * 100).toInt()}%"
        },
        modifier = Modifier.padding(top = 8.dp),
    )
    Slider(
        value = balance,
        onValueChange = { balance = it },
        onValueChangeFinished = {
            onSavePlayer(player.copy(channelBalance = balance))
        },
        valueRange = -1f..1f,
        steps = 19,
        modifier = Modifier.fillMaxWidth(),
    )
    // RONDA 6: ReplayGain de verdad (antes el ajuste existía en
    // el modelo pero nada lo aplicaba). Usa la etiqueta ReplayGain
    // del archivo: sin etiqueta, no cambia el volumen.
    SettingsGroupTitle("ReplayGain (normalización de volumen)")
    SettingsGroupDescription("Iguala el volumen entre canciones con la etiqueta ReplayGain del archivo. Sin etiqueta, no cambia nada.")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((mode, label) in listOf(
            "off" to "Apagado",
            "track" to "Por canción",
            "album" to "Por álbum",
            "smart" to "Inteligente",
        )) {
            if (player.replayGainMode == mode) {
                Button(
                    onClick = { onSavePlayer(player.copy(replayGainMode = mode)) },
                    colors = auroraButtonColors(),
                ) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSavePlayer(player.copy(replayGainMode = mode)) }) {
                    Text(label)
                }
            }
        }
    }
    if (player.replayGainMode != "off") {
        var preamp by remember(player.replayGainPreAmpDb) {
            mutableStateOf(player.replayGainPreAmpDb)
        }
        Text(
            "Preamp: ${if (preamp >= 0) "+" else ""}${preamp.toInt()} dB",
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = preamp,
            onValueChange = { preamp = it },
            onValueChangeFinished = {
                onSavePlayer(player.copy(replayGainPreAmpDb = preamp))
            },
            valueRange = -12f..12f,
            steps = 23,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
