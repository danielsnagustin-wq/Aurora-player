package com.aurora.player

import android.Manifest
import android.content.ContentUris
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.FavoriteEntity
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.PlayHistoryEntity
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.QueueStore
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.Song
import com.aurora.player.model.ThemeSettings
import com.aurora.player.playback.PlaybackService
import com.aurora.player.playback.PlayerControls
import com.aurora.player.ui.eq.EqScreen
import com.aurora.player.ui.library.ArtworkImage
import com.aurora.player.ui.library.LibraryScreen
import com.aurora.player.ui.player.PlayerScreen
import com.aurora.player.ui.player.SleepTimerOption
import com.aurora.player.ui.playlists.PlaylistsScreen
import com.aurora.player.ui.settings.SettingsScreen
import com.aurora.player.ui.stats.StatsScreen
import com.aurora.player.ui.theme.AuroraPlayerTheme
import com.aurora.player.util.LrcLine
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.LyricsOffsetStore
import com.aurora.player.util.loadLyricsForPath
import com.aurora.player.widget.AuroraWidgetProvider
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

    /** Ya se pidió el permiso al menos una vez (para saber si la
     * denegación es permanente). */
    private var permissionAsked = false

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

    /** Acción del widget pendiente de aplicar cuando el reproductor esté listo. */
    private var pendingWidgetAction by mutableStateOf<String?>(null)

    companion object {
        const val ACTION_WIDGET_TOGGLE = "com.aurora.player.WIDGET_TOGGLE"
        const val ACTION_WIDGET_NEXT = "com.aurora.player.WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "com.aurora.player.WIDGET_PREV"
        const val ACTION_WIDGET_OPEN = "com.aurora.player.WIDGET_OPEN"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Botones del teléfono ocultos mientras se usa la app: la barra
        // de navegación se esconde y vuelve con un deslizamiento desde
        // el borde inferior.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        handleWidgetAction(intent)
        // Registro de errores (depuración, se quitará en el release):
        // un cierre inesperado queda guardado y sanitizado en el
        // teléfono, y se puede ver y compartir desde Ajustes.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                ErrorLog.log(this, "CRASH", throwable)
            } catch (_: Exception) {
                // El registro nunca puede impedir el cierre.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
        hasAudioPermission = audioPermissionGranted()
        setContent {
            val themeStore = ThemeSettingsStore(applicationContext)
            val themeSettings by themeStore.settingsFlow.collectAsState(
                initial = ThemeSettings.DEFAULT,
            )
            AuroraPlayerTheme(settings = themeSettings) {
                if (hasAudioPermission) {
                    AuroraApp(
                        hasAudioPermission = hasAudioPermission,
                        widgetAction = pendingWidgetAction,
                        onWidgetActionHandled = { pendingWidgetAction = null },
                    )
                } else {
                    WelcomeScreen(
                        onGrant = { requestAudioPermission() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetAction(intent)
    }

    private fun handleWidgetAction(intent: Intent?) {
        when (intent?.action) {
            ACTION_WIDGET_TOGGLE, ACTION_WIDGET_NEXT, ACTION_WIDGET_PREV -> {
                pendingWidgetAction = intent.action
            }
        }
    }

    /**
     * AUDITORÍA (2026-11-09): si el usuario denegaba el permiso para
     * siempre, el botón "Permitir acceso" no hacía nada visible (el
     * sistema ya no muestra su diálogo) y se quedaba atorado en la
     * bienvenida. Ahora ese botón abre Ajustes del sistema para
     * concederlo a mano.
     */
    private fun requestAudioPermission() {
        if (permissionAsked &&
            !shouldShowRequestPermissionRationale(audioPermissionName)
        ) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
            return
        }
        permissionAsked = true
        val perms = mutableListOf(audioPermissionName)
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
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
    STATS("Estadísticas"),
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
                // La ruta viaja en el MediaItem: las letras (.lrc) se
                // leen directas, sin escanear toda la biblioteca al
                // cambiar de pista (AUDITORÍA 2026-11-09).
                .setExtras(Bundle().apply { putString("aurora_path", song.path) })
                .build(),
        )
        .build()

/** Pantalla de inicio: qué es la app y por qué pide el permiso. */
@Composable
private fun WelcomeScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = "Aurora Player",
            modifier = Modifier.size(128.dp).clip(RoundedCornerShape(28.dp)),
        )
        Text(
            "Aurora Player",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "El amor por la música no tiene que tener precio",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(
            modifier = Modifier.padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeatureRow("Toda tu música local, todos los formatos")
            FeatureRow("Sin anuncios, sin cuentas, sin internet")
            FeatureRow("Todo queda en tu teléfono, nada se sube")
        }
        Text(
            "Para mostrar tus canciones necesitamos acceso a los archivos de audio de tu teléfono.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        ) {
            Text("Permitir acceso a la música")
        }
        Text(
            "Gratis siempre",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun AuroraApp(
    hasAudioPermission: Boolean,
    widgetAction: String? = null,
    onWidgetActionHandled: () -> Unit = {},
) {
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
    val db = remember { AuroraDatabase.getInstance(context) }
    var speed by remember { mutableStateOf(1f) }
    var queueTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    var queueIndex by remember { mutableStateOf(0) }
    var listenedMs by remember { mutableStateOf(0L) }
    var listenedId by remember { mutableStateOf<Long?>(null) }
    var historyDone by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var queueRestored by remember { mutableStateOf(false) }
    // Último estado guardado en el widget, para no reescribirlo en
    // cada cambio de estado del reproductor.
    var lastWidgetTitle by remember { mutableStateOf<String?>(null) }
    var lastWidgetArtist by remember { mutableStateOf<String?>(null) }
    var lastWidgetPlaying by remember { mutableStateOf(false) }

    // ETAPA 5: letras del .lrc de la canción actual y temporizador.
    var currentSongId by remember { mutableStateOf<Long?>(null) }
    var currentSongPath by remember { mutableStateOf<String?>(null) }
    val songUri = currentSongId?.let {
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
    }
    var lyrics by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var sleepTimer by remember { mutableStateOf(SleepTimerOption.OFF) }
    var sleepUntilMs by remember { mutableStateOf<Long?>(null) }
    var sleepAtSongEnd by remember { mutableStateOf(false) }

    // Ideas open source: favorito de la canción actual y ajuste de
    // sincronización de letras por canción (se guarda en DataStore).
    // AUDITORÍA (2026-11-09): isFavorite ya no es un estado que se
    // escribe a mano en dos sitios: se deriva del flujo de favoritos
    // de Room, así que el corazón del reproductor y el de la
    // biblioteca siempre dicen lo mismo.
    var favoriteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val offsetStore = remember { LyricsOffsetStore(context) }
    var lyricsOffsetMs by remember { mutableStateOf(0) }

    // Animación de inicio: una sola vez por arranque de la app.
    var showStart by remember { mutableStateOf(true) }

    // Favoritos en vivo (Room avisa de cada cambio, venga de donde venga).
    LaunchedEffect(Unit) {
        db.libraryDao().favoriteIdsFlow().collect { favoriteIds = it.toSet() }
    }

    fun toggleFavorite() {
        val id = currentSongId ?: return
        scope.launch(Dispatchers.IO) {
            val dao = db.libraryDao()
            if (id in favoriteIds) {
                dao.removeFavorite(id)
            } else {
                dao.addFavorite(FavoriteEntity(id))
            }
        }
    }

    /** Cola flexible (ideas de Namida/Auxio): suena justo después. */
    fun playNext(song: Song) {
        val c = controller ?: return
        val at = (c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount)
        c.addMediaItem(at, songToMediaItem(context, song))
    }

    /** Cola flexible: se añade al final de la cola actual. */
    fun addToQueue(song: Song) {
        controller?.addMediaItem(songToMediaItem(context, song))
    }

    // Acción del widget (play/pausa/siguiente/anterior): se aplica en
    // cuanto el reproductor está listo.
    LaunchedEffect(widgetAction, controller) {
        val action = widgetAction ?: return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        when (action) {
            MainActivity.ACTION_WIDGET_TOGGLE -> if (c.isPlaying) c.pause() else c.play()
            MainActivity.ACTION_WIDGET_NEXT -> c.seekToNextMediaItem()
            MainActivity.ACTION_WIDGET_PREV -> c.seekToPreviousMediaItem()
        }
        onWidgetActionHandled()
    }

    fun onSleepTimerSelected(option: SleepTimerOption) {
        sleepTimer = option
        sleepAtSongEnd = option == SleepTimerOption.END_OF_SONG
        sleepUntilMs = if (option.minutes > 0) {
            System.currentTimeMillis() + option.minutes * 60_000L
        } else {
            null
        }
    }

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
                // ANTI-CIERRE: un fallo al actualizar la UI (widget,
                // cola, favorito) nunca puede tumbar la app al abrir.
                try {
                    refreshUnsafe(c)
                } catch (e: Exception) {
                    ErrorLog.log(context, "Actualizar reproductor", e)
                }
            }

            private fun refreshUnsafe(c: MediaController) {
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
                // Widget: se guarda solo cuando cambia lo que suena (o
                // el estado play/pausa). Antes se reescribía y se
                // actualizaba el widget en cada cambio de estado del
                // reproductor, también al buscar o al cargar.
                val newTitle = meta?.title?.toString()
                val newArtist = meta?.artist?.toString()
                if (newTitle != lastWidgetTitle || newArtist != lastWidgetArtist ||
                    c.isPlaying != lastWidgetPlaying
                ) {
                    lastWidgetTitle = newTitle
                    lastWidgetArtist = newArtist
                    lastWidgetPlaying = c.isPlaying
                    AuroraWidgetProvider.saveNowPlaying(
                        context, newTitle, newArtist, c.isPlaying,
                    )
                }
                queueTitles = (0 until c.mediaItemCount).map {
                    c.getMediaItemAt(it).mediaMetadata.title?.toString() ?: ""
                }
                queueIndex = c.currentMediaItemIndex
                currentSongId = c.currentMediaItem?.mediaId?.toLongOrNull()
                // La ruta de la canción actual, para las letras (.lrc).
                currentSongPath = meta?.extras?.getString("aurora_path")
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
    // ETAPA 7: en pausa y con el reproductor cerrado no hay nada que
    // actualizar: el bucle duerme sin leer el reproductor (ahorra
    // batería; la corrutina en sí no consume nada mientras duerme).
    LaunchedEffect(controller, isPlaying) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            if (c.isPlaying || showPlayer) {
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
                // Temporizador de apagado (ETAPA 5): por minutos o al final
                // de la canción. Nunca se activa solo.
                val until = sleepUntilMs
                if (until != null && System.currentTimeMillis() >= until) {
                    c.pause()
                    sleepUntilMs = null
                    sleepTimer = SleepTimerOption.OFF
                }
                if (sleepAtSongEnd && c.isPlaying && c.duration > 0 &&
                    c.duration - c.currentPosition < 1000
                ) {
                    c.pause()
                    sleepAtSongEnd = false
                    sleepTimer = SleepTimerOption.OFF
                }
            }
            delay(500)
        }
    }

    // Letras (ETAPA 5): al cambiar de canción se busca su .lrc por su
    // ruta y su ajuste de sincronización guardado. Ya no se escanea
    // toda la biblioteca para encontrar la ruta (AUDITORÍA 2026-11-09).
    LaunchedEffect(currentSongId) {
        val id = currentSongId ?: return@LaunchedEffect
        lyrics = emptyList()
        lyricsOffsetMs = offsetStore.offsetFor(id)
        lyrics = loadLyricsForPath(currentSongPath ?: "")
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
        if (songs.isEmpty()) return
        // indexOfFirst devuelve -1 si la canción no está en la lista
        // (duplicados exactos): nunca se pasa un índice inválido al
        // reproductor.
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        c.setMediaItems(songs.map { songToMediaItem(context, it) }, safeIndex, 0)
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
            songUri = songUri,
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
            lyrics = lyrics,
            sleepTimer = sleepTimer,
            onSleepTimerSelected = { onSleepTimerSelected(it) },
            isFavorite = currentSongId in favoriteIds,
            onToggleFavorite = { toggleFavorite() },
            lyricsOffsetMs = lyricsOffsetMs,
            onLyricsOffsetChange = { newOffset ->
                val id = currentSongId ?: return@PlayerScreen
                lyricsOffsetMs = newOffset
                scope.launch(Dispatchers.IO) { offsetStore.saveOffset(id, newOffset) }
            },
            swipeEnabled = playerSettings.swipeToChangeTrack,
            doubleTapEnabled = playerSettings.doubleTapToFavorite,
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
                            songUri = songUri,
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
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
                        selected = tab == Tab.STATS,
                        onClick = { tab = Tab.STATS },
                        icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                        label = { Text("Estadísticas") },
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
                onSongClick = { song, songs ->
                    playSongs(songs, songs.indexOfFirst { it.id == song.id })
                },
                onPlayNext = { song -> playNext(song) },
                onAddToQueue = { song -> addToQueue(song) },
                showStart = showStart,
                onStartFinished = { showStart = false },
                modifier = Modifier.padding(padding),
            )
            Tab.PLAYLISTS -> PlaylistsScreen(
                hasAudioPermission = hasAudioPermission,
                onSongClick = { song, songs ->
                    playSongs(songs, songs.indexOfFirst { it.id == song.id })
                },
                modifier = Modifier.padding(padding),
            )
            Tab.STATS -> StatsScreen(
                hasAudioPermission = hasAudioPermission,
                modifier = Modifier.padding(padding),
            )
            Tab.SETTINGS -> SettingsScreen(modifier = Modifier.padding(padding))
        }
    }
}
