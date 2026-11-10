package com.aurora.player

import android.net.Uri
import com.aurora.player.library.SmartPlaylistKind
import com.aurora.player.library.smartPlaylistSongs
import com.aurora.player.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartPlaylistsTest {

    private fun song(
        id: Long,
        plays: Int = 0,
        favorite: Boolean = false,
        added: Long = 0L,
        lastPlayed: Long? = null,
    ) = Song(
        id = id, title = "T$id", artist = "A", album = "Al", albumId = 1,
        durationMs = 180_000, path = "/x/$id.mp3", uri = Uri.EMPTY,
        mimeType = "audio/mpeg", sizeBytes = 1000, dateAdded = added,
        isFavorite = favorite, playCount = plays, lastPlayedAt = lastPlayed,
    )

    @Test
    fun masEscuchadas_ordenYsinCero() {
        val songs = listOf(song(1, plays = 3), song(2, plays = 10), song(3, plays = 0))
        val out = smartPlaylistSongs(SmartPlaylistKind.MOST_PLAYED, songs)
        assertEquals(listOf(2L, 1L), out.map { it.id })
    }

    @Test
    fun favoritas_soloLasMarcadas() {
        val songs = listOf(song(1, favorite = true), song(2), song(3, favorite = true))
        assertEquals(listOf(1L, 3L), smartPlaylistSongs(SmartPlaylistKind.FAVORITES, songs).map { it.id })
    }

    @Test
    fun sinEscuchar_soloCero() {
        val songs = listOf(song(1, plays = 5), song(2), song(3))
        assertEquals(listOf(2L, 3L), smartPlaylistSongs(SmartPlaylistKind.NEVER_PLAYED, songs).map { it.id })
    }

    @Test
    fun recientes_porFechaYporUltimaEscucha() {
        val songs = listOf(song(1, added = 100), song(2, added = 300), song(3, added = 200))
        assertEquals(listOf(2L, 3L, 1L), smartPlaylistSongs(SmartPlaylistKind.RECENTLY_ADDED, songs).map { it.id })
        val played = listOf(song(1, lastPlayed = 50), song(2, lastPlayed = 90), song(3))
        assertEquals(listOf(2L, 1L), smartPlaylistSongs(SmartPlaylistKind.RECENTLY_PLAYED, played).map { it.id })
    }

    @Test
    fun limite_recorta() {
        val songs = (1L..10L).map { song(it, plays = it.toInt()) }
        assertEquals(3, smartPlaylistSongs(SmartPlaylistKind.MOST_PLAYED, songs, limit = 3).size)
    }
}
