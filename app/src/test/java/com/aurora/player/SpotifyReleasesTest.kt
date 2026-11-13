package com.aurora.player

import com.aurora.player.online.filterSpotifyReleasesByFollowed
import com.aurora.player.online.parseSpotifyFollowedArtists
import com.aurora.player.online.parseSpotifyNewReleases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyReleasesTest {

    private val releasesFixture = """
{
  "albums": {
    "items": [
      {
        "id": "alb1",
        "name": "Disco nuevo",
        "artists": [
          {
            "id": "sp1",
            "name": "Artista Uno"
          }
        ],
        "release_date": "2026-11-10",
        "images": [
          {
            "url": "https://i.scdn.co/image/pequena",
            "height": 64,
            "width": 64
          },
          {
            "url": "https://i.scdn.co/image/grande",
            "height": 640,
            "width": 640
          }
        ],
        "total_tracks": 12
      },
      {
        "id": "alb2",
        "name": "Sencillo doble",
        "artists": [
          {
            "id": "sp2",
            "name": "Artista Dos"
          },
          {
            "id": "sp3",
            "name": "Artista Tres"
          }
        ],
        "release_date": "2026",
        "images": [],
        "total_tracks": 1
      },
      {
        "id": "alb3",
        "artists": [
          {
            "id": "sp4",
            "name": "Sin nombre"
          }
        ],
        "release_date": "2026-11-01"
      },
      {
        "id": "alb4",
        "name": "Sin artista",
        "artists": [],
        "release_date": "2026-11-01"
      }
    ]
  }
}
    """.trimIndent()

    private val followedFixture = """
{
  "artists": {
    "items": [
      {
        "id": "sp1",
        "name": "Artista Uno"
      },
      {
        "id": "sp2",
        "name": "Artista Dos"
      },
      {
        "name": "Sin id"
      }
    ],
    "total": 3
  }
}
    """.trimIndent()

    @Test
    fun parseaLanzamientosNuevos() {
        val releases = parseSpotifyNewReleases(releasesFixture)
        assertEquals(2, releases.size)
        val first = releases[0]
        assertEquals("alb1", first.id)
        assertEquals("Disco nuevo", first.name)
        assertEquals("sp1", first.artistId)
        assertEquals("Artista Uno", first.artistName)
        assertEquals("2026-11-10", first.releaseDate)
        assertEquals("https://i.scdn.co/image/pequena", first.imageUrl)
        val second = releases[1]
        assertEquals("alb2", second.id)
        assertEquals("Sencillo doble", second.name)
        assertEquals("sp2", second.artistId)
        assertEquals("Artista Dos", second.artistName)
        assertEquals("2026", second.releaseDate)
        assertNull(second.imageUrl)
    }

    @Test
    fun parseaArtistasSeguidos() {
        val artists = parseSpotifyFollowedArtists(followedFixture)
        assertEquals(2, artists.size)
        assertEquals("sp1", artists[0].id)
        assertEquals("Artista Uno", artists[0].name)
        assertEquals("sp2", artists[1].id)
    }

    @Test
    fun filtraSoloTusArtistas() {
        val releases = parseSpotifyNewReleases(releasesFixture)
        val filtered = filterSpotifyReleasesByFollowed(releases, setOf("sp1"))
        assertEquals(1, filtered.size)
        assertEquals("alb1", filtered[0].id)
        assertTrue(filterSpotifyReleasesByFollowed(releases, emptySet()).isEmpty())
        assertEquals(2, filterSpotifyReleasesByFollowed(releases, setOf("sp1", "sp2")).size)
    }

    @Test
    fun jsonInvalidoDaVacio() {
        assertTrue(parseSpotifyNewReleases("{ no es json").isEmpty())
        assertTrue(parseSpotifyFollowedArtists("{ no es json").isEmpty())
        assertTrue(parseSpotifyNewReleases("{}").isEmpty())
        assertTrue(parseSpotifyFollowedArtists("{}").isEmpty())
    }
}
