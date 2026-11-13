package com.aurora.player

import com.aurora.player.online.ItunesTrack
import com.aurora.player.online.buildItunesSearchUrl
import com.aurora.player.online.itunesArtwork600
import com.aurora.player.online.parseItunesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItunesSearchTest {

    private val fixture = """
        {
          "resultCount": 2,
          "results": [
            {
              "trackId": 123456789,
              "trackName": "Runaway",
              "artistName": "AURORA",
              "collectionName": "All My Demons Greeting Me as a Friend",
              "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/18/ad/13/18ad13c3-ff24-0b31-45c4-06b9064471cc/0044003184152_Cover.jpg/100x100bb.jpg"
            },
            {
              "trackId": 987654321,
              "artistName": "Sin título"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parseExtraeLaCancionYLaPortada600() {
        val tracks = parseItunesResponse(fixture)
        assertEquals(1, tracks.size)
        val t: ItunesTrack = tracks[0]
        assertEquals(123456789L, t.trackId)
        assertEquals("Runaway", t.title)
        assertEquals("AURORA", t.artist)
        assertEquals("All My Demons Greeting Me as a Friend", t.album)
        assertTrue(t.artworkUrl100!!.endsWith("/100x100bb.jpg"))
        assertTrue(t.artworkUrl600!!.endsWith("/600x600bb.jpg"))
    }

    @Test
    fun parseIgnoraSinNombre() {
        val tracks = parseItunesResponse(fixture)
        assertTrue(tracks.none { it.trackId == 987654321L })
    }

    @Test
    fun parseJsonInvalidoDevuelveVacio() {
        assertEquals(emptyList<ItunesTrack>(), parseItunesResponse("{ no es json"))
    }

    @Test
    fun artwork600ConvierteYRespetaNull() {
        assertNull(itunesArtwork600(null))
        assertNull(itunesArtwork600("   "))
        assertEquals(
            "https://x/cover.jpg/600x600bb.jpg",
            itunesArtwork600("https://x/cover.jpg/100x100bb.jpg")
        )
        assertEquals(
            "https://x/cover.jpg/500x500bb.jpg",
            itunesArtwork600("https://x/cover.jpg/500x500bb.jpg")
        )
    }

    @Test
    fun buildUrlCodificaLaBusqueda() {
        val url = buildItunesSearchUrl("Aurora Runaway")
        assertTrue(url.startsWith("https://itunes.apple.com/search?term="))
        assertTrue(url.contains("term=Aurora+Runaway"))
        assertTrue(url.contains("media=music"))
        assertTrue(url.contains("entity=song"))
        assertTrue(url.contains("limit=25"))
    }
}
