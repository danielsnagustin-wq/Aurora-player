package com.aurora.player

import com.aurora.player.lyrics.buildLyricsOvhUrl
import com.aurora.player.lyrics.cleanTrackTitle
import com.aurora.player.lyrics.parseLyricsOvhResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RONDA LETRAS (2026-11-11): Lyrics.ovh (texto plano, sin clave) y
 * limpieza de títulos con prefijo de pista. Puro, sin Android, para
 * probarlo en la JVM.
 */
class LyricsOvhProviderTest {

    @Test
    fun `url con espacios en porcentaje`() {
        assertEquals(
            "https://api.lyrics.ovh/v1/Blue%20Foundation/Eyes%20on%20Fire",
            buildLyricsOvhUrl("Blue Foundation", "Eyes on Fire"),
        )
    }

    @Test
    fun `respuesta con letra es texto plano`() {
        val json = "{\"lyrics\":\"I'll seek you out\\nOne more word\"}"
        val lyrics = parseLyricsOvhResponse(json)
        assertNull(lyrics?.syncedLrc)
        assertEquals("I'll seek you out\nOne more word", lyrics?.plainText)
    }

    @Test
    fun `respuesta vacia o sin clave es null`() {
        assertNull(parseLyricsOvhResponse("{\"lyrics\":\"\"}"))
        assertNull(parseLyricsOvhResponse("{\"lyrics\":\"   \"}"))
        assertNull(parseLyricsOvhResponse("{\"error\":\"No lyrics found\"}"))
    }

    @Test
    fun `prefijo de pista 2A se quita`() {
        assertEquals(
            "Eyes on Fire (Zeds Dead Remix)",
            cleanTrackTitle("2A - Eyes on Fire (Zeds Dead Remix)"),
        )
    }

    @Test
    fun `prefijo numerico con punto o guion se quita`() {
        assertEquals("Canción", cleanTrackTitle("01. Canción"))
        assertEquals("Canción", cleanTrackTitle("1 - Canción"))
        assertEquals("Canción", cleanTrackTitle("12 – Canción"))
    }

    @Test
    fun `titulo sin prefijo no cambia`() {
        assertEquals("Eyes on Fire", cleanTrackTitle("Eyes on Fire"))
        assertEquals("2001: Odisea", cleanTrackTitle("2001: Odisea"))
        assertEquals("A - Song", cleanTrackTitle("A - Song"))
    }

    @Test
    fun `titulo que es solo prefijo no se vacia`() {
        assertEquals("01 -", cleanTrackTitle("01 - "))
    }
}
