package com.aurora.player.audio

import kotlin.math.abs

/**
 * Forma de onda (Fase 2 app): picos por columna para dibujar la onda
 * de una canción (a lo Poweramp/AIMP). Función pura: recibe PCM
 * mono ya decodificado (el mismo pipeline del LUFS) y devuelve un
 * pico 0..1 por columna, con normalización por el pico global para
 * que la onda llene el alto. Sin PCM no hay onda: nunca se dibuja
 * una forma inventada.
 */
fun waveformPeaks(samples: FloatArray, columns: Int): List<Float> {
    if (columns <= 0 || samples.isEmpty()) return List(columns.coerceAtLeast(0)) { 0f }
    val perColumn = samples.size / columns
    if (perColumn <= 0) {
        // Menos muestras que columnas: una muestra por columna.
        return List(columns) { i -> abs(samples.getOrNull(i) ?: 0f) }
    }
    val peaks = List(columns) { col ->
        var peak = 0f
        val start = col * perColumn
        val end = if (col == columns - 1) samples.size else start + perColumn
        for (i in start until end) {
            val v = abs(samples[i])
            if (v > peak) peak = v
        }
        peak
    }
    val max = peaks.maxOrNull() ?: 0f
    if (max <= 0f) return peaks
    return peaks.map { it / max }
}

/** RMS por columna (onda más suave que con picos), mismo contrato. */
fun waveformRms(samples: FloatArray, columns: Int): List<Float> {
    if (columns <= 0 || samples.isEmpty()) return List(columns.coerceAtLeast(0)) { 0f }
    val perColumn = samples.size / columns
    if (perColumn <= 0) return List(columns) { i -> abs(samples.getOrNull(i) ?: 0f) }
    val values = List(columns) { col ->
        var sum = 0.0
        val start = col * perColumn
        val end = if (col == columns - 1) samples.size else start + perColumn
        for (i in start until end) {
            val v = samples[i].toDouble()
            sum += v * v
        }
        kotlin.math.sqrt(sum / (end - start)).toFloat()
    }
    val max = values.maxOrNull() ?: 0f
    if (max <= 0f) return values
    return values.map { it / max }
}
