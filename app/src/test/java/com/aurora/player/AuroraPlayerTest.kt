package com.aurora.player

import com.aurora.player.audio.EqualizerManager
import com.aurora.player.util.M3u
import com.aurora.player.util.TimeFormat
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
        assertEquals(8, EqualizerManager.PRESETS.size)
        for (preset in EqualizerManager.PRESETS) {
            assertEquals(10, preset.levelsDb.size)
            assertTrue(preset.levelsDb.all { it in -15f..15f })
        }
        assertTrue(EqualizerManager.PRESETS.all { it.levelsDb[0] == 0f }.not())
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            EqualizerManager.PRESETS.first { it.name == "Plano" }.levelsDb)
    }
}
