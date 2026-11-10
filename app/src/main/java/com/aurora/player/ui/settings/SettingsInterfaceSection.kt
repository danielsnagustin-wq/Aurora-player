package com.aurora.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.aurora.player.model.ThemePreset
import com.aurora.player.model.themePresetFromJsonValue
import com.aurora.player.online.JsonParser
import androidx.compose.ui.platform.LocalContext
import com.aurora.player.widget.AuroraWidgetProvider
import com.aurora.player.widget.WidgetStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aurora.player.data.ThemePresetStore
import com.aurora.player.model.AppFont
import com.aurora.player.model.LAB_THEME_PRESETS
import com.aurora.player.model.labThemePreset
import com.aurora.player.model.CardStyle
import com.aurora.player.model.CoverShape
import com.aurora.player.model.ListDensity
import com.aurora.player.model.PerformanceProfile
import com.aurora.player.model.PlayerBackground
import com.aurora.player.model.applyTo
import com.aurora.player.model.TextSize
import com.aurora.player.model.ThemeMode
import com.aurora.player.model.ThemeSettings
import com.aurora.player.ui.design.AuroraOptionChips
import com.aurora.player.ui.design.AuroraSectionHeader
import com.aurora.player.ui.design.AuroraSliderRow
import com.aurora.player.ui.design.AuroraSwitchRow
import com.aurora.player.ui.theme.auroraButtonColors
import com.aurora.player.ui.theme.parseHexOrNull
import kotlinx.coroutines.launch

