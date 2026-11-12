package com.aurora.player

import com.aurora.player.lyrics.QqSearchResult
import com.aurora.player.lyrics.buildQqLyricUrl
import com.aurora.player.lyrics.buildQqSearchUrl
import com.aurora.player.lyrics.parseQqLyricResponse
import com.aurora.player.lyrics.parseQqSearchResponse
import com.aurora.player.lyrics.pickQqResult
import com.aurora.player.lyrics.stripJsonpCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * QQ Music (2026-11-11): búsqueda + letra con Referer obligatorio.
 * Puro, sin Android, para probarlo en la JVM. Los JSON de ejemplo
 * tienen la forma real verificada en vivo con Coldplay "Yellow".
 */
class QqLyricsProviderTest {

    @Test
    fun `url de busqueda con artista y titulo`() {
        assertEquals(
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp" +
                "?format=json&p=1&n=5&w=Coldplay%20Yellow",
            buildQqSearchUrl("Coldplay", "Yellow"),
        )
    }

    @Test
    fun `url de letra con songmid`() {
        assertEquals(
            "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
                "?format=json&nobase64=1&type=1&songmid=000Z74wD1h9F10",
            buildQqLyricUrl("000Z74wD1h9F10"),
        )
    }

    @Test
    fun `jsonp se quita y sin envoltura no cambia`() {
        assertEquals(
            "{\"retcode\":0}",
            stripJsonpCallback("MusicJsonCallback({\"retcode\":0})"),
        )
        assertEquals("{\"retcode\":0}", stripJsonpCallback("{\"retcode\":0}"))
        assertEquals("", stripJsonpCallback(""))
    }

    private fun searchJson(): String =
        "{\"code\":0,\"data\":{\"song\":{\"list\":[" +
            "{\"songmid\":\"000Z74wD1h9F10\",\"songname\":\"Yellow\"," +
            "\"interval\":266,\"singer\":[{\"name\":\"Coldplay\"}]}," +
            "{\"songmid\":\"000OTHER0000001\",\"songname\":\"Yellow Submarine\"," +
            "\"interval\":160,\"singer\":[{\"name\":\"The Beatles\"}]}" +
            "]}}}"

    @Test
    fun `busqueda parsea candidatos con duracion`() {
        val results = parseQqSearchResponse(searchJson())
        assertEquals(2, results.size)
        assertEquals("000Z74wD1h9F10", results[0].songmid)
        assertEquals("Yellow", results[0].songName)
        assertEquals("Coldplay", results[0].singerName)
        assertEquals(266L, results[0].durationSec)
    }

    @Test
    fun `busqueda rota es lista vacia`() {
        assertEquals(emptyList<QqSearchResult>(), parseQqSearchResponse("no-json"))
        assertEquals(emptyList<QqSearchResult>(), parseQqSearchResponse("{\"code\":0}"))
    }

    @Test
    fun `pick elige el candidato que coincide`() {
        val best = pickQqResult(
            parseQqSearchResponse(searchJson()),
            "Yellow",
            "Coldplay",
            266L,
        )
        assertEquals("000Z74wD1h9F10", best?.songmid)
    }

    @Test
    fun `pick descarta otra cancion y otra duracion`() {
        assertNull(
            pickQqResult(
                parseQqSearchResponse(searchJson()),
                "Yellow",
                "The Beatles",
                266L,
            ),
        )
        assertNull(
            pickQqResult(
                parseQqSearchResponse(searchJson()),
                "Yellow",
                "Coldplay",
                100L,
            ),
        )
    }

    @Test
    fun `letra con retcode 0 es LRC sincronizado`() {
        val body = "MusicJsonCallback({\"retcode\":0,\"code\":0,\"subcode\":0," +
            "\"lyric\":\"[ti:Yellow]\\n[ar:Coldplay]\\n[00:33.58]Look at the stars\"})"
        val lyrics = parseQqLyricResponse(body)
        assertEquals(
            "[ti:Yellow]\n[ar:Coldplay]\n[00:33.58]Look at the stars",
            lyrics?.syncedLrc,
        )
        assertNull(lyrics?.plainText)
    }

    @Test
    fun `retcode distinto de 0 es sin letra`() {
        assertNull(
            parseQqLyricResponse(
                "{\"retcode\":-1310,\"code\":-1310,\"subcode\":-1310}",
            ),
        )
        assertNull(parseQqLyricResponse("{\"retcode\":0,\"lyric\":\"\"}"))
        assertNull(parseQqLyricResponse("no-json"))
    }
}
