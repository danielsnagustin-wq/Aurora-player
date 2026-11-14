package com.aurora.player

import com.aurora.player.lyrics.BinimumResult
import com.aurora.player.lyrics.buildBinimumSearchUrl
import com.aurora.player.lyrics.isAllowedLyricsUrl
import com.aurora.player.lyrics.normalizeForMatch
import com.aurora.player.lyrics.parseBinimumSearch
import com.aurora.player.lyrics.pickBinimumResult
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BiniLyrics (proveedores múltiples Fase 2): URL de búsqueda, elección
 * del mejor candidato y lista de hosts permitidos. Puro, sin Android.
 */
class BiniLyricsProviderTest {

    @Test
    fun `url de busqueda con album`() {
        assertEquals(
            "https://lyrics-api.binimum.org/?track=Bonebreaker&artist=Excision&duration=210&album=Onyx",
            buildBinimumSearchUrl("Bonebreaker", "Excision", "Onyx", 210),
        )
    }

    @Test
    fun `sin titulo o artista no hay url`() {
        assertNull(buildBinimumSearchUrl("", "Excision", null, 0))
        assertNull(buildBinimumSearchUrl("Bonebreaker", " ", null, 0))
    }

    @Test
    fun `solo los dos hosts conocidos con https`() {
        assertTrue(isAllowedLyricsUrl("https://lrc.red/s/abc123.ttml"))
        assertTrue(isAllowedLyricsUrl("https://lyrics-api.binimum.org/lyrics/x.ttml"))
        assertFalse(isAllowedLyricsUrl("http://lrc.red/s/abc123.ttml"))
        assertFalse(isAllowedLyricsUrl("https://evil.example.com/x.ttml"))
        assertFalse(isAllowedLyricsUrl("no-es-una-url"))
    }

    @Test
    fun `elige el candidato con duracion mas cercana`() {
        val results = listOf(
            BinimumResult("Bonebreaker", "Excision", 200.0, "https://lrc.red/s/a.ttml"),
            BinimumResult("Bonebreaker", "Excision", 211.0, "https://lrc.red/s/b.ttml"),
            BinimumResult("Bonebreaker", "Excision", 300.0, "https://lrc.red/s/c.ttml"),
        )
        assertEquals("https://lrc.red/s/b.ttml", pickBinimumResult(results, "Bonebreaker", "Excision", 210)?.lyricsUrl)
    }

    @Test
    fun `descarta otra cancion y otro host`() {
        val results = listOf(
            BinimumResult("Otra cancion", "Excision", 210.0, "https://lrc.red/s/a.ttml"),
            BinimumResult("Bonebreaker", "Excision", 210.0, "https://evil.example.com/x.ttml"),
            BinimumResult("Bonebreaker", "Excision", 210.0, "https://lrc.red/s/b.ttml"),
        )
        assertEquals("https://lrc.red/s/b.ttml", pickBinimumResult(results, "Bonebreaker", "Excision", 210)?.lyricsUrl)
    }

    @Test
    fun `normalizeForMatch no depende del locale del sistema`() {
        // RONDA 2 (2026-11-13, P2): en locale turco, lowercase()
        // sin locale convierte "Istanbul" en "ıstanbul" (i sin
        // punto) y la coincidencia se rompe. Con Locale.ROOT da
        // "istanbul" en cualquier locale. Sin el arreglo, este
        // test falla. Se restaura el locale al salir.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertEquals("istanbul", normalizeForMatch("Istanbul"))
            assertEquals("istanbul", normalizeForMatch("ISTANBUL"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `sin candidatos no hay nada`() {
        assertNull(pickBinimumResult(emptyList(), "Bonebreaker", "Excision", 210))
    }

    @Test
    fun `parsea el json de busqueda`() {
        val json = """{"results":[{"track_name":"Bonebreaker","artist_name":"Excision","album_name":"Onyx","duration":211.0,"isrc":"USXXX1234567","lyricsUrl":"https://lrc.red/s/abc.ttml","timing_type":"word"}]}"""
        val results = parseBinimumSearch(json)
        assertEquals(1, results.size)
        assertEquals("Bonebreaker", results[0].trackName)
        assertEquals(211.0, results[0].durationSec, 0.001)
        assertEquals("https://lrc.red/s/abc.ttml", results[0].lyricsUrl)
    }

    @Test
    fun `json roto o sin resultados da lista vacia`() {
        assertTrue(parseBinimumSearch("no es json").isEmpty())
        assertTrue(parseBinimumSearch("""{"results":[]}""").isEmpty())
    }
}
