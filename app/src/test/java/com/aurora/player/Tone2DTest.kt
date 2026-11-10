package com.aurora.player

import com.aurora.player.audio.applyTone2d
import com.aurora.player.audio.tone2dLevels
import org.junit.Assert.assertEquals
import org.junit.Test

class Tone2DTest {

    @Test
    fun cero_esCero() {
        assertEquals(List(10) { 0f }, tone2dLevels(0f, 0f))
    }

    @Test
    fun graves_pesanAbajo() {
        val levels = tone2dLevels(6f, 0f)
        assertEquals(6f, levels[0], 0.001f)
        assertEquals(6f, levels[2], 0.001f)
        assertEquals(3f, levels[3], 0.001f)
        assertEquals(1.5f, levels[4], 0.001f)
        assertEquals(0f, levels[5], 0.001f)
        assertEquals(0f, levels[9], 0.001f)
    }

    @Test
    fun agudos_pesanArriba() {
        val levels = tone2dLevels(0f, -8f)
        assertEquals(0f, levels[0], 0.001f)
        assertEquals(-2f, levels[6], 0.001f)
        assertEquals(-4f, levels[7], 0.001f)
        assertEquals(-8f, levels[8], 0.001f)
        assertEquals(-8f, levels[9], 0.001f)
    }

    @Test
    fun seSumaAlEq_ySeLimita() {
        val eq = List(10) { 12f }
        val out = applyTone2d(eq, 6f, 6f)
        // 12 + 6 = 18 -> limitado a 15 en graves y agudos.
        assertEquals(15f, out[0], 0.001f)
        assertEquals(12f, out[5], 0.001f)
        assertEquals(15f, out[9], 0.001f)
    }
}
