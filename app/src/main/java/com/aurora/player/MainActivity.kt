package com.aurora.player

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.media3.common.Timeline
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
import com.aurora.player.online.OnlineTrack
import com.aurora.player.playback.songToMediaItem
import com.aurora.player.ui.eq.EqScreen
import com.aurora.player.ui.library.ArtworkImage
import com.aurora.player.ui.library.LibraryScreen
import com.aurora.player.ui.player.PlayerScreen
import com.aurora.player.ui.player.SleepTimerOption
import com.aurora.player.ui.playlists.PlaylistsScreen
import com.aurora.player.ui.settings.SettingsScreen
import com.aurora.player.ui.stats.StatsScreen
import com.aurora.player.ui.theme.AuroraPlayerTheme
import com.aurora.player.ui.theme.LocalCardStyle
import com.aurora.player.ui.theme.LocalCornerRadiusDp
import com.aurora.player.ui.theme.cardBackground
import com.aurora.player.ui.theme.cardBorder
import com.aurora.player.ui.theme.cornerRadiusShape
import com.aurora.player.util.LrcLine
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.LyricsOffsetStore
import com.aurora.player.util.resolveEffective
import com.aurora.player.lyrics.loadOnlineLyrics
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

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
        // BISECCIÓN DEL CIERRE (2026-11-09): la APK #105 tampoco abre
        // y la pantalla de diagnóstico ni aparece — o el cierre es
        // ANTES de instalar el handler en onCreate, o no queda registro
        // (proceso matado sin excepción). Por eso el handler se instala
        // aquí (lo primero que corre de la Activity) y cada paso del
        // arranque deja una marca en el registro. La próxima apertura
        // muestra hasta dónde llegó, aunque no haya stack trace.
        ErrorLog.log(this, "BOOT: attach", null)
        installCrashHandler()
    }

    private var crashHandlerInstalled = false

    private fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                ErrorLog.log(this, "CRASH", throwable)
            } catch (_: Exception) {
                // El registro nunca puede impedir el cierre.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashHandler()
        ErrorLog.log(this, "BOOT: onCreate", null)
        super.onCreate(savedInstanceState)
        ErrorLog.log(this, "BOOT: super", null)
        // Botones del teléfono ocultos mientras se usa la app: la barra
        // de navegación se esconde y vuelve con un deslizamiento desde
        // el borde inferior.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        ErrorLog.log(this, "BOOT: insets", null)
        handleWidgetAction(intent)
        hasAudioPermission = audioPermissionGranted()
        ErrorLog.log(this, "BOOT: setContent", null)
        setContent {
            ErrorLog.log(applicationContext, "BOOT: compose", null)
            // DIAGNÓSTICO DE CIERRE (2026-11-09): si la última vez la
            // app se cerró con un crash, el registro queda en el
            // teléfono y esta pantalla lo muestra ANTES que nada (sin
            // reproductor, sin biblioteca, sin tema: nada de lo que
            // pudo tumbarla). El usuario lo comparte y entra borrándolo.
            // También se muestra si el arranque anterior se quedó a
            // medias (hay marcas BOOT: sin un BOOT: OK detrás): eso es
            // un cierre sin excepción, que no deja stack trace.
            val crashLogFile = ErrorLog.file(applicationContext)
            val crashLogText = remember {
                try {
                    crashLogFile.takeIf { it.exists() }?.readText().orEmpty()
                } catch (_: Exception) {
                    ""
                }
            }
            val lastBootIdx = crashLogText.lastIndexOf("BOOT:")
            val diedMidBoot = lastBootIdx >= 0 &&
                !crashLogText.substring(lastBootIdx).startsWith("BOOT: OK")
            var showCrashReport by remember {
                mutableStateOf(crashLogText.contains("CRASH") || diedMidBoot)
            }
            if (showCrashReport) {
                CrashReportScreen(
                    logText = crashLogText,
                    onShare = { text ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Aurora Player — registro de cierre")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        startActivity(Intent.createChooser(intent, "Compartir registro"))
                    },
                    onDismiss = {
                        try {
                            crashLogFile.delete()
                        } catch (_: Exception) {
                        }
                        showCrashReport = false
                    },
                )
            } else {
                // Marca de arranque completo: si la próxima apertura ve
                // marcas BOOT: sin este OK, el arranque anterior murió.
                LaunchedEffect(Unit) {
                    ErrorLog.log(applicationContext, "BOOT: OK", null)
                }
                val themeStore = ThemeSettingsStore(applicationContext)
                val themeSettings by themeStore.settingsFlow.collectAsState(
                    initial = ThemeSettings.DEFAULT,
                )
                // RENDIMIENTO (spec 004): valores visuales EFECTIVOS.
                // La bajada automática (ahorro de energía del sistema,
                // temperatura severa, app no visible) solo cambia lo que
                // se ve; lo guardado en DataStore no se toca nunca.
                val powerManager = remember {
                    applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                }
                var batterySaver by remember { mutableStateOf(powerManager.isPowerSaveMode) }
                var thermalSevere by remember {
                    mutableStateOf(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            powerManager.currentThermalStatus.let {
                                it == PowerManager.THERMAL_STATUS_SEVERE ||
                                    it == PowerManager.THERMAL_STATUS_CRITICAL
                            },
                    )
                }
                var appVisible by remember { mutableStateOf(true) }
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            batterySaver = powerManager.isPowerSaveMode
                        }
                    }
                    val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("DEPRECATION")
                        applicationContext.registerReceiver(receiver, filter)
                    }
                    onDispose { applicationContext.unregisterReceiver(receiver) }
                }
                DisposableEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val listener = PowerManager.OnThermalStatusChangedListener { status ->
                            thermalSevere = status == PowerManager.THERMAL_STATUS_SEVERE ||
                                status == PowerManager.THERMAL_STATUS_CRITICAL
                        }
                        powerManager.addThermalStatusListener(listener)
                        onDispose { powerManager.removeThermalStatusListener(listener) }
                    } else {
                        onDispose { }
                    }
                }
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> appVisible = true
                            Lifecycle.Event.ON_PAUSE -> appVisible = false
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val effective = resolveEffective(themeSettings, batterySaver, thermalSevere, appVisible)
                val effectiveSettings = themeSettings.copy(
                    dynamicColor = effective.dynamicColor,
                    playerBackground = effective.playerBackground,
                    cornerRadiusDp = effective.cornerRadiusDp,
                    animationsEnabled = effective.animationsEnabled,
                )
                AuroraPlayerTheme(settings = effectiveSettings) {
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
            // CAUSA DEL CIERRE (2026-11-09): painterResource NO puede
            // cargar un icono adaptativo (ic_launcher.xml): solo acepta
            // VectorDrawable o imágenes (PNG/JPG/WEBP) y tumbaba la app
            // antes del primer fotograma. Se usa el PNG del frente del
            // icono (ic_launcher_foreground), que es la imagen real.
            painter = painterResource(R.mipmap.ic_launcher_foreground),
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

/**
 * DIAGNÓSTICO DE CIERRE (2026-11-09): pantalla mínima que se muestra
 * al abrir si la última vez hubo un crash. No usa el tema propio, ni
 * el reproductor, ni la biblioteca: solo texto plano con el registro
 * (ya sanitizado: sin rutas, sin nombres de canciones) y dos botones:
 * compartirlo y borrarlo para entrar. Sin esta pantalla, un crash al
 * abrir no se puede diagnosticar desde el teléfono.
 */
@Composable
private fun CrashReportScreen(
    logText: String,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Text(
            "Aurora Player se cerró la última vez",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Este es el registro del cierre. Compártelo con Aurora para que lo arregle. No lleva tus datos personales: las rutas y los nombres van sanitizados.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Box(
            modifier = Modifier.weight(1f)
                .padding(top = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SelectionContainer {
                Text(
                    logText.ifBlank { "(registro vacío)" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onShare(logText) },
                modifier = Modifier.weight(1f),
            ) { Text("Compartir") }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) { Text("Borrar y entrar") }
        }
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
    // RONDA 6: tono (pitch) independiente de la velocidad.
    var pitch by remember { mutableStateOf(1f) }
    // RONDA 6: radio local — no rellenar dos veces a la vez, y no
    // repetir las últimas 50 canciones que puso la radio.
    var radioBusy by remember { mutableStateOf(false) }
    var radioRecentIds by remember { mutableStateOf<List<Long>>(emptyList()) }
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
    // 002-online-youtube: el mediaId tal cual (las pistas online son
    // "yt:<videoId>" y no tienen id numérico; las letras y la cola
    // las distinguen por este prefijo).
    var currentMediaId by remember { mutableStateOf<String?>(null) }
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

    /**
     * RONDA 6: radio local — al acabar la cola, seguir con canciones
     * parecidas de la biblioteca (mismo artista, luego mismo género,
     * luego cualquiera), sin repetir las que ya suenan ni las últimas
     * 50 que puso la radio. Solo biblioteca local, sin internet.
     * Definida ANTES del listener que la llama (las funciones locales
     * no se pueden usar antes de su declaración).
     */
    fun refillRadio() {
        val c = controller ?: return
        if (radioBusy) return
        radioBusy = true
        scope.launch {
            val picked: List<Song> = try {
                withContext(Dispatchers.IO) {
                    val settings = playerStore.settingsFlow.first()
                    val songs = MusicRepository(context).loadSongs(settings.minDurationMs)
                    val currentId = c.currentMediaItem?.mediaId?.toLongOrNull()
                    val current = songs.firstOrNull { it.id == currentId }
                    val inQueue = (0 until c.mediaItemCount)
                        .mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }.toSet()
                    val excluded = inQueue + radioRecentIds.toSet()
                    val sameArtist = songs.filter {
                        it.id !in excluded && current != null && it.artist == current.artist
                    }
                    val sameGenre = songs.filter {
                        it.id !in excluded && current?.genre != null && it.genre == current.genre
                    }
                    val rest = songs.filter { it.id !in excluded }
                    (sameArtist.shuffled() + sameGenre.shuffled() + rest.shuffled())
                        .distinctBy { it.id }.take(10)
                }
            } catch (e: Exception) {
                ErrorLog.log(context, "Radio local", e)
                emptyList()
            }
            if (picked.isNotEmpty()) {
                radioRecentIds = (radioRecentIds + picked.map { it.id }).takeLast(50)
                val startIndex = c.mediaItemCount
                c.addMediaItems(picked.map { songToMediaItem(context, it) })
                c.seekTo(startIndex, 0)
                c.play()
            }
            radioBusy = false
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
                currentMediaId = c.currentMediaItem?.mediaId
                // La ruta de la canción actual, para las letras (.lrc).
                currentSongPath = meta?.extras?.getString("aurora_path")
                // Cola persistente (SPEC 005 FR-002): se guarda aquí
                // y en cada cambio de cola o al pausar (ver saveQueue).
                saveQueue(c)
            }

            /** Guarda la cola actual (ids + índice + posición). */
            private fun saveQueue(c: MediaController) {
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

            // SPEC 005 FR-002: añadir, quitar, reordenar o sustituir
            // la cola cambia la línea de tiempo, no la pista actual:
            // sin esto, lo añadido se perdía al cerrar la app.
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                controller?.let { refresh(it) }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    finalizeHistory()
                    // SPEC 005 FR-002: al pausar, la posición queda
                    // guardada; sin esto, reabrir retomaba desde el
                    // último cambio de pista, no desde donde paró.
                    controller?.let { saveQueue(it) }
                }
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                shuffleEnabled = enabled
            }

            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }

            override fun onPlaybackStateChanged(state: Int) {
                controller?.let { refresh(it) }
                // RONDA 6: radio local — la cola se acabó de verdad
                // (repetición apagada) y el usuario la tiene activa.
                if (state == Player.STATE_ENDED) {
                    val c = controller
                    if (c != null && c.repeatMode == Player.REPEAT_MODE_OFF && playerSettings.localRadio) {
                        refillRadio()
                    }
                }
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
                        // RONDA 4 (2026-11-09): historyDone nunca se
                        // limpiaba: si una canción ya había contado
                        // una vez en esta sesión, sus siguientes
                        // escuchas no se registraban nunca más (ni en
                        // "Más escuchadas" ni en reproducciones).
                        historyDone = historyDone - id
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
    // RONDA 7 P2: sin .lrc y con modo online encendido, se busca en
    // LRCLIB (título/artista/álbum/duración del MediaItem actual); el
    // .lrc local siempre manda y apagado no se toca la red.
    // 002-online-youtube: las pistas "yt:" no tienen .lrc ni id
    // numérico: su letra solo puede ser online, con sus metadatos.
    LaunchedEffect(currentMediaId) {
        val mediaId = currentMediaId
        lyrics = emptyList()
        if (mediaId == null) return@LaunchedEffect
        if (mediaId.startsWith("yt:")) {
            lyricsOffsetMs = 0
            if (playerSettings.onlineMode) {
                val meta = controller?.currentMediaItem?.mediaMetadata
                val track = meta?.title?.toString().orEmpty()
                val artist = meta?.artist?.toString().orEmpty()
                val album = meta?.albumTitle?.toString()
                val durationSec = (controller?.duration ?: 0L).let {
                    if (it > 0) it / 1000 else 0L
                }
                if (track.isNotBlank() && artist.isNotBlank()) {
                    lyrics = loadOnlineLyrics(context, mediaId, track, artist, album, durationSec)
                }
            }
            return@LaunchedEffect
        }
        val id = mediaId.toLongOrNull() ?: return@LaunchedEffect
        lyricsOffsetMs = offsetStore.offsetFor(id)
        lyrics = loadLyricsForPath(currentSongPath ?: "")
        if (lyrics.isEmpty() && playerSettings.onlineMode) {
            val meta = controller?.currentMediaItem?.mediaMetadata
            val track = meta?.title?.toString().orEmpty()
            val artist = meta?.artist?.toString().orEmpty()
            val album = meta?.albumTitle?.toString()
            val durationSec = (controller?.duration ?: 0L).let {
                if (it > 0) it / 1000 else 0L
            }
            if (track.isNotBlank() && artist.isNotBlank()) {
                lyrics = loadOnlineLyrics(context, id.toString(), track, artist, album, durationSec)
            }
        }
    }

    // Velocidad y tono reales (ETAPA 4 + RONDA 6): se aplican al
    // controlador cuando cambian en Ajustes o en el reproductor, y se
    // guardan en DataStore.
    LaunchedEffect(playerSettings.playbackSpeed, playerSettings.playbackPitch, controller) {
        val c = controller ?: return@LaunchedEffect
        PlayerControls.setSpeedAndPitch(c, playerSettings.playbackSpeed, playerSettings.playbackPitch)
        speed = playerSettings.playbackSpeed
        pitch = playerSettings.playbackPitch
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

    /** RONDA 6: el tono cicla como la velocidad (0.5x–2x, 1x = sin cambio). */
    fun cyclePitch() {
        val next = when (pitch) {
            0.5f -> 0.75f
            0.75f -> 1f
            1f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2f
            else -> 0.5f
        }
        scope.launch { playerStore.save(playerSettings.copy(playbackPitch = next)) }
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
        val songs = withContext(Dispatchers.IO) {
            // RONDA 4 (2026-11-09): restauraba con la duración
            // mínima por defecto (5 s), no con la del usuario: la
            // cola podía traer canciones que la biblioteca no muestra.
            MusicRepository(context).loadSongs(settings.minDurationMs)
        }
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

    /** 002-online-youtube: suena una pista de YouTube con su URL ya
     * resuelta (las URLs caducan: se resuelven al tocar, nunca se
     * guardan). El mediaId "yt:<videoId>" la marca como online: no
     * entra en el historial ni en la cola persistente local. */
    fun playOnlineTrack(track: OnlineTrack, streamUrl: String) {
        val c = controller ?: return
        val item = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId("yt:${track.videoId}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setExtras(Bundle().apply { putBoolean("aurora_online", true) })
                    .build(),
            )
            .build()
        c.setMediaItems(listOf(item))
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
            pitch = pitch,
            onCyclePitch = { cyclePitch() },
            queueTitles = queueTitles,
            queueIndex = queueIndex,
            onQueueClick = { index ->
                controller?.let { it.seekTo(index, 0); it.play() }
            },
            onQueueRemove = { index ->
                controller?.removeMediaItem(index)
            },
            onQueueMove = { from, to ->
                val c = controller ?: return@PlayerScreen
                if (to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
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
                    // DISEÑO PREMIUM (spec 003): la mini-barra es una
                    // tarjeta con el estilo y el radio elegidos.
                    Surface(
                        color = cardBackground(
                            LocalCardStyle.current,
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = cornerRadiusShape(LocalCornerRadiusDp.current),
                        border = cardBorder(
                            LocalCardStyle.current,
                            MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { showPlayer = true },
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
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
                onShuffleAll = { songs ->
                    val c = controller
                    if (c != null && songs.isNotEmpty()) {
                        c.shuffleModeEnabled = true
                        playSongs(songs.shuffled(), 0)
                    }
                },
                onlineMode = playerSettings.onlineMode,
                onPlayOnlineTrack = { track, url -> playOnlineTrack(track, url) },
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
