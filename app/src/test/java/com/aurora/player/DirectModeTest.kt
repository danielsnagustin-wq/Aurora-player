package com.aurora.player

import com.aurora.player.audio.directModeStatusLine
import com.aurora.player.audio.isDirectMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectModeTest {

    @Test
    fun soloDirectEsDirecta() {
        assertTrue(isDirectMode("direct"))
        assertFalse(isDirectMode("standard"))
        assertFalse(isDirectMode(""))
        assertFalse(isDirectMode("DIRECT"))
    }

    @Test
    fun estadoEstandar_noPrometeNada() {
        val line = directModeStatusLine(false, 44100, 16)
        assertTrue(line.startsWith("Estándar"))
        assertFalse(line.contains("bit-perfect"))
    }

    @Test
    fun estadoDirecta_conFormatoReal() {
        val line = directModeStatusLine(true, 96000, 24)
        assertTrue(line.contains("96 kHz"))
        assertTrue(line.contains("24 bits"))
        assertTrue(line.contains("sin procesado"))
        assertTrue(line.contains("sin DAC USB no es bit-perfect total"))
    }

    @Test
    fun estadoDirecta_sinFormato_noInventa() {
        val line = directModeStatusLine(true, null, null)
        assertFalse(line.contains("kHz"))
        assertTrue(line.contains("formato de la pista"))
        assertEquals(
            "Directa: sin procesado, formato de la pista. Android puede resamplear en su mezclador; sin DAC USB no es bit-perfect total.",
            line,
        )
    }
}
