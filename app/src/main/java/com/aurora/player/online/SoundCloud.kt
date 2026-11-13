package com.aurora.player.online

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cliente SoundCloud propio, escrito desde cero (clean-room).
 *
 * No usa NewPipe Extractor ni ningún código GPL: solo llama a la web pública
 * de SoundCloud y a sus endpoints api-v2, igual que un navegador. La llave
 * que usa es el client_id web público, incrustado en el JavaScript que
 * SoundCloud sirve a cualquier visitante; se vuelve a sacar solo cuando rota.
 *
 * Cadena (verificada en vivo 2026-11-13):
 * 1. GET soundcloud.com/ -> HTML con bundles a-v2.sndcdn.com/assets/N-hash.js
 * 2. En un bundle vive client_id:"<32 caracteres hex>"
 * 3. GET api-v2.soundcloud.com/search/tracks?q=...&client_id=... -> canciones
 * 4. GET api-v2.soundcloud.com/tracks/{id}?client_id=... -> transcodings HLS
 * 5. GET <transcoding.url>?client_id=... -> {"url": "<HLS m3u8>"}
 *
 * Frágil por naturaleza: el client_id rota, hay rate limits (429) y las
 * pistas solo-DRM no se pueden reproducir. Solo se llama con el modo online
 * encendido; el llamador garantiza el onlineMode, como con iTunes/Last.fm.
 */

/** Una canción de SoundCloud. */
data class SoundCloudTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val permalinkUrl: String?
)

/** Una forma de reproducir una canción (normalmente HLS AAC). */
data class SoundCloudTranscoding(
    val url: String,
    val preset: String?,
    val protocol: String?,
    val mimeType: String?
)

private const val SOUNDCLOUD_WEB_URL = "https://soundcloud.com/"
private const val SOUNDCLOUD_API_V2_URL = "https://api-v2.soundcloud.com"
private const val SOUNDCLOUD_API_UA = "AuroraPlayer/0.1"

/** UA de navegador: la web de SoundCloud sirve sus JS solo como a un navegador. */
private const val SOUNDCLOUD_WEB_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

private val jsBundleRegex =
    Regex("""https://a-v2\.sndcdn\.com/assets/[0-9]+-[A-Za-z0-9]+\.js""")
private val webClientIdRegex =
    Regex("""client_id["']?\s*:\s*["']([A-Za-z0-9]{32})["']""")

/** client_id web en memoria; se vuelve a sacar cuando rota o falla. */
@Volatile
private var cachedWebClientId: String? = null

/**
 * Saca las URLs de los bundles JS de SoundCloud del HTML de su página.
 * Sin duplicados, en el orden en que aparecen.
 */
fun extractJsBundleUrls(html: String): List<String> =
    jsBundleRegex.findAll(html).map { it.value }.distinct().toList()

/**
 * Saca el client_id web (32 caracteres hex) de un bundle JS.
 * Null si el JS no trae la llave.
 */
fun extractWebClientId(js: String): String? =
    webClientIdRegex.find(js)?.groupValues?.getOrNull(1)

/** URL de búsqueda de api-v2. */
fun buildSoundCloudSearchUrl(query: String, clientId: String, limit: Int = 25): String {
    val encoded = URLEncoder.encode(query, "UTF-8")
    return "$SOUNDCLOUD_API_V2_URL/search/tracks?q=$encoded&client_id=$clientId&limit=$limit"
}

/** URL de detalle de una canción en api-v2. */
fun buildSoundCloudTrackUrl(trackId: Long, clientId: String): String =
    "$SOUNDCLOUD_API_V2_URL/tracks/$trackId?client_id=$clientId"

/**
 * Convierte una portada -large.jpg (100x100) a -t500x500.jpg (500x500).
 * Cualquier otra URL se devuelve tal cual.
 */
fun soundCloudArtwork500(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return if (url.endsWith("-large.jpg")) {
        url.dropLast("-large.jpg".length) + "-t500x500.jpg"
    } else {
        url
    }
}

/**
 * Parsea una respuesta de búsqueda de api-v2 a canciones.
 * Descarta las no reproducibles (streamable=false) y las sin título/artista.
 * JSON roto -> lista vacía.
 */
fun parseSoundCloudSearch(json: String): List<SoundCloudTrack> {
    return try {
        val root = JsonParser.parse(json)
        val collection = root.obj("collection")?.arr() ?: return emptyList()
        collection.mapNotNull { item ->
            if (item?.obj("streamable")?.bool() == false) return@mapNotNull null
            val id = item?.obj("id")?.long() ?: return@mapNotNull null
            val title = item.obj("title")?.str() ?: return@mapNotNull null
            val artist = item.obj("user")?.obj("username")?.str() ?: return@mapNotNull null
            val durationMs = item.obj("duration")?.long() ?: 0L
            val artworkUrl = item.obj("artwork_url")?.str()
            val permalinkUrl = item.obj("permalink_url")?.str()
            SoundCloudTrack(
                id = id,
                title = title,
                artist = artist,
                artworkUrl = artworkUrl,
                durationMs = durationMs,
                permalinkUrl = permalinkUrl
            )
        }
    } catch (e: JsonParseException) {
        emptyList()
    }
}

