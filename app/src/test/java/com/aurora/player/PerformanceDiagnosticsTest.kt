package com.aurora.player

import com.aurora.player.util.droppedFrames
import com.aurora.player.util.fpsFromFrameDurations
import com.aurora.player.util.tenthsToCelsius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Aurora Player — tests de diagnóstico (spec 004, lógica pura). */
class PerformanceDiagnosticsTest {

    @Test
    fun `temperatura de bateria - decimas a grados`() {
        assertEquals(25.0f, tenthsToCelsius(250)!!, 0.001f)
        assertEquals(36.5f, tenthsToCelsius(365)!!, 0.001f)
        assertEquals(0.0f, tenthsToCelsius(0)!!, 0.001f)
    }

    @Test
    fun `temperatura de bateria - sin dato da null`() {
        assertNull(tenthsToCelsius(Int.MIN_VALUE))
    }

    @Test
    fun `FPS - 60 frames de 16 ms son unos 62 FPS`() {
        val durations = List(60) { 16L }
        val fps = fpsFromFrameDurations(durations)
        assertEquals(62.5, fps, 0.01)
    }

    @Test
    fun `FPS - sin muestras es cero`() {
        assertEquals(0.0, fpsFromFrameDurations(emptyList()), 0.0)
    }

    @Test
    fun `FPS - un solo frame de 20 ms son 50 FPS`() {
        assertEquals(50.0, fpsFromFrameDurations(listOf(20L)), 0.01)
    }

    @Test
    fun `frames perdidos - solo cuentan los de mas de 34 ms`() {
        val durations = listOf(16L, 17L, 50L, 16L, 100L, 33L, 34L)
        assertEquals(2, droppedFrames(durations))
    }

    @Test
    fun `frames perdidos - sin muestras es cero`() {
        assertEquals(0, droppedFrames(emptyList()))
    }
}
