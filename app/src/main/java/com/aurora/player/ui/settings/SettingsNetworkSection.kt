package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.model.PlayerSettings
import com.aurora.player.ui.theme.auroraButtonColors

/**
 * Sección Red, partida de SettingsScreen (2026-11-09, paso
 * estructural 3c): modo online, orden de proveedores de letras y
 * conexión de Spotify por cookie sp_dc. La lógica de Spotify (el
 * cliente y la tienda) sigue en SettingsScreen; aquí solo llega
 * como callbacks, para que la sección sea solo interfaz.
 */
@Composable
fun SettingsNetworkSection(
    player: PlayerSettings,
    onSavePlayer: (PlayerSettings) -> Unit,
    providerOrder: List<String>,
    onShowProviders: () -> Unit,
    spotifyConnected: Boolean,
    spotifyCookieText: String,
    onSpotifyCookieTextChange: (String) -> Unit,
    spotifyStatus: String?,
    onDisconnectSpotify: () -> Unit,
    onConnectSpotify: () -> Unit,
) {
    // RONDA 7 P2 + 002-online-youtube: modo online. Apagado por
    // defecto: con el interruptor apagado la app no toca la red
    // jamás. Con él encendido: letras online (LRCLIB, solo si no
    // hay .lrc local) y la pestaña Online para escuchar de YouTube.
    SettingsGroupTitle("Modo online")
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Modo online (letras y YouTube)", modifier = Modifier.weight(1f))
        Switch(
            checked = player.onlineMode,
            onCheckedChange = { onSavePlayer(player.copy(onlineMode = it)) },
        )
    }
    SettingsGroupDescription(
        "Usa internet solo para buscar letras cuando no hay .lrc y para escuchar de YouTube. El .lrc local siempre manda. Sin este interruptor, nada sale a la red.",
        modifier = Modifier.padding(top = 4.dp),
    )
    // Fase 2 — proveedores de letras (como BitChord): el primero
    // que encuentre la letra gana; se reordena con las flechas.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Proveedores de letras", modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onShowProviders) {
            Text("Elegir proveedor")
        }
    }
    SettingsGroupDescription(
        "Orden actual: ${providerOrder.joinToString(" → ")}",
        modifier = Modifier.padding(top = 4.dp),
    )
    // Fase 2 — Spotify como BitChord: conexión por cookie sp_dc
    // (sin app registrada ni client_id). Con ella se cargan TUS
    // listas; la música suena por YouTube Music, como BitChord.
    SettingsGroupTitle("Spotify")
    if (spotifyConnected) {
        SettingsGroupDescription(
            "Conectado con tu cookie sp_dc. Tus listas se cargan de Spotify y suenan por YouTube Music.",
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedButton(
            onClick = onDisconnectSpotify,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Desconectar Spotify")
        }
    } else {
        SettingsGroupDescription(
            "1. Entra a open.spotify.com en el navegador e inicia sesión.\n2. Abre las herramientas de desarrollador, Aplicación y Cookies.\n3. Copia el valor de la cookie sp_dc.\n4. Pégala aquí.",
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedTextField(
            value = spotifyCookieText,
            onValueChange = onSpotifyCookieTextChange,
            label = { Text("Cookie sp_dc") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = onConnectSpotify,
            colors = auroraButtonColors(),
            modifier = Modifier.padding(top = 8.dp),
            enabled = spotifyCookieText.isNotBlank(),
        ) {
            Text("Conectar Spotify")
        }
    }
    spotifyStatus?.let {
        SettingsGroupDescription(it, modifier = Modifier.padding(top = 4.dp))
    }
}
