package com.aurora.player.lyrics

import com.aurora.player.online.JsonParser
import com.aurora.player.online.arr
import com.aurora.player.online.long
import com.aurora.player.online.obj
import com.aurora.player.online.str
import java.net.URLEncoder
import java.util.Base64
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kugou (2026-11-11, pedido del usuario: más proveedores de letras).
 * Sin clave, tres pasos (parámetros exactos leídos del fuente
 * music-lib/kugou/lyric.go, master):
 *   1) GET https://songsearch.kugou.com/song_search_v2?keyword={q}&page=1&pagesize=5&...
 *      -> data.lists[] {FileHash, SongName, SingerName, AlbumName, Duration}
 *   2) GET https://krcs.kugou.com/search?ver=1&client=mobi&duration={ms}&hash={FileHash}&album_audio_id=
 *      -> {status, candidates:[{id, accesskey, song}]}
 *   3) GET https://lyrics.kugou.com/download?ver=1&client=pc&id={id}&accesskey={accesskey}&fmt=lrc&charset=utf8
 *      -> {status, content (Base64), fmt, contenttype}
 *
 * Notas honestas:
 * - El paso 1 en vivo (2026-11-11) devolvió status 404 sin
 *   candidatos para el FileHash de "Yellow" (4 variantes probadas);
 *   puede ser el hash, la falta de Cookie o que esa canción no tenga
 *   letra en Kugou. Los parámetros son los del fuente; si no hay
 *   candidatos, este proveedor devuelve null y la carrera sigue.
 * - Se pide fmt=lrc para evitar el decodificador KRC (formato
 *   propio comprimido); si el servidor devuelve KRC igualmente
 *   (fmt != "lrc" y contenttype != 2), se devuelve null en esta
 *   versión, no se inventa nada.
 * - Las URLs van por HTTPS (krcs responde igual por HTTPS que por
 *   HTTP, verificado en vivo); el fuente original usa HTTP.
 */

data class KugouSearchResult(
    val fileHash: String,
    val songName: String,
    val singerName: String,
    val durationSec: Long,
)

data class KugouLyricCandidate(
    val id: String,
    val accessKey: String,
)

/** URL de búsqueda de canciones de Kugou (artista + título). */
fun buildKugouSearchUrl(artist: String, track: String): String =
    "https://songsearch.kugou.com/song_search_v2?keyword=" +
        URLEncoder.encode("$artist $track".trim(), "UTF-8").replace("+", "%20") +
        "&page=1&pagesize=5&userid=-1&clientver=&platform=WebFilter" +
        "&tag=em&filter=2&iscorrection=1&privilege_filter=0"

/** URL del paso 1 de letras (krcs): hash + duración en ms. */
fun buildKugouKrcsUrl(fileHash: String, durationSec: Long): String =
    "https://krcs.kugou.com/search?ver=1&client=mobi" +
        "&duration=" + (durationSec * 1000) +
        "&hash=" + fileHash + "&album_audio_id="

/** URL de descarga de la letra (se pide LRC, no KRC). */
fun buildKugouDownloadUrl(id: String, accessKey: String): String =
    "https://lyrics.kugou.com/download?ver=1&client=pc&id=" + id +
        "&accesskey=" + accessKey + "&fmt=lrc&charset=utf8"

/** Parsea la búsqueda de canciones a la lista de candidatos (vacía si el JSON está roto). */
fun parseKugouSearchResponse(json: String): List<KugouSearchResult> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val items = root.obj("data")?.obj("lists")?.arr() ?: return emptyList()
    return items.mapNotNull { item ->
        val hash = item.obj("FileHash")?.str().orEmpty()
        if (hash.isBlank()) return@mapNotNull null
        KugouSearchResult(
            fileHash = hash,
            songName = item.obj("SongName")?.str().orEmpty(),
            singerName = item.obj("SingerName")?.str().orEmpty(),
            durationSec = item.obj("Duration")?.long() ?: 0L,
        )
    }
}

