package com.aurora.player

import com.aurora.player.util.flacBitDepth
import com.aurora.player.util.wavBitDepth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Profundidad de bits real desde la cabecera del archivo (FLAC
 * STREAMINFO, WAV chunk "fmt "). Los vectores se construyen byte a
 * byte según la especificación de cada formato; nada de valores
 * calculados a mano sin comprobar el layout.
 */
class SongDetailsTest {

    /** Cabecera FLAC mínima: "fLaC" + STREAMINFO (34 bytes). */
    private fun flacHeader(bps: Int, sampleRate: Int = 44100): ByteArray {
        val bytes = ByteArray(42)
        bytes[0] = 'f'.code.toByte()
        bytes[1] = 'L'.code.toByte()
        bytes[2] = 'a'.code.toByte()
        bytes[3] = 'C'.code.toByte()
        bytes[4] = 0x00 // primer bloque, tipo 0 = STREAMINFO
        bytes[5] = 0x00
        bytes[6] = 0x00
        bytes[7] = 0x22 // longitud 34
        bytes[8] = 0x10 // min blocksize 4096
        bytes[9] = 0x00
        bytes[10] = 0x10 // max blocksize 4096
        bytes[11] = 0x00
        // min/max framesize en 0 (bytes 12..17).
        // Campo de 64 bits (bytes 18..25): 20 bits frecuencia,
        // 3 bits canales-1, 5 bits profundidad-1, 36 bits muestras.
        bytes[18] = ((sampleRate ushr 12) and 0xFF).toByte()
        bytes[19] = ((sampleRate ushr 4) and 0xFF).toByte()
        val channelsMinusOne = 1 // estéreo
        val bpsMinusOne = bps - 1
        bytes[20] = (((sampleRate and 0xF) shl 4) or
            (channelsMinusOne shl 1) or ((bpsMinusOne ushr 4) and 0x1)).toByte()
        bytes[21] = (((bpsMinusOne and 0xF) shl 4)).toByte()
        return bytes
    }

    /** Cabecera WAV mínima: RIFF + WAVE + chunk "fmt " (16 bytes). */
    private fun wavHeader(bps: Int, junkBeforeFmt: Boolean = false): ByteArray {
        val bytes = ByteArray(64)
        bytes[0] = 'R'.code.toByte()
        bytes[1] = 'I'.code.toByte()
        bytes[2] = 'F'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[8] = 'W'.code.toByte()
        bytes[9] = 'A'.code.toByte()
        bytes[10] = 'V'.code.toByte()
        bytes[11] = 'E'.code.toByte()
        var fmtOffset = 12
        if (junkBeforeFmt) {
            // Chunk "JUNK" de 4 bytes antes del fmt: el parser debe saltarlo.
            bytes[12] = 'J'.code.toByte()
            bytes[13] = 'U'.code.toByte()
            bytes[14] = 'N'.code.toByte()
            bytes[15] = 'K'.code.toByte()
            bytes[16] = 0x04
            bytes[17] = 0x00
            bytes[18] = 0x00
            bytes[19] = 0x00
            fmtOffset = 24
        }
        bytes[fmtOffset] = 'f'.code.toByte()
        bytes[fmtOffset + 1] = 'm'.code.toByte()
        bytes[fmtOffset + 2] = 't'.code.toByte()
        bytes[fmtOffset + 3] = ' '.code.toByte()
        bytes[fmtOffset + 4] = 0x10 // tamaño del fmt: 16
        bytes[fmtOffset + 5] = 0x00
        bytes[fmtOffset + 6] = 0x00
        bytes[fmtOffset + 7] = 0x00
        bytes[fmtOffset + 8] = 0x01 // PCM
        bytes[fmtOffset + 9] = 0x00
        bytes[fmtOffset + 10] = 0x02 // estéreo
        bytes[fmtOffset + 11] = 0x00
        bytes[fmtOffset + 12] = 0x44 // 44100 Hz little-endian
        bytes[fmtOffset + 13] = 0xAC.toByte()
        bytes[fmtOffset + 14] = 0x00
        bytes[fmtOffset + 15] = 0x00
        bytes[fmtOffset + 20] = 0x04 // blockAlign 4
        bytes[fmtOffset + 21] = 0x00
        bytes[fmtOffset + 22] = (bps and 0xFF).toByte()
        bytes[fmtOffset + 23] = ((bps ushr 8) and 0xFF).toByte()
        return bytes
    }

    @Test
    fun flac16BitsSeLeeDelStreaminfo() {
        assertEquals(16, flacBitDepth(flacHeader(16)))
    }

    @Test
    fun flac24BitsSeLeeDelStreaminfo() {
        assertEquals(24, flacBitDepth(flacHeader(24)))
    }

    @Test
    fun flacSinMagiaDevuelveNull() {
        val bytes = flacHeader(16)
        bytes[0] = 'X'.code.toByte()
        assertNull(flacBitDepth(bytes))
    }

    @Test
    fun flacCortoDevuelveNull() {
        assertNull(flacBitDepth(ByteArray(10)))
    }

    @Test
    fun flacLongitudDistintaDevuelveNull() {
        val bytes = flacHeader(16)
        bytes[7] = 0x21 // 33 en vez de 34
        assertNull(flacBitDepth(bytes))
    }

    @Test
    fun wav16BitsSeLeeDelFmt() {
        assertEquals(16, wavBitDepth(wavHeader(16)))
    }

    @Test
    fun wav24BitsSeLeeDelFmt() {
        assertEquals(24, wavBitDepth(wavHeader(24)))
    }

    @Test
    fun wavSaltaChunksPreviosHastaElFmt() {
        assertEquals(16, wavBitDepth(wavHeader(16, junkBeforeFmt = true)))
    }

    @Test
    fun wavSinMagiaDevuelveNull() {
        val bytes = wavHeader(16)
        bytes[0] = 'X'.code.toByte()
        assertNull(wavBitDepth(bytes))
    }

    @Test
    fun wavCortoDevuelveNull() {
        assertNull(wavBitDepth(ByteArray(8)))
    }
}
