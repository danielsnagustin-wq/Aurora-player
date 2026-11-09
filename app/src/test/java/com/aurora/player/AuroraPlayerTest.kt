package com.aurora.player

import com.aurora.player.audio.EqualizerManager
import com.aurora.player.data.trackAndDisc
import com.aurora.player.ui.player.formatSpeed
import com.aurora.player.ui.player.swipeDirection
import com.aurora.player.util.LrcParser
import com.aurora.player.util.M3u
import com.aurora.player.util.TimeFormat
import com.aurora.player.util.buildMetadataUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aurora Player — tests unitarios básicos (Etapa 1).
 * Sin Android: solo lógica pura (M3U, tiempo, presets de EQ).
 * Los tests de MediaStore / Room / ExoPlayer van en androidTest o
 * con Robolectric en un paso posterior; no se fingen aquí.
 */
class AuroraPlayerTest {

    @Test
    fun `formato de duracion 0 es 0-00`() {
        assertEquals("0:00", TimeFormat.formatDuration(0))
    }

    @Test
    fun `formato de duracion 90 segundos es 1-30`() {
        assertEquals("1:30", TimeFormat.formatDuration(90_000))
    }

    @Test
    fun `formato de duracion 61 minutos`() {
        assertEquals("61:05", TimeFormat.formatDuration(3_665_000))
    }

    @Test
    fun `tiempo total 90 minutos`() {
        assertEquals("1h 30min", TimeFormat.formatTotalListened(5_400_000))
    }

    @Test
    fun `M3U se construye y se vuelve a leer`() {
        val entries = listOf(
            M3u.Entry(200, "Artista - Canción", "/Music/cancion.mp3"),
            M3u.Entry(180, "Otro - Tema", "tema.flac"),
        )
        val text = M3u.build(entries)
        val parsed = M3u.parse(text)
        assertEquals(2, parsed.size)
        assertEquals("/Music/cancion.mp3", parsed[0].path)
        assertEquals(200L, parsed[0].seconds)
        assertEquals("tema.flac", parsed[1].path)
    }

    @Test
    fun `M3U ignora directivas desconocidas y lineas vacias`() {
        val parsed = M3u.parse("#EXTM3U\n#EXTINF:120,A - B\n\n/song.mp3\n#UNKNOWN:1\n")
        assertEquals(1, parsed.size)
        assertEquals("/song.mp3", parsed[0].path)
    }

    @Test
    fun `presets de EQ tienen 10 bandas y estan en rango`() {
        assertEquals(35, EqualizerManager.PRESETS.size)
        for (preset in EqualizerManager.PRESETS) {
            assertEquals(10, preset.levelsDb.size)
            assertTrue(preset.levelsDb.all { it in -15f..15f })
        }
        assertTrue(EqualizerManager.PRESETS.all { it.levelsDb[0] == 0f }.not())
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            EqualizerManager.PRESETS.first { it.name == "Plano" }.levelsDb)
    }

    @Test
    fun `LRC parsea lineas con tiempo e ignora etiquetas`() {
        val lines = LrcParser.parse("[ti:Título]\n[00:01.00]Primera\n[00:05.50]Segunda\n[ar:Artista]\n")
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals("Primera", lines[0].text)
        assertEquals(5500L, lines[1].timeMs)
        assertEquals("Segunda", lines[1].text)
    }

    @Test
    fun `LRC ordena por tiempo y la linea actual es la ultima pasada`() {
        val lines = LrcParser.parse("[00:10.00]Diez\n[00:02.00]Dos\n[00:05.00]Cinco\n")
        assertEquals("Dos", lines[0].text)
        assertEquals(0, LrcParser.currentLineIndex(lines, 3000))
        assertEquals(1, LrcParser.currentLineIndex(lines, 7000))
        assertEquals(-1, LrcParser.currentLineIndex(lines, 1000))
    }

    @Test
    fun `TRACK 1003 es pista 3 disco 1`() {
        assertEquals(3 to 1, trackAndDisc(1003, 0, 0))
    }

    @Test
    fun `columnas modernas mandan sobre TRACK`() {
        assertEquals(5 to 2, trackAndDisc(2005, 5, 2))
    }

    @Test
    fun `sin dato de pista el disco es 1`() {
        assertEquals(0 to 1, trackAndDisc(0, 0, 0))
    }

    @Test
    fun `editor solo escribe campos con valor`() {
        val out = buildMetadataUpdate("X", "", "", "", "", "", "", "", "", true)
        assertEquals(1, out.size)
        assertEquals("X", out["title"])
    }

    @Test
    fun `editor combina pista y disco en TRACK`() {
        val out = buildMetadataUpdate("", "", "", "", "", "", "", "3", "2", true)
        assertEquals(2003, out["track"])
        assertEquals(3, out["cd_track_number"])
        assertEquals(2, out["disc_number"])
    }

    @Test
    fun `sin API 30 no hay columnas nuevas`() {
        val out = buildMetadataUpdate("", "", "Y", "", "Rock", "", "", "5", "1", false)
        assertTrue(!out.containsKey("album_artist"))
        assertTrue(!out.containsKey("genre"))
        assertTrue(!out.containsKey("cd_track_number"))
        assertEquals(1005, out["track"])
    }

    @Test
    fun `ano invalido no se escribe`() {
        assertTrue(!buildMetadataUpdate("", "", "", "", "", "", "abc", "", "", true).containsKey("year"))
        assertTrue(!buildMetadataUpdate("", "", "", "", "", "", "0", "", "", true).containsKey("year"))
        assertEquals(2020, buildMetadataUpdate("", "", "", "", "", "", "2020", "", "", true)["year"])
    }

    @Test
    fun `deslizar a la izquierda es siguiente y a la derecha anterior`() {
        assertEquals(1, swipeDirection(-150f))
        assertEquals(-1, swipeDirection(150f))
        assertEquals(0, swipeDirection(50f))
        assertEquals(0, swipeDirection(-99f))
    }

    @Test
    fun `la velocidad se lee con sus decimales reales`() {
        assertEquals("1", formatSpeed(1f))
        assertEquals("1.25", formatSpeed(1.25f))
        assertEquals("1.5", formatSpeed(1.5f))
        assertEquals("0.75", formatSpeed(0.75f))
        assertEquals("2", formatSpeed(2f))
    }
}
