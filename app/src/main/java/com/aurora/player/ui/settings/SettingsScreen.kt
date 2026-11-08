package com.aurora.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.model.ThemeMode
import com.aurora.player.model.ThemeSettings
import com.aurora.player.ui.theme.parseHexOrNull
import kotlinx.coroutines.launch

/**
 * Aurora Player — Ajustes (Etapa 1, tema + colores).
 *
 * Regla del usuario: cada ajuste con efecto real.
 * - Modo de tema (Claro/Oscuro/AMOLED/Sistema) y TODOS los colores
 *   SÍ tienen efecto: AuroraPlayerTheme se construye desde estos
 *   valores guardados en DataStore (ver Theme.kt).
 * - Tamaño de texto / forma de portada / densidad: existen en el
 *   modelo como datos de Fase 1b, pero NO aparecen aquí todavía porque
 *   su efecto en las listas aún no está cableado. Cuando se cablee,
 *   aparecerá su control; antes no (nada decorativo).
 * - Material You: Fase 1b, apagado por defecto — tampoco aparece aún.
 * - Vista previa: se edita una COPIA (draft); la tarjeta de ejemplo
 *   muestra el draft sin tocar la app. Solo "Aplicar" guarda.
 *   "Descartar" no escribe nada. "Restablecer" vuelve a los valores
 *   de fábrica de Aurora Player.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ThemeSettingsStore(context) }
    val scope = rememberCoroutineScope()
    val applied by store.settingsFlow.collectAsState(initial = ThemeSettings.DEFAULT)
    var draft by remember(applied) { mutableStateOf(applied) }
    val dirty = draft != applied

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Ajustes")
        Text("Aurora Player 0.1.0 — reproductor local, sin anuncios, sin cuenta, sin internet.")

        Text("Tema", modifier = Modifier.padding(top = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (mode in ThemeMode.entries) {
                val selected = draft.themeMode == mode
                if (selected) {
                    Button(onClick = { draft = draft.copy(themeMode = mode) }) { Text(mode.name) }
                } else {
                    OutlinedButton(onClick = { draft = draft.copy(themeMode = mode) }) { Text(mode.name) }
                }
            }
        }

        Text("Color principal (acento)", modifier = Modifier.padding(top = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((name, hex) in ThemeSettings.ACCENT_PRESETS) {
                val color = parseHexOrNull(hex)
                OutlinedButton(onClick = { draft = draft.copy(accentHex = hex) }) {
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
        HexField(
            label = "Color personalizado (HEX, ej. #8CCBEB)",
            value = draft.accentHex,
            onValueChange = { draft = draft.copy(accentHex = it) },
        )

        Text("Vista previa (sin aplicar todavía)", modifier = Modifier.padding(top = 16.dp))
        PreviewCard(draft)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
            Button(
                onClick = { scope.launch { store.save(draft) } },
                enabled = dirty && ThemeSettings.isValidHex(draft.accentHex),
            ) { Text("Aplicar") }
            OutlinedButton(
                onClick = { draft = applied },
                enabled = dirty,
            ) { Text("Descartar") }
            TextButton(onClick = { scope.launch { store.resetToDefault() } }) {
                Text("Restablecer por defecto")
            }
        }
        if (!ThemeSettings.isValidHex(draft.accentHex)) {
            Text("HEX no válido: usa #RRGGBB con 6 cifras hexadecimales.")
        }
    }
}

@Composable
private fun HexField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = !ThemeSettings.isValidHex(value),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/** Tarjeta de ejemplo con los colores del draft: portada, título, botón y barra. */
@Composable
private fun PreviewCard(draft: ThemeSettings) {
    val bg = parseHexOrNull(draft.backgroundHex) ?: return
    val card = parseHexOrNull(draft.cardHex) ?: return
    val accent = parseHexOrNull(draft.accentHex) ?: return
    val text = parseHexOrNull(draft.textHex) ?: return
    val bar = parseHexOrNull(draft.playerBarHex) ?: return
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
        Box(
            Modifier.fillMaxWidth().height(8.dp).padding(top = 8.dp)
                .clip(RoundedCornerShape(4.dp)).background(card),
        )
        Box(
            Modifier.fillMaxWidth().height(40.dp).padding(top = 12.dp)
                .clip(RoundedCornerShape(20.dp)).background(bar),
        ) {
            Text("▶  Barra de reproducción", color = accent, modifier = Modifier.padding(10.dp))
        }
    }
}
