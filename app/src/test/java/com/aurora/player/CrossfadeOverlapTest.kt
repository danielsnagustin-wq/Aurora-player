package com.aurora.player

import com.aurora.player.playback.overlapWindowMs
import com.aurora.player.playback.shouldOverlapCrossfade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeOverlapTest {

    @Test
    fun `apagado nunca solapa`() {
        assertFalse(shouldOverlapCrossfade(0, true, false, "A", "B", true))
    }

    @Test
    fun `sin siguiente no solapa`() {
        assertFalse(shouldOverlapCrossfade(5, false, false, "A", "B", true))
    }

    @Test
    fun `repetir una pista no solapa`() {
        assertFalse(shouldOverlapCrossfade(5, true, true, "A", "B", true))
    }

    @Test
    fun `mismo album con excepcion no solapa`() {
        assertFalse(shouldOverlapCrossfade(5, true, false, "Dark Side", "Dark Side", true))
    }

    @Test
    fun `mismo album sin excepcion si solapa`() {
        assertTrue(shouldOverlapCrossfade(5, true, false, "Dark Side", "Dark Side", false))
    }

    @Test
    fun `distinto album si solapa`() {
        assertTrue(shouldOverlapCrossfade(5, true, false, "A", "B", true))
    }

    @Test
    fun `album vacio no bloquea el solape`() {
        assertTrue(shouldOverlapCrossfade(5, true, false, "", "", true))
        assertTrue(shouldOverlapCrossfade(5, true, false, null, null, true))
    }

    @Test
    fun `la ventana se recorta a la pista siguiente`() {
        assertEquals(5000L, overlapWindowMs(5, 0L))
        assertEquals(3000L, overlapWindowMs(5, 3000L))
        assertEquals(12000L, overlapWindowMs(20, 0L))
    }
}
