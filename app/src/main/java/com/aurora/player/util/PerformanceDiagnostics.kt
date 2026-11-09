package com.aurora.player.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Aurora Player — diagnóstico de rendimiento (spec 004, FR-005).
 *
 * Mediciones REALES de este teléfono, nunca inventadas ni estimadas.
 * Lo que no se puede medir se muestra como "—", no como un número
 * bonito. La estabilidad durante reproducción prolongada no cabe en
 * una pantalla: es un protocolo de prueba manual (reproducir horas y
 * mirar si algo se degrada), no un número.
 */

/** Tiempo de arranque: del inicio del proceso al primer dibujo de la biblioteca. */
object StartupTimer {
    @Volatile
    private var firstFrameMs: Long? = null

    /** Se llama una sola vez, cuando la biblioteca se dibuja de verdad. */
    fun markFirstFrame() {
        if (firstFrameMs == null) firstFrameMs = SystemClock.elapsedRealtime()
    }

    /** Milisegundos desde que arrancó el proceso hasta el primer dibujo; null si aún no se marcó. */
    fun startupMs(): Long? {
        val first = firstFrameMs ?: return null
        return first - Process.getStartElapsedRealtime()
    }
}

/** Temperatura de la batería: décimas de grado -> grados; null si no hay dato. */
fun tenthsToCelsius(tenths: Int): Float? =
    if (tenths == Int.MIN_VALUE) null else tenths / 10f

/** FPS medios a partir de duraciones de frame en ms; 0.0 sin muestras. */
fun fpsFromFrameDurations(durationsMs: List<Long>): Double {
    if (durationsMs.isEmpty()) return 0.0
    return durationsMs.size * 1000.0 / durationsMs.sum()
}

/** Frames perdidos: los que tardaron más de ~2 frames a 60 Hz (> 34 ms). */
fun droppedFrames(durationsMs: List<Long>): Int =
    durationsMs.count { it > 34 }

data class DiagnosticsSnapshot(
    /** RAM que usa la app (MB). */
    val appRamUsedMb: Long,
    /** RAM máxima que puede usar la app (MB). */
    val appRamMaxMb: Long,
    /** RAM libre del dispositivo (MB). */
    val deviceRamAvailMb: Long,
    /** RAM total del dispositivo (MB). */
    val deviceRamTotalMb: Long,
    /** Tiempo de CPU del proceso desde que arrancó (ms). */
    val cpuTimeMs: Long,
    /** Núcleos del procesador. */
    val cpuCores: Int,
    /** Batería en %; null si el sistema no lo da. */
    val batteryPercent: Int?,
    /** Temperatura de la batería en °C; null si no hay dato. */
    val batteryTempC: Float?,
    /** Tiempo de arranque en ms; null si aún no se marcó el primer dibujo. */
    val startupMs: Long?,
)

/** Lee todas las mediciones en este momento. Llamar fuera del hilo principal. */
fun collectSnapshot(context: Context): DiagnosticsSnapshot {
    val rt = Runtime.getRuntime()
    val appRamUsedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
    val appRamMaxMb = rt.maxMemory() / 1_048_576L
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val batteryPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        .takeIf { it >= 0 }
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val batteryTempC = tenthsToCelsius(
        batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE,
    )
    return DiagnosticsSnapshot(
        appRamUsedMb = appRamUsedMb,
        appRamMaxMb = appRamMaxMb,
        deviceRamAvailMb = memInfo.availMem / 1_048_576L,
        deviceRamTotalMb = memInfo.totalMem / 1_048_576L,
        cpuTimeMs = Process.getElapsedCpuTime(),
        cpuCores = rt.availableProcessors(),
        batteryPercent = batteryPercent,
        batteryTempC = batteryTempC,
        startupMs = StartupTimer.startupMs(),
    )
}

/**
 * Medidor de FPS con Choreographer: mide los frames reales mientras
 * está en marcha. Arrancar solo cuando la pantalla de diagnóstico
 * está visible; parar al salir (no se deja midiendo en segundo plano).
 */
class FrameRateMeter {
    private val _fps = MutableStateFlow(0.0)
    val fps: StateFlow<Double> = _fps.asStateFlow()
    private val _dropped = MutableStateFlow(0)
    val dropped: StateFlow<Int> = _dropped.asStateFlow()

    private val durations = ArrayDeque<Long>()
    private var lastNanos = -1L
    private var running = false

    private val callback = Choreographer.FrameCallback { nowNanos ->
        if (lastNanos >= 0) {
            durations.addLast((nowNanos - lastNanos) / 1_000_000L)
            if (durations.size > 120) durations.removeFirst()
            _fps.value = fpsFromFrameDurations(durations)
            _dropped.value = droppedFrames(durations)
        }
        lastNanos = nowNanos
        if (running) Choreographer.getInstance().postFrameCallback(callback)
    }

    fun start() {
        if (running) return
        running = true
        lastNanos = -1L
        durations.clear()
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
    }
}
