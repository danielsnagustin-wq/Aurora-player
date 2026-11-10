package com.aurora.player

import com.aurora.player.util.LrcLine
import com.aurora.player.util.LrcParser
import com.aurora.player.util.LrcWord
import com.aurora.player.util.formatLrcTime
import com.aurora.player.util.lrcLinesToText
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsEditorTest {

    @Test
    fun `el tiempo se formatea en mm_ss_cc`() {
        assertEquals("00:00.00", formatLrcTime(0))
        assertEquals("00:01.00", formatLrcTime(1000))
        assertEquals("01:02.50", formatLrcTime(62500))
        assertEquals("10:05.09", formatLrcTime(605090))
    }

    @Test
    fun `lineas planas se serializan con su etiqueta`() {
        val lines = listOf(
            LrcLine(1000, "Primera"),
            LrcLine(5500, "Segunda"),
        )
        assertEquals("[00:01.00]Primera\n[00:05.50]Segunda\n", lrcLinesToText(lines))
    }

    @Test
    fun `lineas con palabras conservan sus etiquetas`() {
        val lines = listOf(
            LrcLine(
                12000, "Hola mundo hoy",
                listOf(
                    LrcWord(12000, "Hola"),
                    LrcWord(12500, "mundo"),
                    LrcWord(13000, "hoy"),
                ),
            ),
        )
        assertEquals(
            "[00:12.00]Hola <00:12.50>mundo <00:13.00>hoy\n",
            lrcLinesToText(lines),
        )
    }

    @Test
    fun `parsear y serializar da el mismo texto`() {
        val original = "[00:12.00]Hola <00:12.50>mundo <00:13.00>hoy\n[00:15.00]Otra línea\n"
        val parsed = LrcParser.parse(original)
        assertEquals(original, lrcLinesToText(parsed))
    }

    @Test
    fun `las lineas salen ordenadas por tiempo`() {
        val lines = listOf(
            LrcLine(5000, "Cinco"),
            LrcLine(1000, "Uno"),
        )
        assertEquals("[00:01.00]Uno\n[00:05.00]Cinco\n", lrcLinesToText(lines))
    }
}
