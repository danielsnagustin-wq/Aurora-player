package com.aurora.player

import com.aurora.player.audio.waveformPeaks
import com.aurora.player.audio.waveformRms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformTest {

    @Test
    fun vacio_daCeros() {
        assertEquals(listOf(0f, 0f, 0f), waveformPeaks(FloatArray(0), 3))
        assertEquals(emptyList<Float>(), waveformPeaks(floatArrayOf(0.5f), 0))
    }

    @Test
    fun silencio_daCeros() {
        assertEquals(listOf(0f, 0f), waveformPeaks(FloatArray(100), 2))
    }

    @Test
    fun picos_seNormalizan() {
        // Primera mitad a 0.5, segunda a 0.25: normalizado 1.0 y 0.5.
        val samples = FloatArray(100) { i -> if (i < 50) 0.5f else 0.25f }
        val peaks = waveformPeaks(samples, 2)
        assertEquals(1f, peaks[0], 0.001f)
        assertEquals(0.5f, peaks[1], 0.001f)
    }

    @Test
    fun rms_suavizaMasQuePico() {
        // Columna 0: un pico aislado (1,0,0,0); columna 1: nivel
        // constante 0.5. El pico marca 1.0 y 0.5; el RMS suaviza el
        // pico aislado y marca 1.0 en ambas tras normalizar.
        val samples = floatArrayOf(1f, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f, 0.5f)
        val peaks = waveformPeaks(samples, 2)
        val rms = waveformRms(samples, 2)
        assertEquals(1f, peaks[0], 0.001f)
        assertEquals(0.5f, peaks[1], 0.001f)
        assertEquals(1f, rms[0], 0.001f)
        assertEquals(1f, rms[1], 0.001f)
        assertTrue(rms[1] > peaks[1])
    }
}
