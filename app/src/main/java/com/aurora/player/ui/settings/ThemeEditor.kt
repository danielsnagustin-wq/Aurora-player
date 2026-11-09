package com.aurora.player.ui.settings

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aurora.player.model.ThemePreset
import com.aurora.player.model.ThemeSettings
import com.aurora.player.model.normalizePresetName
import com.aurora.player.model.themePresetToJsonValue
import com.aurora.player.ui.theme.auroraButtonColors
import com.aurora.player.ui.theme.cardBackground
import com.aurora.player.ui.theme.cardBorder
import com.aurora.player.ui.theme.cornerRadiusShape
import com.aurora.player.ui.theme.parseHexOrNull
import com.aurora.player.util.hexToRgb
import com.aurora.player.util.hslToRgb
import com.aurora.player.util.rgbToHex
import com.aurora.player.util.rgbToHsl
import com.aurora.player.util.toJsonString
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aurora Player — editor de personalización (SPEC 007, 2026-11-09).
 * Estilo propio de Aurora: filas compactas (muestra + nombre + HEX),
 * picker avanzado (HEX/RGB/HSL + recientes + paletas), Mis temas
 * (guardar/renombrar/duplicar/borrar/importar/exportar) y vista previa
 * real (biblioteca o reproductor) que se actualiza al instante.
 * Edita los MISMOS campos de ThemeSettings con el MISMO `save`:
 * no hay un segundo sistema de colores.
 */

// ---------------------------------------------------------------- colores

