package com.aurora.player

import com.aurora.player.data.NewReleaseItem
import com.aurora.player.data.decodeNewReleaseItem
import com.aurora.player.data.encodeNewReleaseItem
import com.aurora.player.ui.online.formatUpdatedAgo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryTest {

    @Test
    fun codificaYDecodifica() {
        val item = NewReleaseItem(
            source = "Spotify",
            title = "Disco nuevo",
            artist = "Artista Uno",
            dateText = "2026-11-10",
            kind = "Album",
            imageUrl = "https://i.scdn.co/image/x",
            browseId = null,
            trackId = null,
        )
        assertEquals(item, decodeNewReleaseItem(encodeNewReleaseItem(item)))
        val minimal = NewReleaseItem(
            source = "SoundCloud",
            title = "Tema nuevo",
            artist = "alguien",
            dateText = null,
            kind = null,
            imageUrl = null,
            browseId = null,
            trackId = 1001L,
        )
        assertEquals(minimal, decodeNewReleaseItem(encodeNewReleaseItem(minimal)))
    }

    @Test
    fun tituloConSaltosYSeparador_noSePierde() {
        // RONDA 2 (2026-11-13, P2): pin de n=872 — un título con
        // salto de línea o con el separador U+001F partía el registro
        // en dos y el lanzamiento se perdía al leerlo. Ahora se
        // limpian en la escritura y el lanzamiento vuelve (con
        // espacios en lugar de los caracteres peligrosos).
        val item = NewReleaseItem(
            source = "Spotify",
            title = "Disco\nnuevo\u001Fcon sorpresas",
            artist = "Artista\rUno",
            dateText = "2026-11-10",
            kind = "Album",
            imageUrl = "https://i.scdn.co/image/x",
            browseId = null,
            trackId = null,
        )
        val back = decodeNewReleaseItem(encodeNewReleaseItem(item))
        assertEquals("Disco nuevo con sorpresas", back?.title)
        assertEquals("Artista Uno", back?.artist)
        assertEquals(
            item.copy(title = "Disco nuevo con sorpresas", artist = "Artista Uno"),
            back,
        )
    }

    @Test
    fun lineaRotaDaNull() {
        assertNull(decodeNewReleaseItem("solo"))
        assertNull(decodeNewReleaseItem("a\u001Fb\u001Fc"))
        assertNull(decodeNewReleaseItem("\u001FTitulo\u001FArtista\u001F\u001F\u001F\u001F\u001F"))
    }

    @Test
    fun haceCuanto() {
        val now = 1_764_000_000_000L
        assertEquals("Actualizado ahora mismo", formatUpdatedAgo(now, now))
        assertEquals("Actualizado hace 5 min", formatUpdatedAgo(now - 5 * 60_000L, now))
        assertEquals("Actualizado hace 1 h", formatUpdatedAgo(now - 90 * 60_000L, now))
        assertEquals("Actualizado hace 2 d", formatUpdatedAgo(now - 2 * 24 * 60 * 60_000L, now))
        assertTrue(formatUpdatedAgo(now + 60_000L, now).contains("ahora mismo"))
    }
}
