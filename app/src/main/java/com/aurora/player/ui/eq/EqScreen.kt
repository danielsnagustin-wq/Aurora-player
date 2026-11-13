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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.aurora.player.audio.AUTO_EQ_MODELS
import com.aurora.player.audio.OutputRoute
import com.aurora.player.audio.readActiveBtCodec
import com.aurora.player.audio.applyTone2d
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
    // Tone 2D (Fase 2): graves y agudos en dB, capa sobre las 10 bandas.
    var toneBassDb by remember { mutableStateOf(0f) }
    var toneTrebleDb by remember { mutableStateOf(0f) }
    var selectedPreset by remember { mutableStateOf<String?>(null) }
    var newPresetName by remember { mutableStateOf("") }
    // AutoEq: buscador del modelo de auriculares.
    var autoEqQuery by remember { mutableStateOf("") }
    // EQ por salida: la salida actual se lee al entrar; el EQ de cada
    // salida se guarda aparte solo cuando el interruptor está puesto.
    // RONDA 73: la ruta es estado, no un remember fijo: si cambia la
    // salida con la pantalla abierta, la pantalla cambia con ella
    // (antes se quedaba con la salida vieja y guardaba bajo su clave).
    var routeState by remember { mutableStateOf(currentOutputRoute(context)) }
    val (route, routeName) = routeState
    var perOutput by remember { mutableStateOf(false) }
    // RONDA 7 P1: asistente de EQ + gráfica de respuesta.
    var showWizard by remember { mutableStateOf(false) }
    // RONDA EQ: true si el teléfono rechazó activar o aplicar el EQ
    // (setEnabled o applyPreset devolvieron false). Antes la pantalla
    // mostraba "activado" y las barras se quedaban puestas aunque el
    // sonido no cambiara: ahora lo dice claro, sin fingir.
    var eqApplyFailed by remember { mutableStateOf(false) }

    /** Aplica las 10 bandas + Tone 2D al manager real. Sin sesión
     * todavía devuelve true: los niveles quedan guardados y se aplican
     * en cuanto suene la primera canción. */
    fun applyToManager(): Boolean {
        return manager?.applyPreset(
            EqualizerManager.Preset("EQ", applyTone2d(levels, toneBassDb, toneTrebleDb)),
        ) ?: true
    }

    /** Enciende el EQ si está apagado. RONDA EQ: devuelve true si el
     *  efecto quedó encendido (o si todavía no hay sesión: se aplica
     *  en cuanto suene la primera canción); false si el teléfono lo
     *  rechazó, y entonces marca el aviso. Todos los deslizadores y
     *  presets pasan por aquí, para que ninguno finja que suena
     *  distinto cuando el efecto no se encendió. */
    fun ensureEnabled(): Boolean {
        if (enabled) return true
        val ok = manager?.setEnabled(true) ?: true
        if (ok) {
            enabled = true
        } else {
            eqApplyFailed = true
        }
        return ok
    }

    /** Guarda el estado actual del EQ: sobrevive al reinicio del servicio. */
    fun persist() {
        scope.launch {
            val state = EqState(enabled, levels, bass.toInt(), virtualizer.toInt(), toneBassDb, toneTrebleDb)
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
        // RONDA EQ: si el teléfono rechaza encender el efecto, la
        // pantalla lo dice en vez de mostrar el interruptor en
        // "activado" con el sonido igual.
        if (state.enabled && !m.setEnabled(true)) eqApplyFailed = true
        if (!state.enabled) m.setEnabled(false)
        enabled = state.enabled
        levels = state.levelsDb
        bass = state.bassBoost.toFloat()
        virtualizer = state.virtualizer.toFloat()
        toneBassDb = state.toneBassDb
        toneTrebleDb = state.toneTrebleDb
        m.applyPreset(
            EqualizerManager.Preset(
                "Guardado",
                applyTone2d(state.levelsDb, state.toneBassDb, state.toneTrebleDb),
            ),
        )
        m.setBassBoost(state.bassBoost.toShort())
        m.setVirtualizer(state.virtualizer.toShort())
    }

    /** Carga el EQ guardado de la salida actual en los deslizadores y
     * en el manager real (RONDA 73: antes vivía solo dentro del
     * LaunchedEffect, así que un cambio de salida con la pantalla
     * abierta nunca recargaba nada). */
    suspend fun loadForCurrentRoute() {
        val m = manager ?: return
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
                return
            }
        }
        loadState(saved)
    }

    LaunchedEffect(manager) {
        loadForCurrentRoute()
    }

    // RONDA 73: escuchar el cambio de salida mientras la pantalla está
    // abierta (auriculares, Bluetooth, USB). Al cambiar, se recargan
    // los niveles de la nueva salida: la pantalla promete que "el EQ
    // cambia solo" al cambiar de salida, y sin esto no lo hacía.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val ctx = context ?: return
                scope.launch {
                    val newRoute = currentOutputRoute(ctx)
                    if (newRoute != routeState) {
                        routeState = newRoute
                        loadForCurrentRoute()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
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
                    // RONDA EQ: si el teléfono rechaza el efecto, la
                    // curva no se marca como aplicada.
                    val enabledOk = ensureEnabled()
                    levels = curve
                    eqApplyFailed = !enabledOk || !applyToManager()
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
        // Fase 2, códec BT: solo con auriculares BT y solo si el usuario
        // lo pide; la lectura es por reflexión y puede no estar
        // disponible: entonces se dice, no se inventa un códec.
        if (route == OutputRoute.BLUETOOTH) {
            var btCodecLine by remember { mutableStateOf<String?>(null) }
            var btCodecChecked by remember { mutableStateOf(false) }
            val btPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    scope.launch {
                        btCodecLine = readActiveBtCodec(context)?.displayLine()
                            ?: "Códec no disponible"
                        btCodecChecked = true
                    }
                }
            }
            if (!btCodecChecked) {
                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            scope.launch {
                                btCodecLine = readActiveBtCodec(context)?.displayLine()
                                    ?: "Códec no disponible"
                                btCodecChecked = true
                            }
                        } else {
                            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Ver códec Bluetooth")
                }
            } else {
                Text(
                    "Códec: ${btCodecLine ?: "Códec no disponible"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
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
                                EqState(enabled, levels, bass.toInt(), virtualizer.toInt(), toneBassDb, toneTrebleDb),
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
                onCheckedChange = { want ->
                    // RONDA EQ: si el teléfono rechaza encender el
                    // efecto, el interruptor no se queda en "activado"
                    // fingiendo que suena distinto.
                    val ok = manager?.setEnabled(want) ?: true
                    if (ok) {
                        enabled = want
                        persist()
                    } else {
                        eqApplyFailed = true
                    }
                },
            )
        }

        // RONDA ARREGLOS 2026-11-13: estado real del efecto, para
        // saber si el EQ está vivo o no (la pantalla antes no decía
        // en qué sesión estaba ni cuántas bandas tenía el teléfono).
        val eqStatus = when {
            manager == null -> "Sin sesión todavía: suena una canción y aparece."
            !manager.isAvailable -> "No disponible en este teléfono."
            else -> "Sesión ${manager.sessionId} · ${manager.getBandCount()} bandas · " +
                if (enabled) "activo" else "apagado"
        }
        Text(
            eqStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (manager != null) {
            OutlinedButton(
                onClick = { showWizard = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Asistente de EQ")
            }
        }

        // RONDA EQ: si el teléfono rechazó el efecto, la pantalla ya no
        // finge que todo va bien. Los cambios se guardan igual y se
        // reaplican en cuanto haya sesión.
        if (eqApplyFailed) {
            Text(
                "El ecualizador no se pudo activar en este teléfono: los cambios se guardan, pero el sonido no cambia.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
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
                    // RONDA EQ: antes el auto-encendido y el preset se
                    // llamaban sin mirar si el efecto los aceptó: la
                    // barra se quedaba puesta y el sonido no cambiaba.
                    var failed = !ensureEnabled()
                    levels = levels.toMutableList().also {
                        while (it.size <= band) it.add(0f)
                        it[band] = db
                    }
                    if (!applyToManager()) failed = true
                    eqApplyFailed = failed
                    selectedPreset = null
                },
                onValueChangeFinished = { persist() },
                valueRange = -15f..15f,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("Tono", modifier = Modifier.padding(top = 16.dp))
        Text(
            "Graves y agudos como en Poweramp: una capa sobre las 10 bandas, no un preset.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Graves: ${toneBassDb} dB")
        Slider(
            value = toneBassDb,
            onValueChange = {
                // RONDA EQ: el auto-encendido pasa por ensureEnabled,
                // que marca el aviso si el teléfono lo rechaza.
                var failed = !ensureEnabled()
                toneBassDb = it
                if (!applyToManager()) failed = true
                eqApplyFailed = failed
                selectedPreset = null
            },
            onValueChangeFinished = { persist() },
            valueRange = -10f..10f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Agudos: ${toneTrebleDb} dB")
        Slider(
            value = toneTrebleDb,
            onValueChange = {
                var failed = !ensureEnabled()
                toneTrebleDb = it
                if (!applyToManager()) failed = true
                eqApplyFailed = failed
                selectedPreset = null
            },
            onValueChangeFinished = { persist() },
            valueRange = -10f..10f,
            modifier = Modifier.fillMaxWidth(),
        )

        // Ronda 7 (Codex): sin el efecto, el deslizador no se muestra:
        // un valor guardado sin efecto sería un ajuste que no hace nada.
        if (manager == null || manager.bassBoostAvailable) {
            Text("Refuerzo de graves: ${bass.toInt()}")
            Slider(
                value = bass,
                onValueChange = {
                    // RONDA EQ: si el teléfono rechaza encender el
                    // efecto, el aviso aparece aunque el refuerzo de
                    // graves sea otro efecto aparte.
                    if (!ensureEnabled()) eqApplyFailed = true
                    bass = it
                    manager?.setBassBoost(it.toInt().toShort())
                },
                onValueChangeFinished = { persist() },
                valueRange = 0f..1000f,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "Refuerzo de graves no disponible en este dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (manager == null || manager.virtualizerAvailable) {
            Text("Virtualizador: ${virtualizer.toInt()}")
            Slider(
                value = virtualizer,
                onValueChange = {
                    // RONDA EQ: igual que el refuerzo de graves.
                    if (!ensureEnabled()) eqApplyFailed = true
                    virtualizer = it
                    manager?.setVirtualizer(it.toInt().toShort())
                },
                onValueChangeFinished = { persist() },
                valueRange = 0f..1000f,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "Virtualizador no disponible en este dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

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
                        // RONDA EQ: ensureEnabled marca el aviso si el
                        // teléfono rechaza encender el efecto.
                        ensureEnabled()
                        // RONDA 140: antes se mostraban y guardaban los
                        // niveles aunque el efecto no los hubiera aplicado
                        // (solo se protegía la marca del preset): la
                        // pantalla decía una curva y el audio tenía otra.
                        // Ahora solo se confirman si el efecto los aplicó
                        // de verdad; si falla, vuelven los niveles viejos.
                        val oldLevels = levels
                        levels = preset.levelsDb
                        if (applyToManager()) {
                            selectedPreset = preset.name
                            persist()
                        } else {
                            levels = oldLevels
                            eqApplyFailed = true
                        }
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
                        // RONDA EQ: ensureEnabled marca el aviso si el
                        // teléfono rechaza encender el efecto.
                        ensureEnabled()
                        val oldLevels = levels
                        levels = model.levelsDb
                        if (applyToManager()) {
                            selectedPreset = "AutoEq: ${model.name}"
                            persist()
                        } else {
                            levels = oldLevels
                            eqApplyFailed = true
                        }
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
                                // RONDA EQ: ensureEnabled marca el aviso
                                // si el teléfono rechaza encender el efecto.
                                ensureEnabled()
                                val oldLevels = levels
                                levels = preset.levelsDb
                                if (applyToManager()) {
                                    selectedPreset = preset.name
                                    persist()
                                } else {
                                    levels = oldLevels
                                    eqApplyFailed = true
                                }
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
