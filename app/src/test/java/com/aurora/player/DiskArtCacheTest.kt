package com.aurora.player

import com.aurora.player.util.DiskArtCache
import com.aurora.player.util.DiskArtCache.DiskFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 5: carátulas en disco. Solo se prueba la lógica pura
 * (nombres y desalojo); leer/escribir bitmaps necesita Android.
 */
class DiskArtCacheTest {

    @Test
    fun nombrePorAlbumYTamano() {
        assertEquals("123_144.jpg", DiskArtCache.fileName(123, 144))
        assertEquals("123_1024.jpg", DiskArtCache.fileName(123, 1024))
        assertEquals("7_144.jpg", DiskArtCache.fileName(7, 144))
    }

    @Test
    fun nombreTemporalUnicoPorEscritura() {
        // RONDA 2 (2026-11-13, P2): el temporal lleva el nanoTime
        // para que dos escrituras del mismo álbum no compartan .tmp.
        assertEquals("123_144.jpg.1000.tmp", DiskArtCache.tmpFileName(123, 144, 1000L))
        assertEquals("7_1024.jpg.42.tmp", DiskArtCache.tmpFileName(7, 1024, 42L))
        assertFalse(
            DiskArtCache.tmpFileName(123, 144, 1L) ==
                DiskArtCache.tmpFileName(123, 144, 2L),
        )
        val live = DiskArtCache.tmpFileName(123, 144)
        assertTrue(live.startsWith("123_144."))
        assertTrue(live.endsWith(".tmp"))
    }

    @Test
    fun bajoElTope_noBorraNada() {
        val files = listOf(
            DiskFile("1_144.jpg", 100_000, 1000),
            DiskFile("2_144.jpg", 100_000, 2000),
        )
        assertTrue(DiskArtCache.pickEviction(files, 200_000).isEmpty())
        assertTrue(DiskArtCache.pickEviction(files, 200_001).isEmpty())
    }

    @Test
    fun sobreElTope_borraLosMasViejosPrimero() {
        val files = listOf(
            DiskFile("viejo_144.jpg", 100_000, 1000),
            DiskFile("medio_144.jpg", 100_000, 2000),
            DiskFile("nuevo_144.jpg", 100_000, 3000),
        )
        // 300.000 > 200.000: sobra 100.000 -> solo el más viejo.
        assertEquals(
            listOf("viejo_144.jpg"),
            DiskArtCache.pickEviction(files, 200_000),
        )
        // 300.000 > 150.000: sobran 150.000 -> los dos más viejos.
        assertEquals(
            listOf("viejo_144.jpg", "medio_144.jpg"),
            DiskArtCache.pickEviction(files, 150_000),
        )
    }

    @Test
    fun topeCero_loBorraTodo() {
        val files = listOf(DiskFile("1_144.jpg", 10, 1000))
        assertEquals(listOf("1_144.jpg"), DiskArtCache.pickEviction(files, 0))
    }
}
