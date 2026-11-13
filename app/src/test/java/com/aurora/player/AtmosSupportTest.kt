package com.aurora.player

import android.content.Context
import com.aurora.player.audio.AtmosSupport
import com.aurora.player.audio.detectAtmosSupport
import com.aurora.player.audio.mapAtmosSupport
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Slice 5 paso 1: la detección de Atmos es un mapeo puro sobre lo
 * que reporta el dispositivo. En JVM las clases de Android son
 * stubs (todo false), así que el test verifica la tabla de mapeo;
 * el hardware real solo se puede probar en el teléfono.
 */
class AtmosSupportTest {

    @Test
    fun nothingSupported_isNone() {
        assertEquals(AtmosSupport.NONE, mapAtmosSupport(false, false, false))
    }

    @Test
    fun eAc3Only_isDdPlus() {
        assertEquals(AtmosSupport.DD_PLUS, mapAtmosSupport(true, false, false))
    }

    @Test
    fun joc_isAtmos() {
        assertEquals(AtmosSupport.ATMOS, mapAtmosSupport(false, true, false))
        assertEquals(AtmosSupport.ATMOS, mapAtmosSupport(true, true, false))
    }

    @Test
    fun ac4_isAtmos() {
        assertEquals(AtmosSupport.ATMOS, mapAtmosSupport(false, false, true))
        assertEquals(AtmosSupport.ATMOS, mapAtmosSupport(true, false, true))
    }

    @Test
    fun detectOnJvmStubs_isNone() {
        // AudioCapabilities y MediaCodecList son stubs en JVM: cada
        // chequeo da false y la detección cae a NONE sin tirar.
        assertEquals(AtmosSupport.NONE, detectAtmosSupport(mock(Context::class.java)))
    }
}