/** Una fila compacta: muestra + nombre del elemento + código HEX. */
@Composable
fun ColorRow(label: String, hex: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(parseHexOrNull(hex) ?: Color.Gray)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        )
        Text(label, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text(
            hex.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Picker avanzado: pestañas HEX / RGB / HSL + recientes + paletas. */
@Composable
fun ColorPickerDialog(
    currentHex: String,
    recentColors: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var rgb by remember { mutableStateOf(hexToRgb(currentHex) ?: Triple(140, 203, 235)) }
    var hexText by remember { mutableStateOf(currentHex) }
    val (r, g, b) = rgb
    fun applyRgb(newRgb: Triple<Int, Int, Int>) {
        rgb = newRgb
        hexText = rgbToHex(newRgb.first, newRgb.second, newRgb.third)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elige un color") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(r, g, b)),
                )
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    for ((index, label) in listOf("HEX", "RGB", "HSL").withIndex()) {
                        if (tab == index) {
                            Button(
                                onClick = { tab = index },
                                colors = auroraButtonColors(),
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                            ) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = { tab = index },
                                modifier = Modifier.weight(1f).padding(end = 4.dp),
                            ) { Text(label) }
                        }
                    }
                }
                when (tab) {
                    0 -> OutlinedTextField(
                        value = hexText,
                        onValueChange = {
                            hexText = it
                            hexToRgb(it)?.let { rgb = it }
                        },
                        label = { Text("HEX (ej. #8CCBEB)") },
                        singleLine = true,
                        isError = hexToRgb(hexText) == null,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    1 -> Column(Modifier.padding(top = 12.dp)) {
                        for ((value, name, set) in listOf(
                            Triple(r, "Rojo", { v: Int -> applyRgb(Triple(v, g, b)) }),
                            Triple(g, "Verde", { v: Int -> applyRgb(Triple(r, v, b)) }),
                            Triple(b, "Azul", { v: Int -> applyRgb(Triple(r, g, v)) }),
                        )) {
                            Text("$name: $value")
                            Slider(
                                value = value.toFloat(),
                                onValueChange = { set(it.toInt()) },
                                valueRange = 0f..255f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    else -> {
                        val (h, s, l) = rgbToHsl(r, g, b)
                        Column(Modifier.padding(top = 12.dp)) {
                            Text("Tono: ${h.toInt()}°")
                            Slider(
                                value = h,
                                onValueChange = {
                                    applyRgb(hslToRgb(it, s, l))
                                },
                                valueRange = 0f..360f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("Saturación: ${(s * 100).toInt()}%")
                            Slider(
                                value = s,
                                onValueChange = { applyRgb(hslToRgb(h, it, l)) },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("Luminosidad: ${(l * 100).toInt()}%")
                            Slider(
                                value = l,
                                onValueChange = { applyRgb(hslToRgb(h, s, it)) },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (recentColors.isNotEmpty()) {
                    Text("Recientes", modifier = Modifier.padding(top = 12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        for (hex in recentColors) {
                            Box(
                                Modifier.size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(parseHexOrNull(hex) ?: Color.Gray)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .clickable { applyRgb(hexToRgb(hex) ?: Triple(140, 203, 235)) },
                            )
                        }
                    }
                }
                Text("Paletas Aurora", modifier = Modifier.padding(top = 12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    for ((name, hex) in ThemeSettings.ACCENT_PRESETS) {
                        val color = parseHexOrNull(hex)
                        OutlinedButton(onClick = {
                            applyRgb(hexToRgb(hex) ?: Triple(140, 203, 235))
                        }) {
                            if (color != null) {
                                Box(
                                    Modifier.size(16.dp)
                                        .clip(RoundedCornerShape(8.dp)).background(color),
                                )
                            }
                            Text(name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(rgbToHex(r, g, b)) },
                colors = auroraButtonColors(),
            ) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

// ------------------------------------------------------------------ temas

/** "Mis temas": guardar el actual, aplicar, renombrar, duplicar, borrar, exportar, importar. */
@Composable
fun ThemePresetsSection(
    presets: List<ThemePreset>,
    onApply: (ThemePreset) -> Unit,
    onSave: (String) -> Unit,
    onRename: (ThemePreset, String) -> Unit,
    onDelete: (ThemePreset) -> Unit,
    onDuplicate: (ThemePreset) -> Unit,
    onExport: (ThemePreset) -> Unit,
    onImportClick: () -> Unit,
    importMessage: String? = null,
) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ThemePreset?>(null) }
    var renameText by remember { mutableStateOf("") }
    Text("Guardar el tema actual", modifier = Modifier.padding(top = 16.dp))
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Nombre del tema") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                normalizePresetName(newName)?.let { onSave(it) }
                newName = ""
            },
            enabled = normalizePresetName(newName) != null,
            colors = auroraButtonColors(),
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Guardar") }
    }
    if (presets.isEmpty()) {
        Text(
            "Aún no tienes temas guardados. Guarda el actual para volver a él cuando quieras.",
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        for (preset in presets) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    preset.name,
                    modifier = Modifier.weight(1f).clickable { onApply(preset) },
                )
                IconButton(onClick = {
                    renaming = preset
                    renameText = preset.name
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Renombrar ${preset.name}")
                }
                IconButton(onClick = { onDuplicate(preset) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicar ${preset.name}")
                }
                IconButton(onClick = { onExport(preset) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Exportar ${preset.name}")
                }
                IconButton(onClick = { onDelete(preset) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Borrar ${preset.name}")
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        OutlinedButton(onClick = onImportClick, modifier = Modifier.weight(1f)) {
            Text("Importar tema")
        }
    }
    if (importMessage != null) {
        Text(importMessage, modifier = Modifier.padding(top = 8.dp))
    }
    val target = renaming
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renombrar tema") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        normalizePresetName(renameText)?.let { onRename(target, it) }
                        renaming = null
                    },
                    enabled = normalizePresetName(renameText) != null,
                    colors = auroraButtonColors(),
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancelar") }
            },
        )
    }
}

/** Exporta un tema como archivo JSON en Descargas/AuroraPlayer. */
suspend fun exportThemeFile(context: Context, preset: ThemePreset): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val slug = preset.name.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-").trim('-')
                .ifBlank { "tema" }
            val fileName = "aurora-tema-$slug.json"
            val text = themePresetToJsonValue(preset).toJsonString()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/AuroraPlayer")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
                ) ?: throw IllegalStateException("No se pudo crear el archivo")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("No se pudo escribir el archivo")
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "AuroraPlayer",
                ).apply { mkdirs() }
                File(dir, fileName).writeText(text, Charsets.UTF_8)
            }
            "Descargas/AuroraPlayer/$fileName"
        }
    }

// -------------------------------------------------------------- vista previa

/** Vista previa real: una fila de biblioteca con el tema aplicado. */
@Composable
fun MiniLibraryPreview(applied: ThemeSettings) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .clip(cornerRadiusShape(applied.cornerRadiusDp))
            .background(
                cardBackground(applied.cardStyle, scheme.surface),
            )
            .border(
                cardBorder(applied.cardStyle, scheme.outline),
                cornerRadiusShape(applied.cornerRadiusDp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(8.dp)).background(scheme.primary),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text("Canción de ejemplo", color = scheme.onSurface)
                Text(
                    "Artista · Álbum",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).padding(top = 12.dp)
                .clip(RoundedCornerShape(4.dp)).background(scheme.surfaceVariant),
        ) {
            Box(
                Modifier.fillMaxWidth(0.6f).fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp)).background(scheme.primary),
            )
        }
    }
}

/** Vista previa real: un mini reproductor con el tema aplicado. */
@Composable
fun MiniPlayerPreview(applied: ThemeSettings) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .clip(cornerRadiusShape(applied.cornerRadiusDp))
            .background(
                cardBackground(applied.cardStyle, scheme.surface),
            )
            .border(
                cardBorder(applied.cardStyle, scheme.outline),
                cornerRadiusShape(applied.cornerRadiusDp),
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp)
                .clip(RoundedCornerShape(12.dp)).background(scheme.primary),
        )
        Text(
            "Canción de ejemplo",
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Artista · Álbum",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            IconButton(onClick = {}) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = scheme.onSurface)
            }
            IconButton(onClick = {}) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = {}) {
                Icon(Icons.Filled.SkipNext, contentDescription = null, tint = scheme.onSurface)
            }
        }
    }
}
