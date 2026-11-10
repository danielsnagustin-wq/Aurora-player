package com.aurora.player

import com.aurora.player.online.parseSpotifyAccessToken
import com.aurora.player.online.parseSpotifyProfile
import com.aurora.player.online.parseSpotifyPlaylistTracks
import com.aurora.player.online.parseSpotifyPlaylistId
import com.aurora.player.online.parseSpotifyPlaylists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spotify como BitChord: parseo del token, de tus listas y de las
 * canciones de una lista. Puro, sin Android, para probarlo en la JVM.
 */
class SpotifyAuthTest {

    @Test
    fun `token del reproductor web`() {
        assertEquals(
            "BQAbc123",
            parseSpotifyAccessToken("""{"accessToken":"BQAbc123","accessTokenExpirationTimestampMs":1799999999999}"""),
        )
        assertNull(parseSpotifyAccessToken("""{"error":"bad"}"""))
        assertNull(parseSpotifyAccessToken("no es json"))
    }

    @Test
    fun `tus listas`() {
        val json = """{"items":[
            {"id":"37i9dQZF1DXcBWIGoYBM5M","name":"Top 50 Global","tracks":{"total":50},"images":[{"url":"https://i.scdn.co/image/abc"}]},
            {"id":"xyz","name":"Mis favoritas","tracks":{"total":12},"images":[]}
        ]}"""
        val lists = parseSpotifyPlaylists(json)
        assertEquals(2, lists.size)
        assertEquals("Top 50 Global", lists[0].name)
        assertEquals(50, lists[0].trackCount)
        assertEquals("https://i.scdn.co/image/abc", lists[0].imageUrl)
        assertEquals(12, lists[1].trackCount)
        assertNull(lists[1].imageUrl)
    }

    @Test
    fun `canciones de una lista`() {
        val json = """{"items":[
            {"track":{"name":"Bonebreaker","artists":[{"name":"Excision"}],"album":{"name":"Onyx"}}},
            {"track":{"name":"Duro","artists":[{"name":"Skrillex"}],"album":null}},
            {"track":null}
        ]}"""
        val tracks = parseSpotifyPlaylistTracks(json)
        assertEquals(2, tracks.size)
        assertEquals("Bonebreaker", tracks[0].name)
        assertEquals("Excision", tracks[0].artist)
        assertEquals("Onyx", tracks[0].album)
        assertEquals("Duro", tracks[1].name)
        assertNull(tracks[1].album)
    }

    @Test
    fun `perfil de la cuenta`() {
        val json = """{"display_name":"Sn","images":[{"url":"https://i.scdn.co/image/perfil"}]}"""
        val profile = parseSpotifyProfile(json)
        assertEquals("Sn", profile?.displayName)
        assertEquals("https://i.scdn.co/image/perfil", profile?.imageUrl)
        assertNull(parseSpotifyProfile("""{"display_name":""}"""))
        assertNull(parseSpotifyProfile("no es json"))
    }

    @Test
    fun `json roto da vacio`() {
        assertTrue(parseSpotifyPlaylists("no es json").isEmpty())
        assertTrue(parseSpotifyPlaylistTracks("""{"items":[]}""").isEmpty())
    }

    @Test
    fun enlacePublicoConParametrosDaSuId() {
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            parseSpotifyPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc123"),
        )
    }

    @Test
    fun enlacePublicoSinParametrosDaSuId() {
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            parseSpotifyPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"),
        )
    }

    @Test
    fun uriSpotifyDaSuId() {
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            parseSpotifyPlaylistId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"),
        )
    }

    @Test
    fun loQueNoEsUnaListaDaNull() {
        assertNull(parseSpotifyPlaylistId("https://open.spotify.com/track/abc123"))
        assertNull(parseSpotifyPlaylistId("https://youtube.com/watch?v=abc"))
        assertNull(parseSpotifyPlaylistId(""))
        assertNull(parseSpotifyPlaylistId("  "))
    }
}
