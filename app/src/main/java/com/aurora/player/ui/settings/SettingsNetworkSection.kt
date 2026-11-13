package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.StreamQuality
import com.aurora.player.ui.design.AuroraOptionChips
import com.aurora.player.ui.design.AuroraSectionHeader
import com.aurora.player.ui.design.AuroraSwitchRow

/**
 * Sección Red, partida de SettingsScreen (2026-11-09, paso
 * estructural 3c): modo online, calidad de streaming y orden de
 * proveedores de letras. Spotify vive solo en Cuenta e
 * integraciones (ronda 7: estaba duplicado aquí).
 */
@Composable
fun SettingsNetworkSection(
    player: PlayerSettings,
    onSavePlayer: (PlayerSettings) -> Unit,
) {
    // RONDA 7 P2 + 002-online-youtube: modo online. Apagado por
    // defecto: con el interruptor apagado la app no toca la red
    // jamás. Con él encendido: letras online (LRCLIB, solo si no
    // hay .lrc local) y la pestaña Online para escuchar de YouTube.
    AuroraSectionHeader(title = "Modo online")
    AuroraSwitchRow(
        title = "Modo online (letras y YouTube)",
        checked = player.onlineMode,
        onCheckedChange = { onSavePlayer(player.copy(onlineMode = it)) },
        description = "Usa internet solo para buscar letras cuando no hay .lrc y para escuchar de YouTube. El .lrc local siempre manda. Sin este interruptor, nada sale a la red.",
    )
    // Calidad de streaming (BitChord, 2026-11-10): Alta es lo
    // mejor que da YouTube (Opus ~160 kbps); no hay tier sin
    // perdida porque YouTube no lo ofrece. El coste en MB/h es
    // el real del bitrate.
    // Calidad por conexión (slice 3): una para Wi-Fi y otra para
    // datos móviles; la app usa la que toque al reproducir.
    AuroraSectionHeader(title = "Calidad de streaming")
    SettingsGroupDescription(
        "Cuanto suena cada hora al escuchar de YouTube, según la conexión.",
    )
    Text(
        "Con Wi-Fi",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    SettingsGroupDescription(player.wifiStreamQuality.description)
    AuroraOptionChips(
        options = StreamQuality.entries.map { it.name to it.label },
        selected = player.wifiStreamQuality.name,
        onSelect = { name ->
            StreamQuality.entries.find { it.name == name }?.let {
                onSavePlayer(player.copy(wifiStreamQuality = it))
            }
        },
    )
    Text(
        "Con datos móviles",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    SettingsGroupDescription(player.mobileStreamQuality.description)
    AuroraOptionChips(
        options = StreamQuality.entries.map { it.name to it.label },
        selected = player.mobileStreamQuality.name,
        onSelect = { name ->
            StreamQuality.entries.find { it.name == name }?.let {
                onSavePlayer(player.copy(mobileStreamQuality = it))
            }
        },
    )
    // Letras automáticas (2026-11-10, como BitChord): el orden
    // LRCLIB → BetterLyrics → BiniLyrics es fijo y el primero que
    // encuentre la letra gana. Sin hoja para elegir proveedor.
    // Spotify vive solo en Cuenta e integraciones.
}
