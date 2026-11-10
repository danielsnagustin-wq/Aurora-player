package com.aurora.player.audio

/**
 * Salida Directa (Fase 2 audio, mitad honesta del "bit-perfect"):
 * en modo Directa la app NO toca el audio — sin ReplayGain, sin
 * saltar silencios, sin balance, sin EQ del sistema, sin crossfade
 * y con el volumen a 1.0. El PCM llega a AudioTrack tal cual sale
 * del decodificador.
 *
 * Lo que NO se promete: Android puede resamplear en su mezclador
 * si la frecuencia de la pista no coincide con la del sistema, y
 * sin un DAC USB no hay bit-perfect total. La línea de estado lo
 * dice así, con la frecuencia real de la pista que suena. El DVC
 * (control de volumen directo del DAC) y la salida USB directa
 * quedan para la ronda de audio USB, no se fingen aquí.
 */
fun isDirectMode(mode: String): Boolean = mode == "direct"

/** Todo lo que la Directa apaga: cada procesador de la cadena. */
data class DirectProcessingState(
    val replayGainOff: Boolean,
    val skipSilenceOff: Boolean,
    val balanceOff: Boolean,
    val systemEqOff: Boolean,
    val crossfadeOff: Boolean,
    val volumeAtOne: Boolean,
) {
    val allOff: Boolean
        get() = replayGainOff && skipSilenceOff && balanceOff &&
            systemEqOff && crossfadeOff && volumeAtOne
}

/**
 * Línea de estado honesta para la pantalla de Sonido: qué está
 * sonando y qué se está saltando. Sin frecuencia conocida, no se
 * inventa ninguna.
 */
fun directModeStatusLine(
    direct: Boolean,
    sampleRateHz: Int?,
    bitDepth: Int?,
): String {
    if (!direct) return "Estándar: con procesado (ReplayGain, EQ y balance según tus ajustes)."
    val format = when {
        sampleRateHz != null && bitDepth != null -> "${sampleRateHz / 1000} kHz · $bitDepth bits"
        sampleRateHz != null -> "${sampleRateHz / 1000} kHz"
        else -> "formato de la pista"
    }
    return "Directa: sin procesado, $format. Android puede resamplear en su mezclador; sin DAC USB no es bit-perfect total."
}
