package com.aurora.player

import com.aurora.player.util.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcKaraokeTest {

    @Test
    fun `linea mejorada parsea palabras con su tiempo`() {
        val lines = LrcParser.parse("[00:12.00]Hola <00:12.50>mundo <00:13.00>hoy\n")
        assertEquals(1, lines.size)
        assertEquals("Hola mundo hoy", lines[0].text)
        assertEquals(12000L, lines[0].timeMs)
        assertEquals(3, lines[0].words.size)
        assertEquals(12000L to "Hola", lines[0].words[0].timeMs to lines[0].words[0].text)
        assertEquals(12500L to "mundo", lines[0].words[1].timeMs to lines[0].words[1].text)
        assertEquals(13000L to "hoy", lines[0].words[2].timeMs to lines[0].words[2].text)
    }

    @Test
    fun `tiempos de palabra entre corchetes se leen como karaoke`() {
        val lines = LrcParser.parse("[00:10.00]ho [00:11.00]la [00:12.00]mundo\n")
        assertEquals(1, lines.size)
        assertEquals("ho la mundo", lines[0].text)
        assertEquals(10000L, lines[0].timeMs)
        assertEquals(3, lines[0].words.size)
        assertEquals(10000L to "ho", lines[0].words[0].timeMs to lines[0].words[0].text)
        assertEquals(11000L to "la", lines[0].words[1].timeMs to lines[0].words[1].text)
        assertEquals(12000L to "mundo", lines[0].words[2].timeMs to lines[0].words[2].text)
    }

    @Test
    fun `varias etiquetas sin texto entre ellas siguen siendo multi-tiempo`() {
        val lines = LrcParser.parse("[00:01.00][00:05.00]coro\n")
        assertEquals(2, lines.size)
        assertEquals("coro", lines[0].text)
        assertEquals("coro", lines[1].text)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals(5000L, lines[1].timeMs)
    }

    @Test
    fun `linea plana no tiene palabras`() {
        val lines = LrcParser.parse("[00:01.00]Primera línea\n")
        assertEquals(1, lines.size)
        assertTrue(lines[0].words.isEmpty())
        assertEquals("Primera línea", lines[0].text)
    }

    @Test
    fun `palabra sin espacio antes de la etiqueta tambien se separa`() {
        val lines = LrcParser.parse("[00:01.00]a<00:01.50>b\n")
        assertEquals("a b", lines[0].text)
        assertEquals(2, lines[0].words.size)
        assertEquals(1000L to "a", lines[0].words[0].timeMs to lines[0].words[0].text)
        assertEquals(1500L to "b", lines[0].words[1].timeMs to lines[0].words[1].text)
    }

    @Test
    fun `la palabra actual es la ultima pasada`() {
        val lines = LrcParser.parse("[00:12.00]Hola <00:12.50>mundo <00:13.00>hoy\n")
        val line = lines[0]
        assertEquals(-1, LrcParser.currentWordIndex(line, 11000))
        assertEquals(0, LrcParser.currentWordIndex(line, 12000))
        assertEquals(1, LrcParser.currentWordIndex(line, 12700))
        assertEquals(2, LrcParser.currentWordIndex(line, 14000))
    }

    @Test
    fun `linea con dos etiquetas lleva las mismas palabras`() {
        val lines = LrcParser.parse("[00:01.00][00:05.00]Coro <00:01.50>otra\n")
        assertEquals(2, lines.size)
        assertEquals("Coro otra", lines[0].text)
        assertEquals("Coro otra", lines[1].text)
        assertEquals(2, lines[0].words.size)
        assertEquals(2, lines[1].words.size)
    }
}
