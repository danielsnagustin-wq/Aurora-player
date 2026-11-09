package com.aurora.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.data.ThemeSettingsStore
import com.aurora.player.model.PlayerSettings
import com.aurora.player.model.ThemeMode
import com.aurora.player.model.ThemeSettings
import com.aurora.player.ui.theme.parseHexOrNull
import kotlinx.coroutines.launch

/**
 * Aurora Player — Ajustes (Etapa 1, tema + colores).
 *
 * Regla del usuario: cada ajuste con efecto real.
 * - Modo de tema (Claro/Oscuro/AMOLED/Sistema) y TODOS los colores
 *   se aplican AL TOCAR: se guardan en DataStore al instante, sin
 *   botón "Aplicar" que bloquee nada. La lista hace scroll y los
 *   chips usan FlowRow (nada se corta ni se escribe en vertical).
 * - Tamaño de texto / forma de portada / densidad: existen en el
 *   modelo como datos de Fase 1b, pero NO aparecen aquí todavía porque
 *   su efecto en las listas aún no está cableado. Cuando se cablee,
 *   aparecerá su control; antes no (nada decorativo).
 * - Material You: Fase 1b, apagado por defecto — tampoco aparece aún.
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
        Text("Ajustes")
        Text("Aurora Player 0.1.0 — reproductor local, sin anuncios, sin cuenta, sin internet.")

        Text("Tema", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (mode in ThemeMode.entries) {
                val selected = applied.themeMode == mode
                if (selected) {
                    Button(onClick = { save(applied.copy(themeMode = mode)) }) {
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((name, hex) in ThemeSettings.ACCENT_PRESETS) {
                val color = parseHexOrNull(hex)
                val selected = applied.accentHex.equals(hex, ignoreCase = true)
                if (selected) {
                    Button(onClick = { save(applied.copy(accentHex = hex)) }) {
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

        // ETAPA 4: reproducción real (crossfade con efecto, auto-pausa y
        // reanudar la cola). Todo se guarda al tocar, como el tema.
        Text("Reproducción", modifier = Modifier.padding(top = 16.dp))
        Text(
            "Crossfade: ${player.crossfadeSeconds} s (0 = apagado)",
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = player.crossfadeSeconds.toFloat(),
            onValueChange = { savePlayer(player.copy(crossfadeSeconds = it.toInt())) },
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

        TextButton(
            onClick = { scope.launch { store.resetToDefault() } },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Restablecer por defecto")
        }
    }
}

/** Tarjeta de ejemplo con los colores aplicados: portada, título, botón y barra. */
@Composable
private fun PreviewCard(applied: ThemeSettings) {
    val bg = parseHexOrNull(applied.backgroundHex) ?: return
    val card = parseHexOrNull(applied.cardHex) ?: return
    val accent = parseHexOrNull(applied.accentHex) ?: return
    val text = parseHexOrNull(applied.textHex) ?: return
    val bar = parseHexOrNull(applied.playerBarHex) ?: return
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
