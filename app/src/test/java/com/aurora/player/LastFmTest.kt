package com.aurora.player

import com.aurora.player.online.JsonParser
import com.aurora.player.online.buildLastFmUrl
import com.aurora.player.online.getArtistInfo
import com.aurora.player.online.lastFmImageUrl
import com.aurora.player.online.parseArtistInfo
import com.aurora.player.online.parseChartTopArtists
import com.aurora.player.online.parseChartTopTracks
import com.aurora.player.online.parseSimilarArtists
import com.aurora.player.online.parseTopTracks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmTest {

    private val artistInfoFixture = """
        {
          "artist": {
            "name": "AURORA",
            "stats": {
              "listeners": "1234567",
              "playcount": "89012345"
            },
            "bio": {
              "summary": "Aurora Aksnes es una cantante noruega. <a href=\"https://www.last.fm/music/AURORA\">Read more on Last.fm</a>"
            },
            "image": [
              { "#text": "https://lastfm.freetls.fastly.net/i/u/34s/abc.jpg", "size": "small" },
              { "#text": "https://lastfm.freetls.fastly.net/i/u/174s/abc.jpg", "size": "medium" },
              { "#text": "https://lastfm.freetls.fastly.net/i/u/300x300/abc.jpg", "size": "large" },
              { "#text": "https://lastfm.freetls.fastly.net/i/u/300x300/abc.jpg", "size": "extralarge" },
              { "#text": "https://lastfm.freetls.fastly.net/i/u/300x300/abc.jpg", "size": "mega" }
            ]
          }
        }
    """.trimIndent()

    private val similarFixture = """
        {
          "similarartists": {
            "artist": [
              { "name": "Billie Eilish", "image": [{ "#text": "https://img/x.jpg", "size": "mega" }] },
              { "image": [{ "#text": "https://img/y.jpg", "size": "mega" }] },
              { "name": "Lorde", "image": [] }
            ]
          }
        }
    """.trimIndent()

    private val topTracksFixture = """
        {
          "toptracks": {
            "track": [
              { "name": "Runaway", "playcount": "456789", "listeners": "234567", "artist": { "name": "AURORA" }, "image": [{ "#text": "https://img/r.jpg", "size": "large" }] },
              { "name": "Sin artista", "playcount": "1" },
              { "playcount": "2", "artist": { "name": "AURORA" } }
            ]
          }
        }
    """.trimIndent()

    private val chartArtistsFixture = """
        {
          "artists": {
            "artist": [
              { "name": "Taylor Swift", "playcount": "999999", "listeners": "888888", "image": [{ "#text": "https://img/t.jpg", "size": "mega" }] },
              { "playcount": "1" }
            ]
          }
        }
    """.trimIndent()

    private val chartTracksFixture = """
        {
          "tracks": {
            "track": [
              { "name": "Blinding Lights", "playcount": "777777", "listeners": "666666", "artist": "The Weeknd", "image": [{ "#text": "https://img/b.jpg", "size": "extralarge" }] }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun parseArtistInfoExtraeNombreNumerosBioEImagenMayor() {
        val artist = parseArtistInfo(artistInfoFixture)!!
        assertEquals("AURORA", artist.name)
        // Last.fm devuelve listeners/playcount como STRINGS, no números.
        assertEquals(1234567L, artist.listeners)
        assertEquals(89012345L, artist.playcount)
        assertEquals("Aurora Aksnes es una cantante noruega.", artist.bioSummary)
        assertEquals("https://lastfm.freetls.fastly.net/i/u/300x300/abc.jpg", artist.imageUrl)
    }

    @Test
    fun parseArtistInfoSinNombreONoJsonDevuelveNull() {
        assertNull(parseArtistInfo("""{"artist": {"stats": {}}}"""))
        assertNull(parseArtistInfo("""{"artist": """))
        assertNull(parseArtistInfo("no-json"))
    }

    @Test
    fun parseSimilarArtistsSaltaSinNombre() {
        val similar = parseSimilarArtists(similarFixture)
        assertEquals(2, similar.size)
        assertEquals("Billie Eilish", similar[0].name)
        assertEquals("https://img/x.jpg", similar[0].imageUrl)
        assertEquals("Lorde", similar[1].name)
        assertNull(similar[1].imageUrl)
        assertEquals(emptyList<Any>(), parseSimilarArtists("no-json"))
    }

    @Test
    fun parseTopTracksLeeArtistaAnidadoYSaltaIncompletos() {
        val tracks = parseTopTracks(topTracksFixture)
        assertEquals(1, tracks.size)
        assertEquals("Runaway", tracks[0].name)
        assertEquals("AURORA", tracks[0].artist)
        assertEquals(456789L, tracks[0].playcount)
        assertEquals(234567L, tracks[0].listeners)
        assertEquals("https://img/r.jpg", tracks[0].imageUrl)
    }

    @Test
    fun parseChartTopArtistsYTopTracks() {
        val artists = parseChartTopArtists(chartArtistsFixture)
        assertEquals(1, artists.size)
        assertEquals("Taylor Swift", artists[0].name)
        assertEquals(999999L, artists[0].playcount)
        assertEquals("https://img/t.jpg", artists[0].imageUrl)
        val tracks = parseChartTopTracks(chartTracksFixture)
        assertEquals(1, tracks.size)
        assertEquals("Blinding Lights", tracks[0].name)
        // El artista del chart viene como string, no como objeto.
        assertEquals("The Weeknd", tracks[0].artist)
        assertEquals(777777L, tracks[0].playcount)
    }

    @Test
    fun buildUrlLlevaMetodoLlaveYParamsCodificados() {
        val url = buildLastFmUrl("artist.getInfo", "LLAVE123", mapOf("artist" to "AURORA"))
        assertTrue(url.startsWith("https://ws.audioscrobbler.com/2.0/?method=artist.getInfo"))
        assertTrue(url.contains("api_key=LLAVE123"))
        assertTrue(url.contains("format=json"))
        assertTrue(url.contains("artist=AURORA"))
        val conEspacio = buildLastFmUrl("artist.getInfo", "K", mapOf("artist" to "Billie Eilish"))
        assertTrue(conEspacio.contains("artist=Billie+Eilish"))
    }

    @Test
    fun lastFmImageUrlEligeLaMayorYRespetaVacio() {
        assertNull(lastFmImageUrl(null))
        val soloSmall = JsonParser.parse("""[{"#text": "https://img/s.jpg", "size": "small"}]""")
        assertEquals("https://img/s.jpg", lastFmImageUrl(soloSmall))
        val megaVacia = JsonParser.parse(
            """[{"#text": "", "size": "mega"}, {"#text": "https://img/l.jpg", "size": "large"}]"""
        )
        assertEquals("https://img/l.jpg", lastFmImageUrl(megaVacia))
    }

    @Test
    fun getArtistInfoConLlaveEnBlancoNoHaceRed() = runBlocking {
        assertNull(getArtistInfo("", "AURORA"))
        assertNull(getArtistInfo("   ", "AURORA"))
        // Y el JSON de error de la API (error 10) no tiene "artist".
        assertNull(parseArtistInfo("""{"message":"Invalid API key - You must be granted a valid key by last.fm","error":10}"""))
    }
}
