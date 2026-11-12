package com.aurora.player

import com.aurora.player.lyrics.buildLiriqoUrl
import com.aurora.player.lyrics.liriqoMatches
import com.aurora.player.lyrics.liriqoToOnlineLyrics
import com.aurora.player.lyrics.parseLiriqoResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LiriQo (2026-11-11): agregador sin clave, pista `primary` con
 * lrc / plain / ttml. Puro, sin Android, para probarlo en la JVM.
 * El JSON de ejemplo tiene la forma real verificada en vivo con
 * Coldplay "Yellow".
 */
class LiriqoLyricsProviderTest {

    @Test
    fun `url con titulo artista y duracion`() {
        assertEquals(
            "https://api.liriqo-alfarrizi.my.id/v1/lyrics" +
                "?title=Yellow&artist=Coldplay&duration=266",
            buildLiriqoUrl("Yellow", "Coldplay", 266L),
        )
    }

    @Test
    fun `url sin duracion no lleva el parametro`() {
        assertEquals(
            "https://api.liriqo-alfarrizi.my.id/v1/lyrics" +
                "?title=Yellow&artist=Coldplay",
            buildLiriqoUrl("Yellow", "Coldplay", 0L),
        )
    }

    private fun responseJson(): String =
        "{\"matchScore\":1,\"primary\":{" +
            "\"title\":\"Yellow\",\"artist\":\"Coldplay\",\"album\":\"Parachutes\"," +
            "\"lrc\":\"[00:33.52]Look at the stars\\n[00:36.38]Look how they shine for you\"," +
            "\"plain\":\"Look at the stars\\nLook how they shine for you\"," +
            "\"ttml\":\"\",\"syncLevel\":\"syllable\"}}"

    @Test
    fun `parsea la pista primary con su lrc`() {
        val track = parseLiriqoResponse(responseJson())
        assertEquals("Yellow", track?.title)
        assertEquals("Coldplay", track?.artist)
        assertTrue(track?.lrc?.contains("[00:33.52]Look at the stars") == true)
    }

    @Test
    fun `sin primary usa la primera de tracks`() {
        val track = parseLiriqoResponse(
            "{\"tracks\":[{\"title\":\"Yellow\",\"artist\":\"Coldplay\"," +
                "\"plain\":\"Look at the stars\"}]}",
        )
        assertEquals("Yellow", track?.title)
        assertEquals("Look at the stars", track?.plain)
    }

    @Test
    fun `json roto o sin pista es null`() {
        assertNull(parseLiriqoResponse("no-json"))
        assertNull(parseLiriqoResponse("{\"matchScore\":0}"))
    }

    @Test
    fun `la coincidencia exige titulo y artista`() {
        val track = parseLiriqoResponse(responseJson())!!
        assertTrue(liriqoMatches(track, "Yellow", "Coldplay"))
        assertTrue(!liriqoMatches(track, "Yellow", "The Beatles"))
        assertTrue(!liriqoMatches(track, "Clocks", "Coldplay"))
    }

    @Test
    fun `lrc manda sobre plain y ttml`() {
        val lyrics = liriqoToOnlineLyrics(parseLiriqoResponse(responseJson())!!)
        assertTrue(lyrics?.syncedLrc?.contains("[00:33.52]") == true)
        assertNull(lyrics?.plainText)
    }

    @Test
    fun `sin lrc cae a plain`() {
        val track = parseLiriqoResponse(
            "{\"primary\":{\"title\":\"Yellow\",\"artist\":\"Coldplay\"," +
                "\"plain\":\"Look at the stars\"}}",
        )!!
        val lyrics = liriqoToOnlineLyrics(track)
        assertNull(lyrics?.syncedLrc)
        assertEquals("Look at the stars", lyrics?.plainText)
    }

    @Test
    fun `sin nada es null`() {
        val track = parseLiriqoResponse(
            "{\"primary\":{\"title\":\"Yellow\",\"artist\":\"Coldplay\"}}",
        )!!
        assertNull(liriqoToOnlineLyrics(track))
    }
}
