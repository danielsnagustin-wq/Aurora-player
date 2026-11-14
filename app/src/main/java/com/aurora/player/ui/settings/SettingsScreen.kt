package com.aurora.player.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.alarm.AlarmScreen
import com.aurora.player.audio.AtmosSupport
import com.aurora.player.audio.detectAtmosSupport
import com.aurora.player.data.BackupData
import com.aurora.player.data.BackupValidation
import com.aurora.player.data.FolderStore
import com.aurora.player.data.LibraryPrefsStore
import com.aurora.player.data.ThemePresetStore
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.SoundCloudStore
import com.aurora.player.data.SpotifyStore
import com.aurora.player.online.SpotifyClient
import com.aurora.player.online.fetchSoundCloudMe
import com.aurora.player.online.fetchSoundCloudWebClientId
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.data.validateBackup
import com.aurora.player.model.CardStyle
import com.aurora.player.model.CoverShape
import com.aurora.player.model.ListDensity
import com.aurora.player.model.PerformanceProfile
import com.aurora.player.model.applyTo
import com.aurora.player.model.PlayerBackground
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.TextSize
import com.aurora.player.model.ThemeMode
import com.aurora.player.model.ThemeSettings
import com.aurora.player.online.JsonParser
import com.aurora.player.online.OnlineTrack
import com.aurora.player.ui.theme.PureBlack
import com.aurora.player.ui.theme.auroraButtonColors
import com.aurora.player.ui.theme.brightnessOf
import com.aurora.player.ui.theme.parseHexOrNull
import com.aurora.player.util.BackupManager
import com.aurora.player.util.DiagnosticsSnapshot
import com.aurora.player.util.ErrorLog
import com.aurora.player.util.FrameRateMeter
import com.aurora.player.util.RestoreReport
import com.aurora.player.util.collectSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aurora Player — Ajustes (Etapa 1, tema + colores).
 *
 * Regla del usuario: cada ajuste con efecto real.
 * - Modo de tema (Claro/Oscuro/AMOLED/Sistema) y TODOS los colores
 *   se aplican AL TOCAR: se guardan en DataStore al instante, sin
 *   botón "Aplicar" que bloquee nada. La lista hace scroll y los
 *   chips usan FlowRow (nada se corta ni se escribe en vertical).
 * - ETAPA 6: tamaño de texto, forma de portada, densidad de lista
 *   y Material You. Todo tiene efecto real (ver ui/theme/Theme.kt).
 *   La personalización por HEX de cada parte (fondo, tarjetas,
 *   textos...) se quitó en la ronda de pulido (2026-11-09): el
 *   color principal (HEX) sigue, y el tema usa sus colores.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenEq: () -> Unit = {},
    onImportTracks: (List<Pair<OnlineTrack, String>>) -> Unit = {},
    onShareErrorLog: () -> Unit = {},
    ytMusicConnected: Boolean = false,
    onConnectYouTubeMusic: (String) -> Unit = {},
    onDisconnectYouTubeMusic: () -> Unit = {},
    ytMusicCookies: String? = null,
    // Ronda 7 (Gemini): abrir Ajustes directo en una sección.
    initialSection: SettingsSection? = null,
    onInitialSectionConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { ThemeSettingsStore(context) }
    val playerStore = remember { PlayerSettingsStore(context) }
    val scope = rememberCoroutineScope()
    val applied by store.settingsFlow.collectAsState(initial = ThemeSettings.DEFAULT)
    val player by playerStore.settingsFlow.collectAsState(initial = PlayerSettings.DEFAULT)
    // Slice 5 paso 5: soporte Atmos del dispositivo, calculado una
    // sola vez (la sonda toca AudioCapabilities y la lista de
    // códecs). Solo decide si se muestra el interruptor.
    val atmosSupported = remember(context) {
        detectAtmosSupport(context) == AtmosSupport.ATMOS
    }
    var hexText by rememberSaveable(applied.accentHex) { mutableStateOf(applied.accentHex) }
    // RONDA 7 P4: alarma musical.
    // RONDA CAZA 2026-11-13 (P1): con remember se perdía al girar.
    var showAlarm by rememberSaveable { mutableStateOf(false) }
    // RONDA PULIDO (2026-11-09): Ajustes por categorías estilo
    // AIMP/Musicolet: null muestra la lista de categorías; al tocar
    // una se abre solo esa sección. RONDA CAZA 2026-11-13 (P1): con
    // remember se perdía al girar; el enum va por su nombre.
    var section by rememberSaveable(
        saver = Saver<androidx.compose.runtime.MutableState<SettingsSection?>, String>(
            save = { it.value?.name },
            restore = {
                mutableStateOf(runCatching { SettingsSection.valueOf(it) }.getOrNull())
            },
        ),
    ) { mutableStateOf<SettingsSection?>(null) }
    // Ronda 7 (Gemini): si quien abre Ajustes pidió una sección,
    // se entra directo en ella y se consume el pedido.
    LaunchedEffect(initialSection) {
        if (initialSection != null) {
            section = initialSection
            onInitialSectionConsumed()
        }
    }
    // SPEC 007 (2026-11-09): editor de color + Mis temas.
    val presetStore = remember { ThemePresetStore(context) }
    val recentColors by presetStore.recentColorsFlow.collectAsState(initial = emptyList())
    var editingColor by rememberSaveable { mutableStateOf<String?>(null) }
    var previewMode by rememberSaveable { mutableStateOf(0) } // 0 biblioteca, 1 reproductor
    // RONDA 2: inicio personalizable (pestañas visibles + portada en fila).
    val libraryPrefs = remember { LibraryPrefsStore(context) }
    val visibleTabs by libraryPrefs.visibleTabsFlow.collectAsState(
        initial = LibraryPrefsStore.ALL_TABS,
    )
    // Reordenar pestañas (copia BitChord, slice 2): orden guardado.
    val tabOrder by libraryPrefs.tabOrderFlow.collectAsState(
        initial = (0 until LibraryPrefsStore.TAB_COUNT).toList(),
    )
    val rowCoverDp by libraryPrefs.rowCoverSizeFlow.collectAsState(
        initial = LibraryPrefsStore.DEFAULT_ROW_COVER_DP,
    )

    // Fase 2 — Spotify como BitChord (cookie sp_dc, solo en el teléfono).
    val spotifyStore = remember { SpotifyStore(context) }
    val spotifyConnected by spotifyStore.connectedFlow.collectAsState(initial = false)
    var spotifyStatus by remember { mutableStateOf<String?>(null) }

    // Descubrimiento — SoundCloud (token oauth_token, solo en el teléfono).
    val soundCloudStore = remember { SoundCloudStore(context) }
    val soundCloudConnected by soundCloudStore.connectedFlow.collectAsState(initial = false)
    var soundCloudStatus by remember { mutableStateOf<String?>(null) }

    fun save(next: ThemeSettings) {
        scope.launch { store.save(next) }
    }

    fun savePlayer(next: PlayerSettings) {
        scope.launch { playerStore.save(next) }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (showAlarm) {
            AlarmScreen(onClose = { showAlarm = false })
            return@Column
        }
        if (section == null) {
            Text("Ajustes", style = MaterialTheme.typography.titleLarge)
            SettingsHome(onOpen = { section = it })
            return@Column
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { section = null }) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Volver a Ajustes",
                )
            }
            Text(
                section?.title ?: "Ajustes",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (section == SettingsSection.INTERFACE) {
            SettingsInterfaceSection(
                applied = applied,
                onSaveTheme = { save(it) },
                hexText = hexText,
                onHexTextChange = { hexText = it },
                editingColor = editingColor,
                onEditingColorChange = { editingColor = it },
                recentColors = recentColors,
                presetStore = presetStore,
                previewMode = previewMode,
                onPreviewModeChange = { previewMode = it },
                onResetTheme = { scope.launch { store.resetToDefault() } },
            )
        }

        if (section == SettingsSection.ADVANCED) {
            SettingsAdvancedSection(
                onResetDefaults = {
                    scope.launch {
                        store.resetToDefault()
                        playerStore.resetToDefault()
                    }
                },
                onShareErrorLog = onShareErrorLog,
            )
        }

        if (section == SettingsSection.SOUND) {
            SettingsSoundSection(
                player = player,
                onSavePlayer = { savePlayer(it) },
                onOpenEq = onOpenEq,
                atmosSupported = atmosSupported,
            )
        }
        if (section == SettingsSection.PLAYBACK) {
            SettingsPlaybackSection(
                player = player,
                onSavePlayer = { savePlayer(it) },
            )
        }

        if (section == SettingsSection.CONTROL) {
            SettingsControlSection(
                player = player,
                onSavePlayer = { savePlayer(it) },
            )
        }

        if (section == SettingsSection.LIBRARY) {
            SettingsLibrarySection(
                player = player,
                onSavePlayer = { savePlayer(it) },
                visibleTabs = visibleTabs,
                onSetTabVisible = { index, visible ->
                    scope.launch { libraryPrefs.setTabVisible(index, visible) }
                },
                tabOrder = tabOrder,
                onSaveTabOrder = { order ->
                    scope.launch { libraryPrefs.saveTabOrder(order) }
                },
                rowCoverDp = rowCoverDp,
                onSetRowCoverSize = { dp ->
                    scope.launch { libraryPrefs.setRowCoverSize(dp) }
                },
            )
        }

        // Spotify: conectar/desconectar vive aquí porque lo usa
        // la pantalla Cuenta e integraciones (ronda 7: Red ya no
        // lo duplica). El tipo explícito () -> Unit es necesario:
        // sin él, la lambda devuelve el Job de scope.launch y no
        // compila (run #286: Argument type mismatch, líneas 284 y 297).
        val disconnectSpotify: () -> Unit = {
            scope.launch {
                try {
                    spotifyStore.clear()
                    spotifyStatus = null
                } catch (e: Exception) {
                    ErrorLog.log(context, "Desconectar Spotify", e)
                    spotifyStatus = "No se pudo desconectar. Inténtalo de nuevo."
                }
            }
        }
        val connectSpotify: (String) -> Unit = { cookie ->
            scope.launch {
                spotifyStatus = "Comprobando la sesión…"
                try {
                    val token = SpotifyClient().fetchAccessToken(cookie.trim())
                    if (token != null) {
                        spotifyStore.setCookie(cookie.trim())
                        spotifyStatus = "Conectado."
                    } else {
                        spotifyStatus = "Esa sesión no funcionó. Vuelve a iniciar sesión con Spotify."
                    }
                } catch (e: Exception) {
                    // fetchAccessToken ya devuelve null ante fallos de
                    // red; esto cubre el guardado de la cookie, que
                    // también puede fallar y antes cerraba la app.
                    ErrorLog.log(context, "Conectar Spotify", e)
                    spotifyStatus = "No se pudo conectar. Inténtalo de nuevo."
                }
            }
        }
        // SoundCloud: el token se comprueba contra /me antes de
        // guardarlo; sin client_id web no hay nada que pedir.
        val disconnectSoundCloud: () -> Unit = {
            scope.launch {
                try {
                    soundCloudStore.clear()
                    soundCloudStatus = null
                } catch (e: Exception) {
                    ErrorLog.log(context, "Desconectar SoundCloud", e)
                    soundCloudStatus = "No se pudo desconectar. Inténtalo de nuevo."
                }
            }
        }
        val connectSoundCloud: (String) -> Unit = { token ->
            scope.launch {
                soundCloudStatus = "Comprobando la sesión…"
                try {
                    val clientId = fetchSoundCloudWebClientId()
                    val me = clientId?.let { fetchSoundCloudMe(it, token.trim()) }
                    if (me != null) {
                        soundCloudStore.setToken(token.trim())
                        soundCloudStatus = "Conectado."
                    } else {
                        soundCloudStatus = "Esa sesión no funcionó. Vuelve a iniciar sesión con SoundCloud."
                    }
                } catch (e: Exception) {
                    ErrorLog.log(context, "Conectar SoundCloud", e)
                    soundCloudStatus = "No se pudo conectar. Inténtalo de nuevo."
                }
            }
        }
        if (section == SettingsSection.INTEGRATIONS) {
            IntegrationsScreen(
                spotifyConnected = spotifyConnected,
                spotifyStatus = spotifyStatus,
                onConnectSpotify = connectSpotify,
                onDisconnectSpotify = disconnectSpotify,
                onImportTracks = onImportTracks,
                ytMusicConnected = ytMusicConnected,
                onConnectYouTubeMusic = onConnectYouTubeMusic,
                onDisconnectYouTubeMusic = onDisconnectYouTubeMusic,
                ytMusicCookies = ytMusicCookies,
                soundCloudConnected = soundCloudConnected,
                soundCloudStatus = soundCloudStatus,
                onConnectSoundCloud = connectSoundCloud,
                onDisconnectSoundCloud = disconnectSoundCloud,
                wifiStreamQuality = player.wifiStreamQuality,
                mobileStreamQuality = player.mobileStreamQuality,
            )
        }

        if (section == SettingsSection.NETWORK) {
            SettingsNetworkSection(
                player = player,
                onSavePlayer = { savePlayer(it) },
            )
        }

        if (section == SettingsSection.ALARM) {
            // RONDA 7 P4: alarma musical.
            SettingsAlarmSection(onShowAlarm = { showAlarm = true })
        }

        if (section == SettingsSection.BACKUP) {
            SettingsBackupSection()
        }


    }
}

