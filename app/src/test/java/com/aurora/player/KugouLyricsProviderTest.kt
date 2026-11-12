package com.aurora.player

import com.aurora.player.lyrics.KugouSearchResult
import com.aurora.player.lyrics.buildKugouDownloadUrl
import com.aurora.player.lyrics.buildKugouKrcsUrl
import com.aurora.player.lyrics.buildKugouSearchUrl
import com.aurora.player.lyrics.parseKugouDownloadResponse
import com.aurora.player.lyrics.parseKugouKrcsResponse
import com.aurora.player.lyrics.parseKugouSearchResponse
import com.aurora.player.lyrics.pickKugouResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kugou (2026-11-11): búsqueda + krcs + descarga LRC en Base64.
 * Puro, sin Android, para probarlo en la JVM. Los JSON de ejemplo
 * tienen la forma real (búsqueda verificada en vivo con Coldplay
 * "Yellow"; krcs y descarga con la forma del fuente music-lib).
 */
class KugouLyricsProviderTest {

    @Test
    fun `url de busqueda con artista y titulo`() {
        assertEquals(
            "https://songsearch.kugou.com/song_search_v2" +
                "?keyword=Coldplay%20Yellow&page=1&pagesize=5&userid=-1" +
                "&clientver=&platform=WebFilter&tag=em&filter=2" +
                "&iscorrection=1&privilege_filter=0",
            buildKugouSearchUrl("Coldplay", "Yellow"),
        )
    }

    @Test
    fun `url krcs con hash y duracion en ms`() {
        assertEquals(
            "https://krcs.kugou.com/search?ver=1&client=mobi" +
                "&duration=266000&hash=2B5EB843D47CABA68EBA4A22D98632FF" +
                "&album_audio_id=",
            buildKugouKrcsUrl("2B5EB843D47CABA68EBA4A22D98632FF", 266L),
        )
    }

    @Test
    fun `url de descarga con id y accesskey`() {
        assertEquals(
            "https://lyrics.kugou.com/download?ver=1&client=pc" +
                "&id=123456&accesskey=ABCDEF123456&fmt=lrc&charset=utf8",
            buildKugouDownloadUrl("123456", "ABCDEF123456"),
        )
    }

    private fun searchJson(): String =
        "{\"status\":1,\"error_code\":0,\"data\":{\"lists\":[" +
            "{\"FileHash\":\"2B5EB843D47CABA68EBA4A22D98632FF\"," +
            "\"SongName\":\"Yellow\",\"SingerName\":\"Coldplay\"," +
            "\"AlbumName\":\"Parachutes\",\"Duration\":266}," +
            "{\"FileHash\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"," +
            "\"SongName\":\"Yellow Submarine\",\"SingerName\":\"The Beatles\"," +
            "\"AlbumName\":\"Revolver\",\"Duration\":160}" +
            "]}}"

    @Test
    fun `busqueda parsea candidatos con hash y duracion`() {
        val results = parseKugouSearchResponse(searchJson())
        assertEquals(2, results.size)
        assertEquals("2B5EB843D47CABA68EBA4A22D98632FF", results[0].fileHash)
        assertEquals("Yellow", results[0].songName)
        assertEquals("Coldplay", results[0].singerName)
        assertEquals(266L, results[0].durationSec)
    }

    @Test
    fun `busqueda rota es lista vacia`() {
        assertEquals(emptyList<KugouSearchResult>(), parseKugouSearchResponse("no-json"))
        assertEquals(emptyList<KugouSearchResult>(), parseKugouSearchResponse("{\"status\":1}"))
    }

    @Test
    fun `pick elige el candidato que coincide`() {
        val best = pickKugouResult(
            parseKugouSearchResponse(searchJson()),
            "Yellow",
            "Coldplay",
            266L,
        )
        assertEquals("2B5EB843D47CABA68EBA4A22D98632FF", best?.fileHash)
    }

    @Test
    fun `pick descarta otra cancion`() {
        assertNull(
            pickKugouResult(
                parseKugouSearchResponse(searchJson()),
                "Yellow",
                "The Beatles",
                266L,
            ),
        )
    }

    @Test
    fun `krcs parsea id numerico y accesskey`() {
        val candidate = parseKugouKrcsResponse(
            "{\"status\":200,\"candidates\":[" +
                "{\"id\":123456,\"accesskey\":\"ABCDEF123456\",\"song\":\"Yellow\"}]}",
        )
        assertEquals("123456", candidate?.id)
        assertEquals("ABCDEF123456", candidate?.accessKey)
    }

    @Test
    fun `krcs acepta id como texto y sin candidatos es null`() {
        val candidate = parseKugouKrcsResponse(
            "{\"status\":200,\"candidates\":[" +
                "{\"id\":\"789\",\"accesskey\":\"KEY\",\"song\":\"Yellow\"}]}",
        )
        assertEquals("789", candidate?.id)
        assertNull(
            parseKugouKrcsResponse(
                "{\"status\":404,\"candidates\":[]}",
            ),
        )
        assertNull(parseKugouKrcsResponse("no-json"))
    }

    @Test
    fun `descarga LRC en Base64 se decodifica`() {
        val lyrics = parseKugouDownloadResponse(
            "{\"status\":200," +
                "\"content\":\"WzAwOjMzLjUyXUxvb2sgYXQgdGhlIHN0YXJz\"," +
                "\"fmt\":\"lrc\",\"contenttype\":2}",
        )
        assertEquals("[00:33.52]Look at the stars", lyrics?.syncedLrc)
        assertNull(lyrics?.plainText)
    }

    @Test
    fun `descarga KRC no se decodifica en esta version`() {
        assertNull(
            parseKugouDownloadResponse(
                "{\"status\":200,\"content\":\"AAAA\",\"fmt\":\"krc\",\"contenttype\":1}",
            ),
        )
        assertNull(
            parseKugouDownloadResponse(
                "{\"status\":200,\"content\":\"\",\"fmt\":\"lrc\",\"contenttype\":2}",
            ),
        )
        assertNull(parseKugouDownloadResponse("no-json"))
    }
}
