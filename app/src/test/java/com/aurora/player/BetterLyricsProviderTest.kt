package com.aurora.player

import com.aurora.player.lyrics.buildBetterLyricsUrl
import com.aurora.player.lyrics.ttmlToOnlineLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BetterLyrics (proveedores múltiples Fase 2): URLs de la escalera
 * y conversión TTML -> LRC. Puro, sin Android, para probarlo en la JVM.
 */
class BetterLyricsProviderTest {

    @Test
    fun `url con album y duracion`() {
        assertEquals(
            "https://lyrics-api.boidu.dev/getLyrics?a=Excision&s=Bonebreaker&al=Onyx&d=210",
            buildBetterLyricsUrl("Bonebreaker", "Excision", "Onyx", 210, true, true),
        )
    }

    @Test
    fun `escalones sin album ni duracion`() {
        assertEquals(
            "https://lyrics-api.boidu.dev/getLyrics?a=Excision&s=Bonebreaker&d=210",
            buildBetterLyricsUrl("Bonebreaker", "Excision", "Onyx", 210, false, true),
        )
        assertEquals(
            "https://lyrics-api.boidu.dev/getLyrics?a=Excision&s=Bonebreaker",
            buildBetterLyricsUrl("Bonebreaker", "Excision", "Onyx", 210, false, false),
        )
    }

    @Test
    fun `sin titulo o artista no hay url`() {
        assertNull(buildBetterLyricsUrl("", "Excision", null, 0, true, true))
        assertNull(buildBetterLyricsUrl("Bonebreaker", " ", null, 0, true, true))
    }

    @Test
    fun `ttml sincronizado se convierte a lrc con palabras`() {
        val ttml = """<tt><body><div><p begin="00:00:05.120" end="00:00:08.400">""" +
            """<span begin="00:00:05.120">Hola</span> <span begin="00:00:06.000">mundo</span></p></div></body></tt>"""
        val lyrics = ttmlToOnlineLyrics(ttml)
        assertEquals("[00:05.12]Hola <00:06.00>mundo\n", lyrics?.syncedLrc)
        // RONDA 2 (2026-11-14, P2#15-fix): plainText ya no es null cuando hay LRC:
        // es el respaldo con TODAS las líneas (por si el LRC no se pudiera leer).
        assertEquals("Hola mundo", lyrics?.plainText)
    }

    @Test
    fun `ttml sin tiempos se convierte a texto plano`() {
        val ttml = "<tt><body><div><p>Maybe I don't really wanna know</p><p>How your garden grows</p></div></body></tt>"
        val lyrics = ttmlToOnlineLyrics(ttml)
        assertNull(lyrics?.syncedLrc)
        assertEquals("Maybe I don't really wanna know\nHow your garden grows", lyrics?.plainText)
    }

    @Test
    fun `ttml vacio es null`() {
        assertNull(ttmlToOnlineLyrics("<tt><body></body></tt>"))
        assertTrue(ttmlToOnlineLyrics("").let { it == null })
    }
}
