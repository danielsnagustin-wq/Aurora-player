package com.aurora.player.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastMediaTest {

    @Test
    fun online_yt_esEnviable() {
        assertTrue(CastMedia.isCastableOnlineTrack("yt:dQw4w9WgXcQ"))
    }

    @Test
    fun local_numerica_noEsEnviable() {
        assertFalse(CastMedia.isCastableOnlineTrack("12345"))
        assertFalse(CastMedia.isCastableOnlineTrack(""))
    }

    @Test
    fun itag251_esWebm() {
        assertEquals("audio/webm", CastMedia.contentTypeForItag(251L))
    }

    @Test
    fun itagsAac_sonMp4() {
        assertEquals("audio/mp4", CastMedia.contentTypeForItag(140L))
        assertEquals("audio/mp4", CastMedia.contentTypeForItag(139L))
    }

    @Test
    fun itagDesconocido_caeEnMp4() {
        assertEquals("audio/mp4", CastMedia.contentTypeForItag(999L))
    }

    @Test
    fun urlConItag251_esWebm() {
        assertEquals(
            "audio/webm",
            CastMedia.contentTypeForStreamUrl(
                "https://rr1---sn.googlevideo.com/videoplayback?expire=1&itag=251&source=youtube",
            ),
        )
    }

    @Test
    fun urlConItag140_esMp4() {
        assertEquals(
            "audio/mp4",
            CastMedia.contentTypeForStreamUrl(
                "https://rr1---sn.googlevideo.com/videoplayback?expire=1&itag=140&source=youtube",
            ),
        )
    }

    @Test
    fun urlConMimeWebm_sinItag_esWebm() {
        assertEquals(
            "audio/webm",
            CastMedia.contentTypeForStreamUrl(
                "https://example.com/audio?mime=audio%2Fwebm&x=1",
            ),
        )
    }

    @Test
    fun urlSinItagNiMime_caeEnMp4() {
        assertEquals(
            "audio/mp4",
            CastMedia.contentTypeForStreamUrl("https://example.com/audio?id=9"),
        )
    }
}
