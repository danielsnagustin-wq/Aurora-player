package com.aurora.player.lyrics

import android.content.Context
import com.aurora.player.util.LrcLine
import com.aurora.player.util.LrcParser
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RONDA 7 P2: letras online — registro de proveedores con fallback.
 * Ideas de Metrolist (registro de proveedores), código propio.
 *
 * Orden de búsqueda: 1) el .lrc local (siempre primero, nunca se
 * toca la red si existe); 2) caché de letras ya buscadas; 3) LRCLIB
 * (gratis, sin clave); 4) búsqueda difusa de LRCLIB. La letra que
 * llega se valida con el LrcParser antes de mostrarse: si los
 * tiempos no cuadran, no se muestra nada inventado.
 *
 * La red SOLO se usa si el usuario encendió "Letras online" en
 * Ajustes (apagado por defecto). Con el interruptor apagado, este
 * archivo no se ejecuta jamás.
 */
data class OnlineLyrics(val syncedLrc: String?, val plainText: String?)

interface LyricsProvider {
    val name: String
    suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): OnlineLyrics?
}

/** URL de coincidencia exacta de LRCLIB (título + artista + álbum + duración). */
fun buildLrclibGetUrl(track: String, artist: String, album: String?, durationSec: Long): String {
    val params = StringBuilder("https://lrclib.net/api/get?")
    params.append("track_name=").append(URLEncoder.encode(track, "UTF-8"))
    params.append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
    if (!album.isNullOrBlank()) {
        params.append("&album_name=").append(URLEncoder.encode(album, "UTF-8"))
    }
    if (durationSec > 0) params.append("&duration=").append(durationSec)
    return params.toString()
}

/** URL de búsqueda difusa de LRCLIB (fallback si la exacta da 404). */
fun buildLrclibSearchUrl(query: String): String =
    "https://lrclib.net/api/search?q=" + URLEncoder.encode(query, "UTF-8")

/**
 * Extrae el valor string de una clave JSON de primer nivel (o null
 * si es null, no es string o no existe). Sin librerías: org.json es
 * parte de Android y en los unit tests sus métodos son stubs que
 * lanzan; este extractor es Kotlin puro y testeable.
 */
fun extractJsonString(json: String, key: String): String? {
    val keyToken = "\"$key\""
    var idx = json.indexOf(keyToken)
    if (idx < 0) return null
    idx = json.indexOf(':', idx + keyToken.length)
    if (idx < 0) return null
    idx++
    while (idx < json.length && json[idx].isWhitespace()) idx++
    if (idx + 4 <= json.length && json.startsWith("null", idx)) return null
    if (idx >= json.length || json[idx] != '"') return null
    val sb = StringBuilder()
    idx++
    while (idx < json.length) {
        val ch = json[idx]
        when {
            ch == '"' -> return sb.toString()
            ch == '\\' && idx + 1 < json.length -> {
                when (val e = json[idx + 1]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        if (idx + 5 < json.length) {
                            sb.append(json.substring(idx + 2, idx + 6).toIntOrNull(16)?.toChar() ?: '?')
                            idx += 4
                        }
                    }
                    else -> sb.append(e)
                }
                idx += 2
            }
            else -> {
                sb.append(ch)
                idx++
            }
        }
    }
    return null // cadena sin cerrar
}

/** Parsea una respuesta de LRCLIB (acepta camelCase y snake_case). */
fun parseLrclibResponse(json: String): OnlineLyrics? {
    val synced = extractJsonString(json, "syncedLyrics")
        ?: extractJsonString(json, "synced_lyrics")
    val plain = extractJsonString(json, "plainLyrics")
        ?: extractJsonString(json, "plain_lyrics")
    if (synced.isNullOrBlank() && plain.isNullOrBlank()) return null
    return OnlineLyrics(
        syncedLrc = synced?.takeIf { it.isNotBlank() },
        plainText = plain?.takeIf { it.isNotBlank() },
    )
}

/** Letra sin tiempos → líneas sin sincronizar (se muestran sin resaltar). */
fun plainToLrcLines(plainText: String): List<LrcLine> =
    plainText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { LrcLine(Long.MAX_VALUE, it) }
        .toList()

/**
 * Resuelve la letra con prioridad honesta: .lrc local primero; si no
 * hay, la online sincronizada (validada con el parser); si tampoco,
 * la online sin tiempos. Nunca mezcla letras de otra canción.
 */
fun resolveLyrics(localLrcText: String?, online: OnlineLyrics?): List<LrcLine> {
    if (!localLrcText.isNullOrBlank()) {
        val parsed = LrcParser.parse(localLrcText)
        if (parsed.isNotEmpty()) return parsed
    }
    val synced = online?.syncedLrc
    if (!synced.isNullOrBlank()) {
        val parsed = LrcParser.parse(synced)
        if (parsed.isNotEmpty()) return parsed
    }
    val plain = online?.plainText
    if (!plain.isNullOrBlank()) return plainToLrcLines(plain)
    return emptyList()
}

/** Prueba los proveedores en orden; un proveedor caído no tumba la búsqueda. */
suspend fun fetchLyricsFirst(
    providers: List<LyricsProvider>,
    track: String,
    artist: String,
    album: String?,
    durationSec: Long,
): OnlineLyrics? {
    for (provider in providers) {
        try {
            val result = provider.fetch(track, artist, album, durationSec)
            if (result != null &&
                (!result.syncedLrc.isNullOrBlank() || !result.plainText.isNullOrBlank())
            ) {
                return result
            }
        } catch (_: Exception) {
            // Siguiente proveedor.
        }
    }
    return null
}

/** LRCLIB: coincidencia exacta primero, búsqueda difusa si da 404. */
class LrclibProvider : LyricsProvider {
    override val name: String = "LRCLIB"

    override suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): OnlineLyrics? = withContext(Dispatchers.IO) {
        val exact = httpGet(buildLrclibGetUrl(track, artist, album, durationSec))
        val parsed = exact?.let { parseLrclibResponse(it) }
        if (parsed != null) return@withContext parsed
        val search = httpGet(buildLrclibSearchUrl("$track $artist"))
        search?.let { parseLrclibResponse(it) }
    }

    private fun httpGet(url: String): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
            conn.setRequestProperty("Accept", "application/json")
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

/**
 * Busca la letra online de una canción (caché primero, luego
 * proveedores). Devuelve las líneas listas para mostrar, o vacío.
 * Solo se llama con el interruptor de letras online encendido.
 */
suspend fun loadOnlineLyrics(
    context: Context,
    cacheKey: String,
    track: String,
    artist: String,
    album: String?,
    durationSec: Long,
    providers: List<LyricsProvider> = listOf(LrclibProvider()),
): List<LrcLine> = withContext(Dispatchers.IO) {
    LyricsCache.read(context, cacheKey)?.let { cached ->
        if (cached.isNotBlank()) {
            val parsed = LrcParser.parse(cached)
            if (parsed.isNotEmpty()) return@withContext parsed
            return@withContext plainToLrcLines(cached)
        }
    }
    val found = fetchLyricsFirst(providers, track, artist, album, durationSec)
        ?: return@withContext emptyList()
    val synced = found.syncedLrc
    if (!synced.isNullOrBlank()) {
        val parsed = LrcParser.parse(synced)
        if (parsed.isNotEmpty()) {
            LyricsCache.write(context, cacheKey, synced)
            return@withContext parsed
        }
    }
    val plain = found.plainText
    if (!plain.isNullOrBlank()) {
        LyricsCache.write(context, cacheKey, plain)
        return@withContext plainToLrcLines(plain)
    }
    emptyList()
}
