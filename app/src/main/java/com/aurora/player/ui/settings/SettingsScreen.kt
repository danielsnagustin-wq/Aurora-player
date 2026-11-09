package com.aurora.player.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.verticalScroll
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
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.model.CoverShape
import com.aurora.player.model.ListDensity
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.TextSize
import com.aurora.player.model.ThemeMode
import com.aurora.player.model.ThemeSettings
import com.aurora.player.ui.theme.PureBlack
import com.aurora.player.ui.theme.auroraButtonColors
import com.aurora.player.ui.theme.brightnessOf
import com.aurora.player.ui.theme.parseHexOrNull
import com.aurora.player.util.ErrorLog
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
 * - ETAPA 6: la personalización avanzada expone fondo, tarjetas,
 *   textos, botones, barra y visualizador (HEX), tamaño de texto,
 *   forma de portada, densidad de lista y Material You. Todo tiene
 *   efecto real (ver ui/theme/Theme.kt).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ThemeSettingsStore(context) }
    val playerStore = remember { PlayerSettingsStore(context) }
    val scope = rememberCoroutineScope()
    val applied by store.settingsFlow.collectAsState(initial = ThemeSettings.DEFAULT)
    val player by playerStore.settingsFlow.collectAsState(initial = PlayerSettings.DEFAULT)
    var hexText by remember(applied.accentHex) { mutableStateOf(applied.accentHex) }
    // Reporte de errores (depuración, se quitará en el release).
    var reportText by remember { mutableStateOf<String?>(null) }
    var showReport by remember { mutableStateOf(false) }
    var hasLog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasLog = withContext(Dispatchers.IO) { ErrorLog.hasEntries(context) }
    }

    fun save(next: ThemeSettings) {
        scope.launch { store.save(next) }
    }

    fun savePlayer(next: PlayerSettings) {
        scope.launch { playerStore.save(next) }
    }

    fun shareReport(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Aurora Player - reporte de errores")
        }
        context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
    }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Ajustes")
        Text("Aurora Player 0.1.0 — reproductor local, sin anuncios, sin cuenta, sin internet.")

        Text("Tema", modifier = Modifier.padding(top = 16.dp))
        Text("Claro, oscuro o negro AMOLED. AMOLED apaga los píxeles negros y ahorra batería.")
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

        Text("Color principal (acento)", modifier = Modifier.padding(top = 16.dp))
        Text("El color que tiñe botones, iconos y detalles. Toca un preset o escribe tu HEX abajo.")
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

        Text("Así se ve ahora", modifier = Modifier.padding(top = 16.dp))
        PreviewCard(applied)

        // ETAPA 6: personalización avanzada. Cada color se aplica al
        // tocar (HEX válido); cada selector cambia la UI de verdad.
        Text("Personalización avanzada", modifier = Modifier.padding(top = 16.dp))
        Text(
            "Cada color es un código HEX (ej. #8CCBEB). El círculo muestra el color actual de cada parte de la app: toca el campo y escribe otro código para cambiarlo.",
            modifier = Modifier.padding(top = 4.dp),
        )
        HexField("Fondo", applied.backgroundHex) { save(applied.copy(backgroundHex = it)) }
        HexField("Tarjetas", applied.cardHex) { save(applied.copy(cardHex = it)) }
        HexField("Texto principal", applied.textHex) { save(applied.copy(textHex = it)) }
        HexField("Texto secundario", applied.secondaryTextHex) {
            save(applied.copy(secondaryTextHex = it))
        }
        HexField("Botones", applied.buttonHex) { save(applied.copy(buttonHex = it)) }
        HexField("Barra del reproductor", applied.playerBarHex) {
            save(applied.copy(playerBarHex = it))
        }
        HexField("Visualizador", applied.visualizerHex) {
            save(applied.copy(visualizerHex = it))
        }

        Text("Tamaño de texto", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
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

        Text("Forma de portada", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
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

        Text("Densidad de lista", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
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
        Text(
            "Android 12 o más: el tema toma los colores del fondo de pantalla. Tu color de botones sigue valiendo.",
            modifier = Modifier.padding(top = 4.dp),
        )

        // ETAPA 4: reproducción real (crossfade con efecto, auto-pausa y
        // reanudar la cola). Todo se guarda al tocar, como el tema.
        Text("Reproducción", modifier = Modifier.padding(top = 16.dp))
        Text("Cómo suena y qué pasa al quitar los auriculares o al reabrir la app.")
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

        // AUDITORÍA (2026-11-09): los gestos del reproductor existían
        // pero no se podían apagar; sus ajustes vivían muertos en el
        // modelo. Ahora cada gesto tiene su interruptor real.
        Text("Gestos", modifier = Modifier.padding(top = 16.dp))
        Text("Lo que hace la portada del reproductor al tocarla.")
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

        // Reporte de errores (depuración): se quitará en el release.
        Text("Reporte de errores (depuración)", modifier = Modifier.padding(top = 24.dp))
        Text(
            "Solo para encontrar errores mientras desarrollamos. El reporte está sanitizado: sin rutas, sin nombres de canciones, sin datos personales. Nada sale del teléfono salvo que tú lo compartas.",
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        reportText = withContext(Dispatchers.IO) {
                            ErrorLog.buildReportText(context)
                        }
                        showReport = true
                    }
                },
                colors = auroraButtonColors(),
            ) { Text("Ver reporte") }
            OutlinedButton(onClick = {
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        ErrorLog.buildReportText(context)
                    }
                    shareReport(text)
                }
            }) { Text("Compartir") }
            TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { ErrorLog.clear(context) }
                    hasLog = false
                }
            }) { Text("Borrar") }
        }
        if (!hasLog) {
            Text("Sin errores registrados todavía.", modifier = Modifier.padding(top = 4.dp))
        }
        if (showReport && reportText != null) {
            AlertDialog(
                onDismissRequest = { showReport = false },
                confirmButton = {
                    TextButton(onClick = { showReport = false }) { Text("Cerrar") }
                },
                title = { Text("Reporte de errores") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(reportText ?: "")
                    }
                },
            )
        }
    }
}

