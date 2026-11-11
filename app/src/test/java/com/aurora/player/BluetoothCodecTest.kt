package com.aurora.player

import com.aurora.player.audio.bluetoothCodecLine
import com.aurora.player.audio.bluetoothCodecName
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothCodecTest {

    @Test
    fun nombres_conocidos() {
        // Tipos AOSP 0-based (BluetoothCodecConfig.SOURCE_CODEC_TYPE_*).
        assertEquals("SBC", bluetoothCodecName(0))
        assertEquals("AAC", bluetoothCodecName(1))
        assertEquals("aptX", bluetoothCodecName(2))
        assertEquals("aptX HD", bluetoothCodecName(3))
        assertEquals("LDAC", bluetoothCodecName(4))
        assertEquals("aptX Adaptive", bluetoothCodecName(5))
        assertEquals("aptX TWS+", bluetoothCodecName(6))
        assertEquals("LC3", bluetoothCodecName(7))
        assertEquals("Opus", bluetoothCodecName(8))
        assertEquals("Desconocido", bluetoothCodecName(99))
    }

    @Test
    fun linea_conDatos() {
        assertEquals("LDAC · 96 kHz · 24 bits", bluetoothCodecLine(4, 96000, 24))
        assertEquals("SBC · 44 kHz", bluetoothCodecLine(0, 44100, null))
        assertEquals("AAC", bluetoothCodecLine(1, null, null))
    }
}
