package com.aurora.player.ui.eq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.aurora.player.audio.AUTO_EQ_MODELS
import com.aurora.player.audio.EqualizerManager
import com.aurora.player.audio.isDirectMode
import com.aurora.player.audio.currentOutputRoute
import com.aurora.player.audio.routeKey
import com.aurora.player.audio.routeLabel
import com.aurora.player.data.CustomPresetStore
import com.aurora.player.data.EqState
import com.aurora.player.data.EqStateStore
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.model.PlayerSettings
import com.aurora.player.playback.AudioEffects
import com.aurora.player.ui.theme.auroraButtonColors
import kotlinx.coroutines.flow.first
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
    val eqStateStore = remember { EqStateStore(context) }
    val customPresets by presetStore.presetsFlow.collectAsState(initial = emptyList())

    var enabled by remember { mutableStateOf(false) }
    var levels by remember { mutableStateOf(List(10) { 0f }) }
    var bass by remember { mutableStateOf(0f) }
    var virtualizer by remember { mutableStateOf(0f) }
    var selectedPreset by remember { mutableStateOf<String?>(null) }
    var newPresetName by remember { mutableStateOf("") }
    // AutoEq: buscador del modelo de auriculares.
    var autoEqQuery by remember { mutableStateOf("") }
    // EQ por salida: la salida actual se lee al entrar; el EQ de cada
    // salida se guarda aparte solo cuando el interruptor está puesto.
    val (route, routeName) = remember { currentOutputRoute(context) }
    var perOutput by remember { mutableStateOf(false) }
    // RONDA 7 P1: asistente de EQ + gráfica de respuesta.
    var showWizard by remember { mutableStateOf(false) }

    /** Guarda el estado actual del EQ: sobrevive al reinicio del servicio. */
    fun persist() {
        scope.launch {
            val state = EqState(enabled, levels, bass.toInt(), virtualizer.toInt())
            if (perOutput) {
                eqStateStore.saveRoute(routeKey(route, routeName), state)
            } else {
                eqStateStore.save(state)
            }
        }
    }

    /** Carga un estado en los deslizadores y en el manager real. */
    fun loadState(state: EqState) {
        val m = manager ?: return
        m.setEnabled(state.enabled)
        m.applyPreset(EqualizerManager.Preset("Guardado", state.levelsDb))
        m.setBassBoost(state.bassBoost.toShort())
        m.setVirtualizer(state.virtualizer.toShort())
        enabled = state.enabled
        levels = state.levelsDb
        bass = state.bassBoost.toFloat()
        virtualizer = state.virtualizer.toFloat()
    }

    LaunchedEffect(manager) {
        val m = manager ?: return@LaunchedEffect
        // Estado guardado: el EQ vuelve a como lo dejó el usuario, no en
        // cero. Si nunca se guardó nada, el estado por defecto es apagado
        // con niveles en cero, igual que un manager recién creado.
        val saved = eqStateStore.stateFlow.first()
        perOutput = eqStateStore.perOutputFlow.first()
        if (perOutput) {
            // Con EQ por salida, esta pantalla muestra el EQ de la
            // salida actual; si no tiene uno, empieza como copia del
            // global y se guarda al primer cambio.
            val routeSaved = eqStateStore.routeStateFlow(routeKey(route, routeName)).first()
            if (routeSaved != null) {
                loadState(routeSaved)
                return@LaunchedEffect
            }
        }
        loadState(saved)
    }

    Column(
        modifier = modifier.fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
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

        // Salida Directa: el EQ está apagado mientras dure; aquí se
        // puede volver a Estándar sin salir de la pantalla.
        val playerSettingsStore = remember { PlayerSettingsStore(context) }
        val playerSettings by playerSettingsStore.settingsFlow.collectAsState(
            initial = PlayerSettings.DEFAULT,
        )
        if (isDirectMode(playerSettings.audioOutputMode)) {
            Text(
                "Salida Directa activa: el ecualizador está apagado mientras dure.",
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(
                onClick = {
                    scope.launch {
                        playerSettingsStore.save(playerSettings.copy(audioOutputMode = "standard"))
                    }
                },
                colors = auroraButtonColors(),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Volver a Estándar")
            }
            return@Column
        }

        if (manager == null && AudioEffects.equalizerUnsupported) {
            Text(
                "Ecualizador no disponible en este dispositivo.",
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }
        if (manager == null) {
            // RONDA 2 (2026-11-09): sin sesión todavía (se crea al
            // preparar la primera canción), la pantalla ya NO se
            // vacía: los controles se muestran igual, todo se guarda
            // y se aplica solo en cuanto suene la primera canción
            // (el LaunchedEffect de arriba lo aplica al aparecer).
            Text(
                "Sin sesión todavía: ajusta y guarda; se aplica en cuanto suene una canción.",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        if (manager != null && !manager.isAvailable) {
            Text(
                "Ecualizador no disponible en este dispositivo.",
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }
        if (showWizard && manager != null) {
            EqWizardScreen(
                manager = manager,
                onClose = { showWizard = false },
                onApplyCurve = { curve ->
                    if (!enabled) {
                        enabled = true
                        manager?.setEnabled(true)
                    }
                    manager?.applyPreset(EqualizerManager.Preset("Asistente", curve))
                    levels = curve
                    selectedPreset = null
                    persist()
                },
            )
            return@Column
        }

        Text(
            "Salida actual: ${routeLabel(route, routeName)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("EQ distinto por salida")
            Switch(
                checked = perOutput,
                onCheckedChange = { on ->
                    perOutput = on
                    scope.launch {
                        eqStateStore.savePerOutput(on)
                        if (on) {
                            // Al encenderlo, la salida actual empieza
                            // con el EQ que se está viendo.
                            eqStateStore.saveRoute(
                                routeKey(route, routeName),
                                EqState(enabled, levels, bass.toInt(), virtualizer.toInt()),
                            )
                        } else {
                            // Al apagarlo, vuelve el EQ global.
                            loadState(eqStateStore.stateFlow.first())
                        }
                    }
                },
            )
        }
        if (perOutput) {
            Text(
                "Cada salida guarda su propio EQ: cambia de altavoz a auriculares y el EQ cambia solo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    manager?.setEnabled(it)
                    persist()
                },
            )
        }

        if (manager != null) {
            OutlinedButton(
                onClick = { showWizard = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Asistente de EQ")
            }
        }

        val freqs = manager?.bandFrequenciesHz?.takeIf { it.isNotEmpty() }
            ?: listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val bandCount = (manager?.getBandCount()?.takeIf { it > 0 } ?: levels.size).coerceAtMost(10)
        for (band in 0 until bandCount) {
            val label = freqs.getOrNull(band)?.let { hz ->
                if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
            } ?: "Banda ${band + 1}"
            Text("$label: ${levels.getOrNull(band) ?: 0f} dB")
            Slider(
                value = levels.getOrNull(band) ?: 0f,
                onValueChange = { db ->
                    if (!enabled) {
                        enabled = true
                        manager?.setEnabled(true)
                    }
                    levels = levels.toMutableList().also {
                        while (it.size <= band) it.add(0f)
                        it[band] = db
                    }
                    manager?.setBandLevel(band, (db * 100).toInt().toShort())
                    selectedPreset = null
                },
                onValueChangeFinished = { persist() },
                valueRange = -15f..15f,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("Refuerzo de graves: ${bass.toInt()}")
        Slider(
            value = bass,
            onValueChange = {
                if (!enabled) {
                    enabled = true
                    manager?.setEnabled(true)
                }
                bass = it
                manager?.setBassBoost(it.toInt().toShort())
            },
            onValueChangeFinished = { persist() },
            valueRange = 0f..1000f,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Virtualizador: ${virtualizer.toInt()}")
        Slider(
            value = virtualizer,
            onValueChange = {
                if (!enabled) {
                    enabled = true
                    manager?.setEnabled(true)
                }
                virtualizer = it
                manager?.setVirtualizer(it.toInt().toShort())
            },
            onValueChangeFinished = { persist() },
            valueRange = 0f..1000f,
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
                        // AUDITORÍA (2026-11-09): tocar un preset con el
                        // EQ apagado cambiaba los niveles en silencio,
                        // sin que se oyera nada distinto. Un preset se
                        // toca para oírlo: enciende el EQ.
                        if (!enabled) {
                            enabled = true
                            manager?.setEnabled(true)
                        }
                        manager?.applyPreset(preset)
                        levels = preset.levelsDb
                        selectedPreset = preset.name
                        persist()
                    },
                )
            }
        }

        Text("AutoEq", modifier = Modifier.padding(top = 16.dp))
        Text(
            "Corrección medida para tu modelo de auriculares (proyecto AutoEq, mediciones oratory1990). " +
                "Las frecuencias de las bandas del teléfono no son exactamente las de AutoEq: " +
                "es una aproximación, ajústala de oído si hace falta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = autoEqQuery,
            onValueChange = { autoEqQuery = it },
            label = { Text("Buscar tu modelo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        val autoEqMatches = remember(autoEqQuery) {
            if (autoEqQuery.isBlank()) AUTO_EQ_MODELS
            else AUTO_EQ_MODELS.filter { it.name.contains(autoEqQuery, ignoreCase = true) }
        }
        if (autoEqMatches.isEmpty()) {
            Text(
                "Ningún modelo coincide. Hay ${AUTO_EQ_MODELS.size} modelos medidos; prueba con la marca.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            for (model in autoEqMatches) {
                PresetChip(
                    name = model.name,
                    selected = selectedPreset == "AutoEq: ${model.name}",
                    onClick = {
                        if (!enabled) {
                            enabled = true
                            manager?.setEnabled(true)
                        }
                        manager?.applyPreset(EqualizerManager.Preset(model.name, model.levelsDb))
                        levels = model.levelsDb
                        selectedPreset = "AutoEq: ${model.name}"
                        persist()
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
                                if (!enabled) {
                                    enabled = true
                                    manager?.setEnabled(true)
                                }
                                manager?.applyPreset(preset)
                                levels = preset.levelsDb
                                selectedPreset = preset.name
                                persist()
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
                        // RONDA 4 (2026-11-09): el nombre se guarda
                        // recortado a 40 caracteres; el seleccionado
                        // quedaba con el nombre completo y el chip no
                        // se marcaba como elegido.
                        selectedPreset = newPresetName.trim().take(40)
                        newPresetName = ""
                    }
                },
                enabled = newPresetName.isNotBlank(),
                colors = auroraButtonColors(),
            ) {
                Text("Guardar")
            }
        }
    }
}

@Composable
private fun PresetChip(name: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, colors = auroraButtonColors()) { Text(name) }
    } else {
        OutlinedButton(onClick = onClick) { Text(name) }
    }
}
