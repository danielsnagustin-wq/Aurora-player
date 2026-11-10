package com.aurora.player.audio

import kotlin.math.hypot

/**
 * Visualizador real (Fase 2 audio): convierte los datos FFT del
 * Visualizer de Android (android.media.audiofx.Visualizer, sobre la
 * sesión de audio real de ExoPlayer) en alturas de barras 0..1 para
 * dibujar en el reproductor. Hasta ahora el "visualizador" era solo
 * un color del tema; esto es sonido de verdad.
 *
 * Formato FFT de Android: bytes intercalados [real, imaginario] por
 * bin; la magnitud es sqrt(re² + im²). Los bins se agrupan en barras
 * en escala cuadrática — detalle fino en graves, grueso en agudos,
 * como oye el oído — y cada magnitud se normaliza a 0..1.
 */
fun fftMagnitudes(fft: ByteArray): List<Float> {
    val bins = fft.size / 2
    if (bins <= 0) return emptyList()
    return List(bins) { i ->
        val re = fft[i * 2].toFloat()
        val im = fft[i * 2 + 1].toFloat()
        hypot(re, im)
    }
}

/** Magnitudes de bins a alturas de barras 0..1 (barCount barras). */
fun fftToBars(fft: ByteArray, barCount: Int): List<Float> {
    if (barCount <= 0) return emptyList()
    val magnitudes = fftMagnitudes(fft)
    if (magnitudes.isEmpty()) return List(barCount) { 0f }
    val bins = magnitudes.size
    return List(barCount) { bar ->
        val start = (bins * (bar.toDouble() / barCount).let { it * it }).toInt()
        val end = (bins * ((bar + 1).toDouble() / barCount).let { it * it }).toInt()
            .coerceAtLeast(start + 1)
        var peak = 0f
        for (i in start until end.coerceAtMost(bins)) {
            if (magnitudes[i] > peak) peak = magnitudes[i]
        }
        (peak / 128f).coerceIn(0f, 1f)
    }
}

/** Suavizado exponencial entre fotogramas: evita que las barras tiemblen. */
fun smoothBars(previous: List<Float>, next: List<Float>, alpha: Float = 0.35f): List<Float> {
    if (previous.size != next.size) return next
    val a = alpha.coerceIn(0f, 1f)
    return List(next.size) { i -> previous[i] + (next[i] - previous[i]) * a }
}
