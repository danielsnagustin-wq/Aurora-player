package com.aurora.player

import com.aurora.player.online.CachedStream
import com.aurora.player.online.DEFAULT_STREAM_CACHE_MAX_BYTES
import com.aurora.player.model.StreamQuality
import com.aurora.player.online.filesToEvict
import com.aurora.player.online.readHeader12
import com.aurora.player.online.streamCacheFileName
import com.aurora.player.online.streamCachePartFileName
import com.aurora.player.online.streamExtensionForItag
import com.aurora.player.online.totalCacheSize
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCacheTest {

    private fun cached(id: String, sizeMb: Long, lastUsed: Long) = CachedStream(
        videoId = id,
        itag = 251,
        quality = StreamQuality.HIGH,
        sizeBytes = sizeMb * 1024L * 1024L,
        lastUsedMs = lastUsed,
        fileName = streamCacheFileName(id, 251),
    )

    @Test
    fun opus251EsWebmYAacEsM4a() {
        assertEquals("webm", streamExtensionForItag(251))
        assertEquals("m4a", streamExtensionForItag(140))
        assertEquals("m4a", streamExtensionForItag(139))
    }

    @Test
    fun nombreDeArchivoLlevaVideoEItag() {
        assertEquals("abc123_251.webm", streamCacheFileName("abc123", 251))
        assertEquals("abc123_140.m4a", streamCacheFileName("abc123", 140))
        assertEquals("abc123_251.webm.part", streamCachePartFileName("abc123", 251))
    }

    @Test
    fun limitePorDefectoEs500Mb() {
        assertEquals(500L * 1024L * 1024L, DEFAULT_STREAM_CACHE_MAX_BYTES)
    }

    @Test
    fun siCabeNoSeBorraNada() {
        val files = listOf(cached("a", 100, 1L), cached("b", 100, 2L))
        assertTrue(filesToEvict(files, DEFAULT_STREAM_CACHE_MAX_BYTES).isEmpty())
        assertEquals(200L * 1024L * 1024L, totalCacheSize(files))
    }

    @Test
    fun siPasaElLimiteSeBorraPrimeroElMasViejoSinUsar() {
        val files = listOf(
            cached("viejo", 300, 1L),
            cached("medio", 300, 2L),
            cached("nuevo", 100, 3L),
        )
        val evicted = filesToEvict(files, 500L * 1024L * 1024L)
        assertEquals(listOf("viejo"), evicted.map { it.videoId })
    }

    @Test
    fun seBorranLosNecesariosHastaCaber() {
        val files = listOf(
            cached("a", 300, 1L),
            cached("b", 300, 2L),
            cached("c", 300, 3L),
        )
        val evicted = filesToEvict(files, 500L * 1024L * 1024L)
        assertEquals(listOf("a", "b"), evicted.map { it.videoId })
    }

    @Test
    fun limiteCeroSignificaSinLimite() {
        val files = listOf(cached("a", 900, 1L))
        assertTrue(filesToEvict(files, 0L).isEmpty())
    }

    @Test
    fun elArchivoQueSuenaNoSeBorra() {
        // RONDA 175: el más viejo es el que suena; se salta y se
        // borra el siguiente más viejo hasta caber.
        val files = listOf(
            cached("sonando", 300, 1L),
            cached("medio", 300, 2L),
            cached("nuevo", 100, 3L),
        )
        val evicted = filesToEvict(files, 500L * 1024L * 1024L, setOf("sonando"))
        assertEquals(listOf("medio"), evicted.map { it.videoId })
    }

    // Flujo que entrega como mucho 1 byte por read(), como hacen
    // algunos InputStream reales (RONDA CAZA P1: un solo read() no
    // garantiza llenar el búfer).
    private class OneByteAtATime(private val bytes: ByteArray) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= bytes.size) return -1
            b[off] = bytes[pos++]
            return 1
        }
    }

    @Test
    fun cabeceraSeLeeAunqueElFlujoEntregueUnBytePorLectura() {
        // RONDA 2 (2026-11-13, P1, n=926): fija n=925 — sin el bucle,
        // un .part válido se borraba como corrupto.
        val bytes = ByteArray(12) { it.toByte() }
        val header = readHeader12(OneByteAtATime(bytes))
        assertEquals(bytes.toList(), header?.toList())
    }

    @Test
    fun cabeceraCortaDaNullSinExcepcion() {
        val header = readHeader12(java.io.ByteArrayInputStream(ByteArray(5)))
        assertNull(header)
    }
}
