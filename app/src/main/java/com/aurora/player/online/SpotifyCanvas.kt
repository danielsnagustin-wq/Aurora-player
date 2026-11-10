package com.aurora.player.online

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spotify Canvas como BitChord (Fase 2 — usuario 2026-11-09).
 * Endpoint verificado en el código fuente del plugin yt-dlp
 * (qbnu/yt-dlp-SpotifyCanvas, yt_dlp_plugins/extractor/spotifycanvas.py
 * + proto/canvas.proto): NO hay API pública de Canvas; se pide a
 *   POST https://spclient.wg.spotify.com/canvaz-cache/v0/canvases
 * con un protobuf EntityCanvazRequest (entity_uri =
 * "spotify:track:{id}") y el token de acceso como Bearer. La
 * respuesta es un protobuf EntityCanvazResponse con canvases[].url
 * y canvases[].type (IMAGE=0, VIDEO=1, VIDEO_LOOPING=2,
 * VIDEO_LOOPING_RANDOM=3, GIF=4).
 *
 * El protobuf se codifica y se lee a mano (el mensaje es diminuto;
 * no se añade una librería entera por esto). Funciones puras,
 * probadas en la JVM.
 *
 * Para encontrar el id de Spotify de una canción se busca en
 * /v1/search con el token (el mismo token de la cookie sp_dc).
 */

data class SpotifyCanvas(val url: String, val isVideo: Boolean)

/** Codifica EntityCanvazRequest{entities=[{entity_uri}]} a bytes. */
fun buildCanvasRequestBytes(trackId: String): ByteArray {
    val uri = "spotify:track:$trackId".toByteArray(Charsets.UTF_8)
    val inner = ByteArrayOutputStream().apply {
        write(0x0A) // campo 1 (entity_uri), length-delimited
        writeVarint(uri.size)
        write(uri)
    }.toByteArray()
    return ByteArrayOutputStream().apply {
        write(0x0A) // campo 1 (entities), length-delimited
        writeVarint(inner.size)
        write(inner)
    }.toByteArray()
}

private fun ByteArrayOutputStream.writeVarint(value: Int) {
    var v = value
    while (true) {
        if (v and 0x7F.inv() == 0) {
            write(v)
            return
        }
        write(v and 0x7F or 0x80)
        v = v ushr 7
    }
}

private class ProtoReader(private val bytes: ByteArray) {
    var pos = 0

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (pos < bytes.size) {
            val b = bytes[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift >= 64) return result
        }
        return result
    }

    fun readBytes(length: Int): ByteArray {
        val end = (pos + length).coerceAtMost(bytes.size)
        val out = bytes.copyOfRange(pos, end)
        pos = end
        return out
    }

    fun skip(wireType: Int) {
        when (wireType) {
            0 -> readVarint()
            1 -> pos += 8
            2 -> pos += readVarint().toInt()
            5 -> pos += 4
            else -> pos = bytes.size
        }
    }
}

/**
 * Lee EntityCanvazResponse: devuelve el primer Canvas con vídeo
 * (VIDEO/VIDEO_LOOPING/VIDEO_LOOPING_RANDOM/GIF) y, si no hay
 * ninguno, el primero con imagen. Null si no hay Canvas.
 */
fun parseCanvasResponse(bytes: ByteArray): SpotifyCanvas? {
    val reader = ProtoReader(bytes)
    var imageFallback: SpotifyCanvas? = null
    while (reader.pos < bytes.size) {
        val tag = reader.readVarint().toInt()
        val field = tag ushr 3
        val wireType = tag and 0x07
        if (field != 1 || wireType != 2) {
            reader.skip(wireType)
            continue
        }
        val messageBytes = reader.readBytes(reader.readVarint().toInt())
        val canvas = parseCanvaz(messageBytes) ?: continue
        if (canvas.isVideo) return canvas
        if (imageFallback == null) imageFallback = canvas
    }
    return imageFallback
}

private fun parseCanvaz(bytes: ByteArray): SpotifyCanvas? {
    val reader = ProtoReader(bytes)
    var url: String? = null
    var type = 0
    while (reader.pos < bytes.size) {
        val tag = reader.readVarint().toInt()
        val field = tag ushr 3
        val wireType = tag and 0x07
        when {
            field == 2 && wireType == 2 -> {
                url = reader.readBytes(reader.readVarint().toInt())
                    .toString(Charsets.UTF_8)
            }
            field == 4 && wireType == 0 -> type = reader.readVarint().toInt()
            else -> reader.skip(wireType)
        }
    }
    if (url.isNullOrBlank()) return null
    return SpotifyCanvas(url, type != 0)
}

/** GET /v1/search para el id de Spotify de una canción. */
fun buildSpotifySearchUrl(track: String, artist: String): String? {
    if (track.isBlank() || artist.isBlank()) return null
    val query = "track:${track.trim()} artist:${artist.trim()}"
    return "https://api.spotify.com/v1/search?q=" +
        URLEncoder.encode(query, "UTF-8") + "&type=track&limit=1"
}

/** Primer id de tracks.items[].id, o null. */
fun parseSpotifyTrackId(json: String): String? {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return null
    }
    return root.obj("tracks")?.obj("items")?.arr()?.firstOrNull()
        ?.obj("id")?.str()?.takeIf { it.isNotBlank() }
}

class SpotifyCanvasClient {
    /** Busca el id de Spotify de una canción (título + artista). */
    suspend fun searchTrackId(token: String, track: String, artist: String): String? =
        withContext(Dispatchers.IO) {
            val url = buildSpotifySearchUrl(track, artist) ?: return@withContext null
            val body = httpGet(url, token) ?: return@withContext null
            parseSpotifyTrackId(body)
        }

    /** Pide el Canvas de una canción por su id de Spotify. */
    suspend fun fetchCanvas(token: String, trackId: String): SpotifyCanvas? =
        withContext(Dispatchers.IO) {
            val requestBytes = buildCanvasRequestBytes(trackId)
            val responseBytes = httpPost(
                "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases",
                token,
                requestBytes,
            ) ?: return@withContext null
            parseCanvasResponse(responseBytes)
        }

    private fun httpGet(url: String, token: String): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            try {
                if (conn.responseCode != 200) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun httpPost(url: String, token: String, body: ByteArray): ByteArray? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/x-protobuf")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            try {
                conn.outputStream.use { it.write(body) }
                if (conn.responseCode != 200) return null
                // Tope de 1 MB: la respuesta es un manifiesto
                // protobuf (URLs del vídeo), nunca megabytes. Sin
                // tope, una respuesta gigante tumba la app por
                // memoria (misma familia del crash, 2026-11-10).
                conn.inputStream.use { stream ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = stream.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > 1024 * 1024) return null
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