/**
 * RONDA PULIDO (2026-11-09): categorías de Ajustes estilo
 * AIMP/Musicolet: icono + título + descripción. Cada una abre solo
 * su sección; nada de un scroll infinito con todo amontonado.
 */
enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    INTEGRATIONS(
        "Cuenta e integraciones",
        "YouTube Music y Spotify: tus cuentas y tus listas",
        Icons.Filled.AccountCircle,
    ),
    INTERFACE(
        "Interfaz",
        "Tema, colores, texto, portadas, diseño y rendimiento",
        Icons.Filled.Palette,
    ),
    SOUND(
        "Motor de sonido",
        "Ecualizador, crossfade, balance y normalización",
        Icons.Filled.GraphicEq,
    ),
    PLAYBACK(
        "Reproducción",
        "Pausar al quitar auriculares, reanudar cola, radio local",
        Icons.Filled.PlayArrow,
    ),
    CONTROL(
        "Control",
        "Auriculares, botones y gestos del reproductor",
        Icons.Filled.Headphones,
    ),
    LIBRARY(
        "Biblioteca",
        "Duración mínima, pestañas, portadas y carpetas excluidas",
        Icons.Filled.LibraryMusic,
    ),
    NETWORK(
        "Red",
        "Modo online: letras y YouTube (opcional, apagado)",
        Icons.Filled.Public,
    ),
    ALARM(
        "Alarma",
        "Despiértate con tu música",
        Icons.Filled.Timer,
    ),
    BACKUP(
        "Respaldo",
        "Copia de seguridad y restauración",
        Icons.Filled.Backup,
    ),
    ADVANCED(
        "Avanzadas",
        "Restablecer por defecto",
        Icons.Filled.Settings,
    ),
}

@Composable
private fun SettingsHome(onOpen: (SettingsSection) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        for (section in SettingsSection.entries) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onOpen(section) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        section.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
