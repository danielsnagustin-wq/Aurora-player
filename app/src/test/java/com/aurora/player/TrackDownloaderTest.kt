package com.aurora.player

import android.content.Context
import android.os.Environment
import com.aurora.player.model.StreamQuality
import com.aurora.player.online.ResolvedStream
import com.aurora.player.online.StreamCacheStore
import com.aurora.player.online.TrackDownloader
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

class TrackDownloaderTest {

    private fun downloader(): TrackDownloader =
        TrackDownloader(mock(Context::class.java), mock(StreamCacheStore::class.java))

    @Test
    fun buildDownloadFileNameNormal() {
        assertEquals(
            "AURORA - Runaway.m4a",
            downloader().buildDownloadFileName("AURORA", "Runaway", "m4a"),
        )
    }

    @Test
    fun buildDownloadFileNameSanitizaProhibidos() {
        assertEquals(
            "A B C - Tí tulo.m4a",
            downloader().buildDownloadFileName("A/B:C", "Tí:tulo", "m4a"),
        )
        assertEquals(
            "A B - C D.webm",
            downloader().buildDownloadFileName("A?B", "C<D>", "webm"),
        )
    }

    @Test
    fun buildDownloadFileNameVaciosUsanSinArtistaSinTitulo() {
        assertEquals(
            "Sin artista - Sin título.m4a",
            downloader().buildDownloadFileName("", "   ", "m4a"),
        )
    }

    @Test
    fun buildDownloadFileNameTruncaCadaParteA200() {
        val largo = "x".repeat(250)
        val nombre = downloader().buildDownloadFileName(largo, largo, "m4a")
        assertEquals("x".repeat(200) + " - " + "x".repeat(200) + ".m4a", nombre)
    }

    @Test
    fun extensionForItag() {
        val d = downloader()
        assertEquals("m4a", d.extensionForItag(140))
        assertEquals("m4a", d.extensionForItag(139))
        assertEquals("webm", d.extensionForItag(251))
        assertEquals("bin", d.extensionForItag(999))
    }

    @Test
    fun mimeTypeForItag() {
        val d = downloader()
        assertEquals("audio/mp4", d.mimeTypeForItag(140))
        assertEquals("audio/mp4", d.mimeTypeForItag(139))
        assertEquals("audio/webm", d.mimeTypeForItag(251))
        assertEquals("application/octet-stream", d.mimeTypeForItag(999))
    }

    @Test
    fun itagDelArchivoDeCache() {
        // RONDA CAZA 2026-11-13 (P1): la ext/MIME de la descarga se
        // deriva del itag del archivo de caché real, no del itag
        // pedido (cachedFile puede devolver otro itag). El videoId
        // puede llevar "_" y el nombre puede no tener punto.
        val d = downloader()
        assertEquals(140, d.itagFromCacheFileName("dQw4w9WgXcQ_140.m4a"))
        assertEquals(251, d.itagFromCacheFileName("abc_def_251.webm"))
        assertEquals(140, d.itagFromCacheFileName("dQw4w9WgXcQ_140"))
        assertNull(d.itagFromCacheFileName("sin_itag.m4a"))
        assertNull(d.itagFromCacheFileName("video123.m4a"))
    }

    @Test
    fun downloadTrack_sinStreamResuelto_falla() = runBlocking {
        val result = downloader().downloadTrack(
            videoId = "vid",
            title = "Runaway",
            artist = "AURORA",
            quality = StreamQuality.HIGH,
        ) { _, _ -> null }
        assertTrue(result.isFailure)
    }

    @Test
    fun downloadTrack_sinArchivoDeCache_falla() = runBlocking {
        // El mock de StreamCacheStore devuelve null por defecto en
        // cachedFile y en download: no hay archivo que copiar.
        val result = downloader().downloadTrack(
            videoId = "vid",
            title = "Runaway",
            artist = "AURORA",
            quality = StreamQuality.HIGH,
        ) { _, _ -> ResolvedStream("https://ejemplo.invalid/audio", 140) }
        assertTrue(result.isFailure)
    }

    @Test
    fun downloadTrack_nombreDelArchivoUsaElItagDeLaCache() {
        runBlocking {
            // RONDA 2 (2026-11-13, P2): pin del cableado del arreglo P1
            // (itagDelArchivoDeCache): la extensión del archivo sale del
            // itag del archivo de caché (251 → webm), NO del itag recién
            // resuelto (140 → m4a). cachedFile devuelve el primero de
            // preferredItags que exista, que puede ser otro itag.
            //
            // En la JVM, Build.VERSION.SDK_INT es 0: se toma la rama
            // pre-Q (archivo en disco) y Uri.fromFile tira "Stub!" → el
            // Result es failure. Pero el archivo YA se escribió antes, con
            // el nombre correcto: eso es lo que este test comprueba.
            val dir = Files.createTempDirectory("aurora-dl-").toFile()
            val cacheDir = Files.createTempDirectory("aurora-cache-").toFile()
            val cacheFile = File(cacheDir, "vid_251.webm").apply {
                writeBytes("audio".toByteArray())
            }
            val context = mock(Context::class.java)
            Mockito.`when`(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
                .thenReturn(dir)
            val cache = mock(StreamCacheStore::class.java)
            Mockito.`when`(cache.cachedFile("vid", StreamQuality.HIGH)).thenReturn(cacheFile)
            val d = TrackDownloader(context, cache)
            val result = d.downloadTrack(
                videoId = "vid",
                title = "Runaway",
                artist = "AURORA",
                quality = StreamQuality.HIGH,
            ) { _, _ -> ResolvedStream("https://ejemplo.invalid/audio", 140) }
            assertTrue(result.isFailure)
            val outDir = File(dir, "AuroraPlayer")
            val written = outDir.listFiles().orEmpty()
            assertEquals(1, written.size)
            assertEquals("AURORA - Runaway.webm", written[0].name)
            assertEquals("audio", written[0].readText())
            dir.deleteRecursively()
            cacheDir.deleteRecursively()
        }
    }
}
