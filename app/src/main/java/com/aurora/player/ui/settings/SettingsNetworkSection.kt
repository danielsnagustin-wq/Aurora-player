package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.model.PlayerSettings
import com.aurora.player.ui.design.AuroraSectionHeader
import com.aurora.player.ui.design.AuroraSwitchRow
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
    onConnectSpotify: (String) -> Unit,
) {
    // Inicio de sesión con Spotify dentro de la app (como BitChord,
    // 2026-11-10): nada de copiar la cookie a mano.
    var showSpotifyLogin by remember { mutableStateOf(false) }
    LaunchedEffect(spotifyConnected) {
        if (spotifyConnected) showSpotifyLogin = false
    }
    if (showSpotifyLogin) {
        SpotifyLoginDialog(
            onConnect = onConnectSpotify,
            onDismiss = { showSpotifyLogin = false },
        )
    }
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
    // Fase 2 — Spotify como BitChord: se inicia sesión con la
    // cuenta dentro de la app y Aurora guarda la sesión sola. Con
    // ella se cargan TUS listas; la música suena por YouTube Music.
    AuroraSectionHeader(title = "Spotify")
    if (spotifyConnected) {
        SettingsGroupDescription(
            "Conectado con tu cuenta de Spotify. Tus listas se cargan de Spotify y suenan por YouTube Music.",
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
            "Inicia sesión con tu cuenta de Spotify y Aurora guarda la sesión en tu teléfono. Sin copiar nada a mano.",
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = { showSpotifyLogin = true },
            colors = auroraButtonColors(),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Iniciar sesión con Spotify")
        }
        // Respaldo manual, por si el inicio dentro de la app falla.
        OutlinedTextField(
            value = spotifyCookieText,
            onValueChange = onSpotifyCookieTextChange,
            label = { Text("Cookie sp_dc (solo si el inicio falla)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Button(
            onClick = { onConnectSpotify(spotifyCookieText) },
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
