package com.aurora.player

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
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
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
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
import com.aurora.player.data.FolderStore
import com.aurora.player.data.MusicRepository
import com.aurora.player.data.NamedQueueEntity
import com.aurora.player.data.PlayHistoryEntity
import com.aurora.player.data.IntroStore
import com.aurora.player.data.LyricsProviderStore
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.online.GoogleAccountAuth
import com.aurora.player.online.SpotifyCanvasClient
import com.aurora.player.online.SpotifyClient
import com.aurora.player.cast.CastManager
import com.aurora.player.cast.CastState
import com.aurora.player.data.SpotifyStore
import com.aurora.player.data.YouTubeMusicStore
import com.aurora.player.library.autoDjQueue
import com.aurora.player.library.enrichSongs
import com.aurora.player.lyrics.DEFAULT_PROVIDER_ORDER
import com.aurora.player.lyrics.orderedProviders
import com.aurora.player.data.QueueStore
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.model.GestureAction
import com.aurora.player.model.isInExcludedFolder
import com.aurora.player.model.MAX_NAMED_QUEUES
import com.aurora.player.model.parseGestureAction
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.Song
import com.aurora.player.model.ThemeSettings
import com.aurora.player.playback.PlaybackService
import com.aurora.player.playback.PlayerControls
import com.aurora.player.online.OnlineTrack
import com.aurora.player.online.ResolvedVideo
import com.aurora.player.online.fetchCaptions
import com.aurora.player.online.selectCaptionTrack
import com.aurora.player.ui.online.VideoPlayerDialog
import com.aurora.player.playback.songToMediaItem
import com.aurora.player.ui.eq.EqScreen
import com.aurora.player.ui.library.ArtworkImage
import com.aurora.player.ui.library.LibraryScreen
import com.aurora.player.ui.player.PlayerScreen
import com.aurora.player.ui.player.SleepTimerOption
import com.aurora.player.ui.playlists.PlaylistsScreen
import com.aurora.player.ui.settings.SettingsScreen
import com.aurora.player.ui.settings.SettingsSection
import com.aurora.player.ui.setup.SetupWizardScreen
import com.aurora.player.ui.stats.StatsScreen
import com.aurora.player.ui.theme.AuroraPlayerTheme
import com.aurora.player.ui.theme.LocalCardStyle
import com.aurora.player.ui.theme.PureBlack
import com.aurora.player.ui.theme.LocalCornerRadiusDp
import com.aurora.player.ui.theme.cardBackground
import com.aurora.player.ui.theme.cardBorder
import com.aurora.player.ui.theme.cornerRadiusShape
import com.aurora.player.util.BackupManager
import com.aurora.player.util.LrcLine
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.ShakeDetector
import com.aurora.player.util.LyricsOffsetStore
import com.aurora.player.util.resolveEffective
import com.aurora.player.lyrics.LyricsTranslator
import com.aurora.player.lyrics.loadOnlineLyrics
import com.aurora.player.lyrics.nextIndexToPrefetch
import com.aurora.player.util.codecDisplayName
import com.aurora.player.util.loadLyricsForPath
import com.aurora.player.util.saveLyricsForSong
import com.aurora.player.util.dsdQualityLine
import com.aurora.player.util.loadDsdInfo
import com.aurora.player.util.loadSongDetails
import com.aurora.player.widget.AuroraWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Permiso de notificaciones (Android 13+): sin él no sale el aviso de reproducción. */
    private var hasNotificationPermission by mutableStateOf(false)

    /** Ya se pidió el permiso al menos una vez (para saber si la
     * denegación es permanente). */
    private var permissionAsked = false

    /** Ya se pidió el permiso de notificaciones al menos una vez. */
    private var notificationAsked = false

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
        if (Build.VERSION.SDK_INT >= 33) {
            hasNotificationPermission = grants[Manifest.permission.POST_NOTIFICATIONS] == true
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted
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
        hasNotificationPermission = notificationsGranted()
        ErrorLog.log(this, "BOOT: setContent", null)
        setContent {
            // RONDA PULIDO (2026-11-09): la pantalla del reporte de
            // cierre ya no se muestra al abrir (el usuario la pidió
            // fuera), y el registro BOOT: en cada composición
            // escribía en disco en cada redibujado y hacía lenta la
            // interfaz: fuera también.
                val themeStore = ThemeSettingsStore(applicationContext)
                val themeSettings by themeStore.settingsFlow.collectAsState(
                    initial = ThemeSettings.DEFAULT,
                )
                // Asistente de configuración (estilo Namida, 2026-11-09):
                // la primera vez la app abre el asistente en vez de la
                // biblioteca. Solo se marca hecho con "Empezar"/"Saltar".
                val introStore = remember { IntroStore(applicationContext) }
                val setupDone by introStore.setupDoneFlow.collectAsState(initial = null)
                // Sin animación de inicio (2026-11-10 13:24, orden del
                // usuario: quitar el brillo/polvo del inicio, entrar
                // directo). initial=null en setupDone: mientras
                // DataStore no responde no se dibuja nada (negro),
                // para que nunca parpadee la pantalla equivocada.
                val wizardScope = rememberCoroutineScope()
                // RENDIMIENTO (spec 004): valores visuales EFECTIVOS.
                // La bajada automática (ahorro de energía del sistema,
                // temperatura severa, app no visible) solo cambia lo que
                // se ve; lo guardado en DataStore no se toca nunca.
                // as? y no as (auditoría Gemini, ronda 2): si el servicio
                // no existiera, el cast directo tumbaría la app al abrir.
                val powerManager = remember {
                    applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                }
                var batterySaver by remember { mutableStateOf(powerManager?.isPowerSaveMode == true) }
                var thermalSevere by remember {
                    mutableStateOf(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            powerManager?.currentThermalStatus.let {
                                it == PowerManager.THERMAL_STATUS_SEVERE ||
                                    it == PowerManager.THERMAL_STATUS_CRITICAL
                            },
                    )
                }
                var appVisible by remember { mutableStateOf(true) }
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            batterySaver = powerManager?.isPowerSaveMode == true
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
                        powerManager?.addThermalStatusListener(listener)
                        onDispose { powerManager?.removeThermalStatusListener(listener) }
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
                    if (setupDone == null) {
                        // Cargando los ajustes del disco: negro, sin parpadeo.
                        Box(Modifier.fillMaxSize().background(PureBlack))
                    } else if (setupDone == false) {
                        SetupWizardScreen(
                            onFinish = { wizardScope.launch { introStore.markSetupDone() } },
                            onRequestPermission = { requestAudioPermission() },
                            hasAudioPermission = hasAudioPermission,
                        )
                    } else if (hasAudioPermission) {
                        AuroraApp(
                            hasAudioPermission = hasAudioPermission,
                            widgetAction = pendingWidgetAction,
                            onWidgetActionHandled = { pendingWidgetAction = null },
                            notificationsGranted = hasNotificationPermission,
                            onRequestNotificationPermission = { requestNotificationPermission() },
                        )
                    } else {
                        WelcomeScreen(
                            onGrant = { requestAudioPermission() },
                        )
                    }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        // RONDA PULIDO (2026-11-09): si el permiso de música se
        // concedió en Ajustes del sistema (denegación permanente), al
        // volver a la app no se volvía a comprobar y la biblioteca no
        // cargaba hasta cerrar y reabrir. Ahora se comprueba siempre.
        hasAudioPermission = audioPermissionGranted()
        hasNotificationPermission = notificationsGranted()
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
            openAppSettings()
            return
        }
        permissionAsked = true
        val perms = mutableListOf(audioPermissionName)
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    /** Abre la pantalla de Ajustes del sistema de la app. */
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun notificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * RONDA PULIDO (2026-11-09): permiso de notificaciones. Sin él,
     * en Android 13+ no sale el aviso de reproducción con los
     * controles. Si se denegó para siempre, abre Ajustes del sistema.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (notificationAsked &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            openAppSettings()
            return
        }
        notificationAsked = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        // RONDA PULIDO (2026-11-09): bienvenida mínima: solo el
        // icono, el nombre y el botón del permiso. Nada de lemas,
        // listas de funciones ni "Gratis siempre".
        Text(
            "Aurora Player",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(top = 24.dp),
        )
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        ) {
            Text("Permitir acceso a la música")
        }
    }
}

