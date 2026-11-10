package com.aurora.player

import com.aurora.player.audio.bluetoothCodecLine
import com.aurora.player.audio.bluetoothCodecName
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothCodecTest {

    @Test
    fun nombres_conocidos() {
        assertEquals("SBC", bluetoothCodecName(1))
        assertEquals("AAC", bluetoothCodecName(2))
        assertEquals("aptX", bluetoothCodecName(3))
        assertEquals("aptX HD", bluetoothCodecName(4))
        assertEquals("LDAC", bluetoothCodecName(5))
        assertEquals("LHDC", bluetoothCodecName(6))
        assertEquals("LC3", bluetoothCodecName(7))
        assertEquals("Desconocido", bluetoothCodecName(0))
        assertEquals("Desconocido", bluetoothCodecName(99))
    }

    @Test
    fun linea_conDatos() {
        assertEquals("LDAC · 96 kHz · 24 bits", bluetoothCodecLine(5, 96000, 24))
        assertEquals("SBC · 44 kHz", bluetoothCodecLine(1, 44100, null))
        assertEquals("AAC", bluetoothCodecLine(2, null, null))
    }
}
