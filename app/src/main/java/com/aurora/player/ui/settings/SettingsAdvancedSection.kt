package com.aurora.player.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.util.DiagnosticsSnapshot
import com.aurora.player.util.FrameRateMeter
import com.aurora.player.util.collectSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sección Avanzadas, partida de SettingsScreen (2026-11-09, paso
 * estructural 2): une los DOS bloques ADVANCED que vivían separados
 * en el archivo (Diagnóstico y Restablecer). El diagnóstico se mide
 * solo: crea su propio medidor de FPS y su snapshot; el botón de
 * restablecer llega como callback.
 */
@Composable
fun SettingsAdvancedSection(onResetDefaults: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    TextButton(
        onClick = onResetDefaults,
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Text("Restablecer por defecto")
    }
}
