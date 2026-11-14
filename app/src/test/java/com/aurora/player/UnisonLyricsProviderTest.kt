package com.aurora.player

import com.aurora.player.lyrics.UnisonResult
import com.aurora.player.lyrics.buildUnisonSearchUrl
import com.aurora.player.lyrics.parseUnisonSearchResponse
import com.aurora.player.lyrics.pickUnisonResult
import com.aurora.player.lyrics.ttmlToOnlineLyrics
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unison (2026-11-11): búsqueda TTML por palabra, sin clave.
 * Puro, sin Android, para probarlo en la JVM. El JSON de ejemplo
 * tiene la forma real verificada en vivo con Coldplay "Yellow".
 */
class UnisonLyricsProviderTest {

    @Test
    fun `url de busqueda con song y artist`() {
        assertEquals(
            "https://unison.boidu.dev/lyrics/search" +
                "?song=Yellow&artist=Coldplay",
            buildUnisonSearchUrl("Yellow", "Coldplay"),
        )
    }

    private fun searchJson(): String =
        "{\"success\":true,\"data\":[" +
            "{\"song\":\"Yellow\",\"artist\":\"Coldplay\",\"album\":\"Parachutes\"," +
            "\"lyrics\":\"<tt xmlns=\\\"http://www.w3.org/ns/ttml\\\">" +
            "<body><div><p begin=\\\"00:00:33.58\\\">Look at the stars</p>" +
            "<p begin=\\\"00:00:36.30\\\">Look how they shine for you</p>" +
            "</div></body></tt>\"}," +
            "{\"song\":\"Yellow Submarine\",\"artist\":\"The Beatles\"," +
            "\"lyrics\":\"<tt><body><div><p begin=\\\"00:00:01.00\\\">In the town</p>" +
            "</div></body></tt>\"}" +
            "]}"

    @Test
    fun `busqueda parsea candidatos con su TTML`() {
        val results = parseUnisonSearchResponse(searchJson())
        assertEquals(2, results.size)
        assertEquals("Yellow", results[0].song)
        assertEquals("Coldplay", results[0].artist)
        assertTrue(results[0].lyricsTtml.contains("Look at the stars"))
    }

    @Test
    fun `success falso o json roto es lista vacia`() {
        assertEquals(
            emptyList<UnisonResult>(),
            parseUnisonSearchResponse("{\"success\":false,\"data\":[]}"),
        )
        assertEquals(emptyList<UnisonResult>(), parseUnisonSearchResponse("no-json"))
    }

    @Test
    fun `pick elige el candidato que coincide`() {
        val best = pickUnisonResult(
            parseUnisonSearchResponse(searchJson()),
            "Yellow",
            "Coldplay",
        )
        assertEquals("Yellow", best?.song)
        assertEquals("Coldplay", best?.artist)
    }

    @Test
    fun `pick coincide Istanbul con locale turco`() {
        // RONDA 2 (2026-11-13, P2): pin de extremo a extremo de
        // normalizeForMatch con Locale.ROOT (la I mayúscula turca
        // es la que rompía la coincidencia). El test que realmente
        // caza la regresión está en BiniLyricsProviderTest
        // (normalizeForMatch directo bajo locale tr-TR).
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val candidates = listOf(UnisonResult("ISTANBUL", "Sezen Aksu", ""))
            val best = pickUnisonResult(candidates, "Istanbul", "Sezen Aksu")
            assertEquals("ISTANBUL", best?.song)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `pick descarta otra cancion`() {
        assertNull(
            pickUnisonResult(
                parseUnisonSearchResponse(searchJson()),
                "Yellow",
                "The Beatles",
            ),
        )
    }

    @Test
    fun `el TTML del candidato se convierte a LRC sincronizado`() {
        val best = pickUnisonResult(
            parseUnisonSearchResponse(searchJson()),
            "Yellow",
            "Coldplay",
        )!!
        val lyrics = ttmlToOnlineLyrics(best.lyricsTtml)
        assertTrue(lyrics?.syncedLrc?.contains("Look at the stars") == true)
        assertTrue(lyrics?.syncedLrc?.contains("[00:33.58]") == true)
        // RONDA 2 (2026-11-14, P2#15-fix): plainText ya no es null cuando hay LRC:
        // es el respaldo con TODAS las líneas (por si el LRC no se pudiera leer).
        assertTrue(lyrics?.plainText?.contains("Look at the stars") == true)
    }
}
