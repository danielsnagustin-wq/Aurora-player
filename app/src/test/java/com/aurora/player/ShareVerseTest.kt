package com.aurora.player

import com.aurora.player.util.formatSharedVerse
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareVerseTest {

    @Test
    fun versoConCancionYArtista() {
        assertEquals(
            "«Hola, ¿qué tal?»\n— Aurora · Amanecer",
            formatSharedVerse("Hola, ¿qué tal?", "Amanecer", "Aurora"),
        )
    }

    @Test
    fun sinArtistaNiTitulo_soloVerso() {
        assertEquals("«Hola»", formatSharedVerse("Hola", null, null))
        assertEquals("«Hola»", formatSharedVerse("  Hola  ", "", "  "))
    }

    @Test
    fun soloTitulo_sinArtista() {
        assertEquals("«Hola»\n— Amanecer", formatSharedVerse("Hola", "Amanecer", null))
    }
}
