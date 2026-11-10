package com.aurora.player

import com.aurora.player.util.ShakeDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeDetectorTest {

    @Test
    fun `en reposo no dispara`() {
        var count = 0
        val detector = ShakeDetector { count++ }
        // Reposo: solo gravedad en un eje (~9,81).
        detector.onSensorChanged(0f, 0f, 9.81f, 1000L)
        detector.onSensorChanged(0f, 9.81f, 0f, 1100L)
        assertEquals(0, count)
    }

    @Test
    fun `una sacudida dispara una vez`() {
        var count = 0
        val detector = ShakeDetector { count++ }
        detector.onSensorChanged(15f, 15f, 15f, 1000L)
        assertEquals(1, count)
    }

    @Test
    fun `dos sacudidas seguidas solo cuentan una`() {
        var count = 0
        val detector = ShakeDetector { count++ }
        detector.onSensorChanged(15f, 15f, 15f, 1000L)
        detector.onSensorChanged(15f, 15f, 15f, 1200L)
        assertEquals(1, count)
    }

    @Test
    fun `pasado el intervalo vuelve a disparar`() {
        var count = 0
        val detector = ShakeDetector { count++ }
        detector.onSensorChanged(15f, 15f, 15f, 1000L)
        detector.onSensorChanged(15f, 15f, 15f, 1600L)
        assertEquals(2, count)
    }

    @Test
    fun `un bache suave no es una sacudida`() {
        var count = 0
        val detector = ShakeDetector { count++ }
        // ~1,5 G: un bache, no una sacudida.
        detector.onSensorChanged(8f, 8f, 8f, 1000L)
        assertEquals(0, count)
    }
}
