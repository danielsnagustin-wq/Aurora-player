package com.aurora.player.ui.eq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.audio.EqualizerManager
import com.aurora.player.data.CustomPresetStore
import com.aurora.player.playback.AudioEffects
import kotlinx.coroutines.launch

/**
 * Aurora Player — Ecualizador (10 bandas + graves + virtualizador).
 * Hoy no existía pantalla de EQ: el manager existía pero nadie lo
 * veía. Todo aquí actúa sobre el Equalizer real de la sesión de
 * audio de ExoPlayer. Si el dispositivo no lo soporta, se dice y no
 * se finge. 35 presets de fábrica + presets propios (crear/borrar).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = AudioEffects.equalizerManager
    val scope = rememberCoroutineScope()
    val presetStore = remember { CustomPresetStore(context) }
    val customPresets by presetStore.presetsFlow.collectAsState(initial = emptyList())

    var enabled by remember { mutableStateOf(true) }
    var levels by remember { mutableStateOf(List(10) { 0f }) }
    var bass by remember { mutableStateOf(0f) }
    var virtualizer by remember { mutableStateOf(0f) }
    var selectedPreset by remember { mutableStateOf<String?>(null) }
    var newPresetName by remember { mutableStateOf("") }

    LaunchedEffect(manager) {
        val m = manager ?: return@LaunchedEffect
        levels = List(10) { i -> m.getBandLevel(i) / 100f }
        bass = m.getBassBoostStrength().toFloat()
        virtualizer = m.getVirtualizerStrength().toFloat()
    }

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ecualizador")
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
            }
        }

        if (manager == null || !manager.isAvailable) {
            Text(
                "Ecualizador no disponible en este dispositivo.",
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ecualizador activado")
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    manager.setEnabled(it)
                },
            )
        }

        val freqs = manager.bandFrequenciesHz
        val bandCount = manager.getBandCount().coerceAtMost(10)
        for (band in 0 until bandCount) {
            val label = freqs.getOrNull(band)?.let { hz ->
                if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
            } ?: "Banda ${band + 1}"
            Text("$label: ${levels.getOrNull(band) ?: 0f} dB")
            Slider(
                value = levels.getOrNull(band) ?: 0f,
                onValueChange = { db ->
                    levels = levels.toMutableList().also { it[band] = db }
                    manager.setBandLevel(band, (db * 100).toInt().toShort())
                    selectedPreset = null
                },
                valueRange = -15f..15f,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("Refuerzo de graves: ${bass.toInt()}")
        Slider(
            value = bass,
            onValueChange = {
                bass = it
                manager.setBassBoost(it.toInt().toShort())
            },
            valueRange = 0f..1000f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Virtualizador: ${virtualizer.toInt()}")
        Slider(
            value = virtualizer,
            onValueChange = {
                virtualizer = it
                manager.setVirtualizer(it.toInt().toShort())
            },
            valueRange = 0f..1000f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Presets", modifier = Modifier.padding(top = 16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (preset in EqualizerManager.PRESETS) {
                PresetChip(
                    name = preset.name,
                    selected = selectedPreset == preset.name,
                    onClick = {
                        manager.applyPreset(preset)
                        levels = preset.levelsDb
                        selectedPreset = preset.name
                    },
                )
            }
        }

        if (customPresets.isNotEmpty()) {
            Text("Mis presets", modifier = Modifier.padding(top = 16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (preset in customPresets) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PresetChip(
                            name = preset.name,
                            selected = selectedPreset == preset.name,
                            onClick = {
                                manager.applyPreset(preset)
                                levels = preset.levelsDb
                                selectedPreset = preset.name
                            },
                        )
                        IconButton(onClick = {
                            scope.launch { presetStore.delete(preset.name) }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Borrar ${preset.name}")
                        }
                    }
                }
            }
        }

        Text("Guardar como preset propio", modifier = Modifier.padding(top = 16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newPresetName,
                onValueChange = { newPresetName = it },
                label = { Text("Nombre del preset") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    scope.launch {
                        presetStore.save(newPresetName, levels)
                        selectedPreset = newPresetName.trim()
                        newPresetName = ""
                    }
                },
                enabled = newPresetName.isNotBlank(),
            ) {
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun PresetChip(name: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(name) }
    } else {
        OutlinedButton(onClick = onClick) { Text(name) }
    }
}
