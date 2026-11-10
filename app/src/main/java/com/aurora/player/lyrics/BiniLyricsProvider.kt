package com.aurora.player.lyrics

import com.aurora.player.online.JsonParser
import java.net.URI
import java.net.URLEncoder
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BiniLyrics (Fase 2, proveedores múltiples — usuario 2026-11-09).
 * API pública sin clave, formato verificado en el código fuente del
 * plugin de Navidrome (ranokay/navidrome-better-lyrics-plugin,
 * internal/source/binimum.go):
 *   1) GET https://lyrics-api.binimum.org/?track={título}&artist={artista}&duration={seg}&album={álbum}
 *      -> JSON {results: [{track_name, artist_name, duration, lyricsUrl, ...}]}
 *   2) se elige el mejor candidato (título+artista y duración a ≤2 s)
 *   3) GET {lyricsUrl} -> TTML (palabra a palabra, formato Apple Music)
 *
 * La URL de la letra viene del proveedor, así que NO se sigue a
 * ciegas: solo HTTPS y solo los hosts lrc.red y lyrics-api.binimum.org
 * (misma regla que el plugin). El TTML se convierte a LRC con
 * ttmlToOnlineLyrics, como BetterLyrics.
 */

data class BinimumResult(
    val trackName: String,
    val artistName: String,
    val durationSec: Double,
    val lyricsUrl: String,
)

/** URL de búsqueda, o null si faltan título/artista. */
fun buildBinimumSearchUrl(
    track: String,
    artist: String,
    album: String?,
    durationSec: Long,
): String? {
    if (track.isBlank() || artist.isBlank()) return null
    val params = StringBuilder("https://lyrics-api.binimum.org/?")
    params.append("track=").append(URLEncoder.encode(track.trim(), "UTF-8"))
    params.append("&artist=").append(URLEncoder.encode(artist.trim(), "UTF-8"))
    params.append("&duration=").append(durationSec.coerceAtLeast(0L))
    if (!album.isNullOrBlank()) {
        params.append("&album=").append(URLEncoder.encode(album.trim(), "UTF-8"))
    }
    return params.toString()
}

/** Solo HTTPS y solo los dos hosts que de verdad sirven la letra. */
fun isAllowedLyricsUrl(raw: String): Boolean {
    val uri = try {
        URI(raw.trim())
    } catch (_: Exception) {
        return false
    }
    if (uri.scheme != "https") return false
    return when (uri.host?.lowercase()) {
        "lrc.red", "lyrics-api.binimum.org" -> true
        else -> false
    }
}

fun normalizeForMatch(text: String): String =
    text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N} ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

/** Mejor candidato: título+artista coinciden y la duración no se desvía más de 2 s. */
fun pickBinimumResult(
    results: List<BinimumResult>,
    track: String,
    artist: String,
    durationSec: Long,
): BinimumResult? {
    val wantedTrack = normalizeForMatch(track)
    val wantedArtist = normalizeForMatch(artist)
    var best: BinimumResult? = null
    var bestDelta = Long.MAX_VALUE
    for (candidate in results) {
        if (!isAllowedLyricsUrl(candidate.lyricsUrl)) continue
        val candTrack = normalizeForMatch(candidate.trackName)
        val candArtist = normalizeForMatch(candidate.artistName)
        val trackOk = candTrack == wantedTrack ||
            candTrack.contains(wantedTrack) || wantedTrack.contains(candTrack)
        val artistOk = candArtist == wantedArtist ||
            candArtist.contains(wantedArtist) || wantedArtist.contains(candArtist)
        if (!trackOk || !artistOk) continue
        if (durationSec > 0 && candidate.durationSec > 0) {
            val delta = abs(candidate.durationSec.toLong() - durationSec)
            if (delta > 2) continue
            if (delta < bestDelta) {
                best = candidate
                bestDelta = delta
            }
        } else if (best == null) {
            best = candidate
        }
    }
    return best
}

/** Parsea el JSON de búsqueda a la lista de candidatos. */
fun parseBinimumSearch(json: String): List<BinimumResult> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val items = root.obj("results")?.arr() ?: return emptyList()
    return items.mapNotNull { item ->
        val url = item.obj("lyricsUrl")?.str() ?: return@mapNotNull null
        BinimumResult(
            trackName = item.obj("track_name")?.str().orEmpty(),
            artistName = item.obj("artist_name")?.str().orEmpty(),
            durationSec = item.obj("duration")?.double() ?: 0.0,
            lyricsUrl = url,
        )
    }
}

class BiniLyricsProvider : LyricsProvider {
    override val name: String = "BiniLyrics"

    override suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): OnlineLyrics? = withContext(Dispatchers.IO) {
        val searchUrl = buildBinimumSearchUrl(track, artist, album, durationSec)
            ?: return@withContext null
        val searchBody = httpGet(searchUrl, "application/json") ?: return@withContext null
        val best = pickBinimumResult(
            parseBinimumSearch(searchBody),
            track,
            artist,
            durationSec,
        ) ?: return@withContext null
        val ttml = httpGet(best.lyricsUrl, "application/xml") ?: return@withContext null
        ttmlToOnlineLyrics(ttml)
    }

    private fun httpGet(url: String, accept: String): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
            conn.setRequestProperty("Accept", accept)
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
