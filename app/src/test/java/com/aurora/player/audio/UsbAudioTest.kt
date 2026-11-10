package com.aurora.player.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioTest {

    private val dac = UsbDacInfo(
        name = "DAC de prueba",
        sampleRatesHz = listOf(44100, 48000, 96000, 192000),
        channelCounts = listOf(2),
    )

    @Test
    fun pickNativeRate_trackEnLista_devuelveLaTasa() {
        assertEquals(44100, pickNativeRate(44100, dac.sampleRatesHz))
        assertEquals(192000, pickNativeRate(192000, dac.sampleRatesHz))
    }

    @Test
    fun pickNativeRate_trackFueraDeLista_devuelveNull() {
        assertNull(pickNativeRate(88200, dac.sampleRatesHz))
        assertNull(pickNativeRate(176400, dac.sampleRatesHz))
    }

    @Test
    fun pickNativeRate_sinTasaOListaVacia_devuelveNull() {
        assertNull(pickNativeRate(null, dac.sampleRatesHz))
        assertNull(pickNativeRate(44100, emptyList()))
    }

    @Test
    fun formatUsbRates_listaConocida() {
        assertEquals("44.1, 48, 96, 192 kHz", formatUsbRates(dac.sampleRatesHz))
    }

    @Test
    fun formatUsbRates_vacia() {
        assertEquals("sin tasas conocidas", formatUsbRates(emptyList()))
    }

    @Test
    fun usbStatusLine_sinDac_vacia() {
        assertEquals("", usbStatusLine(null, 44100, 48000))
    }

    @Test
    fun usbStatusLine_tasaNativa_loDice() {
        val line = usbStatusLine(dac, 96000, 48000)
        assertTrue(line.contains("tasa nativa"))
        assertTrue(line.contains("96 kHz"))
    }

    @Test
    fun usbStatusLine_tasaNoSoportada_avisaResampleo() {
        val line = usbStatusLine(dac, 88200, 48000)
        assertTrue(line.contains("resamplea"))
        assertTrue(line.contains("48 kHz"))
    }

    @Test
    fun usbStatusLine_sinTasaDePista_listaTasas() {
        val line = usbStatusLine(dac, null, 48000)
        assertTrue(line.contains("tasas: 44.1, 48, 96, 192 kHz"))
    }
}
