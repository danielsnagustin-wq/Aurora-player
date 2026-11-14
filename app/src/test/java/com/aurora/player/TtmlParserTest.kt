package com.aurora.player

import com.aurora.player.util.TtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser TTML (proveedores de letras Fase 2): convierte el formato
 * de Apple Music (BetterLyrics / BiniLyrics / lrc.red) a LrcLine.
 * Puro, sin Android, para probarlo en la JVM.
 */
class TtmlParserTest {

    @Test
    fun `tiempo en formato reloj`() {
        assertEquals(83_456L, TtmlParser.parseTime("00:01:23.456"))
        assertEquals(83_456L, TtmlParser.parseTime("01:23.456"))
    }

    @Test
    fun `tiempo en segundos con o sin s`() {
        assertEquals(83_500L, TtmlParser.parseTime("83.5s"))
        assertEquals(83_500L, TtmlParser.parseTime("83.5"))
    }

    @Test
    fun `tiempo invalido o con frames es null`() {
        assertNull(TtmlParser.parseTime("hola"))
        assertNull(TtmlParser.parseTime("00:00:05:12"))
        assertNull(TtmlParser.parseTime(""))
    }

    @Test
    fun `linea con palabras sincronizadas`() {
        val ttml = """<tt><body><div><p begin="00:00:05.120" end="00:00:08.400">""" +
            """<span begin="00:00:05.120">Hola</span> <span begin="00:00:06.000">mundo</span></p></div></body></tt>"""
        val lines = TtmlParser.parse(ttml)
        assertEquals(1, lines.size)
        assertEquals(5_120L, lines[0].timeMs)
        assertEquals("Hola mundo", lines[0].text)
        assertEquals(2, lines[0].words.size)
        assertEquals(5_120L, lines[0].words[0].timeMs)
        assertEquals("Hola", lines[0].words[0].text)
        assertEquals(6_000L, lines[0].words[1].timeMs)
    }

    @Test
    fun `linea sin begin queda sin sincronizar pero se conserva`() {
        val ttml = "<tt><body><div><p>Maybe I don't really wanna know</p></div></body></tt>"
        val lines = TtmlParser.parse(ttml)
        assertEquals(1, lines.size)
        assertEquals(Long.MAX_VALUE, lines[0].timeMs)
        assertEquals("Maybe I don't really wanna know", lines[0].text)
        assertTrue(lines[0].words.isEmpty())
    }

    @Test
    fun `entidades xml se desescapan`() {
        val ttml = "<tt><body><div><p begin=\"1.0\">Rock &amp; roll &lt;3</p></div></body></tt>"
        val lines = TtmlParser.parse(ttml)
        // RONDA 2 (2026-11-13, P2): el test solo comprobaba el
        // texto; ahora también que el begin se parseó (1000 ms).
        // (Verificado por od: el fixture ya trae comillas normales,
        // sin barras literales — el aviso del auditor era un
        // artefacto del escape JSON del informe.)
        assertEquals(1_000L, lines[0].timeMs)
        assertEquals("Rock & roll <3", lines[0].text)
    }

    @Test
    fun `entrada vacia o sin parrafos da lista vacia`() {
        assertTrue(TtmlParser.parse("").isEmpty())
        assertTrue(TtmlParser.parse("<tt><body></body></tt>").isEmpty())
    }
}
