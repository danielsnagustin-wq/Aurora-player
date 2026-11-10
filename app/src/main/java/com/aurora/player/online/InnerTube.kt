package com.aurora.player.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RONDA 7 (002-online-youtube): cliente InnerTube propio para escuchar
 * de YouTube Music, sin copiar código de nadie (Metrolist/InnerTune son
 * GPL: solo la IDEA del mecanismo, verificada con documentación pública
 * — docs de MeeTube, LovelyMusic y el commit de Auqw-plugins, ene 2026).
 *
 * Cómo funciona: YouTube no da API oficial para reproducir música; sus
 * propias apps hablan con InnerTube (POST a youtubei/v1/<endpoint> con
 * un bloque `context.client` que dice qué cliente eres). La búsqueda va
 * con WEB_REMIX (YouTube Music web, clave pública verificada). El audio
 * se resuelve con el endpoint `player` probando clientes en orden hasta
 * que uno devuelva una URL directa: los formatos cifrados
 * (`signatureCipher`, que exigirían ejecutar el JavaScript ofuscado de
 * YouTube) NO se tocan. Las identidades de cliente son DATOS en la
 * tabla de abajo: si YouTube cambia una versión, se edita la tabla.
 *
 * Sin login en v1: solo contenido público. Sin descargas en v1. La URL
 * de audio caduca en horas: se resuelve al tocar, nunca se guarda.
 * Si YouTube cambia InnerTube, esto se rompe: la UI lo dice claro.
 */
data class OnlineTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSec: Long?,
    val thumbnailUrl: String?,
)

data class AudioFormat(
    val itag: Long,
    val mimeType: String,
    val url: String,
    val bitrate: Long,
)

/** Una identidad de cliente InnerTube: todo son datos, no lógica. */
data class YtClient(
    val name: String,
    val id: String,
    val version: String,
    /** Clave pública del cliente (null = sin clave, como yt-dlp/NewPipe). */
    val apiKey: String?,
    val userAgent: String,
    val host: String,
    val androidSdkVersion: Int? = null,
)

private const val WEB_REMIX_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
private const val ANDROID_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

/** Cliente de búsqueda: YouTube Music web (clave pública verificada). */
val SEARCH_CLIENT = YtClient(
    name = "WEB_REMIX",
    id = "67",
    version = "1.20260114.01.00",
    apiKey = WEB_REMIX_KEY,
    userAgent = DESKTOP_UA,
    host = "music.youtube.com",
)

/**
 * Escalera para resolver audio: se prueba en orden hasta que un
 * cliente devuelva URL directa. Versiones de ene 2026 (MeeTube,
 * Auqw-plugins, bedrock-api). Sin clave: la autenticación es el
 * bloque context.client + las cabeceras X-YouTube-Client-*.
 */
val PLAYER_CLIENTS = listOf(
    YtClient(
        name = "ANDROID_VR",
        id = "28",
        version = "1.61.48",
        apiKey = null,
        userAgent = ANDROID_UA,
        host = "www.youtube.com",
        androidSdkVersion = 34,
    ),
    YtClient(
        name = "IOS",
        id = "5",
        version = "20.10.4",
        apiKey = null,
        userAgent = ANDROID_UA,
        host = "www.youtube.com",
    ),
    YtClient(
        name = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        id = "85",
        version = "2.0",
        apiKey = null,
        userAgent = ANDROID_UA,
        host = "www.youtube.com",
    ),
    YtClient(
        name = "ANDROID",
        id = "3",
        version = "21.03.38",
        apiKey = null,
        userAgent = ANDROID_UA,
        host = "www.youtube.com",
        androidSdkVersion = 34,
    ),
)

/** Parámetro de búsqueda: solo canciones (filtro de YouTube Music). */
const val SEARCH_PARAMS_SONGS = "EgWKAQIIAWoQEAMQBBAJEAoQGAgAEEAMQBg%3D%3D"

/** Escapa un string para meterlo en JSON. Función pura, testeable. */
fun jsonEscape(value: String): String {
    val sb = StringBuilder()
    for (ch in value) {
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
        }
    }
    return sb.toString()
}

