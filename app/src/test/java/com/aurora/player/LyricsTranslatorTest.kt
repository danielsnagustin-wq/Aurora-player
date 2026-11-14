package com.aurora.player

import com.aurora.player.lyrics.LyricsTranslator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Traductor de letras: el parseo de la respuesta gtx es puro. */
class LyricsTranslatorTest {

    @Test
    fun respuestaRealGtx() {
        // Respuesta real de translate.googleapis.com (2026-11-10).
        val json = "[[[\"Hola mundo, esta es una canción.\",\"Hello world, this is a song\",null,null,3,null,null,[[]],[[[\"e050167b38f2a566522b4157651fc616\",\"en_es_2023q1.md\"]]]]],null,\"en\",null,null,null,1,[],[[\"en\"],null,[1],[\"en\"]]]"
        val parsed = LyricsTranslator.parseGtxResponse(json)
        assertEquals(listOf("Hola mundo, esta es una canción."), parsed?.first)
        assertEquals("en", parsed?.second)
    }

    @Test
    fun variosSegmentosSeJuntan() {
        val json = "[[[\"Hola \",\"Hello \",null,null,3],[\"mundo.\",\"world.\",null,null,3]],null,\"en\"]"
        val parsed = LyricsTranslator.parseGtxResponse(json)
        assertEquals(listOf("Hola ", "mundo."), parsed?.first)
        assertEquals("en", parsed?.second)
    }

    @Test
    fun chunkLines_cortaPorTamanoSinPartirLineas() {
        // RONDA 2 (2026-11-13, P2): pin de P2#17 — translateLines
        // traduce en trozos de <= 2000 caracteres unidos; chunkLines
        // es pura y nunca parte una línea por la mitad.
        assertEquals(
            listOf(listOf("a", "b"), listOf("c")),
            LyricsTranslator.chunkLines(listOf("a", "b", "c"), maxChars = 3),
        )
        // Cabe justo (2 + 1 + 2 = 5): no se corta.
        assertEquals(
            listOf(listOf("ab", "cd")),
            LyricsTranslator.chunkLines(listOf("ab", "cd"), maxChars = 5),
        )
        // Un carácter más y se corta.
        assertEquals(
            listOf(listOf("ab"), listOf("cde")),
            LyricsTranslator.chunkLines(listOf("ab", "cde"), maxChars = 5),
        )
        // Una línea sola más larga que el tope va sola, sin partirse.
        val larga = "x".repeat(10)
        assertEquals(
            listOf(listOf(larga), listOf("y")),
            LyricsTranslator.chunkLines(listOf(larga, "y"), maxChars = 5),
        )
        assertEquals(emptyList<List<String>>(), LyricsTranslator.chunkLines(emptyList()))
    }

    @Test
    fun respuestaRotaEsNull() {
        assertNull(LyricsTranslator.parseGtxResponse("esto no es json"))
        assertNull(LyricsTranslator.parseGtxResponse("[]"))
        assertNull(LyricsTranslator.parseGtxResponse("[[],null,\"en\"]"))
    }
}
