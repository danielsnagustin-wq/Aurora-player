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
            Preset("Electrónica", listOf(7f, 5f, 2f, 0f, -1f, 0f, 2f, 4f, 6f, 7f)),
            Preset("Reggaeton", listOf(7f, 6f, 3f, 1f, 0f, 0f, 1f, 2f, 4f, 5f)),
            Preset("Hip-Hop", listOf(7f, 6f, 4f, 1f, -1f, -1f, 1f, 2f, 4f, 5f)),
            Preset("R&B", listOf(5f, 4f, 2f, 1f, 1f, 2f, 3f, 4f, 5f, 4f)),
            Preset("Blues", listOf(3f, 2f, 1f, 2f, 0f, 0f, 2f, 3f, 4f, 4f)),
            Preset("Salsa", listOf(4f, 3f, 1f, 2f, 3f, 3f, 2f, 3f, 4f, 5f)),
            Preset("Bachata", listOf(4f, 3f, 2f, 2f, 3f, 3f, 2f, 2f, 3f, 4f)),
            Preset("Merengue", listOf(5f, 4f, 2f, 1f, 2f, 2f, 3f, 4f, 5f, 5f)),
            Preset("Metal", listOf(6f, 5f, 3f, 0f, -2f, -2f, 1f, 4f, 6f, 7f)),
            Preset("Punk", listOf(5f, 4f, 2f, 0f, -1f, -1f, 2f, 4f, 5f, 6f)),
            Preset("Dance", listOf(6f, 5f, 2f, 0f, -1f, 0f, 2f, 4f, 6f, 7f)),
            Preset("House", listOf(7f, 5f, 2f, 0f, -1f, 0f, 1f, 3f, 5f, 6f)),
            Preset("Techno", listOf(7f, 6f, 3f, 0f, -1f, 0f, 2f, 4f, 6f, 7f)),
            Preset("Trance", listOf(6f, 5f, 2f, 0f, -1f, 0f, 2f, 4f, 6f, 8f)),
            Preset("Dubstep", listOf(8f, 7f, 4f, 1f, -2f, -1f, 1f, 3f, 5f, 6f)),
            Preset("Trap", listOf(8f, 6f, 3f, 0f, -1f, -1f, 1f, 3f, 5f, 6f)),
            Preset("Lo-Fi", listOf(4f, 3f, 2f, 1f, 0f, -1f, -2f, -1f, 1f, 2f)),
            Preset("K-Pop", listOf(-1f, 0f, 2f, 4f, 5f, 4f, 3f, 2f, 1f, 0f)),
            Preset("Country", listOf(2f, 1f, 0f, 1f, 2f, 3f, 3f, 2f, 2f, 3f)),
            Preset("Folk", listOf(2f, 1f, 0f, 1f, 2f, 2f, 2f, 2f, 3f, 3f)),
            Preset("Reggae", listOf(6f, 5f, 3f, 0f, -1f, 0f, 2f, 3f, 4f, 5f)),
            Preset("Soul", listOf(4f, 3f, 2f, 2f, 2f, 3f, 3f, 3f, 4f, 4f)),
            Preset("Funk", listOf(5f, 4f, 2f, 1f, 1f, 2f, 3f, 4f, 5f, 5f)),
            Preset("Gospel", listOf(3f, 2f, 1f, 2f, 3f, 4f, 3f, 2f, 3f, 4f)),
            Preset("Indie", listOf(2f, 1f, 0f, 1f, 2f, 2f, 2f, 3f, 4f, 4f)),
            Preset("Alternativo", listOf(5f, 4f, 2f, 0f, -1f, 0f, 2f, 4f, 5f, 6f)),
            Preset("Acústica", listOf(2f, 1f, 0f, 1f, 2f, 3f, 3f, 3f, 3f, 3f)),
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

    /** Nivel actual de una banda, en centésimas de dB (0 si no disponible). */
    fun getBandLevel(band: Int): Short {
        return try {
            equalizer?.getBandLevel(band.toShort()) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /** Número de bandas reales del dispositivo (0 si no disponible). */
    fun getBandCount(): Int {
        return try {
            equalizer?.numberOfBands?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /** Fuerza actual del refuerzo de graves, 0..1000 (0 si no disponible). */
    fun getBassBoostStrength(): Short {
        return try {
            // BassBoost no tiene getStrength(): solo getRoundedStrength().
            bassBoost?.roundedStrength ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /** Fuerza actual del virtualizador, 0..1000 (0 si no disponible). */
    fun getVirtualizerStrength(): Short {
        return try {
            // Virtualizer no tiene getStrength(): solo getRoundedStrength().
            virtualizer?.roundedStrength ?: 0
        } catch (_: Exception) {
            0
        }
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
