package com.aurora.player

import com.aurora.player.model.StreamQuality
import com.aurora.player.online.AudioFormat
import com.aurora.player.online.selectAudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamQualityTest {

    private fun formats() = listOf(
        AudioFormat(251L, "audio/webm", "u251", 160_000L),
        AudioFormat(140L, "audio/mp4", "u140", 128_000L),
        AudioFormat(139L, "audio/mp4", "u139", 48_000L),
    )

    @Test
    fun altaPrefiereOpus251() {
        assertEquals(251L, selectAudioFormat(formats(), StreamQuality.HIGH.preferredItags)?.itag)
    }

    @Test
    fun mediaPrefiereAac140() {
        assertEquals(140L, selectAudioFormat(formats(), StreamQuality.MEDIUM.preferredItags)?.itag)
    }

    @Test
    fun bajaPrefiereAac139() {
        assertEquals(139L, selectAudioFormat(formats(), StreamQuality.LOW.preferredItags)?.itag)
    }

    @Test
    fun sinItagPreferidoCaeAlSiguiente() {
        val solo140 = listOf(AudioFormat(140L, "audio/mp4", "u140", 128_000L))
        assertEquals(140L, selectAudioFormat(solo140, StreamQuality.HIGH.preferredItags)?.itag)
    }

    @Test
    fun sinFormatosNoHayAudio() {
        assertNull(selectAudioFormat(emptyList(), StreamQuality.HIGH.preferredItags))
    }

    @Test
    fun descripcionesLlevanElCosteEnMbPorHora() {
        assertEquals("Opus ~160 kbps · ~72 MB/h", StreamQuality.HIGH.description)
        assertEquals("AAC ~128 kbps · ~58 MB/h", StreamQuality.MEDIUM.description)
        assertEquals("AAC ~48 kbps · ~22 MB/h", StreamQuality.LOW.description)
    }
}
