package com.aurora.player

import android.net.Uri
import com.aurora.player.library.autoDjNext
import com.aurora.player.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AutoDjTest {

    private fun song(
        id: Long,
        artist: String = "A",
        genre: String? = null,
        album: String = "Al",
        plays: Int = 0,
    ) = Song(
        id = id, title = "T$id", artist = artist, album = album, albumId = 1,
        durationMs = 180_000, path = "/x/$id.mp3", uri = Uri.EMPTY,
        mimeType = "audio/mpeg", sizeBytes = 1000, dateAdded = 0,
        genre = genre, playCount = plays,
    )

    @Test
    fun prefiereMismoArtista() {
        val current = song(1, artist = "X")
        val pool = listOf(song(2, artist = "Y"), song(3, artist = "X"), song(4, artist = "X"))
        val next = autoDjNext(current, pool, random = Random(1))
        assertTrue(next?.artist == "X")
    }

    @Test
    fun sinArtista_pasaAGeneroYAlbum() {
        val current = song(1, artist = "X", genre = "Rock", album = "Al1")
        val pool = listOf(song(2, artist = "Y", genre = "Pop"), song(3, artist = "Z", genre = "Rock"))
        assertEquals(3L, autoDjNext(current, pool, random = Random(1))?.id)
        val pool2 = listOf(song(2, artist = "Y", genre = "Pop"), song(3, artist = "Z", album = "Al1"))
        assertEquals(3L, autoDjNext(current, pool2, random = Random(1))?.id)
    }

    @Test
    fun nuncaRepiteActualNiRecientes() {
        val current = song(1, artist = "X")
        val pool = listOf(song(1, artist = "X"), song(2, artist = "X"), song(3, artist = "X"))
        val next = autoDjNext(current, pool, recentlyPlayedIds = setOf(2L), random = Random(1))
        assertEquals(3L, next?.id)
    }

    @Test
    fun sinNadaElegible_null() {
        val current = song(1)
        assertNull(autoDjNext(current, listOf(song(1))))
        assertNull(autoDjNext(current, emptyList()))
    }

    @Test
    fun sinActual_usaMasEscuchadas() {
        val pool = listOf(song(1, plays = 2), song(2, plays = 9), song(3, plays = 5))
        assertEquals(2L, autoDjNext(null, pool, random = Random(1))?.id)
    }
}
