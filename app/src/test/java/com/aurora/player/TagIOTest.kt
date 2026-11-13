package com.aurora.player

import com.aurora.player.tags.SongTags
import com.aurora.player.tags.TagIO
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TagIO con archivos sintéticos (sin audio real): un MP3 con dos
 * tramas MPEG1 Layer III válidas (128 kbps, 44,1 kHz) y un FLAC con
 * solo su bloque STREAMINFO. Si YouTube cambia, nada de esto se
 * entera: son bytes nuestros, no un servicio.
 */
class TagIOTest {

    private fun mp3File(): File {
        // Trama MPEG1 Layer III, 128 kbps, 44,1 kHz: cabecera
        // FF FB 90 00 y 417 bytes por trama (144 * 128000 / 44100).
        val frame = ByteArray(417)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00.toByte()
        val file = File.createTempFile("tagio-", ".mp3")
        file.outputStream().use { out ->
            out.write(frame)
            out.write(frame)
        }
        return file
    }

    private fun flacFile(): File {
        // "fLaC" + bloque STREAMINFO (34 bytes): 4096/4096,
        // 44,1 kHz, estéreo, 16 bits, 0 muestras + bloque PADDING
        // de 1024 bytes: sin él jaudiotagger rechaza el archivo por
        // "too small to be valid audio file" (verificado en local).
        val header = byteArrayOf(
            0x66, 0x4C, 0x61, 0x43, // fLaC
            0x00, 0x00, 0x00, 0x22, // tipo 0, 34 bytes (no es el último)
            0x10, 0x00, 0x10, 0x00, // min/max blocksize 4096
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // min/max framesize 0
            0x0A, 0xC4.toByte(), 0x42, 0xF0.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x81.toByte(), 0x00, 0x04, 0x00, // último bloque, tipo 1, 1024 bytes
        )
        val file = File.createTempFile("tagio-", ".flac")
        file.outputStream().use {
            it.write(header)
            it.write(ByteArray(1024))
        }
        return file
    }

    private val fullTags = SongTags(
        title = "Aurora",
        artist = "La Cantante",
        albumArtist = "La Cantante",
        album = "Disco de Prueba",
        composer = "La Compositora",
        genre = "Pop",
        year = 2026,
        track = 3,
        trackTotal = 12,
        disc = 1,
        discTotal = 2,
        lyricist = "La Letrista",
        comment = "Comentario de prueba",
    )

    @Test
    fun mp3_escribeYLeeEtiquetas() {
        val file = mp3File()
        assertTrue(TagIO.write(file, fullTags))
        val read = TagIO.read(file)
        assertNotNull(read)
        assertEquals("Aurora", read!!.title)
        assertEquals("La Cantante", read.artist)
        assertEquals("La Cantante", read.albumArtist)
        assertEquals("Disco de Prueba", read.album)
        assertEquals("La Compositora", read.composer)
        assertEquals("Pop", read.genre)
        assertEquals(2026, read.year)
        assertEquals(3, read.track)
        assertEquals(12, read.trackTotal)
        assertEquals(1, read.disc)
        assertEquals(2, read.discTotal)
        assertEquals("La Letrista", read.lyricist)
        assertEquals("Comentario de prueba", read.comment)
    }

    @Test
    fun flac_escribeYLeeEtiquetas() {
        val file = flacFile()
        assertTrue(TagIO.write(file, fullTags))
        val read = TagIO.read(file)
        assertNotNull(read)
        assertEquals("Aurora", read!!.title)
        assertEquals("La Cantante", read.artist)
        assertEquals("Disco de Prueba", read.album)
        assertEquals(2026, read.year)
        assertEquals(3, read.track)
        assertEquals(12, read.trackTotal)
    }

    @Test
    fun leerArchivoSinEtiquetas_devuelveVacias() {
        val read = TagIO.read(mp3File())
        assertNotNull(read)
        assertNull(read!!.title)
        assertNull(read.artist)
        assertNull(read.year)
        assertNull(read.track)
    }

    @Test
    fun leerArchivoCorrupto_devuelveNull() {
        val file = File.createTempFile("tagio-", ".bin")
        file.outputStream().use { it.write(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
        assertNull(TagIO.read(file))
    }

    @Test
    fun escribirEnArchivoCorrupto_devuelveFalse() {
        val file = File.createTempFile("tagio-", ".bin")
        file.outputStream().use { it.write(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
        assertFalse(TagIO.write(file, fullTags))
    }

    @Test
    fun escribirSoloTitulo_noBorraElResto() {
        val file = mp3File()
        assertTrue(TagIO.write(file, fullTags))
        assertTrue(TagIO.write(file, SongTags(title = "Nuevo Título")))
        val read = TagIO.read(file)
        assertNotNull(read)
        assertEquals("Nuevo Título", read!!.title)
        assertEquals("La Cantante", read.artist)
        assertEquals(3, read.track)
    }
}
