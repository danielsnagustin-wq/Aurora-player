package com.aurora.player

import com.aurora.player.online.CaptionTrack
import com.aurora.player.online.parseCaptionTracks
import com.aurora.player.online.parsePlayerVideoFormats
import com.aurora.player.online.selectCaptionTrack
import com.aurora.player.online.selectVideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YouTube con vídeo + subtítulos (Fase 2 app, Namida), Serie A:
 * el player trae audio, progresivos y solo-vídeo; hay que quedarse
 * solo con los progresivos y elegir el mejor hasta 720p, y elegir
 * la pista de subtítulos en español a mano antes que la automática.
 */
class VideoFormatsTest {

    private val playerJson = """
{"playabilityStatus": {"status": "OK"}, "streamingData": {"adaptiveFormats": [{"itag": 140, "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "url": "https://example.com/a140", "bitrate": 128000}, {"itag": 18, "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", "url": "https://example.com/v18", "width": 640, "height": 360, "audioChannels": 2}, {"itag": 22, "mimeType": "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"", "url": "https://example.com/v22", "width": 1280, "height": 720, "audioChannels": 2}, {"itag": 37, "mimeType": "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"", "url": "https://example.com/v37", "width": 1920, "height": 1080, "audioChannels": 2}, {"itag": 137, "mimeType": "video/mp4; codecs=\"avc1.640028\"", "url": "https://example.com/v137", "width": 1920, "height": 1080}]}, "captions": {"playerCaptionsTracklistRenderer": {"captionTracks": [{"baseUrl": "https://example.com/tt?lang=es", "languageCode": "es", "name": {"simpleText": "Espa\u00f1ol"}}, {"baseUrl": "https://example.com/tt?lang=en", "languageCode": "en", "name": {"simpleText": "English (auto-generated)"}, "kind": "asr"}, {"baseUrl": "https://example.com/tt?lang=fr", "languageCode": "fr", "name": {"simpleText": "Fran\u00e7ais"}}]}}}
    """.trimIndent()

    @Test
    fun soloProgresivosConAudio() {
        val formats = parsePlayerVideoFormats(playerJson)!!
        assertEquals(listOf(18L, 22L, 37L), formats.map { it.itag })
        assertTrue(formats.all { it.url.startsWith("https://example.com/v") })
    }

    @Test
    fun elige720AunqueHaya1080() {
        val formats = parsePlayerVideoFormats(playerJson)!!
        assertEquals(22L, selectVideoFormat(formats)?.itag)
    }

    @Test
    fun sinProgresivos_noHayVideo() {
        val soloAudio = """{"playabilityStatus": {"status": "OK"}, "streamingData": {"adaptiveFormats": [{"itag": 140, "mimeType": "audio/mp4", "url": "https://example.com/a140", "bitrate": 128000}]}}"""
        assertTrue(parsePlayerVideoFormats(soloAudio)!!.isEmpty())
        assertNull(selectVideoFormat(parsePlayerVideoFormats(soloAudio)!!))
    }

    @Test
    fun estadoNoOk_noHayFormatos() {
        val error = """{"playabilityStatus": {"status": "ERROR"}}"""
        assertNull(parsePlayerVideoFormats(error))
    }

    @Test
    fun subtitulos_seLeenTodos() {
        val tracks = parseCaptionTracks(playerJson)
        assertEquals(listOf("es", "en", "fr"), tracks.map { it.languageCode })
        assertEquals(listOf(false, true, false), tracks.map { it.isAuto })
        assertEquals("Español", tracks[0].name)
    }

    @Test
    fun eligeEspanolAManoAntesQueAutomatico() {
        val tracks = parseCaptionTracks(playerJson)
        assertEquals("es", selectCaptionTrack(tracks)?.languageCode)
        assertEquals(false, selectCaptionTrack(tracks)?.isAuto)
    }

    @Test
    fun sinEspanol_prefiereInglesAunqueSeaAutomatico() {
        val tracks = listOf(
            CaptionTrack("fr", "Français", "https://example.com/tt?lang=fr", null),
            CaptionTrack("en", "English (auto-generated)", "https://example.com/tt?lang=en", "asr"),
        )
        assertEquals("en", selectCaptionTrack(tracks)?.languageCode)
    }

    @Test
    fun sinPistas_noHaySubtitulo() {
        assertNull(selectCaptionTrack(emptyList()))
        assertTrue(parseCaptionTracks("{}").isEmpty())
    }
}
