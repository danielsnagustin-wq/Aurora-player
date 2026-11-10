package com.aurora.player

import com.aurora.player.model.Song
import com.aurora.player.playback.AutoBrowse
import com.aurora.player.playback.AutoNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android Auto (Fase 2 app), Serie A: el árbol que ve el coche —
 * raíz con cuatro carpetas, álbumes y artistas distintos, y el
 * filtro de canciones de cada nodo.
 */
class AutoBrowseTest {

    private fun song(id: Long, title: String, artist: String, album: String, albumId: Long) =
        Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            durationMs = 180_000L,
            path = "/music/$title.mp3",
            uri = fakeUri(),
            mimeType = "audio/mpeg",
            sizeBytes = 1L,
            dateAdded = 1L,
        )

    private val songs = listOf(
        song(1, "Una", "Luna", "Noche", 10),
        song(2, "Dos", "Luna", "Noche", 10),
        song(3, "Tres", "Sol", "Día", 20),
    )

    @Test
    fun raiz_cuatroCarpetas() {
        assertEquals(
            listOf(
                AutoNode("canciones", "Canciones"),
                AutoNode("favoritas", "Favoritas"),
                AutoNode("albumes", "Álbumes"),
                AutoNode("artistas", "Artistas"),
            ),
            AutoBrowse.rootNodes(),
        )
    }

    @Test
    fun albumes_distintosYOrdenados() {
        assertEquals(
            listOf(AutoNode("album:20", "Día"), AutoNode("album:10", "Noche")),
            AutoBrowse.albumNodes(songs),
        )
    }

    @Test
    fun artistas_distintosYOrdenados() {
        assertEquals(
            listOf(AutoNode("artist:Luna", "Luna"), AutoNode("artist:Sol", "Sol")),
            AutoBrowse.artistNodes(songs),
        )
    }

    @Test
    fun cancionesDeCadaNodo() {
        assertEquals(listOf(1L, 2L, 3L), AutoBrowse.songsForNode("canciones", songs, setOf(2L)).map { it.id })
        assertEquals(listOf(2L), AutoBrowse.songsForNode("favoritas", songs, setOf(2L)).map { it.id })
        assertEquals(listOf(1L, 2L), AutoBrowse.songsForNode("album:10", songs, emptySet()).map { it.id })
        assertEquals(listOf(3L), AutoBrowse.songsForNode("artist:Sol", songs, emptySet()).map { it.id })
        assertTrue(AutoBrowse.songsForNode("no-existe", songs, emptySet()).isEmpty())
        assertTrue(AutoBrowse.songsForNode("album:xx", songs, emptySet()).isEmpty())
    }

    @Test
    fun carpeta_navegableNoReproducible() {
        val item = AutoBrowse.nodeMediaItem(AutoNode("canciones", "Canciones"))
        assertEquals("canciones", item.mediaId)
        assertEquals(true, item.mediaMetadata.isBrowsable)
        assertEquals(false, item.mediaMetadata.isPlayable)
    }
}