@Composable
private fun AuroraApp(
    hasAudioPermission: Boolean,
    widgetAction: String? = null,
    onWidgetActionHandled: () -> Unit = {},
    notificationsGranted: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.LIBRARY) }
    // Ronda 7 (Gemini): sección de Ajustes que se abre directo
    // (p. ej. Red, desde la pestaña Online con el modo apagado).
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    // Fase 2 app — YouTube con vídeo: el vídeo que se está viendo
    // (null = no hay vídeo en pantalla) y sus subtítulos.
    var videoTrack by remember { mutableStateOf<OnlineTrack?>(null) }
    var videoCaptions by remember { mutableStateOf<List<LrcLine>>(emptyList()) }

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
    // Fase 2 app — Cast: la sesión vive mientras la UI está viva;
    // el botón del reproductor la descubre y este manager la usa.
    val castManager = remember { CastManager(context) }
    val castState by castManager.state.collectAsState()
    val playerStore = remember { PlayerSettingsStore(context) }
    val playerSettings by playerStore.settingsFlow.collectAsState(initial = PlayerSettings.DEFAULT)
    // Fase 2 — orden de los proveedores de letras (hoja "Elegir
    // proveedor"): el primero que encuentre la letra gana.
    val lyricsProviderStore = remember { LyricsProviderStore(context) }
    val lyricsProviderOrder by lyricsProviderStore.orderFlow.collectAsState(initial = DEFAULT_PROVIDER_ORDER)
    val lyricsProviders = remember(lyricsProviderOrder) { orderedProviders(lyricsProviderOrder.joinToString(",")) }
    // Fase 2 — Spotify como BitChord: tus listas en la pestaña Online.
    val spotifyStore = remember { SpotifyStore(context) }
    val spotifyConnected by spotifyStore.connectedFlow.collectAsState(initial = false)
    val spotifyCookie by spotifyStore.cookieFlow.collectAsState(initial = "")
    // Sesión de YouTube Music (2026-11-10): cookies del WebView o
    // token OAuth de la cuenta de Google del teléfono, en el teléfono.
    val ytMusicStore = remember { YouTubeMusicStore(context) }
    val ytMusicCookies by ytMusicStore.cookiesFlow.collectAsState(initial = "")
    val ytMusicToken by ytMusicStore.tokenFlow.collectAsState(initial = "")
    // Token OAuth caducado (2026-11-10): Google lo rechaza con 401
    // al cabo de ~1 h. Al rechazarlo, se invalida el viejo y se pide
    // uno nuevo con la misma cuenta; si no se puede, se borra y la
    // búsqueda cae a cookies/anónimo. Sin esto, la búsqueda de
    // YouTube se queda muda para siempre tras la primera hora.
    val onYtTokenRejected: (String) -> Unit = { rejected ->
        if (rejected.isNotBlank() && rejected == ytMusicToken) {
            scope.launch {
                when (val refreshed = GoogleAccountAuth.refreshToken(context, rejected)) {
                    is GoogleAccountAuth.TokenResult.Ok -> ytMusicStore.setToken(refreshed.token)
                    else -> ytMusicStore.setToken("")
                }
            }
        }
    }
    // Ronda 7b: cookies de YouTube Music caducadas (401 sin token
    // OAuth): no hay refresco silencioso, se borran para que la
    // búsqueda caiga a anónimo y se avisa para volver a entrar.
    val onYtCookiesRejected: () -> Unit = {
        scope.launch { ytMusicStore.setCookies("") }
        Toast.makeText(
            context,
            "Tu sesión de YouTube Music caducó. Vuelve a iniciar sesión en Ajustes → Cuenta.",
            Toast.LENGTH_LONG,
        ).show()
    }
    val queueStore = remember { QueueStore(context) }
    // RONDA 39: guardar la cola periódicamente mientras suena —
    // antes solo se guardaba al cambiar de pista o al pausar, así que
    // cerrar la app a la fuerza a mitad de una canción retomaba desde
    // un minuto viejo. Se escribe como mucho cada 10 s (ver el bucle
    // de progreso), no en cada tick de 500 ms.
    val persistQueue: (MediaController) -> Unit = { c ->
        scope.launch {
            try {
                queueStore.save(
                    (0 until c.mediaItemCount).mapNotNull {
                        c.getMediaItemAt(it).mediaId.toLongOrNull()
                    },
                    c.currentMediaItemIndex,
                    c.currentPosition,
                )
            } catch (e: Exception) {
                ErrorLog.log(context, "Guardar cola", e)
            }
        }
    }
    // SPEC 005 FR-004: respaldo automático al abrir (si está activo
    // y pasaron más de 24 h desde el último). Nunca tumba el arranque.
    LaunchedEffect(Unit) {
        BackupManager.maybeAutoBackup(context)
    }
    val db = remember { AuroraDatabase.getInstance(context) }
    // SPEC 005 FR-007: colas guardadas con nombre.
    val namedQueueDao = remember { db.namedQueueDao() }
    val savedQueues by namedQueueDao.allQueuesWithCount()
        .collectAsState(initial = emptyList())
    var savedQueueMessage by remember { mutableStateOf<String?>(null) }
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
    // Ronda 14: escuchas con la inserción en Room en curso (evita el
    // doble insert si finalizeHistory se llama dos veces seguidas, y
    // que un insert fallido deje el id marcado como hecho).
    var historyPending by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // RONDA 85: escucha en curso ya guardada en Room (su playedAt):
    // el bucle la escribe al cumplir 30 s y actualiza su duración
    // cada 10 s, así que si Android mata la actividad a mitad de
    // canción la escucha no se pierde; al terminar, finalizeHistory
    // solo actualiza esa misma fila con la duración final.
    var historyFlushedId by remember { mutableStateOf<Long?>(null) }
    var historyFlushedAt by remember { mutableStateOf<Long?>(null) }
    var queueRestored by remember { mutableStateOf(false) }
    // Último estado guardado en el widget, para no reescribirlo en
    // cada cambio de estado del reproductor.
    var lastWidgetTitle by remember { mutableStateOf<String?>(null) }
    var lastWidgetArtist by remember { mutableStateOf<String?>(null) }
    var lastWidgetPlaying by remember { mutableStateOf(false) }
    var lastWidgetArtUri by remember { mutableStateOf<String?>(null) }

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
    var translatedLyrics by remember { mutableStateOf<List<String>>(emptyList()) }
    // Fase 2 — Spotify Canvas: URL del vídeo en bucle de la canción
    // que suena (null = sin Canvas, se ve la portada de siempre).
    var canvasUrl by remember { mutableStateOf<String?>(null) }
    // RONDA 2 (2026-11-09): línea de calidad real del tema actual.
    var qualityText by remember { mutableStateOf<String?>(null) }
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
    // RONDA 2 (2026-11-09): la intro solo se muestra la primera vez
    // (IntroStore); antes se repetía al volver tras un rato porque
    // Android había matado el proceso en segundo plano.
    var lyricsOffsetMs by remember { mutableStateOf(0) }

    // Favoritos en vivo (Room avisa de cada cambio, venga de donde venga).
    LaunchedEffect(Unit) {
        db.libraryDao().favoriteIdsFlow().collect { favoriteIds = it.toSet() }
    }

    /** Llamadas al controlador protegidas (auditoría Gemini, ronda 2):
     * si el servicio muere entre medias, el MediaController lanza
     * IllegalStateException; sin esto, un toque en ese momento tumba
     * la app. Como playSongs/playOnlineTrack, que ya lo hacen. */
    fun controllerCall(tag: String, block: MediaController.() -> Unit) {
        val c = controller ?: return
        try {
            c.block()
        } catch (e: Exception) {
            ErrorLog.log(context, tag, e)
        }
    }

    // RONDA 109: igual que en la biblioteca, dos toques rápidos
    // decidían con el mismo estado y el segundo se perdía. Cambio
    // optimista serializado con candado; el flujo de Room lo
    // confirma al emitir.
    val favoriteMutex = remember { Mutex() }

    fun toggleFavorite() {
        val id = currentSongId ?: return
        scope.launch(Dispatchers.IO) {
            favoriteMutex.withLock {
                val adding = id !in favoriteIds
                favoriteIds = if (adding) favoriteIds + id else favoriteIds - id
                try {
                    val dao = db.libraryDao()
                    if (adding) {
                        dao.addFavorite(FavoriteEntity(id))
                    } else {
                        dao.removeFavorite(id)
                    }
                } catch (e: Exception) {
                    favoriteIds = if (adding) favoriteIds - id else favoriteIds + id
                    ErrorLog.log(context, "Favorito", e)
                }
            }
        }
    }

    /** Siguiente (2026-11-10): con repetición de una canción, el
     * "siguiente" de ExoPlayer reinicia la misma canción (así lo
     * define Media3); como Spotify y YouTube Music, aquí avanza a
     * la siguiente de la cola. En la última, no hace nada. */
    fun skipToNext() {
        controllerCall("Siguiente") {
            if (repeatMode == Player.REPEAT_MODE_ONE) {
                val next = currentMediaItemIndex + 1
                if (next < mediaItemCount) {
                    seekTo(next, 0)
                    play()
                }
            } else {
                seekToNextMediaItem()
            }
        }
    }

    /** Cola flexible (ideas de Namida/Auxio): suena justo después. */
    fun playNext(song: Song) {
        controllerCall("Cola") {
            val at = (currentMediaItemIndex + 1).coerceIn(0, mediaItemCount)
            addMediaItem(at, songToMediaItem(context, song))
        }
    }

    /** Cola flexible: se añade al final de la cola actual. */
    fun addToQueue(song: Song) {
        controllerCall("Cola") { addMediaItem(songToMediaItem(context, song)) }
    }

    // Acción del widget (play/pausa/siguiente/anterior): se aplica en
    // cuanto el reproductor está listo.
    LaunchedEffect(widgetAction, controller) {
        val action = widgetAction ?: return@LaunchedEffect
        if (controller == null) return@LaunchedEffect
        when (action) {
            MainActivity.ACTION_WIDGET_TOGGLE -> controllerCall("Widget") { if (isPlaying) pause() else play() }
            MainActivity.ACTION_WIDGET_NEXT -> skipToNext()
            MainActivity.ACTION_WIDGET_PREV -> controllerCall("Widget") { seekToPreviousMediaItem() }
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
        if (id in historyPending) {
            // RONDA 98: la inserción periódica está en curso: si se
            // vuelve sin más, la fila queda hasta 10 s corta (la
            // duración final nunca se escribe). Se espera a que la
            // inserción aterrice y se actualiza entonces; si no hay
            // fila (la inserción falló), se crea.
            val at = historyFlushedAt
            if (at != null && historyFlushedId == id) {
                val finalMs = ms
                scope.launch(Dispatchers.IO) {
                    delay(500)
                    try {
                        val updated = db.libraryDao().updateHistoryDuration(id, at, finalMs)
                        if (updated == 0) {
                            db.libraryDao().addHistory(PlayHistoryEntity(id, at, finalMs))
                            // RONDA 110: la inserción periódica puede
                            // aterrizar entre la actualización (0
                            // filas) y este insert (IGNORE): la fila
                            // quedaría con la duración corta y el
                            // insert de respaldo no haría nada. Se
                            // vuelve a actualizar para que la duración
                            // final quede siempre, aterrice cuando
                            // aterrice la inserción.
                            db.libraryDao().updateHistoryDuration(id, at, finalMs)
                        }
                        historyDone = historyDone + id
                    } catch (e: Exception) {
                        ErrorLog.log(context, "Historial", e)
                    }
                }
            }
            return
        }
        val at = historyFlushedAt
        if (at != null && historyFlushedId == id) {
            // RONDA 85: esta escucha ya está en Room (el bucle la
            // guardó al cumplir 30 s): solo se actualiza su duración
            // final, sin crear otra fila.
            historyFlushedId = null
            historyFlushedAt = null
            scope.launch(Dispatchers.IO) {
                try {
                    db.libraryDao().updateHistoryDuration(id, at, ms)
                    historyDone = historyDone + id
                } catch (e: Exception) {
                    ErrorLog.log(context, "Historial", e)
                }
            }
            return
        }
        // Ronda 14: el id se marca como hecho DESPUÉS de que el insert
        // tenga éxito (antes se marcaba antes de lanzar la corrutina y
        // un insert fallido dejaba la escucha perdida sin reintento).
        historyPending = historyPending + id
        scope.launch(Dispatchers.IO) {
            try {
                db.libraryDao().addHistory(PlayHistoryEntity(id, System.currentTimeMillis(), ms))
                historyDone = historyDone + id
            } catch (e: Exception) {
                ErrorLog.log(context, "Historial", e)
            } finally {
                historyPending = historyPending - id
            }
        }
    }

    /** RONDA 85: guarda la escucha en curso en Room en cuanto cumple
     * 30 s y actualiza su duración cada 10 s (junto al guardado de
     * la cola). Si el sistema mata la actividad con la música
     * sonando, la escucha ya está guardada; se pierde como mucho
     * lo de los últimos 10 s. No marca historyDone: la duración
     * final la pone finalizeHistory al cambiar de pista o pausar. */
    fun flushHistoryProgress() {
        val id = listenedId ?: return
        val ms = listenedMs
        if (ms < 30_000L || id in historyDone || id in historyPending) return
        val at = historyFlushedAt
        if (at != null && historyFlushedId == id) {
            // RONDA 133: esta actualización periódica NO estaba marcada
            // en historyPending: si aterrizaba DESPUÉS de que
            // finalizeHistory escribiera la duración final, la
            // sobrescribía con un valor viejo y la escucha quedaba
            // corta. Marcada igual que la inserción, finalizeHistory
            // espera 500 ms y escribe la final después.
            historyPending = historyPending + id
            scope.launch(Dispatchers.IO) {
                try {
                    db.libraryDao().updateHistoryDuration(id, at, ms)
                } catch (_: Exception) {
                } finally {
                    historyPending = historyPending - id
                }
            }
            return
        }
        val now = System.currentTimeMillis()
        historyFlushedId = id
        historyFlushedAt = now
        historyPending = historyPending + id
        scope.launch(Dispatchers.IO) {
            try {
                db.libraryDao().addHistory(PlayHistoryEntity(id, now, ms))
            } catch (e: Exception) {
                ErrorLog.log(context, "Historial", e)
                historyFlushedId = null
                historyFlushedAt = null
            } finally {
                historyPending = historyPending - id
            }
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
    // Fase 2 — Auto-DJ: la radio ahora elige con autoDjQueue
    // (mismo artista > mismo género > mismo álbum > más
    // escuchadas, nunca repite), sobre canciones ENRIQUECIDAS con
    // sus reproducciones reales. Antes mezclaba a ciegas: mismo
    // artista, mismo género y resto, sin saber qué se escucha más.
    fun refillRadio() {
        val c = controller ?: return
        if (radioBusy) return
        radioBusy = true
        scope.launch {
            val picked: List<Song> = try {
                withContext(Dispatchers.IO) {
                    val settings = playerStore.settingsFlow.first()
                    // Ronda 17: la radio cargaba la biblioteca directa,
                    // sin el filtro de carpetas excluidas: podía sonar
                    // música de carpetas que el usuario escondió.
                    val excludedFolders = FolderStore(context).excludedFlow.first()
                    val rawSongs = MusicRepository(context).loadSongs(settings.minDurationMs)
                        .filter { !isInExcludedFolder(it.path, excludedFolders) }
                    val dao = db.libraryDao()
                    val stats = dao.statsPerSong().associateBy { it.songId }
                    val favs = dao.favoriteIdsFlow().first().toSet()
                    val songs = enrichSongs(rawSongs, favs, stats)
                    val currentId = c.currentMediaItem?.mediaId?.toLongOrNull()
                    val current = songs.firstOrNull { it.id == currentId }
                    val inQueue = (0 until c.mediaItemCount)
                        .mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }.toSet()
                    val excluded = inQueue + radioRecentIds.toSet()
                    autoDjQueue(current, songs, count = 10, recentlyPlayedIds = excluded)
                }
            } catch (e: Exception) {
                ErrorLog.log(context, "Radio local", e)
                emptyList()
            }
            if (picked.isNotEmpty()) {
                radioRecentIds = (radioRecentIds + picked.map { it.id }).takeLast(50)
                try {
                    val startIndex = c.mediaItemCount
                    c.addMediaItems(picked.map { songToMediaItem(context, it) })
                    c.seekTo(startIndex, 0)
                    c.play()
                } catch (e: Exception) {
                    ErrorLog.log(context, "Radio local", e)
                }
            } else if (playerSettings.localRadio) {
                // Ronda 4: el Auto-DJ se quedó sin candidatas; avisar
                // en vez de parar en silencio.
                Toast.makeText(context, "Auto-DJ terminó: no quedan canciones parecidas", Toast.LENGTH_SHORT).show()
            }
            radioBusy = false
        }
    }

    // Fase 2 app — Cast: al conectar con la TV, la pista online
    // que suena pasa al receptor y el audio local se pausa para no
    // sonar dos veces. Una pista local no tiene URL alcanzable: se
    // avisa y sigue sonando en el teléfono.
    fun transferCurrentTrackToCast() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        if (!item.mediaId.startsWith("yt:")) {
            Toast.makeText(
                context,
                "Conectado al Chromecast: las pistas locales suenan en el teléfono.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val url = item.localConfiguration?.uri?.toString() ?: return
        val meta = item.mediaMetadata
        val sent = castManager.loadOnlineTrack(
            streamUrl = url,
            title = meta.title?.toString() ?: "",
            artist = meta.artist?.toString() ?: "",
            durationMs = c.duration.takeIf { it > 0 } ?: 0L,
            coverUrl = meta.artworkUri?.toString(),
        )
        if (sent) c.pause()
    }

    DisposableEffect(castManager) {
        castManager.start(onSessionStarted = { transferCurrentTrackToCast() })
        onDispose { castManager.stop() }
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
                // Fase 2 — estilos de widget: la portada del álbum
                // (artworkUri del MediaItem) alimenta el estilo Con
                // portada; si cambia, el widget también se actualiza.
                val newArtUri = meta?.artworkUri?.toString()
                if (newTitle != lastWidgetTitle || newArtist != lastWidgetArtist ||
                    c.isPlaying != lastWidgetPlaying || newArtUri != lastWidgetArtUri
                ) {
                    lastWidgetTitle = newTitle
                    lastWidgetArtist = newArtist
                    lastWidgetPlaying = c.isPlaying
                    lastWidgetArtUri = newArtUri
                    AuroraWidgetProvider.saveNowPlaying(
                        context, newTitle, newArtist, c.isPlaying, meta?.artworkUri,
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
            private fun saveQueue(c: MediaController) { persistQueue(c) }

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

    // Fase 1b: agitar para pasar a la siguiente (OFF por defecto).
    // Solo salta si está sonando. El sensor se registra solo
    // mientras la app está en primer plano (DisposableEffect).
    // Fase 2 — gestos configurables: agitar hace lo que el usuario
    // eligió (siguiente, anterior, pausar/reanudar o favorito).
    DisposableEffect(playerSettings.shakeToNext, playerSettings.shakeAction, controller) {
        val c = controller
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var listener: SensorEventListener? = null
        if (playerSettings.shakeToNext && c != null && sensorManager != null && accelerometer != null) {
            val shakeAction = parseGestureAction(playerSettings.shakeAction, GestureAction.NEXT)
            val detector = ShakeDetector {
                when (shakeAction) {
                    GestureAction.NEXT -> c.seekToNext()
                    GestureAction.PREVIOUS -> c.seekToPrevious()
                    GestureAction.PLAY_PAUSE -> if (c.isPlaying) c.pause() else c.play()
                    GestureAction.FAVORITE -> toggleFavorite()
                    // Ronda 4: ±10 s; con duración desconocida no se
                    // recorta (recortar contra 0 llevaría al inicio).
                    GestureAction.FAST_FORWARD -> {
                        val target = c.currentPosition + 10_000L
                        c.seekTo(if (c.duration > 0) target.coerceAtMost(c.duration) else target)
                    }
                    GestureAction.REWIND -> c.seekTo((c.currentPosition - 10_000L).coerceAtLeast(0L))
                    GestureAction.TOGGLE_MUTE -> {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        if (am != null) {
                            val action = if (am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                                AudioManager.ADJUST_UNMUTE
                            } else {
                                AudioManager.ADJUST_MUTE
                            }
                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, action, AudioManager.FLAG_SHOW_UI)
                        }
                    }
                }
            }
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    detector.onSensorChanged(
                        event.values[0], event.values[1], event.values[2],
                        SystemClock.elapsedRealtime(),
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            if (listener != null) sensorManager?.unregisterListener(listener)
        }
    }

    // Reloj de progreso: actualiza la barra mientras suena.
    // ETAPA 7: en pausa y con el reproductor cerrado no hay nada que
    // actualizar: el bucle duerme sin leer el reproductor (ahorra
    // batería; la corrutina en sí no consume nada mientras duerme).
    // RONDA 122: isPlaying NO es clave del efecto: cada cambio de
    // play/pausa lo reiniciaba y lastTickMs volvía a empezar, así que
    // el intervalo entre el último tick y el reinicio no se sumaba al
    // plazo del temporizador y este avanzaba en pausa.
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        // Reloj monótono para medir el tiempo REAL entre iteraciones
        // (ronda 14): el bucle duerme 500 ms pero puede tardar más por
        // carga; contar 500 fijos infravaloraba lo escuchado y el
        // temporizador corría también en pausa.
        var lastTickMs = SystemClock.elapsedRealtime()
        var lastQueueSaveMs = SystemClock.elapsedRealtime()
        while (true) {
            try {
                val tickNow = SystemClock.elapsedRealtime()
                val tickDelta = (tickNow - lastTickMs).coerceAtLeast(0L)
                lastTickMs = tickNow
                if (c.isPlaying || showPlayer) {
                    positionMs = c.currentPosition
                durationMs = c.duration
                if (c.isPlaying) {
                    val id = c.currentMediaItem?.mediaId?.toLongOrNull()
                    if (id != null && id != listenedId) {
                        listenedId = id
                        // RONDA 58: el primer tick tras el cambio de pista
                        // solo inicializaba y no sumaba nada, así que una
                        // canción de 30 s justos se quedaba en ~29,5 s y
                        // no contaba como escucha. El intervalo del tick
                        // pertenece casi todo a la pista nueva (el cambio
                        // cayó dentro de él), así que se suma.
                        listenedMs = tickDelta
                        // RONDA 84: si el temporizador de fin de canción
                        // está armado y la pista CAMBIÓ, la canción
                        // terminó: se para aquí mismo. El chequeo de
                        // < 500 ms de abajo puede no verlo nunca: un
                        // tick tardío salta por encima del final, y con
                        // crossfade el cambio llega antes de que la
                        // pista vieja termine (el solape adelanta el
                        // cambio). Si el usuario pasó de pista a mano
                        // con el temporizador armado, también se para:
                        // pidió parar al final de la canción y la
                        // canción terminó; reanudar es un toque.
                        if (sleepAtSongEnd) {
                            c.pause()
                            sleepAtSongEnd = false
                            sleepTimer = SleepTimerOption.OFF
                        }
                        // RONDA 4 (2026-11-09): historyDone nunca se
                        // limpiaba: si una canción ya había contado
                        // una vez en esta sesión, sus siguientes
                        // escuchas no se registraban nunca más (ni en
                        // "Más escuchadas" ni en reproducciones).
                        historyDone = historyDone - id
                        // RONDA 85: la pista nueva empieza una escucha
                        // nueva; si quedaba una fila en curso de la
                        // pista vieja, ya la cerró finalizeHistory
                        // (el listener corre antes que este tick).
                        historyFlushedId = null
                        historyFlushedAt = null
                    } else if (id != null) {
                        listenedMs += tickDelta
                    }
                    // RONDA 39: guardar la posición cada 10 s mientras
                    // suena, para que un cierre forzoso retome cerca de
                    // donde iba (antes solo se guardaba al cambiar de
                    // pista o al pausar).
                    if (tickNow - lastQueueSaveMs >= 10_000L) {
                        lastQueueSaveMs = tickNow
                        persistQueue(c)
                        // RONDA 85: la escucha en curso se guarda en
                        // Room cada 10 s, por si el sistema mata la
                        // actividad con la música sonando.
                        flushHistoryProgress()
                    }
                }
                // Temporizador de apagado (ETAPA 5): por minutos o al final
                // de la canción. Nunca se activa solo.
                val until = sleepUntilMs
                if (until != null) {
                    if (!c.isPlaying) {
                        // En pausa el temporizador no corre: el plazo se
                        // desplaza lo mismo que duró la pausa.
                        sleepUntilMs = until + tickDelta
                    } else if (System.currentTimeMillis() >= until) {
                        c.pause()
                        sleepUntilMs = null
                        sleepTimer = SleepTimerOption.OFF
                    }
                }
                // Ronda 24: el umbral era < 1000 ms y cortaba hasta el
                // último segundo de la canción. Con ticks de 500 ms,
                // < 500 ms dispara como mucho medio segundo antes.
                if (sleepAtSongEnd && c.isPlaying && c.duration > 0 &&
                    c.duration - c.currentPosition < 500
                ) {
                    c.pause()
                    sleepAtSongEnd = false
                    sleepTimer = SleepTimerOption.OFF
                }
            }
            } catch (e: Exception) {
                // El controlador murió entre medias: se sale del bucle;
                // el efecto se reinicia cuando cambia el controlador.
                ErrorLog.log(context, "Progreso", e)
                break
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
        translatedLyrics = emptyList()
        qualityText = null
        canvasUrl = null
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
                    lyrics = loadOnlineLyrics(context, mediaId, track, artist, album, durationSec, lyricsProviders)
                }
            }
            // Traducción automática (2026-11-10, como BitChord).
            if (playerSettings.onlineMode && lyrics.isNotEmpty()) {
                translatedLyrics = LyricsTranslator.translateLines(
                    context, mediaId, lyrics.map { it.text },
                ) ?: emptyList()
            }
            return@LaunchedEffect
        }
        val id = mediaId.toLongOrNull() ?: return@LaunchedEffect
        lyricsOffsetMs = try {
            offsetStore.offsetFor(id)
        } catch (_: Exception) {
            // offsetFor devuelve Int: el 0 tiene que ser Int, no Long
            // (run #292: el 0L hacía el try de tipo Number).
            0
        }
        lyrics = loadLyricsForPath(context, currentSongPath ?: "")
        // RONDA 2: línea de calidad real (códec/bitrate/muestreo);
        // solo se muestra si hay al menos un dato real.
        // La canción se reconstruye del MediaItem actual (uri +
        // mimeType son lo único que usa loadSongDetails); no hay una
        // lista de canciones en este ámbito para buscarla.
        val mediaItem = controller?.currentMediaItem
        val songForDetails = mediaItem?.localConfiguration?.let { config ->
            Song(
                id = id,
                title = currentTitle ?: "",
                artist = currentArtist ?: "",
                album = currentAlbum ?: "",
                albumId = 0L,
                durationMs = controller?.duration ?: 0L,
                path = currentSongPath ?: "",
                uri = config.uri,
                mimeType = config.mimeType ?: "",
                sizeBytes = 0L,
                dateAdded = 0L,
            )
        }
        qualityText = songForDetails?.let { song ->
            // Ronda USB Serie B: un DSD muestra su tasa real (DoP),
            // no lo que diga el retriever (que no lee DSD).
            val dsdInfo = loadDsdInfo(context, song)
            if (dsdInfo != null) return@let dsdQualityLine(dsdInfo)
            val details = loadSongDetails(context, song)
            if (details.bitrateKbps == null && details.sampleRateHz == null) {
                null
            } else {
                val parts = mutableListOf(codecDisplayName(song.mimeType, song.path))
                details.bitrateKbps?.let { parts.add("$it kbps") }
                details.sampleRateHz?.let { parts.add("${it / 1000} kHz") }
                parts.joinToString(" · ")
            }
        }
        if (lyrics.isEmpty() && playerSettings.onlineMode) {
            val meta = controller?.currentMediaItem?.mediaMetadata
            val track = meta?.title?.toString().orEmpty()
            val artist = meta?.artist?.toString().orEmpty()
            val album = meta?.albumTitle?.toString()
            val durationSec = (controller?.duration ?: 0L).let {
                if (it > 0) it / 1000 else 0L
            }
            if (track.isNotBlank() && artist.isNotBlank()) {
                lyrics = loadOnlineLyrics(context, id.toString(), track, artist, album, durationSec, lyricsProviders)
            }
        }
        // Precarga (idea de BitChord, 2026-11-09): mientras suena la
        // actual, la letra de la siguiente se busca en segundo plano
        // (solo con el modo online encendido) y queda en LyricsCache,
        // así que al empezar la siguiente ya está ahí, antes de que
        // empiece a sonar. El resultado se descarta: loadOnlineLyrics
        // escribe en la caché. Si cambia la canción, el efecto se
        // reinicia y la precarga vieja se cancela.
        if (playerSettings.onlineMode) {
            val c = controller
            if (c != null) {
                val next = nextIndexToPrefetch(
                    c.currentMediaItemIndex,
                    c.mediaItemCount,
                    c.repeatMode == Player.REPEAT_MODE_ALL,
                )
                if (next != null) {
                    launch {
                        val item = try {
                            c.getMediaItemAt(next)
                        } catch (_: Exception) {
                            // La cola cambió antes de leerla: no hay
                            // nada que precargar.
                            return@launch
                        }
                        val meta = item.mediaMetadata
                        val track = meta.title?.toString().orEmpty()
                        val artist = meta.artist?.toString().orEmpty()
                        if (track.isNotBlank() && artist.isNotBlank()) {
                            val album = meta.albumTitle?.toString()
                            val window = Timeline.Window()
                            val durationSec = try {
                                c.currentTimeline.getWindow(next, window).durationMs.let {
                                    if (it > 0) it / 1000 else 0L
                                }
                            } catch (_: Exception) {
                                0L
                            }
                            loadOnlineLyrics(context, item.mediaId, track, artist, album, durationSec, lyricsProviders)
                        }
                    }
                }
            }
        }
        // Canvas de Spotify (Fase 2): si el modo online está
        // encendido y Spotify conectado, busca el id de la canción
        // y pide su Canvas; solo vídeo (la imagen no aporta nada
        // sobre la portada). Si cambia la canción, el efecto se
        // reinicia y la búsqueda vieja se cancela.
        try {
            if (playerSettings.onlineMode && spotifyConnected) {
                val meta = controller?.currentMediaItem?.mediaMetadata
                val track = meta?.title?.toString().orEmpty()
                val artist = meta?.artist?.toString().orEmpty()
                if (track.isNotBlank() && artist.isNotBlank()) {
                    val token = SpotifyClient().fetchAccessToken(spotifyCookie)
                    if (token != null) {
                        val trackId = SpotifyCanvasClient().searchTrackId(token, track, artist)
                        val canvas = trackId?.let { SpotifyCanvasClient().fetchCanvas(token, it) }
                        if (canvas?.isVideo == true) canvasUrl = canvas.url
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ErrorLog.log(context, "CANVAS", e)
        }
        // Traducción automática (2026-11-10, como BitChord).
        if (playerSettings.onlineMode && lyrics.isNotEmpty()) {
            translatedLyrics = LyricsTranslator.translateLines(
                context, id.toString(), lyrics.map { it.text },
            ) ?: emptyList()
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
        // RONDA 106: no marcar la restauración como hecha ANTES de
        // hacerla: si leer la cola o reconstruirla fallaba, la sesión
        // se quedaba sin cola y sin reintento (ni al reconectar el
        // controlador). Solo se marca al terminar bien o cuando no hay
        // nada que restaurar; si falla, el próximo controlador
        // reintenta.
        val settings = playerStore.settingsFlow.first()
        if (!settings.resumeWhereLeft) { queueRestored = true; return@LaunchedEffect }
        val saved = queueStore.queueFlow.first()
        if (saved.songIds.isEmpty()) { queueRestored = true; return@LaunchedEffect }
        val songs = withContext(Dispatchers.IO) {
            // RONDA 4 (2026-11-09): restauraba con la duración
            // mínima por defecto (5 s), no con la del usuario: la
            // cola podía traer canciones que la biblioteca no muestra.
            MusicRepository(context).loadSongs(settings.minDurationMs)
        }
        val ordered = saved.songIds.mapNotNull { id -> songs.firstOrNull { it.id == id } }
        if (ordered.isEmpty()) { queueRestored = true; return@LaunchedEffect }
        // Ronda 18: el índice guardado es sobre la cola original;
        // si se borraron canciones anteriores, el índice numérico
        // apuntaba a otra canción. Se busca la canción actual por
        // su ID; si ya no está, se cae al hueco que dejó: cuántas
        // de las anteriores siguen vivas (esa es la siguiente
        // canción, no el índice numérico de antes, que se pasaba
        // de largo cuando también se borraron anteriores).
        val currentId = saved.songIds.getOrNull(saved.index)
        val restoreIndex = currentId?.let { id ->
            ordered.indexOfFirst { it.id == id }
        }?.takeIf { it >= 0 } ?: saved.songIds.take(saved.index)
            .count { id -> ordered.any { it.id == id } }
            .coerceIn(0, ordered.size - 1)
        try {
            c.setMediaItems(
                ordered.map { songToMediaItem(context, it) },
                restoreIndex,
                saved.positionMs,
            )
            c.prepare()
            queueRestored = true
        } catch (e: Exception) {
            ErrorLog.log(context, "RESTORE-QUEUE", e)
        }
    }

    // Comparte el registro de errores sanitizado (Ajustes > Avanzadas):
    // sin él, un crash en el teléfono es invisible desde aquí. El
    // registro nunca lleva rutas, títulos ni datos personales.
    fun shareErrorLog() {
        try {
            val text = ErrorLog.buildReportText(context)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Aurora Player — registro de errores")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "Compartir registro de errores"))
        } catch (_: Exception) {
            // Sin app para compartir no pasa nada.
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        if (castManager.isConnected) {
            Toast.makeText(
                context,
                "Solo las pistas online se pueden enviar al Chromecast.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        // indexOfFirst devuelve -1 si la canción no está en la lista
        // (duplicados exactos): nunca se pasa un índice inválido al
        // reproductor.
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        // RONDA 2026-11-10 (crash al tocar una canción): si el
        // controlador muere entre medias, la app no se cierra: se
        // registra y se queda en la biblioteca.
        try {
            c.setMediaItems(songs.map { songToMediaItem(context, it) }, safeIndex, 0)
            c.prepare()
            c.play()
        } catch (e: Exception) {
            ErrorLog.log(context, "PLAY", e)
        }
    }

    // SPEC 005 FR-007: colas guardadas con nombre (hasta 20). La
    // cola activa sigue en QueueStore; estas son plantillas.
    fun saveCurrentQueueAs(name: String) {
        val c = controller ?: return
        scope.launch {
            val dao = db.namedQueueDao()
            // Las pistas online ("yt:...") no son canciones locales:
            // no se guardan (el mensaje cuenta solo las guardadas).
            val ids = (0 until c.mediaItemCount)
                .mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
            if (ids.isEmpty()) {
                savedQueueMessage = "La cola actual está vacía."
                return@launch
            }
            // Ronda 18: el límite se comprobaba ANTES de buscar si
            // el nombre ya existía: con 20 colas no se podía
            // actualizar una cola existente. El límite solo vale
            // para nombres nuevos.
            val existing = dao.allQueues()
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (existing == null && dao.count() >= MAX_NAMED_QUEUES) {
                savedQueueMessage = "Ya tienes $MAX_NAMED_QUEUES colas guardadas."
                return@launch
            }
            if (existing != null) {
                dao.setSongs(existing.id, ids)
                dao.touchQueue(existing.id, System.currentTimeMillis())
                savedQueueMessage = "Cola «$name» actualizada con ${ids.size} canciones."
            } else {
                val id = dao.insertQueue(NamedQueueEntity(name = name))
                dao.setSongs(id, ids)
                savedQueueMessage = "Cola «$name» guardada con ${ids.size} canciones."
            }
        }
    }

    fun loadSavedQueue(queueId: Long) {
        scope.launch {
            val ids = db.namedQueueDao().songIdsOf(queueId)
            if (ids.isEmpty()) {
                savedQueueMessage = "Esa cola está vacía."
                return@launch
            }
            val settings = playerStore.settingsFlow.first()
            val songs = withContext(Dispatchers.IO) {
                MusicRepository(context).loadSongs(settings.minDurationMs)
            }
            val ordered = ids.mapNotNull { id -> songs.firstOrNull { it.id == id } }
            if (ordered.isEmpty()) {
                savedQueueMessage = "Esa cola ya no tiene canciones en tu biblioteca."
                return@launch
            }
            playSongs(ordered, 0)
            val missing = ids.size - ordered.size
            savedQueueMessage = if (missing > 0) {
                "Cola cargada: ${ordered.size} canciones ($missing ya no están en tu biblioteca)."
            } else {
                "Cola cargada: ${ordered.size} canciones."
            }
        }
    }

    fun deleteSavedQueue(queueId: Long) {
        scope.launch {
            db.namedQueueDao().deleteQueueWithSongs(queueId)
            savedQueueMessage = "Cola borrada."
        }
    }

    /** 002-online-youtube: suena una pista de YouTube con su URL ya
     * resuelta (las URLs caducan: se resuelven al tocar, nunca se
     * guardan). El mediaId "yt:<videoId>" la marca como online: no
     * entra en el historial ni en la cola persistente local. */
    fun onlineMediaItem(track: OnlineTrack, streamUrl: String): MediaItem =
        MediaItem.Builder()
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

    fun playOnlineTrack(track: OnlineTrack, streamUrl: String) {
        val c = controller ?: return
        // Como playSongs: si el controlador muere entre medias, la app
        // no se cierra (auditoría Codex, 2026-11-10).
        try {
            c.setMediaItems(listOf(onlineMediaItem(track, streamUrl)))
            c.prepare()
            if (castManager.isConnected) {
                castManager.loadOnlineTrack(
                    streamUrl = streamUrl,
                    title = track.title,
                    artist = track.artist,
                    durationMs = (track.durationSec ?: 0L) * 1000L,
                    coverUrl = track.thumbnailUrl,
                )
            } else {
                c.play()
            }
        } catch (e: Exception) {
            ErrorLog.log(context, "PLAY-ONLINE", e)
        }
    }

    // Fase 2 app — YouTube con vídeo: baja los subtítulos (es a
    // mano, si los hay), abre el diálogo y suena el vídeo por el
    // reproductor de siempre. Sin subtítulos, el vídeo se ve igual.
    // Va DESPUÉS de playOnlineTrack: una función local no puede
    // llamar a otra que se declara después.
    fun playVideo(track: OnlineTrack, video: ResolvedVideo) {
        scope.launch {
            val captions = selectCaptionTrack(video.captions)
                ?.let { fetchCaptions(it.baseUrl) } ?: emptyList()
            videoTrack = track
            videoCaptions = captions
            playOnlineTrack(track, video.url)
        }
    }

    // Fase 2 — Spotify como BitChord: una lista importada suena por
    // YouTube Music, canción por canción, en la cola de reproducción.
    fun playOnlineTracks(tracks: List<Pair<OnlineTrack, String>>) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        // Como playSongs: si el controlador muere entre medias, la app
        // no se cierra (auditoría Codex, 2026-11-10).
        try {
            c.setMediaItems(tracks.map { (track, url) -> onlineMediaItem(track, url) })
            c.prepare()
            c.play()
        } catch (e: Exception) {
            ErrorLog.log(context, "PLAY-ONLINE", e)
        }
    }

    // Fase 2 app — YouTube con vídeo: el diálogo va por encima de
    // todo (también del reproductor); al cerrarlo el audio sigue.
    videoTrack?.let { track ->
        VideoPlayerDialog(
            track = track,
            captions = videoCaptions,
            controller = controller,
            onDismiss = { videoTrack = null },
        )
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
            canvasUrl = canvasUrl,
            songUri = songUri,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            onClose = { showPlayer = false },
            castDeviceName = (castState as? CastState.Connected)?.deviceName,
            onPlayPause = { controllerCall("Reproductor") { if (isPlaying) pause() else play() } },
            onPrevious = { controllerCall("Reproductor") { seekToPreviousMediaItem() } },
            onNext = { skipToNext() },
            onSeek = { controllerCall("Reproductor") { seekTo(it) } },
            onToggleShuffle = { controllerCall("Reproductor") { shuffleModeEnabled = !shuffleModeEnabled } },
            onCycleRepeat = {
                controllerCall("Reproductor") {
                    repeatMode = when (repeatMode) {
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
                controllerCall("Cola") { seekTo(index, 0); play() }
            },
            onQueueRemove = { index ->
                controllerCall("Cola") { removeMediaItem(index) }
            },
            onQueueMove = { from, to ->
                controllerCall("Cola") {
                    if (to in 0 until mediaItemCount) moveMediaItem(from, to)
                }
            },
            savedQueues = savedQueues,
            savedQueueMessage = savedQueueMessage,
            onSaveQueue = { saveCurrentQueueAs(it) },
            onLoadSavedQueue = { loadSavedQueue(it) },
            onDeleteSavedQueue = { deleteSavedQueue(it) },
            onSavedQueueMessageConsumed = { savedQueueMessage = null },
            lyrics = lyrics,
            translatedLyrics = translatedLyrics,
            qualityText = qualityText,
            onlineMode = playerSettings.onlineMode,
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
            doubleTapAction = parseGestureAction(playerSettings.doubleTapAction, GestureAction.FAVORITE),
            karaokeMode = playerSettings.karaokeMode,
            volumeGestureEnabled = playerSettings.volumeGesture,
            onSaveLyrics = { edited ->
                // Fase 1b: editor de letras. Guarda el .lrc junto a
                // la canción y recarga para mostrar lo guardado.
                scope.launch(Dispatchers.IO) {
                    try {
                        val mediaItem = controller?.currentMediaItem
                        val song = mediaItem?.localConfiguration?.let { config ->
                            Song(
                                id = currentSongId ?: 0L,
                                title = currentTitle ?: "",
                                artist = currentArtist ?: "",
                                album = currentAlbum ?: "",
                                albumId = 0L,
                                durationMs = controller?.duration ?: 0L,
                                path = currentSongPath ?: "",
                                uri = config.uri,
                                mimeType = config.mimeType ?: "",
                                sizeBytes = 0L,
                                dateAdded = 0L,
                            )
                        } ?: return@launch
                        val result = saveLyricsForSong(context, song, edited)
                        result.onFailure {
                            ErrorLog.log(context, "Guardar letras", it as? Exception ?: Exception(it.message))
                        }
                        lyrics = loadLyricsForPath(context, currentSongPath ?: "")
                    } catch (e: Exception) {
                        ErrorLog.log(context, "Guardar letras", e)
                    }
                }
            },
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
                                controllerCall("Reproductor") { if (isPlaying) pause() else play() }
                            },
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            )
                        }
                        IconButton(onClick = { skipToNext() }) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = "Siguiente",
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
                onPlayVideo = { track, video -> playVideo(track, video) },
                spotifyConnected = spotifyConnected,
                onImportOnlineTracks = { tracks -> playOnlineTracks(tracks) },
                ytMusicCookies = ytMusicCookies,
                ytMusicToken = ytMusicToken,
                onYtTokenRejected = onYtTokenRejected,
                onYtCookiesRejected = onYtCookiesRejected,
                streamQuality = playerSettings.streamQuality,
                onOpenSettings = { settingsSection = SettingsSection.NETWORK; tab = Tab.SETTINGS },
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
            Tab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                initialSection = settingsSection,
                onInitialSectionConsumed = { settingsSection = null },
                onOpenEq = { showEq = true },
                onImportTracks = { tracks -> playOnlineTracks(tracks) },
                onShareErrorLog = { shareErrorLog() },
                ytMusicConnected = ytMusicCookies.isNotBlank() || ytMusicToken.isNotBlank(),
                onConnectYouTubeMusic = { cookies ->
                    scope.launch { ytMusicStore.setCookies(cookies) }
                },
                onConnectYouTubeMusicToken = { token ->
                    scope.launch { ytMusicStore.setToken(token) }
                },
                onDisconnectYouTubeMusic = {
                    scope.launch { ytMusicStore.clear() }
                },
                ytMusicCookies = ytMusicCookies,
            )
        }
    }
}
