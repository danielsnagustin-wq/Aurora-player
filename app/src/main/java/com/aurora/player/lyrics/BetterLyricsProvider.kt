package com.aurora.player.lyrics

import com.aurora.player.util.TtmlParser
import com.aurora.player.util.lrcLinesToText
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BetterLyrics (Fase 2, proveedores múltiples — usuario 2026-11-09).
 * API pública sin clave, formato verificado en el código fuente del
 * plugin de Navidrome (ranokay/navidrome-better-lyrics-plugin,
 * internal/source/betterlyrics.go):
 *   GET https://lyrics-api.boidu.dev/getLyrics?a={artista}&s={título}&al={álbum}&d={duraciónSeg}
 * Devuelve TTML con sílabas. Escalera de 3 intentos como el plugin:
 * con álbum+duración, sin álbum, sin álbum ni duración.
 *
 * El TTML se convierte a LRC con TtmlParser (las líneas con palabras
 * conservan sus tiempos) y se guarda como syncedLrc: el resto del
 * pipeline (caché, LrcParser, karaoke) no cambia.
 */

/** URL de un escalón de la escalera, o null si faltan título/artista. */
fun buildBetterLyricsUrl(
    track: String,
    artist: String,
    album: String?,
    durationSec: Long,
    includeAlbum: Boolean,
    includeDuration: Boolean,
): String? {
    if (track.isBlank() || artist.isBlank()) return null
    val params = StringBuilder("https://lyrics-api.boidu.dev/getLyrics?")
    params.append("a=").append(URLEncoder.encode(artist.trim(), "UTF-8"))
    params.append("&s=").append(URLEncoder.encode(track.trim(), "UTF-8"))
    if (includeAlbum && !album.isNullOrBlank()) {
        params.append("&al=").append(URLEncoder.encode(album.trim(), "UTF-8"))
    }
    if (includeDuration && durationSec > 0) {
        params.append("&d=").append(durationSec)
    }
    return params.toString()
}

/**
 * TTML -> OnlineLyrics. Si TODAS las líneas tienen tiempo, LRC
 * sincronizado (con palabras); si alguna no lo tiene, texto plano
 * con todas las líneas en orden (sin inventar tiempos).
 */
fun ttmlToOnlineLyrics(ttml: String): OnlineLyrics? {
    val lines = TtmlParser.parse(ttml)
    if (lines.isEmpty()) return null
    return if (lines.all { it.timeMs != Long.MAX_VALUE }) {
        OnlineLyrics(syncedLrc = lrcLinesToText(lines), plainText = null)
    } else {
        OnlineLyrics(
            syncedLrc = null,
            plainText = lines.joinToString("\n") { it.text },
        )
    }
}

class BetterLyricsProvider : LyricsProvider {
    override val name: String = "BetterLyrics"

    override suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): OnlineLyrics? = withContext(Dispatchers.IO) {
        val urls = listOfNotNull(
            buildBetterLyricsUrl(track, artist, album, durationSec, true, true),
            buildBetterLyricsUrl(track, artist, album, durationSec, false, true),
            buildBetterLyricsUrl(track, artist, album, durationSec, false, false),
        ).distinct()
        for (url in urls) {
            val body = httpGet(url) ?: continue
            val lyrics = ttmlToOnlineLyrics(body) ?: continue
            return@withContext lyrics
        }
        null
    }

    private fun httpGet(url: String): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
            conn.setRequestProperty("Accept", "application/xml")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
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
}
