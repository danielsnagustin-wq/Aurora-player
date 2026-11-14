package com.aurora.player

import com.aurora.player.online.SoundCloudTrack
import com.aurora.player.online.SoundCloudTranscoding
import com.aurora.player.online.buildSoundCloudSearchUrl
import com.aurora.player.online.buildSoundCloudTrackUrl
import com.aurora.player.online.extractJsBundleUrls
import com.aurora.player.online.extractWebClientId
import com.aurora.player.online.parseSoundCloudSearch
import com.aurora.player.online.parseSoundCloudStreamUrl
import com.aurora.player.online.parseSoundCloudTranscodings
import com.aurora.player.online.pickSoundCloudTranscoding
import com.aurora.player.online.soundCloudArtwork500
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundCloudTest {

    private val searchFixture = """
        {
          "collection": [
            {
              "id": 2427716250,
              "title": "daft punk feb 97 dj mix",
              "user": {
                "username": "ralphlawson"
              },
              "duration": 3639060,
              "artwork_url": "https://i1.sndcdn.com/artworks-N9HGQvDGQTVnKNBw-pNmZJw-large.jpg",
              "permalink_url": "https://soundcloud.com/ralphlawson/daft-punk-feb-97-dj-mix",
              "streamable": true,
              "policy": "MONETIZE"
            },
            {
              "id": 111,
              "title": "solo con drm",
              "user": {
                "username": "alguien"
              },
              "duration": 200000,
              "artwork_url": null,
              "permalink_url": "https://soundcloud.com/alguien/solo-con-drm",
              "streamable": false,
              "policy": "BLOCK"
            },
            {
              "id": 222,
              "user": {
                "username": "sin titulo"
              },
              "duration": 1000,
              "streamable": true
            },
            {
              "id": 333,
              "title": "bloqueado por policy",
              "user": {
                "username": "otro"
              },
              "duration": 1000,
              "streamable": true,
              "policy": "BLOCK"
            }
          ],
          "total_results": 4
        }
    """.trimIndent()

    private val trackFixture = """
        {
          "id": 2427716250,
          "title": "daft punk feb 97 dj mix",
          "media": {
            "transcodings": [
              {
                "url": "https://api-v2.soundcloud.com/media/soundcloud:tracks:2427716250/abc/stream/hls",
                "preset": "aac_96k",
                "format": {
                  "protocol": "hls",
                  "mime_type": "audio/mp4; codecs=\"mp4a.40.2\""
                }
              },
              {
                "url": "https://api-v2.soundcloud.com/media/soundcloud:tracks:2427716250/def/stream/hls",
                "preset": "aac_160k",
                "format": {
                  "protocol": "hls",
                  "mime_type": "audio/mp4; codecs=\"mp4a.40.2\""
                }
              },
              {
                "url": "https://api-v2.soundcloud.com/media/soundcloud:tracks:2427716250/ghi/stream/progressive",
                "preset": "mp3_128k",
                "format": {
                  "protocol": "progressive",
                  "mime_type": "audio/mpeg"
                }
              }
            ]
          }
        }
    """.trimIndent()

    private val streamFixture = """
        {
          "url": "https://cf-hls-media.sndcdn.com/playlist/xyz/playlist.m3u8?Policy=abc&Signature=def&Key-Pair-Id=ghi"
        }
    """.trimIndent()

    private val homeFixture = """
        <html><head>
        <script src="https://a-v2.sndcdn.com/assets/55-a70a06aa.js"></script>
        <script src="https://a-v2.sndcdn.com/assets/0-c3f2890f.js"></script>
        <script src="https://a-v2.sndcdn.com/assets/55-a70a06aa.js"></script>
        </head></html>
    """.trimIndent()

    private val jsFixture =
        "var o={client_id:\"0123456789abcdef0123456789abcdef\",client_is_expiring:!1};"

    @Test
    fun extractJsBundleUrlsEncuentraYBorraDuplicados() {
        val urls = extractJsBundleUrls(homeFixture)
        assertEquals(2, urls.size)
        assertTrue(urls[0].endsWith("/55-a70a06aa.js"))
        assertTrue(urls[1].endsWith("/0-c3f2890f.js"))
    }

    @Test
    fun extractWebClientIdEncuentraLaLlave() {
        assertEquals(
            "0123456789abcdef0123456789abcdef",
            extractWebClientId(jsFixture)
        )
        assertNull(extractWebClientId("var o={sin_llave:1};"))
        assertNull(extractWebClientId("client_id:\"corta\""))
    }

    @Test
    fun parseSearchExtraeLaPistaYDescarta() {
        // RONDA 2 (2026-11-13, P2): la entrada 333 es
        // streamable:true pero policy:BLOCK — también se descarta.
        val tracks = parseSoundCloudSearch(searchFixture)
        assertEquals(1, tracks.size)
        assertTrue(tracks.none { it.id == 333L })
        val t = tracks[0]
        assertEquals(2427716250L, t.id)
        assertEquals("daft punk feb 97 dj mix", t.title)
        assertEquals("ralphlawson", t.artist)
        assertEquals(3639060L, t.durationMs)
        assertTrue(t.artworkUrl!!.endsWith("-large.jpg"))
        assertEquals(
            "https://soundcloud.com/ralphlawson/daft-punk-feb-97-dj-mix",
            t.permalinkUrl
        )
        assertTrue(tracks.none { it.id == 111L })
        assertTrue(tracks.none { it.id == 222L })
    }

    @Test
    fun parseSearchJsonInvalidoDevuelveVacio() {
        assertEquals(emptyList<SoundCloudTrack>(), parseSoundCloudSearch("{ no es json"))
    }

    @Test
    fun parseTranscodingsYElige160k() {
        val transcodings = parseSoundCloudTranscodings(trackFixture)
        assertEquals(3, transcodings.size)
        val picked = pickSoundCloudTranscoding(transcodings)
        assertEquals("aac_160k", picked!!.preset)
        assertEquals("hls", picked.protocol)
        assertTrue(picked.url.contains("/def/stream/hls"))
    }

    @Test
    fun pickSinHlsDevuelveElPrimeroYVacioNull() {
        val soloProgresivo = listOf(
            SoundCloudTranscoding(
                url = "https://x/progresivo",
                preset = "mp3_128k",
                protocol = "progressive",
                mimeType = "audio/mpeg"
            )
        )
        assertEquals("mp3_128k", pickSoundCloudTranscoding(soloProgresivo)!!.preset)
        assertNull(pickSoundCloudTranscoding(emptyList()))
    }

    @Test
    fun parseStreamUrlExtraeLaUrl() {
        val url = parseSoundCloudStreamUrl(streamFixture)
        assertTrue(url!!.startsWith("https://cf-hls-media.sndcdn.com/playlist/"))
        assertTrue(url.endsWith(".m3u8?Policy=abc&Signature=def&Key-Pair-Id=ghi"))
        assertNull(parseSoundCloudStreamUrl("{ no es json"))
        assertNull(parseSoundCloudStreamUrl("{\"otro\": 1}"))
    }

    @Test
    fun artwork500ConvierteYRespetaNull() {
        assertNull(soundCloudArtwork500(null))
        assertNull(soundCloudArtwork500("   "))
        assertEquals(
            "https://i1.sndcdn.com/artworks-X-t500x500.jpg",
            soundCloudArtwork500("https://i1.sndcdn.com/artworks-X-large.jpg")
        )
        assertEquals(
            "https://i1.sndcdn.com/artworks-X-t300x300.jpg",
            soundCloudArtwork500("https://i1.sndcdn.com/artworks-X-t300x300.jpg")
        )
    }

    @Test
    fun buildSearchUrlCodificaLaBusqueda() {
        val url = buildSoundCloudSearchUrl("daft punk", "0123456789abcdef0123456789abcdef")
        assertTrue(url.startsWith("https://api-v2.soundcloud.com/search/tracks?q="))
        assertTrue(url.contains("q=daft+punk"))
        assertTrue(url.contains("client_id=0123456789abcdef0123456789abcdef"))
        assertTrue(url.contains("limit=25"))
    }

    @Test
    fun buildTrackUrlLlevaElIdYLaLlave() {
        val url = buildSoundCloudTrackUrl(2427716250L, "0123456789abcdef0123456789abcdef")
        assertEquals(
            "https://api-v2.soundcloud.com/tracks/2427716250?client_id=0123456789abcdef0123456789abcdef",
            url
        )
    }
}
