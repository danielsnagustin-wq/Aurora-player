package com.aurora.player.lyrics

import com.aurora.player.online.JsonParser
import com.aurora.player.online.arr
import com.aurora.player.online.obj
import com.aurora.player.online.str
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LiriQo (2026-11-11, pedido del usuario: más proveedores de letras).
 * Agregador sin clave que consulta 11 fuentes (NetEase, QQ, Kugou,
 * Kuwo, Migu, PetitLyrics, Apple, Unison, LRCLib, YTM) y devuelve la
 * mejor en una sola llamada:
 *   GET https://api.liriqo-alfarrizi.my.id/v1/lyrics?title={título}&artist={artista}&duration={seg}
 *   -> {"matchScore":1,"primary":{"title","artist","lrc","plain","ttml","syncLevel",...},"tracks":[...]}
 *
 * Verificado en vivo 2026-11-11 (Coldplay "Yellow", duración 266):
 * matchScore 1, primary con lrc completo (etiquetas de palabra
 * <mm:ss.xx>, syncLevel "syllable") + plain + ttml. Los endpoints
 * /lrc y /plain exigen `v` (videoId), así que se usa /lyrics, que
 * acepta título+artista. Servicio nuevo (sep 2026, un solo
 * operador): va como respaldo, no como única fuente nueva.
 *
 * La coincidencia se comprueba como en BiniLyrics (título+artista).
 * Se prefiere el campo `lrc`; sin él, el `ttml` (vía
 * ttmlToOnlineLyrics); sin él, el texto plano.
 */

data class LiriqoTrack(
    val title: String,
    val artist: String,
    val lrc: String?,
    val plain: String?,
    val ttml: String?,
)

/** URL de LiriQo (título+artista; duración solo si se conoce). */
fun buildLiriqoUrl(track: String, artist: String, durationSec: Long): String {
    val params = StringBuilder("https://api.liriqo-alfarrizi.my.id/v1/lyrics?")
    params.append("title=").append(URLEncoder.encode(track.trim(), "UTF-8").replace("+", "%20"))
    params.append("&artist=").append(URLEncoder.encode(artist.trim(), "UTF-8").replace("+", "%20"))
    if (durationSec > 0) params.append("&duration=").append(durationSec)
    return params.toString()
}

/** Parsea la respuesta de LiriQo: la pista `primary` (o la primera de `tracks`). */
fun parseLiriqoResponse(json: String): LiriqoTrack? {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return null
    }
    val primary = root.obj("primary")
        ?: root.obj("tracks")?.arr()?.firstOrNull()
        ?: return null
    return LiriqoTrack(
        title = primary.obj("title")?.str().orEmpty(),
        artist = primary.obj("artist")?.str().orEmpty(),
        lrc = primary.obj("lrc")?.str()?.takeIf { it.isNotBlank() },
        plain = primary.obj("plain")?.str()?.takeIf { it.isNotBlank() },
        ttml = primary.obj("ttml")?.str()?.takeIf { it.isNotBlank() },
    )
}

/** La pista coincide con lo pedido (título+artista, contiene en cualquier sentido). */
fun liriqoMatches(track: LiriqoTrack, wantedTrack: String, wantedArtist: String): Boolean {
    val candTrack = normalizeForMatch(track.title)
    val candArtist = normalizeForMatch(track.artist)
    val wantTrack = normalizeForMatch(wantedTrack)
    val wantArtist = normalizeForMatch(wantedArtist)
    val trackOk = candTrack == wantTrack ||
        candTrack.contains(wantTrack) || wantTrack.contains(candTrack)
    val artistOk = candArtist == wantArtist ||
        candArtist.contains(wantArtist) || wantArtist.contains(candArtist)
    return trackOk && artistOk
}

/** La pista a OnlineLyrics: lrc primero, luego ttml, luego texto plano. */
fun liriqoToOnlineLyrics(track: LiriqoTrack): OnlineLyrics? {
    if (!track.lrc.isNullOrBlank()) return OnlineLyrics(syncedLrc = track.lrc, plainText = null)
    if (!track.ttml.isNullOrBlank()) return ttmlToOnlineLyrics(track.ttml)
    if (!track.plain.isNullOrBlank()) return OnlineLyrics(syncedLrc = null, plainText = track.plain)
    return null
}

class LiriqoLyricsProvider : LyricsProvider {
    override val name: String = "LiriQo"

    override suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): OnlineLyrics? = withContext(Dispatchers.IO) {
        val body = httpGet(buildLiriqoUrl(track, artist, durationSec)) ?: return@withContext null
        val found = parseLiriqoResponse(body) ?: return@withContext null
        if (!liriqoMatches(found, track, artist)) return@withContext null
        liriqoToOnlineLyrics(found)
    }

    // RONDA 153: un error de red NO es "sin letra": se propaga (el
    // que busca cuenta los fallos) y solo un código distinto de 200
    // devuelve null.
    private fun httpGet(url: String): String? {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        try {
            if (conn.responseCode != 200) return null
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
