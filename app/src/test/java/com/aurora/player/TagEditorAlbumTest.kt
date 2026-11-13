package com.aurora.player

import com.aurora.player.tags.SongTags
import com.aurora.player.ui.library.albumOnlyTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagEditorAlbumTest {

    @Test
    fun conservaSoloCamposDeAlbum() {
        val tags = SongTags(
            title = "La Canción",
            artist = "Aurora",
            albumArtist = "Aurora",
            album = "El Álbum",
            composer = "Compositor",
            genre = "Pop",
            year = 2026,
            track = 3,
            trackTotal = 12,
            disc = 1,
            discTotal = 2,
            lyricist = "Letrista",
            comment = "Comentario",
        )
        val resultado = albumOnlyTags(tags)
        assertEquals("El Álbum", resultado.album)
        assertEquals("Aurora", resultado.albumArtist)
        assertEquals("Compositor", resultado.composer)
        assertEquals("Pop", resultado.genre)
        assertEquals(2026, resultado.year)
        assertNull(resultado.title)
        assertNull(resultado.artist)
        assertNull(resultado.track)
        assertNull(resultado.trackTotal)
        assertNull(resultado.disc)
        assertNull(resultado.discTotal)
        assertNull(resultado.lyricist)
        assertNull(resultado.comment)
    }

    @Test
    fun vacioSigueVacio() {
        val resultado = albumOnlyTags(SongTags())
        assertNull(resultado.album)
        assertNull(resultado.title)
        assertNull(resultado.year)
    }
}