/**
 * Sección Interfaz, partida de SettingsScreen (2026-11-09, paso
 * estructural 4, la grande): tema, color principal, colores con
 * picker, vista previa, tamaño de texto, forma de portada,
 * densidad, Material You, diseño y rendimiento. Con ella, ninguna
 * sección queda dentro de SettingsScreen: el archivo solo coordina.
 * Usa FlowRow: lleva su propio @OptIn(ExperimentalLayoutApi::class)
 * — lección del #233.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsInterfaceSection(
    applied: ThemeSettings,
    onSaveTheme: (ThemeSettings) -> Unit,
    hexText: String,
    onHexTextChange: (String) -> Unit,
    editingColor: String?,
    onEditingColorChange: (String?) -> Unit,
    recentColors: List<String>,
    presetStore: ThemePresetStore,
    previewMode: Int,
    onPreviewModeChange: (Int) -> Unit,
    onResetTheme: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    AuroraSectionHeader(
        title = "Tema",
        description = "Claro, oscuro o negro AMOLED. AMOLED apaga los píxeles negros y ahorra batería.",
    )
    AuroraOptionChips(
        options = ThemeMode.entries.map { it.name to it.label },
        selected = applied.themeMode.name,
        onSelect = { onSaveTheme(applied.copy(themeMode = ThemeMode.valueOf(it))) },
    )

    AuroraSectionHeader(
        title = "Color principal (acento)",
        description = "El color que tiñe botones, iconos y detalles. Toca un preset o escribe tu HEX abajo.",
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((name, hex) in ThemeSettings.ACCENT_PRESETS) {
            val color = parseHexOrNull(hex)
            val selected = applied.accentHex.equals(hex, ignoreCase = true)
            if (selected) {
                Button(
                    onClick = { onSaveTheme(applied.copy(accentHex = hex)) },
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
                OutlinedButton(onClick = { onSaveTheme(applied.copy(accentHex = hex)) }) {
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
            onHexTextChange(it)
            if (ThemeSettings.isValidHex(it)) {
                onSaveTheme(applied.copy(accentHex = it))
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

    // LABORATORIO DE DISEÑO (Fase 4 del AI DEVELOPMENT STUDIO):
    // las 3 propuestas como temas completos a un toque. Cada una
    // aplica TODO el ThemeSettings (colores, fondo del reproductor,
    // cuadrícula, radio...), no solo el acento. Si el tema actual
    // no coincide con ninguna propuesta (lo personalizó después),
    // ninguna queda marcada: es el estado real, no un invento.
    AuroraSectionHeader(
        title = "Temas del laboratorio",
        description = "Las 3 propuestas de diseño, listas para aplicar a un toque: Aurora Hielo (la actual, evolucionada), Medianoche Lavanda (degradado + onda) y Turquesa Vivo (cuadrícula + espectro).",
    )
    AuroraOptionChips(
        options = LAB_THEME_PRESETS.map { it.name to it.name },
        selected = LAB_THEME_PRESETS.find { it.settings == applied }?.name ?: "",
        onSelect = { name -> labThemePreset(name)?.let { onSaveTheme(it.settings) } },
    )

    // SPEC 007: cada color en su fila compacta (muestra + nombre
    // + HEX); tocar abre el picker avanzado (HEX/RGB/HSL).
    AuroraSectionHeader(
        title = "Colores",
        description = "Toca un color para afinarlo. Cada cambio se aplica al instante y se guarda solo.",
    )
    ColorRow("Color principal (acento)", applied.accentHex) { onEditingColorChange("accent") }
    ColorRow("Fondo", applied.backgroundHex) { onEditingColorChange("background") }
    ColorRow("Tarjeta", applied.cardHex) { onEditingColorChange("card") }
    ColorRow("Texto", applied.textHex) { onEditingColorChange("text") }
    ColorRow("Texto secundario", applied.secondaryTextHex) { onEditingColorChange("secondaryText") }
    ColorRow("Botón", applied.buttonHex) { onEditingColorChange("button") }
    ColorRow("Barra del reproductor", applied.playerBarHex) { onEditingColorChange("playerBar") }
    ColorRow("Color secundario", applied.visualizerHex) { onEditingColorChange("visualizer") }
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
            onDismiss = { onEditingColorChange(null) },
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
                onSaveTheme(next)
                scope.launch { presetStore.addRecentColor(hex) }
                onEditingColorChange(null)
            },
        )
    }

    AuroraSectionHeader(title = "Así se ve ahora")
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (previewMode == 0) {
            Button(
                onClick = { onPreviewModeChange(0) },
                colors = auroraButtonColors(),
                modifier = Modifier.weight(1f),
            ) { Text("Biblioteca") }
        } else {
            OutlinedButton(
                onClick = { onPreviewModeChange(0) },
                modifier = Modifier.weight(1f),
            ) { Text("Biblioteca") }
        }
        if (previewMode == 1) {
            Button(
                onClick = { onPreviewModeChange(1) },
                colors = auroraButtonColors(),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            ) { Text("Reproductor") }
        } else {
            OutlinedButton(
                onClick = { onPreviewModeChange(1) },
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            ) { Text("Reproductor") }
        }
    }
    Box(Modifier.padding(top = 8.dp)) {
        if (previewMode == 0) MiniLibraryPreview(applied) else MiniPlayerPreview(applied)
    }

    // Fase 2 — estilos de widget (Retro): cómo se ve el widget en
    // la pantalla de inicio. Se guarda en las preferencias del
    // widget y se aplica al instante (sin abrir la app).
    val widgetContext = LocalContext.current
    var widgetStyle by remember {
        mutableStateOf(AuroraWidgetProvider.getStyle(widgetContext))
    }
    AuroraSectionHeader(
        title = "Estilo del widget",
        description = "Cómo se ve el widget en la pantalla de inicio. «Con portada» muestra la portada del álbum que suena.",
    )
    AuroraOptionChips(
        options = WidgetStyle.entries.map { it.name to it.label },
        selected = widgetStyle.name,
        onSelect = {
            val next = WidgetStyle.valueOf(it)
            widgetStyle = next
            AuroraWidgetProvider.setStyle(widgetContext, next)
        },
    )

    // Fase 2 — skins por archivo (AIMP): el tema actual se exporta
    // como un archivo JSON y un archivo así se importa y se aplica.
    // La biblioteca "Mis temas" sigue fuera (él pidió quitarla): el
    // skin es el archivo, no una lista guardada. La maquinaria de
    // exportar/importar ya existía en ThemeEditor/SettingsThemesSection
    // pero sin uso; aquí se expone solo el archivo.
    val skinContext = LocalContext.current
    var skinMessage by remember { mutableStateOf<String?>(null) }
    val pickSkin = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = try {
                skinContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) {
                null
            }
            if (text == null) {
                skinMessage = "No se pudo leer ese archivo."
                return@launch
            }
            val preset = try {
                themePresetFromJsonValue(JsonParser.parse(text))
            } catch (_: Exception) {
                null
            }
            if (preset == null) {
                skinMessage = "Ese archivo no es un tema de Aurora Player."
                return@launch
            }
            onSaveTheme(preset.settings)
            skinMessage = "Tema «${preset.name}» aplicado."
        }
    }
    AuroraSectionHeader(
        title = "Tema como archivo (skin)",
        description = "Exporta el tema actual como un archivo para guardarlo o compartirlo; importar uno lo aplica al instante.",
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = {
                scope.launch {
                    val result = exportThemeFile(skinContext, ThemePreset("Mi tema", applied))
                    skinMessage = result.fold(
                        onSuccess = { "Tema exportado en $it." },
                        onFailure = { "No se pudo exportar el tema." },
                    )
                }
            },
            colors = auroraButtonColors(),
        ) { Text("Exportar tema actual") }
        OutlinedButton(onClick = { pickSkin.launch(arrayOf("application/json")) }) {
            Text("Importar tema…")
        }
    }
    skinMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }

    // Fase 2 — selector de fuentes: solo familias del sistema, sin
    // descargar nada. Se aplica a toda la app al instante.
    AuroraSectionHeader(
        title = "Fuente",
        description = "La letra de toda la app. Son las fuentes del sistema, sin descargar nada.",
    )
    AuroraOptionChips(
        options = AppFont.entries.map { it.name to it.label },
        selected = applied.fontFamily.name,
        onSelect = { onSaveTheme(applied.copy(fontFamily = AppFont.valueOf(it))) },
    )

    AuroraSectionHeader(title = "Tamaño de texto")
    AuroraOptionChips(
        options = listOf(
            TextSize.SMALL to "Pequeño",
            TextSize.MEDIUM to "Normal",
            TextSize.LARGE to "Grande",
        ).map { (size, label) -> size.name to label },
        selected = applied.textSize.name,
        onSelect = { onSaveTheme(applied.copy(textSize = TextSize.valueOf(it))) },
    )

    AuroraSectionHeader(title = "Forma de portada")
    AuroraOptionChips(
        options = listOf(
            CoverShape.SQUARE to "Cuadrada",
            CoverShape.ROUNDED to "Redondeada",
            CoverShape.CIRCLE to "Circular",
        ).map { (shape, label) -> shape.name to label },
        selected = applied.coverShape.name,
        onSelect = { onSaveTheme(applied.copy(coverShape = CoverShape.valueOf(it))) },
    )

    AuroraSectionHeader(title = "Densidad de lista")
    AuroraOptionChips(
        options = listOf(
            ListDensity.COMPACT to "Compacta",
            ListDensity.COMFORTABLE to "Cómoda",
        ).map { (density, label) -> density.name to label },
        selected = applied.listDensity.name,
        onSelect = { onSaveTheme(applied.copy(listDensity = ListDensity.valueOf(it))) },
    )

    AuroraSwitchRow(
        title = "Material You (colores del sistema)",
        checked = applied.useMaterialYou,
        onCheckedChange = { onSaveTheme(applied.copy(useMaterialYou = it)) },
        description = "Android 12 o más: el tema toma los colores del fondo de pantalla. Tu color de botones sigue valiendo.",
    )
    // RONDA 5 (2026-11-09): "AMOLED cuando el sistema está en
    // oscuro" existía en el modelo, se guardaba y el tema lo
    // leía, pero NO HABÍA interruptor para cambiarlo: siempre
    // valía true. Ahora aparece cuando el modo es Sistema.
    if (applied.themeMode == ThemeMode.SYSTEM) {
        AuroraSwitchRow(
            title = "Negro AMOLED cuando el sistema está en oscuro",
            checked = applied.amoledWhenSystemDark,
            onCheckedChange = { onSaveTheme(applied.copy(amoledWhenSystemDark = it)) },
            description = "Con el sistema en oscuro, el fondo es negro puro AMOLED en vez de gris oscuro.",
        )
    }

    // DISEÑO PREMIUM (spec 003): todo personalizable, todo con
    // efecto real. Cada ajuste se aplica al tocar, como el resto.
    AuroraSectionHeader(
        title = "Diseño",
        description = "Cómo se ve la app: colores que siguen a la portada, fondo del reproductor, tarjetas y esquinas.",
    )
    AuroraSwitchRow(
        title = "Color dinámico de la portada",
        checked = applied.dynamicColor,
        // Cambio manual a un ajuste que gobierna el perfil:
        // el perfil pasa a Personalizado.
        onCheckedChange = {
            onSaveTheme(applied.copy(dynamicColor = it, performanceProfile = PerformanceProfile.CUSTOM))
        },
        description = "El acento del reproductor sigue el color de la portada que suena. Apagado, manda tu color principal.",
    )
    AuroraSectionHeader(title = "Fondo del reproductor")
    AuroraOptionChips(
        options = listOf(
            PlayerBackground.COVER to "Portada",
            PlayerBackground.GRADIENT to "Degradado",
            PlayerBackground.SOLID to "Plano",
        ).map { (bg, label) -> bg.name to label },
        selected = applied.playerBackground.name,
        onSelect = {
            val bg = PlayerBackground.valueOf(it)
            // Tocar el fondo ya elegido no cambia nada (y no debe
            // pasar el perfil a Personalizado sin motivo).
            val next = if (applied.playerBackground == bg) applied
                else applied.copy(playerBackground = bg, performanceProfile = PerformanceProfile.CUSTOM)
            onSaveTheme(next)
        },
    )
    AuroraSectionHeader(title = "Estilo de tarjetas")
    AuroraOptionChips(
        options = listOf(
            CardStyle.SOLID to "Sólido",
            CardStyle.GLASS to "Cristal",
            CardStyle.OUTLINE to "Contorno",
        ).map { (style, label) -> style.name to label },
        selected = applied.cardStyle.name,
        onSelect = { onSaveTheme(applied.copy(cardStyle = CardStyle.valueOf(it))) },
    )
    // El radio se guarda al soltar la barra, no a cada paso del
    // arrastre (como el crossfade: si no, escribe en DataStore
    // decenas de veces por arrastre).
    var cornerRadius by remember(applied.cornerRadiusDp) {
        mutableStateOf(applied.cornerRadiusDp.toFloat())
    }
    AuroraSliderRow(
        label = "Radio de esquinas: ${cornerRadius.toInt()} dp",
        value = cornerRadius,
        onValueChange = { cornerRadius = it },
        onValueChangeFinished = {
            // Solo si el radio cambió de verdad: soltar la barra en
            // el mismo valor no debe pasar el perfil a Personalizado.
            val radius = cornerRadius.toInt()
            if (radius != applied.cornerRadiusDp) {
                onSaveTheme(applied.copy(cornerRadiusDp = radius, performanceProfile = PerformanceProfile.CUSTOM))
            }
        },
        valueRange = 0f..28f,
        steps = 27,
    )
    AuroraSwitchRow(
        title = "Álbumes en cuadrícula",
        checked = applied.albumGrid,
        onCheckedChange = { onSaveTheme(applied.copy(albumGrid = it)) },
        description = "La pestaña Álbumes se ve en cuadrícula en vez de lista.",
    )
    // Fase 2 — cuadrícula 1-4 (Retro): cuántas columnas tiene la
    // cuadrícula de álbumes. Solo se muestra con la cuadrícula
    // encendida, para no ser un adorno.
    if (applied.albumGrid) {
        AuroraOptionChips(
            options = listOf("1" to "1 columna", "2" to "2 columnas", "3" to "3 columnas", "4" to "4 columnas"),
            selected = applied.albumGridColumns.toString(),
            onSelect = { onSaveTheme(applied.copy(albumGridColumns = it.toInt())) },
        )
    }

    // RENDIMIENTO (spec 004): perfiles + animaciones + restablecer.
    // Solo gobierna efectos reales; cada cambio se aplica al tocar.
    AuroraSectionHeader(
        title = "Rendimiento",
        description = "Cuánto trabajo visual hace la app. Equilibrado es el aspecto actual; Ahorro gasta menos batería; Alto rendimiento nunca se baja solo.",
    )
    AuroraOptionChips(
        options = PerformanceProfile.entries.map { it.name to it.label },
        selected = applied.performanceProfile.name,
        onSelect = {
            val profile = PerformanceProfile.valueOf(it)
            onSaveTheme(profile.applyTo(applied).copy(performanceProfile = profile))
        },
    )
    AuroraSwitchRow(
        title = "Animaciones",
        checked = applied.animationsEnabled,
        onCheckedChange = {
            onSaveTheme(applied.copy(animationsEnabled = it, performanceProfile = PerformanceProfile.CUSTOM))
        },
        description = "Apagadas, la pantalla de inicio se muestra estática y las letras saltan de línea sin animación.",
    )
    SettingsGroupDescription(
        "Con el perfil Equilibrado, si Android entra en ahorro de energía o el teléfono se calienta mucho, la app baja sola a los valores de Ahorro sin tocar lo que guardaste: al salir, vuelve sola. La música no se corta nunca por esto.",
        modifier = Modifier.padding(top = 4.dp),
    )
    Button(
        onClick = onResetTheme,
        colors = auroraButtonColors(),
        modifier = Modifier.padding(top = 8.dp),
    ) { Text("Restablecer tema y diseño") }
    SettingsGroupDescription(
        "Vuelve todos los ajustes de tema, diseño y rendimiento a los valores de fábrica.",
        modifier = Modifier.padding(top = 4.dp),
    )
}
