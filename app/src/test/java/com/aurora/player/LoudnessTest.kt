package com.aurora.player

import com.aurora.player.audio.Biquad
import com.aurora.player.audio.KWeighting48k
import com.aurora.player.audio.integratedLufs
import com.aurora.player.audio.kWeightedMeanSquare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessTest {

    @Test
    fun biquad_pasaContinua() {
        // Un biquad identidad (b0=1, resto 0) deja pasar la señal tal cual.
        val f = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(0.5, f.process(0.5), 1e-9)
        assertEquals(-0.25, f.process(-0.25), 1e-9)
    }

    @Test
    fun silencio_daNull() {
        assertNull(integratedLufs(emptyList()))
        assertNull(integratedLufs(listOf(0.0, 0.0)))
        assertEquals(0.0, kWeightedMeanSquare(FloatArray(4800)), 1e-12)
    }

    @Test
    fun tonoConstante_enRangoSano() {
        // Seno 1 kHz a -20 dBFS (amplitud 0.1) a 48 kHz: LUFS cerca de -20.
        val samples = FloatArray(48000) { i ->
            (0.1 * sin(2.0 * PI * 1000.0 * i / 48000.0)).toFloat()
        }
        val power = kWeightedMeanSquare(samples)
        assertTrue(power > 0.0)
        val lufs = integratedLufs(listOf(power))
        assertTrue(lufs != null)
        // Simulación en Python del mismo filtro: -23.00 LUFS exactos.
        // El filtro K apenas cambia a 1 kHz; margen holgado a ambos lados.
        assertTrue(lufs!! > -24.5 && lufs < -21.5)
    }

    @Test
    fun puertaRelativa_excluyeLoCallado() {
        // Un bloque fuerte (10^-2) y nueve muy callados (10^-6):
        // la puerta relativa (-10 LU) deja solo el fuerte.
        val powers = listOf(0.01) + List(9) { 0.000001 }
        val lufs = integratedLufs(powers)
        assertTrue(lufs != null)
        // Solo el bloque fuerte: -0.691 + 10*log10(0.01) = -20.691.
        assertEquals(-20.691, lufs!!, 0.01)
    }

    @Test
    fun filtroK_noExplota() {
        val f = KWeighting48k()
        var max = 0.0
        for (i in 0 until 48000) {
            val y = f.process(sin(2.0 * PI * 440.0 * i / 48000.0))
            if (kotlin.math.abs(y) > max) max = kotlin.math.abs(y)
        }
        // Ganancia del filtro K a 440 Hz: orden 1, nunca 100x.
        assertTrue(max < 10.0)
    }
}
