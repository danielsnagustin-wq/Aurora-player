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
    fun respuestaRotaEsNull() {
        assertNull(LyricsTranslator.parseGtxResponse("esto no es json"))
        assertNull(LyricsTranslator.parseGtxResponse("[]"))
        assertNull(LyricsTranslator.parseGtxResponse("[[],null,\"en\"]"))
    }
}
