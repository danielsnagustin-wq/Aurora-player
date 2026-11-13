package com.aurora.player

import android.content.Context
import com.aurora.player.online.StreamCacheStore
import com.aurora.player.online.TrackDownloader
import org.junit.Assert.assertEquals
import org.junit.Test
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
}
