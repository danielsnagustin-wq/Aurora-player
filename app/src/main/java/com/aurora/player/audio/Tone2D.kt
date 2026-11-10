package com.aurora.player.audio

/**
 * Tone 2D (Fase 2 audio, a lo Poweramp): dos controles, graves y
 * agudos (-10..+10 dB cada uno), que se suman a los 10 niveles del
 * EQ como estanterías suaves. Los graves pesan enteros en 31-125 Hz
 * y se desvanecen hacia 500 Hz; los agudos igual hacia 2 kHz.
 * Es una capa encima del EQ, no un preset: el EQ que suena es
 * niveles del EQ + tono, limitado a ±15 dB por banda.
 */
val TONE_BAND_WEIGHTS_BASS: List<Float> =
    listOf(1f, 1f, 1f, 0.5f, 0.25f, 0f, 0f, 0f, 0f, 0f)

val TONE_BAND_WEIGHTS_TREBLE: List<Float> =
    listOf(0f, 0f, 0f, 0f, 0f, 0f, 0.25f, 0.5f, 1f, 1f)

/** Graves/agudos en dB -> deltas por banda de 10 (31 Hz..16 kHz). */
fun tone2dLevels(bassDb: Float, trebleDb: Float): List<Float> {
    val bass = bassDb.coerceIn(-10f, 10f)
    val treble = trebleDb.coerceIn(-10f, 10f)
    return List(10) { i ->
        (bass * TONE_BAND_WEIGHTS_BASS[i] + treble * TONE_BAND_WEIGHTS_TREBLE[i])
            .coerceIn(-15f, 15f)
    }
}

/** Aplica el tono sobre los niveles del EQ (suma, limitada a ±15 dB). */
fun applyTone2d(eqLevels: List<Float>, bassDb: Float, trebleDb: Float): List<Float> {
    val tone = tone2dLevels(bassDb, trebleDb)
    return List(10) { i ->
        ((eqLevels.getOrNull(i) ?: 0f) + tone[i]).coerceIn(-15f, 15f)
    }
}
