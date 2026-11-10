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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Notifications
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
import com.aurora.player.data.LyricsProviderStore
import com.aurora.player.lyrics.DEFAULT_PROVIDER_ORDER
import com.aurora.player.lyrics.moveProvider
import com.aurora.player.data.ThemePresetStore
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.SpotifyStore
import com.aurora.player.online.SpotifyClient
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.data.validateBackup
import com.aurora.player.model.CardStyle
import com.aurora.player.model.MAX_THEMES
import com.aurora.player.model.ThemePreset
import com.aurora.player.model.themePresetFromJsonValue
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
    notificationsGranted: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenEq: () -> Unit = {},
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
    val savedThemes by presetStore.themesFlow.collectAsState(initial = emptyList())
    val recentColors by presetStore.recentColorsFlow.collectAsState(initial = emptyList())
    var editingColor by remember { mutableStateOf<String?>(null) }
    var previewMode by remember { mutableStateOf(0) } // 0 biblioteca, 1 reproductor
    var themeImportMessage by remember { mutableStateOf<String?>(null) }
    // RONDA 2: inicio personalizable (pestañas visibles + portada en fila).
    val libraryPrefs = remember { LibraryPrefsStore(context) }
    val visibleTabs by libraryPrefs.visibleTabsFlow.collectAsState(
        initial = LibraryPrefsStore.ALL_TABS,
    )
    val rowCoverDp by libraryPrefs.rowCoverSizeFlow.collectAsState(
        initial = LibraryPrefsStore.DEFAULT_ROW_COVER_DP,
    )

    // Fase 2 — orden de los proveedores de letras (hoja "Elegir
    // proveedor" como BitChord).
    val lyricsProviderStore = remember { LyricsProviderStore(context) }
    val providerOrder by lyricsProviderStore.orderFlow.collectAsState(
        initial = DEFAULT_PROVIDER_ORDER,
    )
    var showProviders by remember { mutableStateOf(false) }

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
        if (showProviders) {
            ProvidersDialog(
                order = providerOrder,
                onMove = { name, up ->
                    scope.launch {
                        lyricsProviderStore.setOrder(moveProvider(providerOrder, name, up))
                    }
                },
                onClose = { showProviders = false },
            )
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
        SettingsGroupTitle("Tema")
        SettingsGroupDescription("Claro, oscuro o negro AMOLED. AMOLED apaga los píxeles negros y ahorra batería.")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (mode in ThemeMode.entries) {
                val selected = applied.themeMode == mode
                if (selected) {
                    Button(
                        onClick = { save(applied.copy(themeMode = mode)) },
                        colors = auroraButtonColors(),
                    ) {
                        Text(mode.label)
                    }
                } else {
                    OutlinedButton(onClick = { save(applied.copy(themeMode = mode)) }) {
                        Text(mode.label)
                    }
                }
            }
        }

        SettingsGroupTitle("Color principal (acento)")
        SettingsGroupDescription("El color que tiñe botones, iconos y detalles. Toca un preset o escribe tu HEX abajo.")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((name, hex) in ThemeSettings.ACCENT_PRESETS) {
                val color = parseHexOrNull(hex)
                val selected = applied.accentHex.equals(hex, ignoreCase = true)
                if (selected) {
                    Button(
                        onClick = { save(applied.copy(accentHex = hex)) },
                        colors = auroraButtonColors(),
                    ) {
                        if (color != null) {
                            Box(
                                Modifier.size(16.dp).clip(RoundedCornerShape(8.dp))
                                    .background(color),
                            )
                        }
                        Text(name)
                    }
                } else {
                    OutlinedButton(onClick = { save(applied.copy(accentHex = hex)) }) {
                        if (color != null) {
                            Box(
                                Modifier.size(16.dp).clip(RoundedCornerShape(8.dp))
                                    .background(color),
                            )
                        }
                        Text(name)
                    }
                }
            }
        }
        OutlinedTextField(
            value = hexText,
            onValueChange = {
                hexText = it
                if (ThemeSettings.isValidHex(it)) {
                    save(applied.copy(accentHex = it))
                }
            },
            label = { Text("Color personalizado (HEX, ej. #8CCBEB)") },
            singleLine = true,
            isError = !ThemeSettings.isValidHex(hexText),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (!ThemeSettings.isValidHex(hexText)) {
            Text("HEX no válido: usa #RRGGBB con 6 cifras hexadecimales.")
        }

        // SPEC 007: cada color en su fila compacta (muestra + nombre
        // + HEX); tocar abre el picker avanzado (HEX/RGB/HSL).
        SettingsGroupTitle("Colores")
        SettingsGroupDescription("Toca un color para afinarlo. Cada cambio se aplica al instante y se guarda solo.")
        ColorRow("Color principal (acento)", applied.accentHex) { editingColor = "accent" }
        ColorRow("Fondo", applied.backgroundHex) { editingColor = "background" }
        ColorRow("Tarjeta", applied.cardHex) { editingColor = "card" }
        ColorRow("Texto", applied.textHex) { editingColor = "text" }
        ColorRow("Texto secundario", applied.secondaryTextHex) { editingColor = "secondaryText" }
        ColorRow("Botón", applied.buttonHex) { editingColor = "button" }
        ColorRow("Barra del reproductor", applied.playerBarHex) { editingColor = "playerBar" }
        ColorRow("Color secundario", applied.visualizerHex) { editingColor = "visualizer" }
        SettingsGroupDescription("El color secundario tiñe detalles y acentos secundarios; también será el color del visualizador cuando llegue.")
        val editing = editingColor
        if (editing != null) {
            val currentHex = when (editing) {
                "accent" -> applied.accentHex
                "background" -> applied.backgroundHex
                "card" -> applied.cardHex
                "text" -> applied.textHex
                "secondaryText" -> applied.secondaryTextHex
                "button" -> applied.buttonHex
                "playerBar" -> applied.playerBarHex
                else -> applied.visualizerHex
            }
            ColorPickerDialog(
                currentHex = currentHex,
                recentColors = recentColors,
                onDismiss = { editingColor = null },
                onConfirm = { hex ->
                    val next = when (editing) {
                        "accent" -> applied.copy(accentHex = hex)
                        "background" -> applied.copy(backgroundHex = hex)
                        "card" -> applied.copy(cardHex = hex)
                        "text" -> applied.copy(textHex = hex)
                        "secondaryText" -> applied.copy(secondaryTextHex = hex)
                        "button" -> applied.copy(buttonHex = hex)
                        "playerBar" -> applied.copy(playerBarHex = hex)
                        else -> applied.copy(visualizerHex = hex)
                    }
                    save(next)
                    scope.launch { presetStore.addRecentColor(hex) }
                    editingColor = null
                },
            )
        }

        SettingsGroupTitle("Así se ve ahora")
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (previewMode == 0) {
                Button(
                    onClick = { previewMode = 0 },
                    colors = auroraButtonColors(),
                    modifier = Modifier.weight(1f),
                ) { Text("Biblioteca") }
            } else {
                OutlinedButton(
                    onClick = { previewMode = 0 },
                    modifier = Modifier.weight(1f),
                ) { Text("Biblioteca") }
            }
            if (previewMode == 1) {
                Button(
                    onClick = { previewMode = 1 },
                    colors = auroraButtonColors(),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                ) { Text("Reproductor") }
            } else {
                OutlinedButton(
                    onClick = { previewMode = 1 },
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                ) { Text("Reproductor") }
            }
        }
        Box(Modifier.padding(top = 8.dp)) {
            if (previewMode == 0) MiniLibraryPreview(applied) else MiniPlayerPreview(applied)
        }

        SettingsGroupTitle("Tamaño de texto")
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((size, label) in listOf(
                TextSize.SMALL to "Pequeño",
                TextSize.MEDIUM to "Normal",
                TextSize.LARGE to "Grande",
            )) {
                if (applied.textSize == size) {
                    Button(
                        onClick = { save(applied.copy(textSize = size)) },
                        colors = auroraButtonColors(),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { save(applied.copy(textSize = size)) }) {
                        Text(label)
                    }
                }
            }
        }

        SettingsGroupTitle("Forma de portada")
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((shape, label) in listOf(
                CoverShape.SQUARE to "Cuadrada",
                CoverShape.ROUNDED to "Redondeada",
                CoverShape.CIRCLE to "Circular",
            )) {
                if (applied.coverShape == shape) {
                    Button(
                        onClick = { save(applied.copy(coverShape = shape)) },
                        colors = auroraButtonColors(),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { save(applied.copy(coverShape = shape)) }) {
                        Text(label)
                    }
                }
            }
        }

        SettingsGroupTitle("Densidad de lista")
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((density, label) in listOf(
                ListDensity.COMPACT to "Compacta",
                ListDensity.COMFORTABLE to "Cómoda",
            )) {
                if (applied.listDensity == density) {
                    Button(
                        onClick = { save(applied.copy(listDensity = density)) },
                        colors = auroraButtonColors(),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { save(applied.copy(listDensity = density)) }) {
                        Text(label)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Material You (colores del sistema)", modifier = Modifier.weight(1f))
            Switch(
                checked = applied.useMaterialYou,
                onCheckedChange = { save(applied.copy(useMaterialYou = it)) },
            )
        }
        SettingsGroupDescription(
            "Android 12 o más: el tema toma los colores del fondo de pantalla. Tu color de botones sigue valiendo.",
            modifier = Modifier.padding(top = 4.dp),
        )
        // RONDA 5 (2026-11-09): "AMOLED cuando el sistema está en
        // oscuro" existía en el modelo, se guardaba y el tema lo
        // leía, pero NO HABÍA interruptor para cambiarlo: siempre
        // valía true. Ahora aparece cuando el modo es Sistema.
        if (applied.themeMode == ThemeMode.SYSTEM) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Negro AMOLED cuando el sistema está en oscuro",
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = applied.amoledWhenSystemDark,
                    onCheckedChange = { save(applied.copy(amoledWhenSystemDark = it)) },
                )
            }
            SettingsGroupDescription(
                "Con el sistema en oscuro, el fondo es negro puro AMOLED en vez de gris oscuro.",
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // DISEÑO PREMIUM (spec 003): todo personalizable, todo con
        // efecto real. Cada ajuste se aplica al tocar, como el resto.
        SettingsGroupTitle("Diseño")
        SettingsGroupDescription("Cómo se ve la app: colores que siguen a la portada, fondo del reproductor, tarjetas y esquinas.")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Color dinámico de la portada", modifier = Modifier.weight(1f))
            Switch(
                checked = applied.dynamicColor,
                // Cambio manual a un ajuste que gobierna el perfil:
                // el perfil pasa a Personalizado.
                onCheckedChange = {
                    save(applied.copy(dynamicColor = it, performanceProfile = PerformanceProfile.CUSTOM))
                },
            )
        }
        SettingsGroupDescription(
            "El acento del reproductor sigue el color de la portada que suena. Apagado, manda tu color principal.",
            modifier = Modifier.padding(top = 4.dp),
        )
        SettingsGroupTitle("Fondo del reproductor")
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((bg, label) in listOf(
                PlayerBackground.COVER to "Portada",
                PlayerBackground.GRADIENT to "Degradado",
                PlayerBackground.SOLID to "Plano",
            )) {
                // Tocar el fondo ya elegido no cambia nada (y no debe
                // pasar el perfil a Personalizado sin motivo).
                val next = if (applied.playerBackground == bg) applied
                    else applied.copy(playerBackground = bg, performanceProfile = PerformanceProfile.CUSTOM)
                if (applied.playerBackground == bg) {
                    Button(
                        onClick = { save(next) },
                        colors = auroraButtonColors(),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { save(next) }) {
                        Text(label)
                    }
                }
            }
        }
        SettingsGroupTitle("Estilo de tarjetas")
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((style, label) in listOf(
                CardStyle.SOLID to "Sólido",
                CardStyle.GLASS to "Cristal",
                CardStyle.OUTLINE to "Contorno",
            )) {
                if (applied.cardStyle == style) {
                    Button(
                        onClick = { save(applied.copy(cardStyle = style)) },
                        colors = auroraButtonColors(),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { save(applied.copy(cardStyle = style)) }) {
                        Text(label)
                    }
                }
            }
        }
        // El radio se guarda al soltar la barra, no a cada paso del
        // arrastre (como el crossfade: si no, escribe en DataStore
        // decenas de veces por arrastre).
        var cornerRadius by remember(applied.cornerRadiusDp) {
            mutableStateOf(applied.cornerRadiusDp.toFloat())
        }
        Text(
            "Radio de esquinas: ${cornerRadius.toInt()} dp",
            modifier = Modifier.padding(top = 16.dp),
        )
        Slider(
            value = cornerRadius,
            onValueChange = { cornerRadius = it },
            onValueChangeFinished = {
                // Solo si el radio cambió de verdad: soltar la barra en
                // el mismo valor no debe pasar el perfil a Personalizado.
                val radius = cornerRadius.toInt()
                if (radius != applied.cornerRadiusDp) {
                    save(applied.copy(cornerRadiusDp = radius, performanceProfile = PerformanceProfile.CUSTOM))
                }
            },
            valueRange = 0f..28f,
            steps = 27,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Álbumes en cuadrícula", modifier = Modifier.weight(1f))
            Switch(
                checked = applied.albumGrid,
                onCheckedChange = { save(applied.copy(albumGrid = it)) },
            )
        }
        SettingsGroupDescription(
            "La pestaña Álbumes se ve en cuadrícula de 2 columnas en vez de lista.",
            modifier = Modifier.padding(top = 4.dp),
        )

        // RENDIMIENTO (spec 004): perfiles + animaciones + restablecer.
        // Solo gobierna efectos reales; cada cambio se aplica al tocar.
        SettingsGroupTitle("Rendimiento")
        SettingsGroupDescription("Cuánto trabajo visual hace la app. Equilibrado es el aspecto actual; Ahorro gasta menos batería; Alto rendimiento nunca se baja solo.")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (profile in PerformanceProfile.entries) {
                val next = profile.applyTo(applied).copy(performanceProfile = profile)
                if (applied.performanceProfile == profile) {
                    Button(
                        onClick = { save(next) },
                        colors = auroraButtonColors(),
                    ) { Text(profile.label) }
                } else {
                    OutlinedButton(onClick = { save(next) }) {
                        Text(profile.label)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Animaciones", modifier = Modifier.weight(1f))
            Switch(
                checked = applied.animationsEnabled,
                onCheckedChange = {
                    save(applied.copy(animationsEnabled = it, performanceProfile = PerformanceProfile.CUSTOM))
                },
            )
        }
        SettingsGroupDescription(
            "Apagadas, la pantalla de inicio se muestra estática y las letras saltan de línea sin animación.",
            modifier = Modifier.padding(top = 4.dp),
        )
        SettingsGroupDescription(
            "Con el perfil Equilibrado, si Android entra en ahorro de energía o el teléfono se calienta mucho, la app baja sola a los valores de Ahorro sin tocar lo que guardaste: al salir, vuelve sola. La música no se corta nunca por esto.",
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = { scope.launch { store.resetToDefault() } },
            colors = auroraButtonColors(),
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Restablecer tema y diseño") }
        SettingsGroupDescription(
            "Vuelve todos los ajustes de tema, diseño y rendimiento a los valores de fábrica.",
            modifier = Modifier.padding(top = 4.dp),
        )
        }

        if (section == SettingsSection.ADVANCED) {
        // RENDIMIENTO (spec 004) — DIAGNÓSTICO: mediciones reales de
        // este teléfono. El medidor de FPS solo mide mientras Ajustes
        // está abierto; al salir se para.
        SettingsGroupTitle("Diagnóstico")
        SettingsGroupDescription("Mediciones reales de este teléfono, ahora mismo. Lo que no se puede medir sale como —, no como un número inventado.")
        val meter = remember { FrameRateMeter() }
        val fps by meter.fps.collectAsState()
        val dropped by meter.dropped.collectAsState()
        var snapshot by remember { mutableStateOf<DiagnosticsSnapshot?>(null) }
        DisposableEffect(Unit) {
            meter.start()
            onDispose { meter.stop() }
        }
        LaunchedEffect(Unit) {
            snapshot = withContext(Dispatchers.IO) { collectSnapshot(context) }
        }
        val snap = snapshot
        Text(
            "FPS: ${if (snap == null) "—" else "%.1f".format(fps)}",
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("Frames perdidos (muestra actual): $dropped")
        Text("RAM de la app: ${snap?.appRamUsedMb?.toString() ?: "—"} MB de ${snap?.appRamMaxMb?.toString() ?: "—"} MB")
        Text("RAM del teléfono: ${snap?.deviceRamAvailMb?.toString() ?: "—"} MB libres de ${snap?.deviceRamTotalMb?.toString() ?: "—"} MB")
        Text("CPU del proceso: ${snap?.cpuTimeMs?.toString() ?: "—"} ms (${snap?.cpuCores?.toString() ?: "—"} núcleos)")
        Text("Batería: ${snap?.batteryPercent?.let { "$it %" } ?: "—"} · Temperatura: ${snap?.batteryTempC?.let { "%.1f °C".format(it) } ?: "—"}")
        Text("Apertura (proceso a biblioteca): ${snap?.startupMs?.let { "$it ms" } ?: "—"}")
        OutlinedButton(
            onClick = {
                scope.launch {
                    snapshot = withContext(Dispatchers.IO) { collectSnapshot(context) }
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Actualizar") }
        }

        if (section == SettingsSection.SOUND) {
        // ETAPA 4: reproducción real (crossfade con efecto, auto-pausa y
        // reanudar la cola). Todo se guarda al tocar, como el tema.
        SettingsGroupTitle("Reproducción")
        SettingsGroupDescription("Cómo suena y qué pasa al quitar los auriculares o al reabrir la app.")
        Button(
            onClick = onOpenEq,
            colors = auroraButtonColors(),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Abrir ecualizador")
        }
        // El crossfade se guarda al soltar la barra, no a cada paso del
        // arrastre (antes escribía en DataStore decenas de veces por
        // arrastre y el servicio reaccionaba a cada una).
        var crossfade by remember(player.crossfadeSeconds) {
            mutableStateOf(player.crossfadeSeconds.toFloat())
        }
        Text(
            "Crossfade: ${crossfade.toInt()} s (0 = apagado)",
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = crossfade,
            onValueChange = { crossfade = it },
            onValueChangeFinished = {
                savePlayer(player.copy(crossfadeSeconds = crossfade.toInt()))
            },
            valueRange = 0f..12f,
            steps = 11,
            modifier = Modifier.fillMaxWidth(),
        )
        // Fase 1b: no solapar dentro del mismo álbum (los discos
        // continuos se rompen si se solapan dos pistas pegadas).
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("No solapar dentro del mismo álbum", modifier = Modifier.weight(1f))
            Switch(
                checked = player.crossfadeGaplessAlbumException,
                onCheckedChange = { savePlayer(player.copy(crossfadeGaplessAlbumException = it)) },
            )
        }
        // SPEC 005 FR-001: balance izquierda/derecha. Se guarda al
        // soltar la barra, como el crossfade.
        var balance by remember(player.channelBalance) {
            mutableStateOf(player.channelBalance)
        }
        Text(
            "Balance: " + when {
                balance == 0f -> "centro"
                balance < 0f -> "izquierda ${(-balance * 100).toInt()}%"
                else -> "derecha ${(balance * 100).toInt()}%"
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = balance,
            onValueChange = { balance = it },
            onValueChangeFinished = {
                savePlayer(player.copy(channelBalance = balance))
            },
            valueRange = -1f..1f,
            steps = 19,
            modifier = Modifier.fillMaxWidth(),
        )
        // RONDA 6: ReplayGain de verdad (antes el ajuste existía en
        // el modelo pero nada lo aplicaba). Usa la etiqueta ReplayGain
        // del archivo: sin etiqueta, no cambia el volumen.
        SettingsGroupTitle("ReplayGain (normalización de volumen)")
        SettingsGroupDescription("Iguala el volumen entre canciones con la etiqueta ReplayGain del archivo. Sin etiqueta, no cambia nada.")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((mode, label) in listOf(
                "off" to "Apagado",
                "track" to "Por canción",
                "album" to "Por álbum",
                "smart" to "Inteligente",
            )) {
                if (player.replayGainMode == mode) {
                    Button(
                        onClick = { savePlayer(player.copy(replayGainMode = mode)) },
                        colors = auroraButtonColors(),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { savePlayer(player.copy(replayGainMode = mode)) }) {
                        Text(label)
                    }
                }
            }
        }
        if (player.replayGainMode != "off") {
            var preamp by remember(player.replayGainPreAmpDb) {
                mutableStateOf(player.replayGainPreAmpDb)
            }
            Text(
                "Preamp: ${if (preamp >= 0) "+" else ""}${preamp.toInt()} dB",
                modifier = Modifier.padding(top = 8.dp),
            )
            Slider(
                value = preamp,
                onValueChange = { preamp = it },
                onValueChangeFinished = {
                    savePlayer(player.copy(replayGainPreAmpDb = preamp))
                },
                valueRange = -12f..12f,
                steps = 23,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }
        if (section == SettingsSection.PLAYBACK) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pausar al quitar los auriculares", modifier = Modifier.weight(1f))
            Switch(
                checked = player.autoPauseOnDisconnect,
                onCheckedChange = { savePlayer(player.copy(autoPauseOnDisconnect = it)) },
            )
        }
        // Fase 1b: reproducir al conectar auriculares/BT (OFF por
        // defecto). Reanuda si hay una canción cargada en pausa; sin
        // canción no hace nada y si la app se cerró del todo, tampoco.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reproducir al conectar auriculares o Bluetooth", modifier = Modifier.weight(1f))
            Switch(
                checked = player.autoplayOnConnect,
                onCheckedChange = { savePlayer(player.copy(autoplayOnConnect = it)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reanudar la cola al abrir la app", modifier = Modifier.weight(1f))
            Switch(
                checked = player.resumeWhereLeft,
                onCheckedChange = { savePlayer(player.copy(resumeWhereLeft = it)) },
            )
        }
        // RONDA 6: saltar silencio (idea de Metrolist) y radio local.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Saltar silencios largos", modifier = Modifier.weight(1f))
            Switch(
                checked = player.skipSilence,
                onCheckedChange = { savePlayer(player.copy(skipSilence = it)) },
            )
        }
        SettingsGroupDescription(
            "Salta los silencios de más de 2 segundos: al final de la pista pasa a la siguiente; en mitad, avanza 3 s.",
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Radio local al acabar la cola", modifier = Modifier.weight(1f))
            Switch(
                checked = player.localRadio,
                onCheckedChange = { savePlayer(player.copy(localRadio = it)) },
            )
        }
        SettingsGroupDescription(
            "Cuando se acaba la cola y la repetición está apagada, sigue con canciones parecidas de tu biblioteca.",
            modifier = Modifier.padding(top = 4.dp),
        )
        }

        if (section == SettingsSection.CONTROL) {
        // AUDITORÍA (2026-11-09): los gestos del reproductor existían
        // pero no se podían apagar; sus ajustes vivían muertos en el
        // modelo. Ahora cada gesto tiene su interruptor real.
        SettingsGroupTitle("Gestos")
        SettingsGroupDescription("Lo que hace la portada del reproductor al tocarla.")
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Deslizar la portada cambia de pista", modifier = Modifier.weight(1f))
            Switch(
                checked = player.swipeToChangeTrack,
                onCheckedChange = { savePlayer(player.copy(swipeToChangeTrack = it)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Doble toque en la portada marca favorito", modifier = Modifier.weight(1f))
            Switch(
                checked = player.doubleTapToFavorite,
                onCheckedChange = { savePlayer(player.copy(doubleTapToFavorite = it)) },
            )
        }
        // Fase 1b: agitar para pasar a la siguiente (OFF por defecto)
        // y deslizar vertical sobre la portada para el volumen.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Agitar el teléfono pasa a la siguiente", modifier = Modifier.weight(1f))
            Switch(
                checked = player.shakeToNext,
                onCheckedChange = { savePlayer(player.copy(shakeToNext = it)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Deslizar vertical sobre la portada cambia el volumen", modifier = Modifier.weight(1f))
            Switch(
                checked = player.volumeGesture,
                onCheckedChange = { savePlayer(player.copy(volumeGesture = it)) },
            )
        }
        }

        if (section == SettingsSection.LIBRARY) {
        // RONDA 2 (2026-11-09): la duración mínima existía en el
        // modelo y la biblioteca la usaba, pero NO HABÍA control para
        // cambiarla en Ajustes: era un ajuste muerto. Ahora sí se
        // puede tocar; se aplica al volver a escanear la biblioteca.
        SettingsGroupTitle("Biblioteca")
        SettingsGroupDescription("Qué canciones entran en la biblioteca. Las muy cortas suelen ser tonos o notas de voz.")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((ms, label) in listOf(
                0L to "Todo",
                5_000L to "Desde 5 s",
                10_000L to "Desde 10 s",
                30_000L to "Desde 30 s",
                60_000L to "Desde 1 min",
            )) {
                if (player.minDurationMs == ms) {
                    Button(
                        onClick = { savePlayer(player.copy(minDurationMs = ms)) },
                        colors = auroraButtonColors(),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { savePlayer(player.copy(minDurationMs = ms)) }) {
                        Text(label)
                    }
                }
            }
        }
        SettingsGroupDescription(
            "Se aplica al volver a escanear la biblioteca.",
            modifier = Modifier.padding(top = 4.dp),
        )
        SettingsGroupTitle("Pestañas visibles")
        SettingsGroupDescription("Elige qué pestañas se ven en el inicio. Siempre queda al menos una visible.")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            for ((index, label) in listOf(
                "Canciones", "Artistas", "Álbumes", "Favoritos", "Géneros", "Carpetas", "Online",
            ).withIndex()) {
                if (index in visibleTabs) {
                    Button(
                        onClick = { scope.launch { libraryPrefs.setTabVisible(index, false) } },
                        colors = auroraButtonColors(),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { scope.launch { libraryPrefs.setTabVisible(index, true) } }) {
                        Text(label)
                    }
                }
            }
        }
        SettingsGroupTitle("Tamaño de portada en la fila")
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((dp, label) in listOf(
                32 to "Pequeña", 40 to "Normal", 56 to "Grande", 72 to "Muy grande",
            )) {
                if (rowCoverDp == dp) {
                    Button(
                        onClick = { scope.launch { libraryPrefs.setRowCoverSize(dp) } },
                        colors = auroraButtonColors(),
                    ) { Text(label) }
                } else {
                    OutlinedButton(onClick = { scope.launch { libraryPrefs.setRowCoverSize(dp) } }) {
                        Text(label)
                    }
                }
            }
        }
        }

        if (section == SettingsSection.NETWORK) {
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
                onCheckedChange = { savePlayer(player.copy(onlineMode = it)) },
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
            OutlinedButton(onClick = { showProviders = true }) {
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
                onClick = {
                    scope.launch {
                        spotifyStore.clear()
                        spotifyStatus = null
                        spotifyCookieText = ""
                    }
                },
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
                onValueChange = { spotifyCookieText = it },
                label = { Text("Cookie sp_dc") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = {
                    scope.launch {
                        spotifyStatus = "Comprobando la cookie…"
                        val token = SpotifyClient().fetchAccessToken(spotifyCookieText.trim())
                        if (token != null) {
                            spotifyStore.setCookie(spotifyCookieText.trim())
                            spotifyCookieText = ""
                            spotifyStatus = "Conectado."
                        } else {
                            spotifyStatus = "Esa cookie no funcionó. Revisa que la copiaste entera de open.spotify.com."
                        }
                    }
                },
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

        if (section == SettingsSection.NOTIFICATIONS) {
            SettingsGroupTitle("Notificaciones")
            SettingsGroupDescription(
                if (notificationsGranted) {
                    "El aviso de reproducción sale en la barra de estado y en la pantalla de bloqueo."
                } else {
                    "Sin este permiso, en Android 13+ no sale el aviso de reproducción con los controles."
                },
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onRequestNotificationPermission,
                colors = auroraButtonColors(),
                modifier = Modifier.padding(top = 8.dp),
                enabled = !notificationsGranted,
            ) {
                Text(
                    if (notificationsGranted) "Notificaciones activadas"
                    else "Activar notificaciones"
                )
            }
        }

        if (section == SettingsSection.ADVANCED) {
        TextButton(
            onClick = {
                scope.launch {
                    store.resetToDefault()
                    playerStore.resetToDefault()
                }
            },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Restablecer por defecto")
        }
        }

        if (section == SettingsSection.ALARM) {
        // RONDA 7 P4: alarma musical.
        SettingsGroupTitle("Alarma")
        SettingsGroupDescription(
            "Despiértate con tu música: elige una hora y qué suena. Suena aunque la app esté cerrada y sobrevive a un reinicio.",
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = { showAlarm = true },
            colors = auroraButtonColors(),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Programar alarma")
        }
        }

        if (section == SettingsSection.BACKUP) {
        // SPEC 005 FR-004: respaldo local (listas, favoritos,
        // historial, ajustes, cola y presets de EQ) en un archivo
        // en Descargas. Restaurar reemplaza lo que hay ahora.
        var backupMessage by remember { mutableStateOf<String?>(null) }
        var pendingRestore by remember { mutableStateOf<BackupData?>(null) }
        var restoreReport by remember { mutableStateOf<RestoreReport?>(null) }
        var autoBackup by remember { mutableStateOf(false) }
        var lastBackup by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            autoBackup = BackupManager.isAutoBackupEnabled(context)
            lastBackup = BackupManager.lastBackupName(context)
        }
        val pickBackup = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = BackupManager.readBackupText(context, uri)
                if (text.isFailure) {
                    backupMessage = "No se pudo leer ese archivo."
                    return@launch
                }
                when (val v = validateBackup(text.getOrThrow())) {
                    is BackupValidation.Valid -> pendingRestore = v.data
                    is BackupValidation.Invalid -> backupMessage = v.reason
                }
            }
        }
        SettingsGroupTitle("Respaldo")
        SettingsGroupDescription("Guarda tus listas, favoritos, historial, ajustes, cola y presets de EQ en un archivo en Descargas. Restaurar reemplaza lo que tienes ahora; lo que no se encuentre en tu biblioteca se lista, nunca se inventa.")
        if (lastBackup.isNotEmpty()) {
            SettingsGroupDescription("Último respaldo: $lastBackup", modifier = Modifier.padding(top = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        val data = BackupManager.collectBackup(context)
                        val result = BackupManager.exportBackup(context, data)
                        if (result.isSuccess) {
                            lastBackup = result.getOrThrow().fileName
                            backupMessage = "Respaldo guardado en ${result.getOrThrow().location}."
                        } else {
                            backupMessage = "No se pudo crear el respaldo."
                        }
                    }
                },
                colors = auroraButtonColors(),
            ) { Text("Crear respaldo") }
            OutlinedButton(onClick = { pickBackup.launch(arrayOf("application/json")) }) {
                Text("Restaurar…")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Respaldo automático diario", modifier = Modifier.weight(1f))
            Switch(
                checked = autoBackup,
                onCheckedChange = {
                    scope.launch {
                        BackupManager.setAutoBackupEnabled(context, it)
                        autoBackup = it
                    }
                },
            )
        }
        backupMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
        restoreReport?.let { report ->
            Text(
                "Restaurado: ${report.playlistsRestored} listas, ${report.favoritesRestored} favoritos, " +
                    "${report.historyRestored} escuchas, ${report.queueSongs} canciones en cola.",
                modifier = Modifier.padding(top = 8.dp),
            )
            if (report.missing.isNotEmpty()) {
                SettingsGroupDescription(
                    "No se encontraron ${report.missing.size} canciones del respaldo (ya no están en tu biblioteca).",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (pendingRestore != null) {
            val data = pendingRestore!!
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text("Restaurar respaldo") },
                text = {
                    Text(
                        "Se reemplazarán tus listas (${data.playlists.size}), favoritos (${data.favorites.size}) e historial " +
                            "por los del respaldo. Tus ajustes, cola y presets de EQ también se aplicarán. " +
                            "Lo que tienes ahora se pierde.",
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingRestore = null
                            scope.launch {
                                restoreReport = BackupManager.restoreBackup(context, data)
                                backupMessage = null
                            }
                        },
                        colors = auroraButtonColors(),
                    ) { Text("Restaurar") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRestore = null }) { Text("Cancelar") }
                },
            )
        }
        }

        if (section == SettingsSection.THEMES) {
        SettingsGroupTitle("Mis temas")
        SettingsGroupDescription("Tus temas guardados usan los mismos colores de Interfaz. Aplicar uno cambia todo el aspecto al instante.")
        val pickTheme = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = try {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) {
                    null
                }
                if (text == null) {
                    themeImportMessage = "No se pudo leer ese archivo."
                    return@launch
                }
                val preset = try {
                    themePresetFromJsonValue(JsonParser.parse(text))
                } catch (_: Exception) {
                    null
                }
                if (preset == null) {
                    themeImportMessage = "Ese archivo no es un tema de Aurora Player."
                    return@launch
                }
                val name = if (savedThemes.any { it.name == preset.name }) {
                    "${preset.name} (2)"
                } else {
                    preset.name
                }
                presetStore.saveThemes((savedThemes + preset.copy(name = name)).takeLast(MAX_THEMES))
                themeImportMessage = "Tema \"$name\" importado."
            }
        }
        ThemePresetsSection(
            presets = savedThemes,
            onApply = { save(it.settings) },
            onSave = { name ->
                scope.launch {
                    val updated = (savedThemes.filter { it.name != name } + ThemePreset(name, applied))
                        .takeLast(MAX_THEMES)
                    presetStore.saveThemes(updated)
                }
            },
            onRename = { preset, name ->
                scope.launch {
                    presetStore.saveThemes(
                        savedThemes.map { if (it.name == preset.name) it.copy(name = name) else it },
                    )
                }
            },
            onDelete = { preset ->
                scope.launch {
                    presetStore.saveThemes(savedThemes.filter { it.name != preset.name })
                }
            },
            onDuplicate = { preset ->
                scope.launch {
                    var name = "${preset.name} (copia)"
                    var n = 2
                    while (savedThemes.any { it.name == name }) {
                        name = "${preset.name} (copia $n)"
                        n++
                    }
                    presetStore.saveThemes(
                        (savedThemes + preset.copy(name = name)).takeLast(MAX_THEMES),
                    )
                }
            },
            onExport = { preset ->
                scope.launch {
                    val result = exportThemeFile(context, preset)
                    themeImportMessage = result.fold(
                        onSuccess = { "Tema exportado en $it." },
                        onFailure = { "No se pudo exportar el tema." },
                    )
                }
            },
            onImportClick = { pickTheme.launch(arrayOf("application/json")) },
            importMessage = themeImportMessage,
        )
        }

        if (section == SettingsSection.LIBRARY) {
        // SPEC 005 FR-006: carpetas excluidas (se excluyen con
        // mantener pulsado en la pestaña Carpetas; aquí se quitan).
        val folderStore = remember { FolderStore(context) }
        val excludedFolders by folderStore.excludedFlow.collectAsState(initial = emptySet())
        SettingsGroupTitle("Carpetas excluidas")
        SettingsGroupDescription("Estas carpetas no aparecen en ninguna pestaña, ni en la búsqueda, ni en la cola. Quítalas para volver a incluirlas.")
        if (excludedFolders.isEmpty()) {
            SettingsGroupDescription("Ninguna carpeta excluida.", modifier = Modifier.padding(top = 8.dp))
        } else {
            for (folder in excludedFolders.sorted()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(folder, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch { folderStore.include(folder) }
                    }) { Text("Quitar") }
                }
            }
            TextButton(
                onClick = { scope.launch { folderStore.clearExcluded() } },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Quitar todas") }
        }
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
    NOTIFICATIONS(
        "Notificaciones",
        "Aviso de reproducción en la barra de estado",
        Icons.Filled.Notifications,
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
    THEMES(
        "Mis temas",
        "Guarda, aplica y comparte tus temas",
        Icons.Filled.Style,
    ),
    ADVANCED(
        "Avanzadas",
        "Diagnóstico y restablecer por defecto",
        Icons.Filled.Settings,
    ),
}

/**
 * Fase 2 — hoja "Elegir proveedor" (como BitChord). Reordena con
 * las flechas: el primero que encuentre la letra gana.
 */
@Composable
private fun ProvidersDialog(
    order: List<String>,
    onMove: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val descriptions = mapOf(
        "LRCLIB" to "Gratis, sin clave. Letras sincronizadas y texto plano.",
        "BetterLyrics" to "Letras con sílabas (TTML). Sin clave.",
        "BiniLyrics" to "Letras palabra a palabra vía lrc.red. Sin clave.",
    )
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Elegir proveedor de letras") },
        text = {
            Column {
                Text("El primero que encuentre la letra gana. Sube o baja cada proveedor con las flechas.")
                order.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name)
                            Text(descriptions[name].orEmpty())
                        }
                        IconButton(
                            onClick = { onMove(name, true) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Subir $name")
                        }
                        IconButton(
                            onClick = { onMove(name, false) },
                            enabled = index < order.size - 1,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Bajar $name")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Cerrar") }
        },
    )
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