/** Mejor candidato de la búsqueda: título+artista coinciden y la duración no se desvía más de 2 s. */
fun pickKugouResult(
    results: List<KugouSearchResult>,
    track: String,
    artist: String,
    durationSec: Long,
): KugouSearchResult? {
    val wantedTrack = normalizeForMatch(track)
    val wantedArtist = normalizeForMatch(artist)
    var best: KugouSearchResult? = null
    var bestDelta = Long.MAX_VALUE
    for (candidate in results) {
        val candTrack = normalizeForMatch(candidate.songName)
        val candArtist = normalizeForMatch(candidate.singerName)
        val trackOk = candTrack == wantedTrack ||
            candTrack.contains(wantedTrack) || wantedTrack.contains(candTrack)
        val artistOk = candArtist == wantedArtist ||
            candArtist.contains(wantedArtist) || wantedArtist.contains(candArtist)
        if (!trackOk || !artistOk) continue
        if (durationSec > 0 && candidate.durationSec > 0) {
            val delta = abs(candidate.durationSec - durationSec)
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

/**
 * Parsea el paso 1 (krcs): el primer candidato con id y accesskey.
 * El `id` viene como número o como texto según la respuesta.
 */
fun parseKugouKrcsResponse(json: String): KugouLyricCandidate? {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return null
    }
    val candidates = root.obj("candidates")?.arr() ?: return null
    for (item in candidates) {
        val id = item.obj("id")?.str()
            ?: item.obj("id")?.long()?.toString()
            ?: continue
        val key = item.obj("accesskey")?.str().orEmpty()
        if (id.isNotBlank() && key.isNotBlank()) return KugouLyricCandidate(id, key)
    }
    return null
}

/**
 * Parsea la descarga: solo LRC en Base64 (fmt "lrc" o contenttype 2).
 * KRC (comprimido) no se decodifica en esta versión: null.
 */
fun parseKugouDownloadResponse(json: String): OnlineLyrics? {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return null
    }
    val content = root.obj("content")?.str()?.takeIf { it.isNotBlank() } ?: return null
    val fmt = root.obj("fmt")?.str().orEmpty()
    val contentType = root.obj("contenttype")?.long() ?: -1L
    if (fmt != "lrc" && contentType != 2L) return null
    val decoded = try {
        String(Base64.getDecoder().decode(content), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (decoded.isBlank()) return null
    return OnlineLyrics(syncedLrc = decoded, plainText = null)
}

class KugouLyricsProvider : LyricsProvider {
    override val name: String = "Kugou"

    override suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): OnlineLyrics? = withContext(Dispatchers.IO) {
        val searchBody = httpGet(buildKugouSearchUrl(artist, track)) ?: return@withContext null
        val best = pickKugouResult(
            parseKugouSearchResponse(searchBody),
            track,
            artist,
            durationSec,
        ) ?: return@withContext null
        val krcsBody = httpGet(buildKugouKrcsUrl(best.fileHash, best.durationSec))
            ?: return@withContext null
        val candidate = parseKugouKrcsResponse(krcsBody) ?: return@withContext null
        val downloadBody = httpGet(buildKugouDownloadUrl(candidate.id, candidate.accessKey))
            ?: return@withContext null
        parseKugouDownloadResponse(downloadBody)
    }

    // RONDA 153: un error de red NO es "sin letra": se propaga (el
    // que busca cuenta los fallos) y solo un 404 devuelve null.
    // RONDA 2 (2026-11-13, P1, n=907): igual que LRCLIB (n=905): un
    // 429 (límite) o un 5xx (servidor caído) se propaga como
    // LyricsProviderHttpException. Referer de Kugou móvil, como en el fuente.
    private fun httpGet(url: String): String? {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Referer", "http://m.kugou.com")
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        try {
            return when (val code = conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }
                404 -> null
                429, in 500..599 -> throw LyricsProviderHttpException(code)
                else -> null
            }
        } finally {
            conn.disconnect()
        }
    }
}
