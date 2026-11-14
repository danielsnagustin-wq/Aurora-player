package com.aurora.player

import com.aurora.player.model.KaraokeMode
import com.aurora.player.model.karaokeWordsActive
import com.aurora.player.model.parseKaraokeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeModeTest {

    @Test
    fun parseaModosConocidos() {
        assertEquals(KaraokeMode.ALWAYS, parseKaraokeMode("ALWAYS", KaraokeMode.OFF))
        assertEquals(KaraokeMode.FULLSCREEN_ONLY, parseKaraokeMode("FULLSCREEN_ONLY", KaraokeMode.ALWAYS))
        // RONDA 2 (2026-11-13, P2): sin distinción de mayúsculas
        // (igual que parseGestureAction): un valor guardado en
        // minúscula casa con su enum en vez de caer al defecto.
        assertEquals(KaraokeMode.ALWAYS, parseKaraokeMode("always", KaraokeMode.OFF))
        assertEquals(
            KaraokeMode.FULLSCREEN_ONLY,
            parseKaraokeMode("fullscreen_only", KaraokeMode.ALWAYS),
        )
        assertEquals(KaraokeMode.OFF, parseKaraokeMode("Off", KaraokeMode.ALWAYS))
    }

    @Test
    fun desconocidoONulo_usaDefecto() {
        assertEquals(
            KaraokeMode.FULLSCREEN_ONLY,
            parseKaraokeMode("SOLO_CON_VOZ", KaraokeMode.FULLSCREEN_ONLY),
        )
        assertEquals(KaraokeMode.OFF, parseKaraokeMode(null, KaraokeMode.OFF))
        assertEquals(KaraokeMode.FULLSCREEN_ONLY, parseKaraokeMode("", KaraokeMode.FULLSCREEN_ONLY))
    }

    @Test
    fun tablaDeVerdad() {
        // Siempre: en las dos vistas.
        assertTrue(karaokeWordsActive(KaraokeMode.ALWAYS, fullscreen = true))
        assertTrue(karaokeWordsActive(KaraokeMode.ALWAYS, fullscreen = false))
        // Solo a pantalla completa: solo ahí.
        assertTrue(karaokeWordsActive(KaraokeMode.FULLSCREEN_ONLY, fullscreen = true))
        assertFalse(karaokeWordsActive(KaraokeMode.FULLSCREEN_ONLY, fullscreen = false))
        // Apagado: en ninguna.
        assertFalse(karaokeWordsActive(KaraokeMode.OFF, fullscreen = true))
        assertFalse(karaokeWordsActive(KaraokeMode.OFF, fullscreen = false))
    }
}
