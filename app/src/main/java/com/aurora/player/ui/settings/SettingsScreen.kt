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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.alarm.AlarmScreen
import com.aurora.player.data.BackupData
import com.aurora.player.data.BackupValidation
import com.aurora.player.data.FolderStore
import com.aurora.player.data.LibraryPrefsStore
import com.aurora.player.data.ThemePresetStore
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.SpotifyStore
import com.aurora.player.online.SpotifyClient
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
) {
    val context = LocalContext.current
    val store = remember { ThemeSettingsStore(context) }
    val playerStore = remember { PlayerSettingsStore(context) }
    val scope = rememberCoroutineScope()
    val applied by store.settingsFlow.collectAsState(initial = ThemeSettings.DEFAULT)
    val player by playerStore.settingsFlow.collectAsState(initial = PlayerSettings.DEFAULT)
    var hexText by remember(applied.accentHex) { mutableStateOf(applied.accentHex) }
    // RONDA 7 P4: alarma musical.
    var showAlarm by remember { mutableStateOf(false) }
    // RONDA PULIDO (2026-11-09): Ajustes por categorías estilo
    // AIMP/Musicolet: null muestra la lista de categorías; al tocar
    // una se abre solo esa sección.
    var section by remember { mutableStateOf<SettingsSection?>(null) }
    // SPEC 007 (2026-11-09): editor de color + Mis temas.
    val presetStore = remember { ThemePresetStore(context) }
    val recentColors by presetStore.recentColorsFlow.collectAsState(initial = emptyList())
    var editingColor by remember { mutableStateOf<String?>(null) }
    var previewMode by remember { mutableStateOf(0) } // 0 biblioteca, 1 reproductor
    // RONDA 2: inicio personalizable (pestañas visibles + portada en fila).
    val libraryPrefs = remember { LibraryPrefsStore(context) }
    val visibleTabs by libraryPrefs.visibleTabsFlow.collectAsState(
        initial = LibraryPrefsStore.ALL_TABS,
    )
    val rowCoverDp by libraryPrefs.rowCoverSizeFlow.collectAsState(
        initial = LibraryPrefsStore.DEFAULT_ROW_COVER_DP,
    )

    // Fase 2 — Spotify como BitChord (cookie sp_dc, solo en el teléfono).
    val spotifyStore = remember { SpotifyStore(context) }
    val spotifyConnected by spotifyStore.connectedFlow.collectAsState(initial = false)
    var spotifyCookieText by remember { mutableStateOf("") }
    var spotifyStatus by remember { mutableStateOf<String?>(null) }

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
                rowCoverDp = rowCoverDp,
                onSetRowCoverSize = { dp ->
                    scope.launch { libraryPrefs.setRowCoverSize(dp) }
                },
            )
        }

        // Spotify: conectar/desconectar vive aquí porque lo usan
        // la sección Red y la pantalla Cuenta e integraciones.
        // El tipo explícito () -> Unit es necesario: sin él, la
        // lambda devuelve el Job de scope.launch y no compila
        // (run #286: Argument type mismatch, líneas 284 y 297).
        val disconnectSpotify: () -> Unit = {
            scope.launch {
                spotifyStore.clear()
                spotifyStatus = null
                spotifyCookieText = ""
            }
        }
        val connectSpotify: (String) -> Unit = { cookie ->
            scope.launch {
                spotifyStatus = "Comprobando la sesión…"
                val token = SpotifyClient().fetchAccessToken(cookie.trim())
                if (token != null) {
                    spotifyStore.setCookie(cookie.trim())
                    spotifyCookieText = ""
                    spotifyStatus = "Conectado."
                } else {
                    spotifyStatus = "Esa sesión no funcionó. Vuelve a iniciar sesión con Spotify."
                }
            }
        }
        if (section == SettingsSection.INTEGRATIONS) {
            IntegrationsScreen(
                spotifyConnected = spotifyConnected,
                onConnectSpotify = connectSpotify,
                onDisconnectSpotify = disconnectSpotify,
                onImportTracks = onImportTracks,
                ytMusicConnected = ytMusicConnected,
                onConnectYouTubeMusic = onConnectYouTubeMusic,
                onDisconnectYouTubeMusic = onDisconnectYouTubeMusic,
                ytMusicCookies = ytMusicCookies,
            )
        }

        if (section == SettingsSection.NETWORK) {
            SettingsNetworkSection(
                player = player,
                onSavePlayer = { savePlayer(it) },
                spotifyConnected = spotifyConnected,
                spotifyCookieText = spotifyCookieText,
                onSpotifyCookieTextChange = { spotifyCookieText = it },
                spotifyStatus = spotifyStatus,
                onDisconnectSpotify = disconnectSpotify,
                onConnectSpotify = connectSpotify,
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
