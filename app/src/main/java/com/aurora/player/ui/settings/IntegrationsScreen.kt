package com.aurora.player.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import com.aurora.player.data.SoundCloudStore
import com.aurora.player.data.SpotifyStore
import com.aurora.player.model.StreamQuality
import com.aurora.player.online.OnlineTrack
import com.aurora.player.online.SoundCloudUser
import com.aurora.player.online.SpotifyClient
import com.aurora.player.online.SpotifyProfile
import com.aurora.player.online.fetchSoundCloudMe
import com.aurora.player.online.fetchSoundCloudWebClientId
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
    // Ronda 7 (Gemini): el estado de la sesión ("Comprobando…",
    // "Conectado.", "Esa sesión no funcionó…") vivía en Red; ahora
    // se muestra aquí, donde vive Spotify.
    spotifyStatus: String? = null,
    onConnectSpotify: (String) -> Unit,
    onDisconnectSpotify: () -> Unit,
    onImportTracks: (List<Pair<OnlineTrack, String>>) -> Unit,
    ytMusicConnected: Boolean = false,
    onConnectYouTubeMusic: (String) -> Unit = {},
    onDisconnectYouTubeMusic: () -> Unit = {},
    ytMusicCookies: String? = null,
    // SoundCloud (Descubrimiento): sesión con oauth_token por WebView;
    // con ella llegan /me y el feed de seguidos al panel de lanzamientos.
    soundCloudConnected: Boolean = false,
    soundCloudStatus: String? = null,
    onConnectSoundCloud: (String) -> Unit = {},
    onDisconnectSoundCloud: () -> Unit = {},
    // Calidad por conexión (copia BitChord, slice 3): una para
    // Wi-Fi y otra para datos móviles.
    wifiStreamQuality: StreamQuality = StreamQuality.HIGH,
    mobileStreamQuality: StreamQuality = StreamQuality.MEDIUM,
) {
    val context = LocalContext.current
    val spotifyStore = remember { SpotifyStore(context) }
    val cookie by spotifyStore.cookieFlow.collectAsState(initial = "")
    val spotify = remember { SpotifyClient() }
    var profile by remember { mutableStateOf<SpotifyProfile?>(null) }
    // Ronda 7 (OpenCode): si la cookie caducó, el token es null y
    // la sesión está muerta aunque diga "Conectado".
    var spotifySessionChecked by remember { mutableStateOf(false) }
    var showSpotifyLogin by remember { mutableStateOf(false) }
    var showYouTubeLogin by remember { mutableStateOf(false) }
    val soundCloudStore = remember { SoundCloudStore(context) }
    val scToken by soundCloudStore.tokenFlow.collectAsState(initial = "")
    var scProfile by remember { mutableStateOf<SoundCloudUser?>(null) }
    var scSessionChecked by remember { mutableStateOf(false) }
    var showSoundCloudLogin by remember { mutableStateOf(false) }

    // Nombre e imagen de la cuenta cuando hay sesión.
    LaunchedEffect(spotifyConnected, cookie) {
        if (!spotifyConnected || cookie.isBlank()) {
            profile = null
            spotifySessionChecked = false
            return@LaunchedEffect
        }
        spotifySessionChecked = false
        val token = spotify.fetchAccessToken(cookie)
        profile = token?.let { spotify.fetchProfile(it) }
        spotifySessionChecked = true
    }

    // Nombre e imagen de SoundCloud cuando hay sesión (el token se
    // comprueba contra /me; sin client_id web no hay nada que pedir).
    LaunchedEffect(soundCloudConnected, scToken) {
        if (!soundCloudConnected || scToken.isBlank()) {
            scProfile = null
            scSessionChecked = false
            return@LaunchedEffect
        }
        scSessionChecked = false
        val clientId = fetchSoundCloudWebClientId()
        scProfile = clientId?.let { fetchSoundCloudMe(it, scToken) }
        scSessionChecked = true
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
    if (showSoundCloudLogin) {
        SoundCloudLoginDialog(
            onConnect = { onConnectSoundCloud(it) },
            onDismiss = { showSoundCloudLogin = false },
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
        YouTubeMusicIcon(modifier = Modifier.size(48.dp))
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
            SpotifyIcon(modifier = Modifier.size(48.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Spotify", style = MaterialTheme.typography.titleMedium)
            // Ronda 7 (OpenCode): con la sesión caducada no se
            // finge "Conectado"; se dice claro y se vuelve a entrar.
            // (profile es delegado: sin smart-cast, con ?. seguro.)
            val spotifySubtitle = if (!spotifyConnected) "Inicia sesión para ver tus listas"
            else profile?.displayName
                ?: if (spotifySessionChecked) "Sesión caducada. Vuelve a iniciar sesión."
                else "Comprobando la sesión…"
            Text(
                spotifySubtitle,
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
    // SoundCloud abajo: tu cuenta con su foto; con sesión, sus
    // lanzamientos llegan al panel de Nuevos lanzamientos.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scAvatar = rememberRemoteBitmap(scProfile?.avatarUrl)
        if (scAvatar != null) {
            Image(
                bitmap = scAvatar.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape),
            )
        } else {
            SoundCloudIcon(modifier = Modifier.size(48.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text("SoundCloud", style = MaterialTheme.typography.titleMedium)
            // Con la sesión caducada no se finge "Conectado".
            val scSubtitle = if (!soundCloudConnected) "Inicia sesión para ver lo nuevo de tus seguidos"
            else scProfile?.username
                ?: if (scSessionChecked) "Sesión caducada. Vuelve a iniciar sesión."
                else "Comprobando la sesión…"
            Text(
                scSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (soundCloudConnected) {
            TextButton(onClick = onDisconnectSoundCloud) { Text("Desconectar") }
        } else {
            Button(onClick = { showSoundCloudLogin = true }) { Text("Conectar") }
        }
    }
    soundCloudStatus?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    spotifyStatus?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (spotifyConnected) {
        SpotifyPlaylistsSection(
            spotifyConnected = true,
            onImportTracks = onImportTracks,
            ytMusicCookies = ytMusicCookies,
            wifiStreamQuality = wifiStreamQuality,
            mobileStreamQuality = mobileStreamQuality,
        )
    }
}