private fun clientContextJson(client: YtClient): String {
    val sdk = client.androidSdkVersion?.let { ",\"androidSdkVersion\":$it" } ?: ""
    return "\"client\":{\"clientName\":\"${client.name}\",\"clientVersion\":\"${client.version}\",\"hl\":\"es\",\"gl\":\"ES\"$sdk},\"user\":{}"
}

/** Cuerpo de la petición de búsqueda (WEB_REMIX, solo canciones). */
fun buildSearchBody(query: String): String =
    "{\"context\":{${clientContextJson(SEARCH_CLIENT)}},\"query\":\"${jsonEscape(query)}\",\"params\":\"$SEARCH_PARAMS_SONGS\"}"

/** Cuerpo de la petición de player para un cliente de la escalera. */
fun buildPlayerBody(client: YtClient, videoId: String): String =
    "{\"context\":{${clientContextJson(client)}},\"videoId\":\"${jsonEscape(videoId)}\"," +
        "\"playbackContext\":{\"contentPlaybackContext\":{\"vis\":0,\"splay\":false,\"autoCaptionsDefaultOn\":false,\"autonavState\":\"STATE_OFF\",\"html5Preference\":\"HTML5_PREF_WANTS\",\"lact\":0}}," +
        "\"contentCheckOk\":true,\"racyCheckOk\":true}"

/** "3:24" o "1:02:03" → segundos. Null si no es duración. */
fun parseDurationToSec(text: String): Long? {
    val parts = text.split(":")
    if (parts.size !in 2..3 || parts.any { it.isBlank() || it.any { c -> !c.isDigit() } }) return null
    return try {
        if (parts.size == 2) parts[0].toLong() * 60 + parts[1].toLong()
        else parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
    } catch (_: NumberFormatException) {
        null
    }
}

private val DURATION_REGEX = Regex("""^\d+:\d+(:\d+)?$""")

/** Extrae los textos de las runs de una flexColumn (sin separadores " • "). */
private fun flexColumnTexts(item: JsonValue?, column: Int): List<String> {
    val columns = item.obj("flexColumns")?.arr() ?: return emptyList()
    if (column >= columns.size) return emptyList()
    val runs = columns[column]
        .obj("musicResponsiveListItemFlexColumnRenderer")
        ?.obj("text")?.obj("runs")?.arr() ?: return emptyList()
    return runs.mapNotNull { it.obj("text")?.str() }.filter { it != " • " }
}

private fun itemVideoId(item: JsonValue?): String? {
    item?.obj("playlistItemData")?.obj("videoId")?.str()?.let { return it }
    return item?.obj("navigationEndpoint")?.obj("watchEndpoint")?.obj("videoId")?.str()
}

private fun itemThumbnail(item: JsonValue?): String? {
    val thumbs = item?.obj("thumbnail")?.obj("musicThumbnailRenderer")
        ?.obj("thumbnail")?.obj("thumbnails")?.arr() ?: return null
    return thumbs.lastOrNull()?.obj("url")?.str()
}

/**
 * Parsea una respuesta de búsqueda de YouTube Music: recorre
 * contents → tabbedSearchResultsRenderer → tabs → sectionListRenderer
 * → musicShelfRenderer → musicResponsiveListItemRenderer. Solo entran
 * los ítems con videoId y título real; el resto se ignora.
 */
fun parseSearchResults(json: String): List<OnlineTrack> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val tabs = root.obj("contents")?.obj("tabbedSearchResultsRenderer")
        ?.obj("tabs")?.arr() ?: return emptyList()
    val results = mutableListOf<OnlineTrack>()
    for (tab in tabs) {
        val sections = tab.obj("tabRenderer")?.obj("content")
            ?.obj("sectionListRenderer")?.obj("contents")?.arr() ?: continue
        for (section in sections) {
            val items = section.obj("musicShelfRenderer")?.obj("contents")?.arr() ?: continue
            for (item in items) {
                val renderer = item.obj("musicResponsiveListItemRenderer") ?: continue
                val videoId = itemVideoId(renderer) ?: continue
                val title = flexColumnTexts(renderer, 0).firstOrNull() ?: continue
                if (title.isBlank()) continue
                val second = flexColumnTexts(renderer, 1)
                val artist = second.firstOrNull() ?: ""
                val durationText = second.firstOrNull { DURATION_REGEX.matches(it) }
                val duration = durationText?.let { parseDurationToSec(it) }
                val album = second.firstOrNull { it != artist && it != durationText }
                results += OnlineTrack(
                    videoId = videoId,
                    title = title,
                    artist = artist,
                    album = album,
                    durationSec = duration,
                    thumbnailUrl = itemThumbnail(renderer),
                )
            }
        }
    }
    return results
}

