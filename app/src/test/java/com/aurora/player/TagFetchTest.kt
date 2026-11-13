package com.aurora.player

import com.aurora.player.online.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class TagFetchTest {

    @Test
    fun `parseMbResponse parses two recordings correctly`() {
        val json = """
            {
              "recordings": [
                {
                  "title": "Song1",
                  "artist-credit": [{ "name": "Artist1" }],
                  "releases": [
                    {
                      "title": "Album1",
                      "date": "2020-05-01",
                      "id": "release123"
                    }
                  ]
                },
                {
                  "title": "Song2",
                  "artist-credit": [{ "name": "Artist2" }],
                  "releases": [
                    {
                      "title": "Album2",
                      "date": "2020",
                      "id": "release456"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = parseMbResponse(json)
        assertEquals(2, result.size)

        val first = result[0]
        assertEquals("Song1", first.title)
        assertEquals("Artist1", first.artist)
        assertEquals("Album1", first.album)
        assertEquals(2020, first.year)
        assertEquals("https://coverartarchive.org/release/release123/front-250", first.coverUrl)
        assertEquals("MusicBrainz", first.source)
        assertEquals("release123", first.mbid)

        val second = result[1]
        assertEquals("Song2", second.title)
        assertEquals("Artist2", second.artist)
        assertEquals("Album2", second.album)
        assertEquals(2020, second.year)
        assertEquals("https://coverartarchive.org/release/release456/front-250", second.coverUrl)
        assertEquals("MusicBrainz", second.source)
        assertEquals("release456", second.mbid)
    }

    @Test
    fun `parseMbResponse with empty recordings returns empty list`() {
        val json = """{ "recordings": [] }"""
        val result = parseMbResponse(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseMbResponse with invalid JSON returns empty list`() {
        val json = """{ not a json """
        val result = parseMbResponse(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseDzResponse parses two tracks correctly`() {
        val json = """
            {
              "data": [
                {
                  "title": "Track1",
                  "artist": { "name": "ArtistA" },
                  "album": { "title": "AlbumA", "cover_medium": "http://example.com/cover1.jpg" }
                },
                {
                  "title": "Track2",
                  "artist": { "name": "ArtistB" },
                  "album": { "title": "AlbumB", "cover_medium": "http://example.com/cover2.jpg" }
                }
              ]
            }
        """.trimIndent()

        val result = parseDzResponse(json)
        assertEquals(2, result.size)

        val first = result[0]
        assertEquals("Track1", first.title)
        assertEquals("ArtistA", first.artist)
        assertEquals("AlbumA", first.album)
        assertEquals("http://example.com/cover1.jpg", first.coverUrl)
        assertEquals("Deezer", first.source)

        val second = result[1]
        assertEquals("Track2", second.title)
        assertEquals("ArtistB", second.artist)
        assertEquals("AlbumB", second.album)
        assertEquals("http://example.com/cover2.jpg", second.coverUrl)
        assertEquals("Deezer", second.source)
    }

    @Test
    fun `parseDzResponse with empty data returns empty list`() {
        val json = """{ "data": [] }"""
        val result = parseDzResponse(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildMbQuery escapes special characters correctly`() {
        val title = """My "Song"+"""
        val artist = """Artist&Name"""
        val query = buildMbQuery(title, artist)
        // Expected: recording:"My \"Song\"\+" AND artist:"Artist\&Name"
        val expected = """recording:"My \"Song\"\+" AND artist:"Artist\&Name""""
        assertEquals(expected, query)
    }

    @Test
    fun `buildDzUrl encodes query parameters`() {
        val title = "My Song"
        val artist = "Artist Name"
        val url = buildDzUrl(title, artist)
        val expectedQuery = URLEncoder.encode(buildDzQuery(title, artist), "UTF-8")
        val expected = "https://api.deezer.com/search/track?q=$expectedQuery"
        assertEquals(expected, url)
    }
}
