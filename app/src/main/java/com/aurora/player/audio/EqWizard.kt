package com.aurora.player.audio

/**
 * RONDA 7 P1: asistente de EQ — lógica pura (sin Android), testeable.
 *
 * Idea de Metrolist (WizardScreen): en vez de mover 10 bandas a ciegas,
 * el usuario dice qué escucha y cómo le gusta que suene, y el asistente
 * propone una curva. La curva parte del preset de fábrica del género y
 * le suma ajustes honestos de graves/agudos/energía, siempre recortada
 * a ±15 dB (lo que el Equalizer acepta).
 */
enum class BassPref { MORE, BALANCED, LESS }
enum class TreblePref { MORE, BALANCED, LESS }
enum class EnergyPref { SOFT, NORMAL, STRONG }

/** Curva sugerida: 10 niveles en dB, en el orden de las bandas del sistema. */
fun suggestCurve(
    genre: String,
    bass: BassPref,
    treble: TreblePref,
    energy: EnergyPref,
): List<Float> {
    val base = EqualizerManager.PRESETS.firstOrNull { it.name == genre }?.levelsDb
        ?: List(10) { 0f }
    val out = base.toMutableList()
    // Graves: bandas 0-1 (y algo la 2-3).
    val bassDelta = when (bass) {
        BassPref.MORE -> listOf(4f, 4f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)
        BassPref.LESS -> listOf(-4f, -4f, -2f, -1f, 0f, 0f, 0f, 0f, 0f, 0f)
        BassPref.BALANCED -> List(10) { 0f }
    }
    // Agudos: bandas 7-9.
    val trebleDelta = when (treble) {
        TreblePref.MORE -> listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 4f)
        TreblePref.LESS -> listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, -2f, -4f, -4f)
        TreblePref.BALANCED -> List(10) { 0f }
    }
    for (i in 0 until 10) {
        out[i] = (out[i] + bassDelta[i] + trebleDelta[i]).coerceIn(-15f, 15f)
    }
    val factor = when (energy) {
        EnergyPref.SOFT -> 0.6f
        EnergyPref.NORMAL -> 1f
        EnergyPref.STRONG -> 1.25f
    }
    return out.map { (it * factor).coerceIn(-15f, 15f) }
}

/**
 * Posición X (0..1) de una frecuencia en escala logarítmica entre
 * minHz y maxHz. Función pura: la gráfica la usa para colocar cada
 * banda donde le toca de verdad.
 */
fun freqToX(freqHz: Int, minHz: Int, maxHz: Int): Float {
    if (minHz <= 0 || maxHz <= minHz || freqHz <= 0) return 0f
    val x = (kotlin.math.log10(freqHz.toFloat()) - kotlin.math.log10(minHz.toFloat())) /
        (kotlin.math.log10(maxHz.toFloat()) - kotlin.math.log10(minHz.toFloat()))
    return x.coerceIn(0f, 1f)
}
