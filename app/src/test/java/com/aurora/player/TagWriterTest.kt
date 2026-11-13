package com.aurora.player

import com.aurora.player.tags.SongTags
import com.aurora.player.tags.TagIO
import com.aurora.player.tags.TagWriter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TagWriter con archivos reales en un directorio temporal: el flujo
 * copiar → etiquetar → reemplazar, sin Android. El MP3 sintético
 * es el mismo de TagIOTest (dos tramas MPEG1 Layer III válidas).
 */
class TagWriterTest {

    private fun mp3File(dir: File): File {
        val frame = ByteArray(417)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00.toByte()
        val file = File(dir, "cancion.mp3")
        file.outputStream().use { out ->
            out.write(frame)
            out.write(frame)
        }
        return file
    }

    private fun tempDir(): File {
        val dir = File.createTempFile("tagwriter-", "")
        dir.delete()
        dir.mkdirs()
        return dir
    }

    @Test
    fun flujoCompleto_etiquetaYReemplaza() {
        val dir = tempDir()
        val original = mp3File(dir)
        val tags = SongTags(title = "Aurora", artist = "La Cantante", track = 3, trackTotal = 12)
        val temp = TagWriter.prepare(original)
        assertNotNull(temp)
        assertTrue(TagIO.write(temp!!, tags))
        assertTrue(TagWriter.commit(temp, original))
        assertFalse(temp.exists())
        assertFalse(File(dir, "cancion.mp3.bak").exists())
        val read = TagIO.read(original)
        assertNotNull(read)
        assertEquals("Aurora", read!!.title)
        assertEquals("La Cantante", read.artist)
        assertEquals(3, read.track)
        assertEquals(12, read.trackTotal)
    }

    @Test
    fun prepararArchivoInexistente_devuelveNull() {
        val dir = tempDir()
        assertNull(TagWriter.prepare(File(dir, "no-existe.mp3")))
    }

    @Test
    fun commitConTempInexistente_devuelveFalseYOriginalIntacto() {
        val dir = tempDir()
        val original = mp3File(dir)
        assertTrue(TagIO.write(original, SongTags(title = "Viejo Título")))
        assertFalse(TagWriter.commit(File(dir, "no-existe.tmp"), original))
        assertEquals("Viejo Título", TagIO.read(original)!!.title)
    }

    @Test
    fun commitConTargetInexistente_devuelveFalse() {
        val dir = tempDir()
        val temp = mp3File(dir)
        assertFalse(TagWriter.commit(temp, File(dir, "no-existe.mp3")))
    }

    @Test
    fun descartar_borraTemporal() {
        val dir = tempDir()
        val original = mp3File(dir)
        val temp = TagWriter.prepare(original)
        assertNotNull(temp)
        assertTrue(temp!!.exists())
        TagWriter.discard(temp)
        assertFalse(temp.exists())
        assertTrue(original.exists())
    }
}
