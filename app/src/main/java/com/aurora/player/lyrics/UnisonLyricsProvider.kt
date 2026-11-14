package com.aurora.player.lyrics

import com.aurora.player.online.JsonParser
import com.aurora.player.online.arr
import com.aurora.player.online.bool
import com.aurora.player.online.obj
import com.aurora.player.online.str
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unison (2026-11-11, pedido del usuario: más proveedores de letras).
 * Espejo comunitario de las letras de Apple Music en TTML (tiempo por
 * palabra), sin clave y sin login:
 *   GET https://unison.boidu.dev/lyrics/search?song={título}&artist={artista}
 *   -> {"success":true,"data":[{"song","artist","album","lyrics":"<tt ...>...</tt>"}]}
 *
 * Verificado en vivo 2026-11-11 (Coldplay "Yellow"): success:true,
 * TTML con itunes:timing="Word", álbum Parachutes. Los parámetros son
 * `song` + `artist` (con `title` da 400 MISSING_QUERY); también
 * acepta `q` (difuso) y `v` (videoId). El TTML se convierte a LRC
 * con ttmlToOnlineLyrics, como BetterLyrics/BiniLyrics.
 *
 * La coincidencia se comprueba como en BiniLyrics (título+artista);
 * la respuesta no trae duración, así que no se filtra por ella.
 */

data class UnisonResult(
    val song: String,
    val artist: String,
    val lyricsTtml: String,
)

/** URL de búsqueda de Unison (parámetros song + artist). */
fun buildUnisonSearchUrl(track: String, artist: String): String =
    "https://unison.boidu.dev/lyrics/search?song=" +
        URLEncoder.encode(track.trim(), "UTF-8").replace("+", "%20") +
        "&artist=" + URLEncoder.encode(artist.trim(), "UTF-8").replace("+", "%20")

/** Parsea la búsqueda de Unison a la lista de candidatos (vacía si success es falso o el JSON está roto). */
fun parseUnisonSearchResponse(json: String): List<UnisonResult> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    if (root.obj("success")?.bool() != true) return emptyList()
    val items = root.obj("data")?.arr() ?: return emptyList()
    return items.mapNotNull { item ->
        val ttml = item.obj("lyrics")?.str().orEmpty()
        if (ttml.isBlank()) return@mapNotNull null
        UnisonResult(
            song = item.obj("song")?.str().orEmpty(),
            artist = item.obj("artist")?.str().orEmpty(),
            lyricsTtml = ttml,
        )
    }
}

/** Mejor candidato: título+artista coinciden (contiene en cualquier sentido). */
fun pickUnisonResult(
    results: List<UnisonResult>,
    track: String,
    artist: String,
): UnisonResult? {
    val wantedTrack = normalizeForMatch(track)
    val wantedArtist = normalizeForMatch(artist)
    for (candidate in results) {
        val candTrack = normalizeForMatch(candidate.song)
        val candArtist = normalizeForMatch(candidate.artist)
        // El título solo vale si es IGUAL o si lo pedido lo CONTIENE
        // (el candidato es la versión corta de lo pedido, p. ej.
        // "Yellow" dentro de "Yellow (Remastered)"). Al revés NO:
        // un título corto ("Yellow") es prefijo de otra canción
        // ("Yellow Submarine") y Unison no devuelve duración para
        // desambiguar, así que esa dirección devolvería la letra
        // de otra canción. (Run #722: "pick descarta otra cancion".)
        val trackOk = candTrack == wantedTrack || wantedTrack.contains(candTrack)
        val artistOk = candArtist == wantedArtist ||
            candArtist.contains(wantedArtist) || wantedArtist.contains(candArtist)
        if (trackOk && artistOk) return candidate
    }
    return null
}

class UnisonLyricsProvider : LyricsProvider {
    override val name: String = "Unison"

    override suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): OnlineLyrics? = withContext(Dispatchers.IO) {
        val searchBody = httpGet(buildUnisonSearchUrl(track, artist)) ?: return@withContext null
        val best = pickUnisonResult(
            parseUnisonSearchResponse(searchBody),
            track,
            artist,
        ) ?: return@withContext null
        ttmlToOnlineLyrics(best.lyricsTtml)
    }

    // RONDA 153: un error de red NO es "sin letra": se propaga (el
    // que busca cuenta los fallos) y solo un 404 devuelve null.
    // RONDA 2 (2026-11-13, P1, n=911): igual que LRCLIB (n=905): un
    // 429 (límite) o un 5xx (servidor caído) se propaga como
    // LyricsProviderHttpException.
    private fun httpGet(url: String): String? {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
        conn.setRequestProperty("Accept", "application/json")
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
