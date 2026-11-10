package com.aurora.player.audio

import kotlin.math.log10
import kotlin.math.pow

/**
 * LUFS (Fase 2 audio): sonoridad integrada según ITU-R BS.1770.
 * Solo la matemática pura y testeable: filtro K (dos biquads con
 * los coeficientes del estándar a 48 kHz) + puertas absoluta
 * (-70 LUFS) y relativa (-10 LU) + integración.
 *
 * La decodificación del archivo a PCM 48 kHz (MediaCodec) es la
 * segunda parte y NO está aquí: sin PCM real no se mide nada, y
 * este archivo no inventa valores. Un archivo sin medir no tiene
 * LUFS; el ReplayGain de la etiqueta sigue mandando hasta entonces.
 */

/** Biquad Direct Form II transpuesta (a0 = 1). */
class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }
}

/**
 * Filtro K a 48 kHz (coeficientes de BS.1770-4): estantería de
 * agudos + paso alto RLB. Solo válido a 48 kHz: el decodificador
 * debe remuestrear a 48 kHz antes de medir.
 */
class KWeighting48k {
    // Estantería: b = [1.53512485958697, -2.69169618940638,
    // 1.19839281085285], a = [1, -1.69065929318241, 0.73248077421585].
    private val shelf = Biquad(
        1.53512485958697, -2.69169618940638, 1.19839281085285,
        -1.69065929318241, 0.73248077421585,
    )
    // Paso alto RLB: b = [1, -2, 1],
    // a = [1, -1.99004745483398, 0.99007225036621].
    private val rlb = Biquad(
        1.0, -2.0, 1.0,
        -1.99004745483398, 0.99007225036621,
    )

    fun process(x: Double): Double = rlb.process(shelf.process(x))

    fun reset() {
        shelf.reset()
        rlb.reset()
    }
}

/** Potencia media de un canal tras el filtro K (peso 1.0; 1.41 para traseros). */
fun kWeightedMeanSquare(samples: FloatArray, channelWeight: Double = 1.0): Double {
    if (samples.isEmpty()) return 0.0
    val filter = KWeighting48k()
    var sum = 0.0
    for (s in samples) {
        val y = filter.process(s.toDouble())
        sum += y * y
    }
    return (sum / samples.size) * channelWeight
}

private const val ABSOLUTE_GATE_LUFS = -70.0
private const val RELATIVE_GATE_LU = -10.0
private const val LUFS_OFFSET = -0.691

private fun powerToLufs(meanPower: Double): Double =
    LUFS_OFFSET + 10.0 * log10(meanPower)

/**
 * Sonoridad integrada de una lista de potencias medias por bloque
 * (400 ms, solape 75%): puerta absoluta, luego relativa, luego
 * media de lo que queda. null si no hay ningún bloque audible.
 */
fun integratedLufs(blockPowers: List<Double>): Double? {
    val audible = blockPowers.filter { it > 0.0 && powerToLufs(it) > ABSOLUTE_GATE_LUFS }
    if (audible.isEmpty()) return null
    val ungated = powerToLufs(audible.average())
    val relativeThreshold = ungated + RELATIVE_GATE_LU
    val gated = audible.filter { powerToLufs(it) > relativeThreshold }
    if (gated.isEmpty()) return null
    return powerToLufs(gated.average())
}
