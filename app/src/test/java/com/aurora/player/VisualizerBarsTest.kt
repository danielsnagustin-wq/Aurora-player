package com.aurora.player

import com.aurora.player.audio.fftMagnitudes
import com.aurora.player.audio.fftToBars
import com.aurora.player.audio.smoothBars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class VisualizerBarsTest {

    @Test
    fun magnitud_esHipotenusa() {
        // Un bin con re=3, im=4 da magnitud 5.
        val fft = byteArrayOf(3, 4, 0, 0)
        val mags = fftMagnitudes(fft)
        assertEquals(2, mags.size)
        assertEquals(5f, mags[0], 0.001f)
        assertEquals(0f, mags[1], 0.001f)
    }

    @Test
    fun silencio_daCero() {
        val fft = ByteArray(64)
        val bars = fftToBars(fft, 8)
        assertEquals(8, bars.size)
        assertTrue(bars.all { it == 0f })
    }

    @Test
    fun vacio_daCeros() {
        assertEquals(listOf(0f, 0f, 0f, 0f), fftToBars(ByteArray(0), 4))
        assertEquals(emptyList<Float>(), fftToBars(ByteArray(0), 0))
    }

    @Test
    fun picoFuerte_llegaCercaDeUno() {
        // Todos los bins al máximo (127, 127): magnitud ~179 > 128.
        val fft = ByteArray(64) { 127 }
        val bars = fftToBars(fft, 8)
        assertTrue(bars.all { it == 1f })
    }

    @Test
    fun suavizado_seAcercaPocoAPoco() {
        val prev = listOf(0f, 1f)
        val next = listOf(1f, 0f)
        val smoothed = smoothBars(prev, next, 0.5f)
        assertEquals(0.5f, smoothed[0], 0.001f)
        assertEquals(0.5f, smoothed[1], 0.001f)
        // Tamaños distintos: se devuelve el nuevo tal cual.
        assertEquals(next, smoothBars(listOf(0f), next))
    }
}
