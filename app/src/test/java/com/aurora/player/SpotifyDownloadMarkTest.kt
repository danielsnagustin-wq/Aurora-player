package com.aurora.player

import com.aurora.player.data.DownloadRecord
import com.aurora.player.data.DownloadStatus
import com.aurora.player.ui.online.isTrackDownloaded
import com.aurora.player.ui.online.normalizeDownloadTitle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 4 paso 7: la marca de descarga en listas importadas compara
 * el título/artista de Spotify con el registro de descargas.
 */
class SpotifyDownloadMarkTest {

    private fun record(
        title: String,
        artist: String,
        status: DownloadStatus = DownloadStatus.READY,
    ) = DownloadRecord(
        videoId = "vid1",
        title = title,
        artist = artist,
        sizeBytes = 1_000L,
        downloadedAtMs = 0L,
        status = status,
        uri = "content://media/1",
    )

    @Test
    fun exactMatch_isDownloaded() {
        assertTrue(isTrackDownloaded("Midnight City", "M83", listOf(record("Midnight City", "M83"))))
    }

    @Test
    fun accentsAndCase_doNotMatter() {
        assertTrue(isTrackDownloaded("Beyoncé", "BEYONCÉ", listOf(record("Beyonce", "beyonce"))))
        assertTrue(normalizeDownloadTitle("  Café   del  Mar ") == "cafe del mar")
    }

    @Test
    fun youtubeSuffixTitle_stillMatches() {
        assertTrue(
            isTrackDownloaded(
                "Midnight City",
                "M83",
                listOf(record("Midnight City (Official Video)", "M83")),
            ),
        )
    }

    @Test
    fun differentArtist_doesNotMatch() {
        assertFalse(
            isTrackDownloaded(
                "Midnight City",
                "M83",
                listOf(record("Midnight City", "The Weeknd")),
            ),
        )
    }

    @Test
    fun failedOrPendingRecord_doesNotMark() {
        assertFalse(
            isTrackDownloaded(
                "Midnight City",
                "M83",
                listOf(record("Midnight City", "M83", DownloadStatus.FAILED)),
            ),
        )
        assertFalse(
            isTrackDownloaded(
                "Midnight City",
                "M83",
                listOf(record("Midnight City", "M83", DownloadStatus.PENDING_WIFI)),
            ),
        )
    }

    @Test
    fun emptyRecords_neverMarks() {
        assertFalse(isTrackDownloaded("Midnight City", "M83", emptyList()))
    }
}
