package com.aurora.player

import com.aurora.player.online.buildCanvasRequestBytes
import com.aurora.player.online.buildSpotifySearchUrl
import com.aurora.player.online.parseCanvasResponse
import com.aurora.player.online.parseSpotifyTrackId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spotify Canvas (Fase 2): codificación protobuf de la petición,
 * lectura de la respuesta y búsqueda del id de la canción. Puro,
 * sin Android, para probarlo en la JVM.
 */
class SpotifyCanvasTest {

    @Test
    fun `peticion protobuf con bytes exactos`() {
        // entity_uri = "spotify:track:abc" (16 bytes):
        // interior [0x0A, 16, uri...], exterior [0x0A, 18, interior...]
        val uri = "spotify:track:abc".toByteArray(Charsets.UTF_8)
        val expected = byteArrayOf(0x0A, 0x12, 0x0A, 0x10) + uri
        assertArrayEquals(expected, buildCanvasRequestBytes("abc"))
    }

    private fun canvasMessage(url: String, type: Int): ByteArray {
        val urlBytes = url.toByteArray(Charsets.UTF_8)
        val out = mutableListOf<Byte>()
        out.add(0x12.toByte()) // campo 2 (url), length-delimited
        out.add(urlBytes.size.toByte())
        out.addAll(urlBytes.toList())
        out.add(0x20.toByte()) // campo 4 (type), varint
        out.add(type.toByte())
        return out.toByteArray()
    }

    private fun responseWith(vararg canvases: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        for (canvas in canvases) {
            out.add(0x0A.toByte()) // campo 1 (canvases), length-delimited
            out.add(canvas.size.toByte())
            out.addAll(canvas.toList())
        }
        return out.toByteArray()
    }

    @Test
    fun `respuesta con video looping devuelve url y es video`() {
        val bytes = responseWith(canvasMessage("https://example.com/c.mp4", 2))
        val canvas = parseCanvasResponse(bytes)
        assertEquals("https://example.com/c.mp4", canvas?.url)
        assertTrue(canvas?.isVideo == true)
    }

    @Test
    fun `solo imagen devuelve url y no es video`() {
        val bytes = responseWith(canvasMessage("https://example.com/c.jpg", 0))
        val canvas = parseCanvasResponse(bytes)
        assertEquals("https://example.com/c.jpg", canvas?.url)
        assertFalse(canvas?.isVideo == true)
    }

    @Test
    fun `prefiere el video cuando hay imagen y video`() {
        val bytes = responseWith(
            canvasMessage("https://example.com/c.jpg", 0),
            canvasMessage("https://example.com/c.mp4", 1),
        )
        val canvas = parseCanvasResponse(bytes)
        assertEquals("https://example.com/c.mp4", canvas?.url)
        assertTrue(canvas?.isVideo == true)
    }

    @Test
    fun `respuesta vacia o basura es null`() {
        assertNull(parseCanvasResponse(byteArrayOf()))
        assertNull(parseCanvasResponse(byteArrayOf(0x0A, 0x00)))
    }

    @Test
    fun `url de busqueda y primer id`() {
        assertEquals(
            "https://api.spotify.com/v1/search?q=track%3ABonebreaker+artist%3AExcision&type=track&limit=1",
            buildSpotifySearchUrl("Bonebreaker", "Excision"),
        )
        assertNull(buildSpotifySearchUrl("", "Excision"))
        val json = """{"tracks":{"items":[{"id":"0DiWol3AO6WpXZgp0goxAV","name":"Bonebreaker"}]}}"""
        assertEquals("0DiWol3AO6WpXZgp0goxAV", parseSpotifyTrackId(json))
        assertNull(parseSpotifyTrackId("""{"tracks":{"items":[]}}"""))
    }
}
