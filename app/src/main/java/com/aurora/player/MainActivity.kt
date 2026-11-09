package com.aurora.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.room.Room
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.PlayHistoryEntity
import com.aurora.player.data.PlayerSettings
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.QueueStore
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.model.Song
import com.aurora.player.model.ThemeSettings
import com.aurora.player.playback.PlaybackService
import com.aurora.player.playback.PlayerControls
import com.aurora.player.ui.eq.EqScreen
import com.aurora.player.ui.library.ArtworkImage
import com.aurora.player.ui.library.LibraryScreen
import com.aurora.player.ui.player.PlayerScreen
import com.aurora.player.ui.playlists.PlaylistsScreen
import com.aurora.player.ui.settings.SettingsScreen
import com.aurora.player.ui.theme.AuroraPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aurora Player — MainActivity (Etapa 1, cableada).
 * Navegación inferior: Biblioteca / Playlists / Ajustes.
 * El MediaController vive aquí: se conecta al PlaybackService y es
 * el único canal entre la UI y el ExoPlayer. Tocar una canción
 * reproduce de verdad; la mini-barra abre el reproductor completo.
 * Permiso: solo audio (+ notificación en 13+). Sin internet, sin cuenta.
 */
class MainActivity : ComponentActivity() {

    private var hasAudioPermission by mutableStateOf(false)

    private val audioPermissionName: String
        get() = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // Solo el permiso de audio decide si hay biblioteca. Negar la
        // notificación no debe dejar la app sin música.
        hasAudioPermission = grants[audioPermissionName] == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasAudioPermission = audioPermissionGranted()
        setContent {
            val themeStore = ThemeSettingsStore(applicationContext)
            val themeSettings by themeStore.settingsFlow.collectAsState(
                initial = ThemeSettings.DEFAULT,
            )
            AuroraPlayerTheme(settings = themeSettings) {
                if (hasAudioPermission) {
                    AuroraApp(hasAudioPermission = hasAudioPermission)
                } else {
                    WelcomeScreen(
                        onGrant = {
                            val perms = mutableListOf(audioPermissionName)
                            if (Build.VERSION.SDK_INT >= 33) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                    )
                }
            }
        }
    }

