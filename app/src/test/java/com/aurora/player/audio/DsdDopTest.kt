package com.aurora.player.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DsdDopTest {

    private val DSF_MINIMO = byteArrayOf(68, 83, 68, 32, 28, 0, 0, 0, 0, 0, 0, 0, 108, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 102, 109, 116, 32, 52, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0, 0, 17, 43, 0, 1, 0, 0, 0, 16, 0, 0, 0, 0, 0, 0, 0, 0, 16, 0, 0, 0, 0, 0, 0, 100, 97, 116, 97, 16, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

    private val DFF_MINIMO = byteArrayOf(70, 82, 77, 56, 0, 0, 0, 0, 0, 0, 0, 94, 68, 83, 68, 32, 70, 86, 69, 82, 0, 0, 0, 0, 0, 0, 0, 4, 1, 5, 0, 0, 80, 82, 79, 80, 0, 0, 0, 0, 0, 0, 0, 34, 83, 78, 68, 32, 70, 83, 32, 32, 0, 0, 0, 0, 0, 0, 0, 4, 0, 43, 17, 0, 67, 72, 78, 76, 0, 0, 0, 0, 0, 0, 0, 2, 0, 2, 68, 83, 68, 32, 0, 0, 0, 0, 0, 0, 0, 16, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

    private val DFF_DST = byteArrayOf(70, 82, 77, 56, 0, 0, 0, 0, 0, 0, 0, 94, 68, 83, 68, 32, 70, 86, 69, 82, 0, 0, 0, 0, 0, 0, 0, 4, 1, 5, 0, 0, 80, 82, 79, 80, 0, 0, 0, 0, 0, 0, 0, 34, 83, 78, 68, 32, 70, 83, 32, 32, 0, 0, 0, 0, 0, 0, 0, 4, 0, 43, 17, 0, 67, 72, 78, 76, 0, 0, 0, 0, 0, 0, 0, 2, 0, 2, 68, 83, 84, 32, 0, 0, 0, 0, 0, 0, 0, 16, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)

    @Test
    fun parseDsfHeader_leeTasaCanalesYOffset() {
        val info = parseDsfHeader(DSF_MINIMO)
        assertNotNull(info)
        info!!
        assertEquals(DsdFormat.DSF, info.format)
        assertEquals(2822400, info.sampleRateHz)
        assertEquals(2, info.channels)
        assertEquals(16L, info.sampleCountPerChannel)
        assertEquals(92, info.dataOffset)
        assertEquals(16L, info.dataSize)
        assertTrue(info.bitOrderLsbFirst)
        assertFalse(info.compressedDst)
    }

    @Test
    fun parseDsfHeader_basura_devuelveNull() {
        assertNull(parseDsfHeader(byteArrayOf(1, 2, 3, 4, 5)))
        assertNull(parseDsfHeader(ByteArray(200)))
    }

    @Test
    fun parseDsfHeader_fmtEnorme_devuelveNullSinRomper() {
        // fmtSize = 0x7FFFFFE4: sin el tope, (28 + fmtSize).toInt()
        // daba negativo y fourCc leía en offset negativo (ronda 9).
        val bytes = DSF_MINIMO.copyOf()
        bytes[32] = 0xE4.toByte()
        bytes[33] = 0xFF.toByte()
        bytes[34] = 0xFF.toByte()
        bytes[35] = 0x7F.toByte()
        bytes[36] = 0
        bytes[37] = 0
        bytes[38] = 0
        bytes[39] = 0
        assertNull(parseDsfHeader(bytes))
    }

    @Test
    fun parseDffHeader_tamanoEnorme_devuelveNullSinRomper() {
        // Tamaño del chunk FVER = 0x7FFFFFE4: sin el avance en Long,
        // pos desbordaba a negativo y fourCc leía fuera (ronda 9).
        val bytes = DFF_MINIMO.copyOf()
        bytes[20] = 0
        bytes[21] = 0
        bytes[22] = 0
        bytes[23] = 0
        bytes[24] = 0x7F.toByte()
        bytes[25] = 0xFF.toByte()
        bytes[26] = 0xFF.toByte()
        bytes[27] = 0xE4.toByte()
        assertNull(parseDffHeader(bytes))
    }

    @Test
    fun parseDffHeader_leeTasaCanalesYOffset() {
        val info = parseDffHeader(DFF_MINIMO)
        assertNotNull(info)
        info!!
        assertEquals(DsdFormat.DFF, info.format)
        assertEquals(2822400, info.sampleRateHz)
        assertEquals(2, info.channels)
        assertEquals(90, info.dataOffset)
        assertEquals(16L, info.dataSize)
        assertFalse(info.bitOrderLsbFirst)
        assertFalse(info.compressedDst)
    }

    @Test
    fun parseDffHeader_dst_seMarcaComprimido() {
        val info = parseDffHeader(DFF_DST)
        assertNotNull(info)
        assertTrue(info!!.compressedDst)
    }

    @Test
    fun parseDffHeader_basura_devuelveNull() {
        assertNull(parseDffHeader(byteArrayOf(1, 2, 3, 4, 5)))
        assertNull(parseDffHeader(ByteArray(200)))
    }

    @Test
    fun dopCarrierRate_yEtiqueta() {
        assertEquals(176400, dopCarrierRateHz(2822400))
        assertEquals(352800, dopCarrierRateHz(5644800))
        assertEquals(705600, dopCarrierRateHz(11289600))
        assertEquals("DSD64", dopLabel(2822400))
        assertEquals("DSD128", dopLabel(5644800))
        assertEquals("DSD256", dopLabel(11289600))
        assertEquals("DSD", dopLabel(1234567))
    }

    @Test
    fun isDsdFile_porExtensionYMime() {
        assertTrue(isDsdFile("/musica/tema.dsf", null))
        assertTrue(isDsdFile("/musica/tema.DFF", null))
        assertTrue(isDsdFile(null, "audio/dsf"))
        assertTrue(isDsdFile(null, "audio/x-dff"))
        assertFalse(isDsdFile("/musica/tema.flac", "audio/flac"))
        assertFalse(isDsdFile(null, null))
    }

    @Test
    fun dopPackStereo24_marcadoresAlternosYRoundTrip() {
        val left = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val right = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
        val packed = dopPackStereo24(left, right)
        // 2 tramas x 6 bytes. Trama 0: marcador 0x05; trama 1: 0xFA.
        assertEquals(12, packed.size)
        assertEquals(0x05.toByte(), packed[2])
        assertEquals(0x05.toByte(), packed[5])
        assertEquals(0xFA.toByte(), packed[8])
        assertEquals(0xFA.toByte(), packed[11])
        val (unL, unR) = dopUnpackStereo24(packed)
        assertArrayEquals(left, unL)
        assertArrayEquals(right, unR)
    }

    @Test
    fun dopPackStereo24_longitudImpar_descartaElSuelto() {
        val packed = dopPackStereo24(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
        assertEquals(6, packed.size)
    }
}
