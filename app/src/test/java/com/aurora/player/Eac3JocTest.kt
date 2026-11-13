package com.aurora.player

import com.aurora.player.util.findEac3Joc
import com.aurora.player.util.parseEac3JocAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 5 paso 2: detección de E-AC-3 JOC con frames sintéticos
 * construidos bit a bit (el layout es ETSI TS 102 366 Anexo E).
 * Sin un archivo JOC real, estos bytes son la única prueba del
 * recorrido de cabecera: cada variante ejercita una rama distinta.
 */
class Eac3JocTest {

    /** Escritor de bits MSB-first para construir frames sintéticos. */
    private class BitWriter {
        private val bits = mutableListOf<Int>()
        fun write(value: Int, n: Int) {
            for (i in n - 1 downTo 0) bits += (value shr i) and 1
        }
        fun bit(b: Int) {
            bits += b and 1
        }
        fun bytes(): ByteArray {
            val out = ByteArray((bits.size + 7) / 8)
            bits.forEachIndexed { i, b ->
                if (b == 1) out[i / 8] = (out[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
            }
            return out
        }
    }

    /**
     * Frame E-AC-3 mínimo con las ramas que se pidan. Por defecto:
     * estéreo, 6 bloques, 48 kHz, sin compresión ni metadatos.
     * addbsi: null = sin additional bitstream info; true/false =
     * flag_ec3_extension_type_a con índice de complejidad 16.
     */
    private fun eac3Frame(
        addbsiFlag: Boolean? = true,
        channelMode: Int = 2,
        lfeOn: Boolean = false,
        compr: Boolean = false,
        mixing: Boolean = false,
        informational: Boolean = false,
        reducedRate: Boolean = false,
        numBlocksCode: Int = 3,
    ): ByteArray {
        val w = BitWriter()
        w.write(0x0B77, 16) // syncword
        w.write(0, 2) // strmtyp: independiente
        w.write(0, 3) // substreamid
        w.write(31, 11) // frmsiz -> frame de 64 B
        if (reducedRate) {
            w.write(3, 2) // fscod reducido
            w.write(1, 2) // fscod2
        } else {
            w.write(0, 2) // fscod 48 kHz
            w.write(numBlocksCode, 2)
        }
        w.write(channelMode, 3) // acmod
        w.bit(if (lfeOn) 1 else 0)
        w.write(16, 5) // bsid = E-AC-3
        val rounds = if (channelMode == 0) 2 else 1
        repeat(rounds) {
            w.write(0, 5) // dialnorm
            w.bit(if (compr) 1 else 0)
            if (compr) w.write(0, 8) // dynrng
        }
        w.bit(if (mixing) 1 else 0)
        if (mixing) {
            if (channelMode > 2) {
                w.write(0, 2) // preferred downmix
                if (channelMode and 1 != 0) {
                    w.write(0, 3) // cmixlev ltrt
                    w.write(0, 3) // cmixlev
                }
                if (channelMode and 4 != 0) {
                    w.write(0, 3) // surmixlev ltrt
                    w.write(0, 3) // surmixlev
                }
            }
            if (lfeOn) {
                w.bit(1) // lfe mix level existe
                w.write(0, 5)
            }
            repeat(rounds) {
                w.bit(1) // program scale factor existe
                w.write(0, 6)
            }
            w.bit(1) // external program scale factor existe
            w.write(0, 6)
            w.write(1, 2) // mixing data: caso 1
            w.write(0, 5)
            if (channelMode < 2) {
                val panRounds = if (channelMode == 0) 2 else 1
                repeat(panRounds) {
                    w.bit(1)
                    w.write(0, 8) // pan mean
                    w.write(0, 6) // reservado
                }
            }
            val numBlocks = when (numBlocksCode) {
                0 -> 1
                1 -> 2
                2 -> 3
                else -> 6
            }
            w.bit(1) // mixing config existe
            for (i in 0 until numBlocks) {
                if (numBlocks != 1) w.bit(1)
                w.write(0, 5)
            }
        }
        w.bit(if (informational) 1 else 0)
        if (informational) {
            w.write(0, 3) // bitstream mode
            w.write(0, 2) // copyright + original
            if (channelMode == 2) {
                w.write(0, 2) // dsurmod
                w.write(0, 2) // dhpmod
            }
            if (channelMode >= 6) w.write(0, 2) // dsurexmod
            repeat(rounds) {
                w.bit(1)
                w.write(0, 8)
            }
            if (!reducedRate) w.bit(0) // source sample rate code
        }
        val numBlocks = when (numBlocksCode) {
            0 -> 1
            1 -> 2
            2 -> 3
            else -> 6
        }
        if (numBlocks != 6) w.bit(0) // converter sync flag
        if (addbsiFlag == null) {
            w.bit(0) // sin additional bitstream info
        } else {
            w.bit(1) // addbsie
            w.write(0, 6) // addbsil = 1 byte
            w.write(0, 7)
            w.bit(if (addbsiFlag) 1 else 0) // flag_ec3_extension_type_a
            if (addbsiFlag) w.write(16, 8) // complexity index
        }
        return w.bytes()
    }

    @Test
    fun minimalJoc_isTrue() {
        assertEquals(true, parseEac3JocAt(eac3Frame(true), 0))
        assertTrue(findEac3Joc(eac3Frame(true)))
    }

    @Test
    fun flagZero_isFalse() {
        assertEquals(false, parseEac3JocAt(eac3Frame(false), 0))
        assertFalse(findEac3Joc(eac3Frame(false)))
    }

    @Test
    fun noAddbsi_isFalse() {
        assertEquals(false, parseEac3JocAt(eac3Frame(null), 0))
        assertFalse(findEac3Joc(eac3Frame(null)))
    }

    @Test
    fun fullBranchesJoc_isTrue() {
        // 3F2R + LFE + compr + mezcla + info + tasa reducida: todas
        // las ramas de longitud variable del recorrido de cabecera.
        val frame = eac3Frame(
            addbsiFlag = true,
            channelMode = 7,
            lfeOn = true,
            compr = true,
            mixing = true,
            informational = true,
            reducedRate = true,
        )
        assertEquals(true, parseEac3JocAt(frame, 0))
    }

    @Test
    fun dualMonoJoc_isTrue() {
        // Dual-mono (2 rondas dialnorm/pan) + 1 bloque + flag de
        // sincronización del conversor.
        val frame = eac3Frame(
            addbsiFlag = true,
            channelMode = 0,
            mixing = true,
            informational = true,
            numBlocksCode = 0,
        )
        assertEquals(true, parseEac3JocAt(frame, 0))
    }

    @Test
    fun ac3Frame_isFalse() {
        // AC-3 normal (bsid 8): syncword válido pero no es E-AC-3,
        // así que no decide y el escaneo no encuentra nada.
        val w = BitWriter()
        w.write(0x0B77, 16) // syncword
        w.write(0, 16) // crc1
        w.write(0, 2) // sr_code
        w.write(10, 6) // frame_size_code
        w.write(8, 5) // bsid = AC-3
        w.write(0, 3) // bitstream mode
        w.write(0, 3) // channel mode
        w.bit(0) // lfe
        val bytes = w.bytes()
        assertNull(parseEac3JocAt(bytes, 0))
        assertFalse(findEac3Joc(bytes))
    }

    @Test
    fun truncated_isFalse() {
        assertNull(parseEac3JocAt(byteArrayOf(0x0B, 0x77, 0x00), 0))
        assertFalse(findEac3Joc(byteArrayOf(0x0B, 0x77, 0x00)))
    }

    @Test
    fun noSyncword_isFalse() {
        assertFalse(findEac3Joc(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test
    fun embeddedFrame_isFound() {
        // Como en un .mka: 100 bytes de cabecera EBML antes del frame.
        val junk = ByteArray(100) { 0x55 }
        val frame = eac3Frame(true)
        val bytes = junk + frame
        assertNull(parseEac3JocAt(bytes, 0))
        assertEquals(true, parseEac3JocAt(bytes, 100))
        assertTrue(findEac3Joc(bytes))
    }
}
