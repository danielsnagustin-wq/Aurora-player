package com.aurora.player

import com.aurora.player.util.DiskArtCache
import com.aurora.player.util.DiskArtCache.DiskFile
import org.junit.Assert.assertEquals
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