/**
 * Parsea los formatos de audio de una respuesta de player: solo los
 * adaptativos con URL directa (los cifrados se descartan: no se
 * ejecuta JavaScript de YouTube). Null si la respuesta no trae nada
 * usable o el estado no es OK.
 */
fun parsePlayerFormats(json: String): List<AudioFormat>? {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return null
    }
    if (root.obj("playabilityStatus")?.obj("status")?.str() != "OK") return null
    val formats = root.obj("streamingData")?.obj("adaptiveFormats")?.arr() ?: return null
    return formats.mapNotNull { format ->
        val url = format.obj("url")?.str() ?: return@mapNotNull null
        val mime = format.obj("mimeType")?.str() ?: return@mapNotNull null
        if (!mime.startsWith("audio/")) return@mapNotNull null
        AudioFormat(
            itag = format.obj("itag")?.long() ?: return@mapNotNull null,
            mimeType = mime,
            url = url,
            bitrate = format.obj("bitrate")?.long() ?: 0L,
        )
    }
}

/** Elige el mejor formato de audio: 251 (Opus) > 140 (AAC) > 139 > mayor bitrate. */
fun selectAudioFormat(formats: List<AudioFormat>): AudioFormat? {
    if (formats.isEmpty()) return null
    for (itag in listOf(251L, 140L, 139L)) {
        formats.firstOrNull { it.itag == itag }?.let { return it }
    }
    return formats.maxByOrNull { it.bitrate }
}

/** Transporte: POST JSON a InnerTube, devuelve el cuerpo si es 200. */
/**
 * Cliente InnerTube. Con [authCookies] (sesión de YouTube Music, solo
 * en el teléfono) la búsqueda por WEB_REMIX va firmada con
 * SAPISIDHASH y es la de SU cuenta; sin cookies, anónima, como hoy.
 * La resolución de audio sigue anónima (la escalera de clientes).
 */
class InnerTubeClient(private val authCookies: String? = null) {
    suspend fun search(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val url = "https://${SEARCH_CLIENT.host}/youtubei/v1/search" +
            "?key=${SEARCH_CLIENT.apiKey}&prettyPrint=false"
        val body = postJson(url, buildSearchBody(query), SEARCH_CLIENT) ?: return@withContext emptyList()
        parseSearchResults(body)
    }

    /** Resuelve la URL de audio directa probando la escalera de clientes. */
    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        for (client in PLAYER_CLIENTS) {
            val url = "https://${client.host}/youtubei/v1/player?prettyPrint=false"
            val body = postJson(url, buildPlayerBody(client, videoId), client) ?: continue
            val formats = parsePlayerFormats(body) ?: continue
            selectAudioFormat(formats)?.let { return@withContext it.url }
        }
        null
    }

    private fun postJson(url: String, bodyJson: String, client: YtClient): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", client.userAgent)
            conn.setRequestProperty("X-YouTube-Client-Name", client.id)
            conn.setRequestProperty("X-YouTube-Client-Version", client.version)
            conn.setRequestProperty("X-Goog-Api-Format-Version", "2")
            conn.setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
            if (client.name == "WEB_REMIX") {
                conn.setRequestProperty("Origin", "https://music.youtube.com")
                conn.setRequestProperty("Referer", "https://music.youtube.com/")
                if (authCookies != null) {
                    YouTubeMusicAuth.buildAuthHeaders(authCookies)?.forEach { (name, value) ->
                        conn.setRequestProperty(name, value)
                    }
                }
            }
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            try {
                conn.outputStream.bufferedWriter().use { it.write(bodyJson) }
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