/** Campo HEX con su propio estado: guarda solo cuando el HEX es válido. */
@Composable
private fun HexField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val color = parseHexOrNull(text)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Muestra del color actual: así se entiende qué es cada código.
        Box(
            modifier = Modifier.size(40.dp)
                .clip(CircleShape)
                .background(color ?: Color.Transparent)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    CircleShape,
                ),
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (ThemeSettings.isValidHex(it)) onChange(it)
            },
            label = { Text("$label (HEX)") },
            singleLine = true,
            isError = !ThemeSettings.isValidHex(text),
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
    if (!ThemeSettings.isValidHex(text)) {
        Text("HEX no válido: usa #RRGGBB con 6 cifras hexadecimales.")
    }
}

/** Tarjeta de ejemplo con los colores aplicados: portada, título, botón y barra.
 * AUDITORÍA (2026-11-09): antes usaba los HEX crudos (fondo #000000,
 * texto #DCE8F5) y en modo claro mostraba una tarjeta oscura que NO
 * era lo que se veía en la app. Ahora usa los colores reales del
 * tema, así que la vista previa miente menos: es lo que se ve ahora. */
@Composable
private fun PreviewCard(applied: ThemeSettings) {
    val scheme = MaterialTheme.colorScheme
    val bg = scheme.background
    val card = scheme.surface
    val accent = scheme.primary
    val text = scheme.onBackground
    val bar = scheme.surfaceVariant
    val button = parseHexOrNull(applied.buttonHex) ?: accent
    // El texto del botón usa contraste real: botón claro -> texto
    // negro. Antes usaba el color de texto claro sobre botón claro y
    // no se leía nada.
    val onButton = if (brightnessOf(button) > 0.5f) PureBlack else Color.White
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(12.dp),
    ) {
        Row {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(accent))
            Column(Modifier.padding(start = 12.dp)) {
                Text("Canción de ejemplo", color = text)
                Text("Artista · Álbum", color = text)
            }
        }
        // Barra de progreso de ejemplo: pista + avance.
        Box(
            Modifier.fillMaxWidth().height(8.dp).padding(top = 12.dp)
                .clip(RoundedCornerShape(4.dp)).background(card),
        ) {
            Box(
                Modifier.fillMaxWidth(0.6f).fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp)).background(accent),
            )
        }
        // Mini-reproductor de ejemplo: barra + canción + play.
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp)
                .clip(RoundedCornerShape(20.dp)).background(bar).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(accent))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    "Canción de ejemplo",
                    color = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Artista", color = text, style = MaterialTheme.typography.bodySmall)
            }
            Text("▶", color = accent)
        }
        Box(
            Modifier.fillMaxWidth().height(40.dp).padding(top = 8.dp)
                .clip(RoundedCornerShape(20.dp)).background(button),
            contentAlignment = Alignment.Center,
        ) {
            Text("Botón", color = onButton)
        }
    }
}
