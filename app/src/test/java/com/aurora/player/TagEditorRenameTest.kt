package com.aurora.player

import com.aurora.player.ui.library.buildRenamedFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagEditorRenameTest {

    @Test
    fun basico() {
        assertEquals("Aurora - La Canción.mp3", buildRenamedFileName("Aurora", "La Canción", "mp3"))
    }

    @Test
    fun sanitizaBarraYDosPuntos() {
        assertEquals("AC DC - Hola Qué Tal.flac", buildRenamedFileName("AC/DC", "Hola: Qué Tal", "flac"))
    }

    @Test
    fun artistaEnBlancoDevuelveNull() {
        assertNull(buildRenamedFileName("   ", "Título", "mp3"))
    }

    @Test
    fun tituloEnBlancoDevuelveNull() {
        assertNull(buildRenamedFileName("Aurora", "", "mp3"))
    }

    @Test
    fun colapsaEspaciosYRecorta() {
        assertEquals("Aurora - La Canción.mp3", buildRenamedFileName("  Aurora   ", "  La   Canción  ", "mp3"))
    }

    @Test
    fun recortaPartesLargasA200() {
        val largo = "a".repeat(250)
        val resultado = buildRenamedFileName(largo, "T", "mp3")!!
        assertEquals(208, resultado.length)
        assertEquals("a".repeat(200) + " - T.mp3", resultado)
    }
}