/**
 * Parsea los transcodings de un detalle de canción.
 * JSON roto -> lista vacía.
 */
fun parseSoundCloudTranscodings(json: String): List<SoundCloudTranscoding> {
    return try {
        val root = JsonParser.parse(json)
        val transcodings = root.obj("media")?.obj("transcodings")?.arr() ?: return emptyList()
        transcodings.mapNotNull { item ->
            val url = item?.obj("url")?.str() ?: return@mapNotNull null
            val preset = item.obj("preset")?.str()
            val protocol = item.obj("format")?.obj("protocol")?.str()
            val mimeType = item.obj("format")?.obj("mime_type")?.str()
            SoundCloudTranscoding(
                url = url,
                preset = preset,
                protocol = protocol,
                mimeType = mimeType
            )
        }
    } catch (e: JsonParseException) {
        emptyList()
    }
}

/**
 * Elige el mejor transcoding: HLS aac_160k, luego HLS aac_96k,
 * luego cualquier HLS, y si no hay HLS, el primero que haya.
 * Null si la lista está vacía.
 */
fun pickSoundCloudTranscoding(
    transcodings: List<SoundCloudTranscoding>
): SoundCloudTranscoding? {
    if (transcodings.isEmpty()) return null
    transcodings.firstOrNull { it.protocol == "hls" && it.preset == "aac_160k" }?.let { return it }
    transcodings.firstOrNull { it.protocol == "hls" && it.preset == "aac_96k" }?.let { return it }
    transcodings.firstOrNull { it.protocol == "hls" }?.let { return it }
    return transcodings.first()
}

/**
 * Parsea la respuesta {"url": "..."} de un transcoding a la URL real del audio.
 * JSON roto o sin url -> null.
 */
fun parseSoundCloudStreamUrl(json: String): String? {
    return try {
        JsonParser.parse(json).obj("url")?.str()
    } catch (e: JsonParseException) {
        null
    }
}

/**
 * GET de texto con timeouts y desconexión en finally.
 * Null en respuestas no-200. IOException propaga al llamador.
 * CancellationException se relanza sin tocarla.
 */
private suspend fun soundCloudHttpGet(url: String, userAgent: String): String? {
    return withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", userAgent)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: CancellationException) {
            throw e
        } finally {
            conn?.disconnect()
        }
    }
}

/**
 * Saca el client_id web público de SoundCloud (de sus propios JS).
 * Lo guarda en memoria; con forceRefresh=true lo vuelve a sacar.
 * Null si la web no responde o ningún bundle trae la llave.
 */
suspend fun fetchSoundCloudWebClientId(forceRefresh: Boolean = false): String? {
    if (!forceRefresh) cachedWebClientId?.let { return it }
    val home = soundCloudHttpGet(SOUNDCLOUD_WEB_URL, SOUNDCLOUD_WEB_UA) ?: return null
    for (bundleUrl in extractJsBundleUrls(home).take(12)) {
        val js = soundCloudHttpGet(bundleUrl, SOUNDCLOUD_WEB_UA) ?: continue
        val id = extractWebClientId(js) ?: continue
        cachedWebClientId = id
        return id
    }
    return null
}

/**
 * Busca canciones en SoundCloud.
 * Null en respuestas no-200 o JSON roto. IOException propaga al llamador.
 */
suspend fun searchSoundCloud(
    query: String,
    clientId: String,
    limit: Int = 25
): List<SoundCloudTrack>? {
    val url = buildSoundCloudSearchUrl(query, clientId, limit)
    val text = soundCloudHttpGet(url, SOUNDCLOUD_API_UA) ?: return null
    return try {
        parseSoundCloudSearch(text)
    } catch (e: JsonParseException) {
        null
    }
}

/**
 * Resuelve la URL real de audio (HLS) de una canción.
 * Null si no hay transcodings, si la pista no se puede reproducir
 * o si alguna respuesta falla. IOException propaga al llamador.
 */
suspend fun resolveSoundCloudStreamUrl(trackId: Long, clientId: String): String? {
    val trackText = soundCloudHttpGet(buildSoundCloudTrackUrl(trackId, clientId), SOUNDCLOUD_API_UA)
        ?: return null
    val transcodings = try {
        parseSoundCloudTranscodings(trackText)
    } catch (e: JsonParseException) {
        return null
    }
    val picked = pickSoundCloudTranscoding(transcodings) ?: return null
    val separator = if (picked.url.contains("?")) "&" else "?"
    val streamText = soundCloudHttpGet(
        picked.url + separator + "client_id=" + clientId,
        SOUNDCLOUD_API_UA
    ) ?: return null
    return try {
        parseSoundCloudStreamUrl(streamText)
    } catch (e: JsonParseException) {
        null
    }
}
