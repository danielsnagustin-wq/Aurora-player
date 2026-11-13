package com.aurora.player

import com.aurora.player.online.parseSoundCloudMe
import com.aurora.player.online.parseSoundCloudStream
import com.aurora.player.online.parseSoundCloudTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundCloudAuthTest {

    private val meFixture = """
{
  "id": 123456,
  "username": "danielito",
  "avatar_url": "https://i1.sndcdn.com/avatars-abc-large.jpg",
  "permalink_url": "https://soundcloud.com/danielito"
}
    """.trimIndent()

    private val streamFixture = """
{
  "collection": [
    {
      "type": "track",
      "track": {
        "id": 1001,
        "title": "Nueva de seguida",
        "user": {
          "username": "artista-seguida"
        },
        "duration": 210000,
        "artwork_url": "https://i1.sndcdn.com/artworks-x-large.jpg",
        "permalink_url": "https://soundcloud.com/artista-seguida/Nueva-de-seguida",
        "streamable": true,
        "policy": "MONETIZE"
      }
    },
    {
      "type": "track-repost",
      "track": {
        "id": 1002,
        "title": "Repost de seguida",
        "user": {
          "username": "otra-seguida"
        },
        "duration": 195000,
        "artwork_url": "https://i1.sndcdn.com/artworks-x-large.jpg",
        "permalink_url": "https://soundcloud.com/otra-seguida/Repost-de-seguida",
        "streamable": true,
        "policy": "MONETIZE"
      }
    },
    {
      "type": "playlist",
      "playlist": {
        "id": 55,
        "title": "Lista ajena"
      }
    },
    {
      "type": "track",
      "track": {
        "id": 1003,
        "title": "Solo con DRM",
        "user": {
          "username": "nadie"
        },
        "duration": 120000,
        "artwork_url": "https://i1.sndcdn.com/artworks-x-large.jpg",
        "permalink_url": "https://soundcloud.com/nadie/Solo-con-DRM",
        "streamable": false,
        "policy": "MONETIZE"
      }
    },
    {
      "type": "track"
    },
    {}
  ],
  "next_href": "https://api-v2.soundcloud.com/stream?offset=xyz"
}
    """.trimIndent()

    @Test
    fun parseaMiPerfil() {
        val user = parseSoundCloudMe(meFixture)
        assertEquals(123456L, user?.id)
        assertEquals("danielito", user?.username)
        assertEquals("https://i1.sndcdn.com/avatars-abc-large.jpg", user?.avatarUrl)
    }

    @Test
    fun perfilSinNombreDaNull() {
        assertNull(parseSoundCloudMe("{\"id\": 1}"))
    }

    @Test
    fun perfilJsonRotoDaNull() {
        assertNull(parseSoundCloudMe("{ no es json"))
    }

    @Test
    fun parseaFeedDeSeguidos() {
        val tracks = parseSoundCloudStream(streamFixture)
        assertEquals(2, tracks.size)
        assertEquals(1001L, tracks[0].id)
        assertEquals("Nueva de seguida", tracks[0].title)
        assertEquals("artista-seguida", tracks[0].artist)
        assertEquals(210000L, tracks[0].durationMs)
        assertEquals(1002L, tracks[1].id)
        assertEquals("Repost de seguida", tracks[1].title)
        assertEquals("otra-seguida", tracks[1].artist)
    }

    @Test
    fun feedSinColeccionDaVacio() {
        assertTrue(parseSoundCloudStream("{\"collection\": []}").isEmpty())
        assertTrue(parseSoundCloudStream("{}").isEmpty())
        assertTrue(parseSoundCloudStream("{ no es json").isEmpty())
    }

    @Test
    fun pistaNoReproducibleDaNull() {
        val noStreamable = "{\"id\": 1, \"title\": \"x\", \"user\": {\"username\": \"y\"}, \"streamable\": false}"
        assertNull(parseSoundCloudTrack(com.aurora.player.online.JsonParser.parse(noStreamable)))
    }
}
