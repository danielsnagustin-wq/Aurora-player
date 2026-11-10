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
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Pausar al quitar los auriculares", modifier = Modifier.weight(1f))
        Switch(
            checked = player.autoPauseOnDisconnect,
            onCheckedChange = { onSavePlayer(player.copy(autoPauseOnDisconnect = it)) },
        )
    }
    // Fase 1b: reproducir al conectar auriculares/BT (OFF por
    // defecto). Reanuda si hay una canción cargada en pausa; sin
    // canción no hace nada y si la app se cerró del todo, tampoco.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Reproducir al conectar auriculares o Bluetooth", modifier = Modifier.weight(1f))
        Switch(
            checked = player.autoplayOnConnect,
            onCheckedChange = { onSavePlayer(player.copy(autoplayOnConnect = it)) },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Reanudar la cola al abrir la app", modifier = Modifier.weight(1f))
        Switch(
            checked = player.resumeWhereLeft,
            onCheckedChange = { onSavePlayer(player.copy(resumeWhereLeft = it)) },
        )
    }
    // RONDA 6: saltar silencio (idea de Metrolist) y radio local.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Saltar silencios largos", modifier = Modifier.weight(1f))
        Switch(
            checked = player.skipSilence,
            onCheckedChange = { onSavePlayer(player.copy(skipSilence = it)) },
        )
    }
    SettingsGroupDescription(
        "Salta los silencios de más de 2 segundos: al final de la pista pasa a la siguiente; en mitad, avanza 3 s.",
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Radio local al acabar la cola", modifier = Modifier.weight(1f))
        Switch(
            checked = player.localRadio,
            onCheckedChange = { onSavePlayer(player.copy(localRadio = it)) },
        )
    }
    SettingsGroupDescription(
        "Cuando se acaba la cola y la repetición está apagada, sigue con canciones parecidas de tu biblioteca.",
        modifier = Modifier.padding(top = 4.dp),
    )
}
