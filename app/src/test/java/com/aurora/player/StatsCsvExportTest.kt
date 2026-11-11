package com.aurora.player

import com.aurora.player.util.StatsCsvRow
import com.aurora.player.util.buildStatsCsv
import com.aurora.player.util.csvField
import com.aurora.player.util.formatCsvDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCsvExportTest {

    @Test
    fun `sin filas solo va la cabecera`() {
        val csv = buildStatsCsv(emptyList())
        assertEquals(
            "\uFEFFtitulo,artista,album,reproducciones,minutos_escuchados,ultima_reproduccion\n",
            csv,
        )
    }

    @Test
    fun `campo con coma o comillas se entrecomilla y se duplican`() {
        assertEquals("\"Hola, mundo\"", csvField("Hola, mundo"))
        assertEquals("\"di \"\"hola\"\"\"", csvField("di \"hola\""))
        assertEquals("simple", csvField("simple"))
    }

    @Test
    fun `fila con datos y fecha con forma yyyy-MM-dd HH_mm`() {
        val rows = listOf(
            StatsCsvRow("Bonebreaker", "Aurora", "Álbum", 12, 45, 1_700_000_000_000),
        )
        val lines = buildStatsCsv(rows).trim().split("\n")
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("Bonebreaker,Aurora,Álbum,12,45,"))
        val date = lines[1].substringAfterLast(",")
        assertTrue(date.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }

    @Test
    fun `la fecha tiene el formato esperado`() {
        assertTrue(formatCsvDate(1_700_000_000_000).matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }
}
