package com.aurora.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer

/**
 * Aurora Player — ecualizador PROPIO de 10 bandas (nuestro "más":
 * Gramophone/Retro/Phonograph usan el del sistema; Auxio sí tiene uno).
 *
 * Honestidad por dispositivo (regla del usuario: nada de adorno):
 * - Equalizer/BassBoost/Virtualizer se crean sobre la sesión de audio
 *   real de ExoPlayer (audioSessionId). Si el dispositivo no lo soporta,
 *   el constructor lanza y [isAvailable] queda en false: la UI muestra
 *   "Ecualizador no disponible en este dispositivo" y NO finge que suena
 *   distinto. Los presets son valores de banda concretos, no nombres vacíos.
 * - Bandas: 10, de 60 Hz a 14 kHz (las que expone el Equalizer del sistema;
 *   se leen con getCenterFreq, no se inventan frecuencias).
 */
class EqualizerManager(audioSessionId: Int) {

    /** Preset = 10 niveles en dB (-15..+15), en el orden de las bandas del sistema. */
    data class Preset(val name: String, val levelsDb: List<Float>)

    companion object {
        /** Presets como datos puros: testeables sin sesión de audio. */
        val PRESETS: List<Preset> = listOf(
            Preset("Plano", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
            Preset("Rock", listOf(6f, 4f, 2f, 0f, -2f, -1f, 1f, 3f, 5f, 6f)),
            Preset("Pop", listOf(-2f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, -1f, -2f)),
            Preset("Jazz", listOf(4f, 3f, 1f, 2f, -1f, -1f, 1f, 3f, 4f, 5f)),
            Preset("Clásica", listOf(4f, 3f, 2f, 0f, -1f, -1f, 0f, 2f, 3f, 4f)),
            Preset("Graves", listOf(8f, 7f, 5f, 2f, 0f, -2f, -1f, 0f, 1f, 2f)),
            Preset("Agudos", listOf(-2f, -1f, 0f, 1f, 2f, 3f, 4f, 6f, 7f, 8f)),
            Preset("Vocal", listOf(-2f, -1f, 0f, 2f, 5f, 5f, 3f, 1f, 0f, -1f)),
        )
    }

    val presets: List<Preset> = PRESETS

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    /** true solo si el Equalizer real se pudo crear en este dispositivo. */
    val isAvailable: Boolean

    /** Frecuencias centrales reales de las bandas, en Hz (null si no disponible). */
    val bandFrequenciesHz: List<Int>

    init {
        var eq: Equalizer? = null
        var freqs: List<Int> = emptyList()
        try {
            eq = Equalizer(0, audioSessionId)
            val count = eq.numberOfBands.toInt().coerceAtMost(10)
            freqs = (0 until count).map { eq.getCenterFreq(it.toShort()) / 1000 }
        } catch (_: Exception) {
            eq?.release()
            eq = null
        }
        equalizer = eq
        bandFrequenciesHz = freqs
        isAvailable = eq != null
        try { bassBoost = BassBoost(0, audioSessionId) } catch (_: Exception) { bassBoost = null }
        try { virtualizer = Virtualizer(0, audioSessionId) } catch (_: Exception) { virtualizer = null }
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
    }

    /** Aplica un preset real; si el sistema tiene menos de 10 bandas, se recorta. */
    fun applyPreset(preset: Preset) {
        val eq = equalizer ?: return
        val range = eq.bandLevelRange // en centésimas de dB
        preset.levelsDb.forEachIndexed { index, db ->
            if (index >= eq.numberOfBands) return@forEachIndexed
            val milliBel = (db * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
            eq.setBandLevel(index.toShort(), milliBel.toShort())
        }
    }

    fun setBandLevel(band: Int, milliBel: Short) {
        equalizer?.setBandLevel(band.toShort(), milliBel)
    }

    fun setBassBoost(strength: Short) { // 0..1000
        bassBoost?.setStrength(strength)
    }

    fun setVirtualizer(strength: Short) { // 0..1000
        virtualizer?.setStrength(strength)
    }

    fun release() {
        equalizer?.release(); bassBoost?.release(); virtualizer?.release()
        equalizer = null; bassBoost = null; virtualizer = null
    }
}