    private fun audioPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            audioPermissionName,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private enum class Tab(val label: String) {
    LIBRARY("Biblioteca"),
    PLAYLISTS("Playlists"),
    SETTINGS("Ajustes"),
}

/** Construye el MediaItem de una canción (metadatos + portada real). */
private fun songToMediaItem(context: android.content.Context, song: Song): MediaItem =
    MediaItem.Builder()
        .setUri(song.uri)
        .setMediaId(song.id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(MusicRepository(context).albumArtUri(song.albumId))
                .build(),
        )
        .build()

/** Pantalla de inicio: qué es la app y por qué pide el permiso. */
@Composable
private fun WelcomeScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Aurora Player",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Tu música, sin anuncios ni cuentas",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Para mostrar tus canciones necesitamos acceso a los archivos de audio de tu teléfono. Todo queda en tu teléfono, nada se sube a ningún servidor.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        ) {
            Text("Permitir acceso a la música")
        }
        Text(
            "Sin internet · Sin anuncios · Gratis siempre",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun AuroraApp(hasAudioPermission: Boolean) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.LIBRARY) }
    var showPlayer by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf<String?>(null) }
    var currentArtist by remember { mutableStateOf<String?>(null) }
    var currentAlbum by remember { mutableStateOf<String?>(null) }
    var currentArtwork by remember { mutableStateOf<android.net.Uri?>(null) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }

    // ETAPA 4: velocidad, cola visible, historial (30 s) y cola persistente.
    val scope = rememberCoroutineScope()
    val playerStore = remember { PlayerSettingsStore(context) }
    val playerSettings by playerStore.settingsFlow.collectAsState(initial = PlayerSettings.DEFAULT)
    val queueStore = remember { QueueStore(context) }
    val db = remember {
        Room.databaseBuilder(context, AuroraDatabase::class.java, "aurora-player.db").build()
    }
    var speed by remember { mutableStateOf(1f) }
    var queueTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    var queueIndex by remember { mutableStateOf(0) }
    var listenedMs by remember { mutableStateOf(0L) }
    var listenedId by remember { mutableStateOf<Long?>(null) }
    var historyDone by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var queueRestored by remember { mutableStateOf(false) }

    /** Historial real: una pista cuenta cuando suena 30 s o más. */
    fun finalizeHistory() {
        val id = listenedId ?: return
        val ms = listenedMs
        if (ms < 30_000L || id in historyDone) return
        historyDone = historyDone + id
        scope.launch(Dispatchers.IO) {
            db.libraryDao().addHistory(PlayHistoryEntity(id, System.currentTimeMillis(), ms))
        }
    }

    DisposableEffect(Unit) {
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        val listener = object : Player.Listener {
            private fun refresh(c: MediaController) {
                val meta = c.currentMediaItem?.mediaMetadata
                currentTitle = meta?.title?.toString()
                currentArtist = meta?.artist?.toString()
                currentAlbum = meta?.albumTitle?.toString()
                currentArtwork = meta?.artworkUri
                isPlaying = c.isPlaying
                shuffleEnabled = c.shuffleModeEnabled
                repeatMode = c.repeatMode
                positionMs = c.currentPosition
                durationMs = c.duration
                queueTitles = (0 until c.mediaItemCount).map {
                    c.getMediaItemAt(it).mediaMetadata.title?.toString() ?: ""
                }
                queueIndex = c.currentMediaItemIndex
                // Cola persistente: se guarda en cada cambio de pista.
                scope.launch {
                    queueStore.save(
                        (0 until c.mediaItemCount).mapNotNull {
                            c.getMediaItemAt(it).mediaId.toLongOrNull()
                        },
                        c.currentMediaItemIndex,
                        c.currentPosition,
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                finalizeHistory()
                controller?.let { refresh(it) }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) finalizeHistory()
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                shuffleEnabled = enabled
            }

            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }

            override fun onPlaybackStateChanged(state: Int) {
                controller?.let { refresh(it) }
            }
        }
        future.addListener(
            {
                try {
                    val c = future.get()
                    c.addListener(listener)
                    controller = c
                    listener.onMediaItemTransition(c.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
                } catch (_: Exception) {
                    controller = null
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller?.removeListener(listener)
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    // Reloj de progreso: actualiza la barra mientras suena.
    LaunchedEffect(controller, isPlaying) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            positionMs = c.currentPosition
            durationMs = c.duration
            if (c.isPlaying) {
                val id = c.currentMediaItem?.mediaId?.toLongOrNull()
                if (id != null && id != listenedId) {
                    listenedId = id
                    listenedMs = 0L
                } else if (id != null) {
                    listenedMs += 500L
                }
            }
            delay(500)
        }
    }

    // Velocidad real (ETAPA 4): se aplica al controlador cuando cambia
    // en Ajustes o en el reproductor, y se guarda en DataStore.
    LaunchedEffect(playerSettings.playbackSpeed, controller) {
        val c = controller ?: return@LaunchedEffect
        PlayerControls.setSpeed(c, playerSettings.playbackSpeed)
        speed = playerSettings.playbackSpeed
    }

    fun cycleSpeed() {
        val next = when (speed) {
            0.5f -> 0.75f
            0.75f -> 1f
            1f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2f
            else -> 0.5f
        }
        scope.launch { playerStore.save(playerSettings.copy(playbackSpeed = next)) }
    }

    // Cola persistente (ETAPA 4): al abrir, si "Reanudar" está activo,
    // se recupera la última cola con su posición (sin reproducir sola).
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (queueRestored || c.mediaItemCount > 0) return@LaunchedEffect
        queueRestored = true
        val settings = playerStore.settingsFlow.first()
        if (!settings.resumeWhereLeft) return@LaunchedEffect
        val saved = queueStore.queueFlow.first()
        if (saved.songIds.isEmpty()) return@LaunchedEffect
        val songs = withContext(Dispatchers.IO) { MusicRepository(context).loadSongs() }
        val ordered = saved.songIds.mapNotNull { id -> songs.firstOrNull { it.id == id } }
        if (ordered.isEmpty()) return@LaunchedEffect
        c.setMediaItems(
            ordered.map { songToMediaItem(context, it) },
            saved.index.coerceIn(0, ordered.size - 1),
            saved.positionMs,
        )
        c.prepare()
    }

    fun playSongs(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        c.setMediaItems(songs.map { songToMediaItem(context, it) }, startIndex, 0)
        c.prepare()
        c.play()
    }

    if (showEq) {
        EqScreen(onClose = { showEq = false })
        return
    }
    if (showPlayer) {
        BackHandler { showPlayer = false }
        PlayerScreen(
            title = currentTitle,
            artist = currentArtist,
            album = currentAlbum,
            artworkUri = currentArtwork,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            onClose = { showPlayer = false },
            onPlayPause = { controller?.let { if (it.isPlaying) it.pause() else it.play() } },
            onPrevious = { controller?.seekToPreviousMediaItem() },
            onNext = { controller?.seekToNextMediaItem() },
            onSeek = { controller?.seekTo(it) },
            onToggleShuffle = { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } },
            onCycleRepeat = {
                controller?.let {
                    it.repeatMode = when (it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            },
            onOpenEq = { showEq = true },
            speed = speed,
            onCycleSpeed = { cycleSpeed() },
            queueTitles = queueTitles,
            queueIndex = queueIndex,
            onQueueClick = { index ->
                controller?.let { it.seekTo(index, 0); it.play() }
            },
        )
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                val title = currentTitle
                if (title != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showPlayer = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtworkImage(
                            uri = currentArtwork,
                            letter = title.take(1).uppercase(),
                            size = 48.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        ) {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                currentArtist ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(
                            onClick = {
                                controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            },
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            )
                        }
                    }
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.LIBRARY,
                        onClick = { tab = Tab.LIBRARY },
                        icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                        label = { Text("Biblioteca") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.PLAYLISTS,
                        onClick = { tab = Tab.PLAYLISTS },
                        icon = { Icon(Icons.Filled.PlaylistPlay, contentDescription = null) },
                        label = { Text("Playlists") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.SETTINGS,
                        onClick = { tab = Tab.SETTINGS },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Ajustes") },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.LIBRARY -> LibraryScreen(
                hasAudioPermission = hasAudioPermission,
                onSongClick = { song, songs -> playSongs(songs, songs.indexOf(song)) },
                modifier = Modifier.padding(padding),
            )
            Tab.PLAYLISTS -> PlaylistsScreen(
                hasAudioPermission = hasAudioPermission,
                onSongClick = { song, songs -> playSongs(songs, songs.indexOf(song)) },
                modifier = Modifier.padding(padding),
            )
            Tab.SETTINGS -> SettingsScreen(modifier = Modifier.padding(padding))
        }
    }
}
