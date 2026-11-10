package com.aurora.player.util

import kotlin.math.sqrt

/**
 * Fase 1b — agitar para pasar a la siguiente (idea de Stellio).
 *
 * La detección es pura y testeable: fuerza G de la aceleración
 * (módulo / 9,81). En reposo es ~1 G; una sacudida supera 2,5 G.
 * Con `minIntervalMs` no dispara dos veces con el mismo gesto.
 * El tiempo lo pasa quien llama (SystemClock.elapsedRealtime()),
 * así los tests usan tiempos falsos.
 */
class ShakeDetector(
    private val thresholdG: Float = 2.5f,
    private val minIntervalMs: Long = 500L,
    private val onShake: () -> Unit,
) {
    private var lastShakeAtMs: Long = Long.MIN_VALUE

    fun onSensorChanged(x: Float, y: Float, z: Float, nowMs: Long) {
        val gForce = sqrt(x * x + y * y + z * z) / 9.81f
        if (gForce < thresholdG) return
        if (lastShakeAtMs != Long.MIN_VALUE && nowMs - lastShakeAtMs < minIntervalMs) return
        lastShakeAtMs = nowMs
        onShake()
    }
}
