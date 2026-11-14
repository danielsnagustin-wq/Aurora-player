package com.aurora.player.lyrics

import com.aurora.player.online.JsonParser
import com.aurora.player.online.arr
import com.aurora.player.online.long
import com.aurora.player.online.obj
import com.aurora.player.online.str
import java.net.URLEncoder
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * QQ Music (2026-11-11, pedido del usuario: más proveedores de letras).
 * Sin clave, dos pasos:
 *   1) GET https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=1&n=5&w={artista+título}
 *      -> data.song.list[] {songmid, songname, singer:[{name}], interval}
 *   2) GET https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?format=json&nobase64=1&type=1&songmid={songmid}
 *      -> JSONP MusicJsonCallback({retcode, lyric, trans}) con el LRC sincronizado.
 *
 * Verificado en vivo 2026-11-11 (Coldplay "Yellow", songmid 000Z74wD1h9F10):
 * sin Referer el paso 2 devuelve retcode -1310; CON
 * `Referer: https://y.qq.com/portal/player.html` devuelve retcode 0
 * con el LRC completo ("[00:33.58]Look at the stars…"). Por eso este
 * proveedor lleva su propio httpGet con ese header (los demás
 * proveedores no ponen Referer). La búsqueda también lo lleva, como
 * hacen los clientes oficiales.
 *
 * La coincidencia se comprueba como en BiniLyrics (título+artista,
 * duración a ≤2 s): la búsqueda de QQ es difusa y la primera
 * coincidencia no siempre es la canción pedida.
 */

data class QqSearchResult(
    val songmid: String,
    val songName: String,
    val singerName: String,
    val durationSec: Long,
)

/** URL de búsqueda de QQ (artista + título, como en la prueba en vivo). */
fun buildQqSearchUrl(artist: String, track: String): String =
    "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=1&n=5&w=" +
        URLEncoder.encode("$artist $track".trim(), "UTF-8").replace("+", "%20")

/** URL de la letra de QQ (LRC sincronizado, nobase64=1 = texto plano). */
fun buildQqLyricUrl(songmid: String): String =
    "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
        "?format=json&nobase64=1&type=1&songmid=" + songmid

/** Quita la envoltura JSONP MusicJsonCallback({...}) — o devuelve el texto tal cual. */
fun stripJsonpCallback(body: String): String {
    val start = body.indexOf('(')
    val end = body.lastIndexOf(')')
    if (start < 0 || end <= start) return body
    return body.substring(start + 1, end)
}

/** Parsea la búsqueda de QQ a la lista de candidatos (vacía si el JSON está roto). */
fun parseQqSearchResponse(json: String): List<QqSearchResult> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val items = root.obj("data")?.obj("song")?.obj("list")?.arr() ?: return emptyList()
    return items.mapNotNull { item ->
        val songmid = item.obj("songmid")?.str().orEmpty()
        if (songmid.isBlank()) return@mapNotNull null
        QqSearchResult(
            songmid = songmid,
            songName = item.obj("songname")?.str().orEmpty(),
            singerName = item.obj("singer")?.arr()?.firstOrNull()
                ?.obj("name")?.str().orEmpty(),
            durationSec = item.obj("interval")?.long() ?: 0L,
        )
    }
}

/** Mejor candidato: título+artista coinciden y la duración no se desvía más de 2 s. */
fun pickQqResult(
    results: List<QqSearchResult>,
    track: String,
    artist: String,
    durationSec: Long,
): QqSearchResult? {
    val wantedTrack = normalizeForMatch(track)
    val wantedArtist = normalizeForMatch(artist)
    var best: QqSearchResult? = null
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
 * Parsea la letra de QQ (con o sin envoltura JSONP). retcode != 0
 * es "sin letra" (p. ej. -1310 sin Referer); el LRC trae etiquetas
 * [ti:]/[ar:]/[al:] que el LrcParser ya ignora.
 */
fun parseQqLyricResponse(body: String): OnlineLyrics? {
    val root = try {
        JsonParser.parse(stripJsonpCallback(body))
    } catch (_: Exception) {
        return null
    }
    val retcode = root.obj("retcode")?.long() ?: return null
    if (retcode != 0L) return null
    val lyric = root.obj("lyric")?.str()?.takeIf { it.isNotBlank() } ?: return null
    return OnlineLyrics(syncedLrc = lyric, plainText = null)
}

class QqLyricsProvider : LyricsProvider {
    override val name: String = "QQMusic"

    override suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): OnlineLyrics? = withContext(Dispatchers.IO) {
        val searchBody = httpGet(buildQqSearchUrl(artist, track)) ?: return@withContext null
        val best = pickQqResult(
            parseQqSearchResponse(searchBody),
            track,
            artist,
            durationSec,
        ) ?: return@withContext null
        val lyricBody = httpGet(buildQqLyricUrl(best.songmid)) ?: return@withContext null
        parseQqLyricResponse(lyricBody)
    }

    // RONDA 153: un error de red NO es "sin letra": se propaga (el
    // que busca cuenta los fallos) y solo un 404 devuelve null.
    // RONDA 2 (2026-11-13, P1, n=910): igual que LRCLIB (n=905): un
    // 429 (límite) o un 5xx (servidor caído) se propaga como
    // LyricsProviderHttpException. El Referer es OBLIGATORIO para QQ (sin él, -1310).
    private fun httpGet(url: String): String? {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Referer", "https://y.qq.com/portal/player.html")
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
