package com.aurora.player

import com.aurora.player.online.captionLineAt
import com.aurora.player.online.parseTimedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Subtítulos de YouTube (Fase 2 app), Serie B: el timedtext srv3
 * trae <p t="ms"> y hay que quedarse con la línea que toca en
 * cada momento de la reproducción.
 */
class CaptionsTest {

    private val timedText = """
        <?xml version="1.0" encoding="utf-8" ?>
        <timedtext format="3">
        <body><div>
        <p t="0" d="1800">Hola, ¿qué tal?</p>
        <p t="2000" d="2500"><span>Mundo</span> &amp; amigos</p>
        <p t="5000" d="2000">Adiós</p>
        </div></body></timedtext>
    """.trimIndent()

    @Test
    fun parseaLineasConSuTiempo() {
        val lines = parseTimedText(timedText)
        assertEquals(listOf(0L, 2000L, 5000L), lines.map { it.timeMs })
        assertEquals("Hola, ¿qué tal?", lines[0].text)
        assertEquals("Mundo & amigos", lines[1].text)
        assertEquals("Adiós", lines[2].text)
    }

    @Test
    fun lineaQueTocaEnCadaMomento() {
        val lines = parseTimedText(timedText)
        assertNull(captionLineAt(lines, -1))
        assertEquals("Hola, ¿qué tal?", captionLineAt(lines, 0)?.text)
        assertEquals("Hola, ¿qué tal?", captionLineAt(lines, 1999)?.text)
        assertEquals("Mundo & amigos", captionLineAt(lines, 2000)?.text)
        assertEquals("Adiós", captionLineAt(lines, 99999)?.text)
    }

    @Test
    fun vacio_noHayLinea() {
        assertTrue(parseTimedText("").isEmpty())
        assertTrue(parseTimedText("<timedtext></timedtext>").isEmpty())
        assertNull(captionLineAt(emptyList(), 1000))
    }
}
