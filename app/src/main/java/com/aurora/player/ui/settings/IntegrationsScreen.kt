package com.aurora.player.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.data.SpotifyStore
import com.aurora.player.model.StreamQuality
import com.aurora.player.online.OnlineTrack
import com.aurora.player.online.SpotifyClient
import com.aurora.player.online.SpotifyProfile
import com.aurora.player.ui.design.AuroraSectionHeader
import com.aurora.player.ui.online.SpotifyPlaylistsSection
import com.aurora.player.util.rememberRemoteBitmap

/**
 * Cuenta e integraciones (2026-11-10, como BitChord): la cuenta de
 * YouTube Music arriba y Spotify abajo, con tus listas y sus
 * portadas. YouTube Music se conecta con tu cuenta de Google en un
 * WebView (las cookies solo quedan en tu teléfono); Spotify se
 * conecta con la cookie sp_dc, como BitChord, y la reproducción es
 * por YouTube.
 */
@Composable
fun IntegrationsScreen(
    spotifyConnected: Boolean,
    onConnectSpotify: (String) -> Unit,
    onDisconnectSpotify: () -> Unit,
    onImportTracks: (List<Pair<OnlineTrack, String>>) -> Unit,
    ytMusicConnected: Boolean = false,
    onConnectYouTubeMusic: (String) -> Unit = {},
    onDisconnectYouTubeMusic: () -> Unit = {},
    ytMusicCookies: String? = null,
    streamQuality: StreamQuality = StreamQuality.HIGH,
) {
    val context = LocalContext.current
    val spotifyStore = remember { SpotifyStore(context) }
    val cookie by spotifyStore.cookieFlow.collectAsState(initial = "")
    val spotify = remember { SpotifyClient() }
    var profile by remember { mutableStateOf<SpotifyProfile?>(null) }
    var showSpotifyLogin by remember { mutableStateOf(false) }
    var showYouTubeLogin by remember { mutableStateOf(false) }

    // Nombre e imagen de la cuenta cuando hay sesión.
    LaunchedEffect(spotifyConnected, cookie) {
        if (!spotifyConnected || cookie.isBlank()) {
            profile = null
            return@LaunchedEffect
        }
        val token = spotify.fetchAccessToken(cookie)
        profile = token?.let { spotify.fetchProfile(it) }
    }

    if (showSpotifyLogin) {
        SpotifyLoginDialog(
            onConnect = { onConnectSpotify(it) },
            onDismiss = { showSpotifyLogin = false },
        )
    }
    if (showYouTubeLogin) {
        YouTubeMusicLoginDialog(
            onConnect = { onConnectYouTubeMusic(it) },
            onDismiss = { showYouTubeLogin = false },
        )
    }

    AuroraSectionHeader(
        title = "Cuenta e integraciones",
        description = "Tus cuentas conectadas. Todo queda solo en tu teléfono.",
    )
    // YouTube Music arriba (BitChord): inicio de sesión real con
    // tu cuenta de Google; la búsqueda pasa a ser la de tu cuenta.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text("YouTube Music", style = MaterialTheme.typography.titleMedium)
            Text(
                if (ytMusicConnected) "Conectado"
                else "Inicia sesión para buscar con tu cuenta",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ytMusicConnected) {
            TextButton(onClick = onDisconnectYouTubeMusic) { Text("Desconectar") }
        } else {
            Button(onClick = { showYouTubeLogin = true }) { Text("Conectar") }
        }
    }
    // Spotify abajo: tu cuenta con su foto, y tus listas con portadas.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatar = rememberRemoteBitmap(profile?.imageUrl)
        if (avatar != null) {
            Image(
                bitmap = avatar.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Spotify", style = MaterialTheme.typography.titleMedium)
            Text(
                if (spotifyConnected) profile?.displayName ?: "Conectado"
                else "Inicia sesión para ver tus listas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (spotifyConnected) {
            TextButton(onClick = onDisconnectSpotify) { Text("Desconectar") }
        } else {
            Button(onClick = { showSpotifyLogin = true }) { Text("Conectar") }
        }
    }
    if (spotifyConnected) {
        SpotifyPlaylistsSection(
            spotifyConnected = true,
            onImportTracks = onImportTracks,
            ytMusicCookies = ytMusicCookies,
            streamQuality = streamQuality,
        )
    }
}
