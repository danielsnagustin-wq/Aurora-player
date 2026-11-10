package com.aurora.player.online

import com.aurora.player.model.StreamQuality
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

/** Audio resuelto: su URL directa y el itag elegido. */
data class ResolvedStream(val url: String, val itag: Int)

/**
 * Vídeo progresivo (Fase 2 app — YouTube con vídeo, Namida): un
 * formato adaptativo con vídeo Y audio a la vez (tiene audioChannels).
 * Los de solo vídeo (sin audio) no sirven: sonarían mudos.
 */
data class VideoFormat(
    val itag: Long,
    val mimeType: String,
    val url: String,
    val width: Int,
    val height: Int,
)

/** Vídeo resuelto: su URL directa, el itag y los subtítulos que trae. */
data class ResolvedVideo(
    val url: String,
    val itag: Int,
    val captions: List<CaptionTrack>,
)

/** Una pista de subtítulos del vídeo (timedtext). */
data class CaptionTrack(
    val languageCode: String,
    val name: String,
    val baseUrl: String,
    /** "asr" = generados automáticamente; null = escritos a mano. */
    val kind: String?,
) {
    val isAuto: Boolean get() = kind == "asr"
}

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

/**
 * Parsea los formatos PROGRESIVOS de vídeo de una respuesta de
 * player: solo los adaptativos con URL directa que traen vídeo y
 * audio a la vez (mime video/ + audioChannels). Null si la
 * respuesta no trae nada usable o el estado no es OK.
 */
fun parsePlayerVideoFormats(json: String): List<VideoFormat>? {
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
        if (!mime.startsWith("video/")) return@mapNotNull null
        // Progresivo = trae audio: sin audioChannels sería mudo.
        if (format.obj("audioChannels") == null) return@mapNotNull null
        VideoFormat(
            itag = format.obj("itag")?.long() ?: return@mapNotNull null,
            mimeType = mime,
            url = url,
            width = format.obj("width")?.long()?.toInt() ?: 0,
            height = format.obj("height")?.long()?.toInt() ?: 0,
        )
    }
}

/**
 * Elige el vídeo progresivo: el más alto hasta 720p (en el teléfono
 * no compensa más: gasta datos y batería para lo mismo). Sin
 * progresivos, null (entonces solo queda el audio).
 */
fun selectVideoFormat(formats: List<VideoFormat>): VideoFormat? {
    val sorted = formats.filter { it.height > 0 }.sortedBy { it.height }
    return sorted.lastOrNull { it.height <= 720 } ?: sorted.firstOrNull()
}

/**
 * Parsea las pistas de subtítulos de una respuesta de player
 * (captions.playerCaptionsTracklistRenderer.captionTracks). Vacía
 * si el vídeo no trae subtítulos. El baseUrl sirve tal cual para
 * bajar el timedtext (srv3/TTML).
 */
fun parseCaptionTracks(json: String): List<CaptionTrack> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val tracks = root.obj("captions")?.obj("playerCaptionsTracklistRenderer")
        ?.obj("captionTracks")?.arr() ?: return emptyList()
    return tracks.mapNotNull { track ->
        val baseUrl = track.obj("baseUrl")?.str() ?: return@mapNotNull null
        val languageCode = track.obj("languageCode")?.str() ?: return@mapNotNull null
        val name = track.obj("name")?.obj("simpleText")?.str() ?: languageCode
        CaptionTrack(
            languageCode = languageCode,
            name = name,
            baseUrl = baseUrl,
            kind = track.obj("kind")?.str(),
        )
    }
}

/**
 * Elige la pista de subtítulos: español a mano, inglés a mano,
 * español automático, inglés automático, la primera a mano y si no
 * la primera que haya. Null sin pistas.
 */
fun selectCaptionTrack(
    tracks: List<CaptionTrack>,
    preferredLanguages: List<String> = listOf("es", "en"),
): CaptionTrack? {
    if (tracks.isEmpty()) return null
    for (language in preferredLanguages) {
        tracks.firstOrNull { it.languageCode == language && !it.isAuto }?.let { return it }
    }
    for (language in preferredLanguages) {
        tracks.firstOrNull { it.languageCode == language }?.let { return it }
    }
    return tracks.firstOrNull { !it.isAuto } ?: tracks.first()
}

/** Una lista de charts: título visible, ID de lista de YouTube (sin el prefijo VL) y portada. */
data class ChartPlaylist(
    val title: String,
    val playlistId: String,
    val thumbnailUrl: String?,
)

/** Un artista en los charts: puesto, tendencia y suscriptores (todo puede faltar sin login). */
data class ChartArtist(
    val title: String,
    val browseId: String?,
    val subscribers: String?,
    val rank: String?,
    val trend: String?,
    val thumbnailUrl: String?,
)

/** Lo que devuelve Para ti: listas de vídeos top y artistas top. Vacío si YouTube no respondió. */
data class ChartsResult(
    val videoPlaylists: List<ChartPlaylist>,
    val artists: List<ChartArtist>,
)

private fun twoRowTitle(item: JsonValue?): String? =
    item?.obj("musicTwoRowItemRenderer")?.obj("title")?.obj("runs")?.arr()
        ?.firstOrNull()?.obj("text")?.str()

private fun twoRowBrowseId(item: JsonValue?): String? =
    item?.obj("musicTwoRowItemRenderer")?.obj("title")?.obj("runs")?.arr()
        ?.firstOrNull()?.obj("navigationEndpoint")?.obj("browseEndpoint")?.obj("browseId")?.str()

private fun twoRowThumbnail(item: JsonValue?): String? {
    val thumbs = item?.obj("musicTwoRowItemRenderer")?.obj("thumbnailRenderer")
        ?.obj("musicThumbnailRenderer")?.obj("thumbnail")?.obj("thumbnails")?.arr() ?: return null
    return thumbs.lastOrNull()?.obj("url")?.str()
}

private fun artistThumbnail(renderer: JsonValue?): String? {
    val thumbs = renderer?.obj("thumbnail")?.obj("musicThumbnailRenderer")
        ?.obj("thumbnail")?.obj("thumbnails")?.arr() ?: return null
    return thumbs.lastOrNull()?.obj("url")?.str()
}

/**
 * Parsea los charts de YouTube Music (browseId FEmusic_charts, forma
 * documentada por ytmusicapi): contents → singleColumnBrowseResultsRenderer
 * → tabs[0] → sectionListRenderer → carruseles. La primera sección es el
 * selector de país y se salta. Un carrusel es de listas cuando su primer
 * ítem enlaza a una browseId que empieza por VL; es de artistas cuando su
 * primer ítem es un musicResponsiveListItemRenderer. Lo demás se ignora.
 * Sin login no hay puesto ni tendencia: quedan en null.
 */
fun parseCharts(json: String): ChartsResult {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return ChartsResult(emptyList(), emptyList())
    }
    val sections = root.obj("contents")?.obj("singleColumnBrowseResultsRenderer")
        ?.obj("tabs")?.arr()?.firstOrNull()
        ?.obj("tabRenderer")?.obj("content")?.obj("sectionListRenderer")
        ?.obj("contents")?.arr() ?: return ChartsResult(emptyList(), emptyList())
    val videoPlaylists = mutableListOf<ChartPlaylist>()
    val artists = mutableListOf<ChartArtist>()
    for (section in sections.drop(1)) {
        val contents = section.obj("musicCarouselShelfRenderer")?.obj("contents")?.arr()
            ?: continue
        if (contents.isEmpty()) continue
        val first = contents[0]
        val browseId = twoRowBrowseId(first)
        if (browseId != null && browseId.startsWith("VL")) {
            for (item in contents) {
                val title = twoRowTitle(item) ?: continue
                val id = twoRowBrowseId(item)?.removePrefix("VL") ?: continue
                videoPlaylists += ChartPlaylist(title, id, twoRowThumbnail(item))
            }
        } else if (first.obj("musicResponsiveListItemRenderer") != null) {
            for (item in contents) {
                val renderer = item.obj("musicResponsiveListItemRenderer") ?: continue
                val title = flexColumnTexts(renderer, 0).firstOrNull() ?: continue
                val browse = renderer.obj("navigationEndpoint")?.obj("browseEndpoint")
                    ?.obj("browseId")?.str()
                val subscribers = flexColumnTexts(renderer, 1).firstOrNull()
                    ?.split(" ")?.firstOrNull()
                val rank = renderer.obj("customIndexColumn")
                    ?.obj("musicCustomIndexColumnRenderer")
                    ?.obj("text")?.obj("runs")?.arr()?.firstOrNull()?.obj("text")?.str()
                val iconType = renderer.obj("customIndexColumn")
                    ?.obj("musicCustomIndexColumnRenderer")
                    ?.obj("icon")?.obj("iconType")?.str()
                val trend = when (iconType) {
                    "ARROW_DROP_UP" -> "sube"
                    "ARROW_DROP_DOWN" -> "baja"
                    "ARROW_CHART_NEUTRAL" -> "igual"
                    else -> null
                }
                artists += ChartArtist(title, browse, subscribers, rank, trend, artistThumbnail(renderer))
            }
        }
    }
    return ChartsResult(videoPlaylists, artists)
}

/** Una canción de la Selección rápida de YouTube Music: se puede sonar al tocarla. */
data class QuickPick(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
)

/**
 * Parsea la Selección rápida de la página de inicio de YouTube Music
 * (browseId FEmusic_home, forma documentada por ytmusicapi): contents
 * → singleColumnBrowseResultsRenderer → tabs[0] → sectionListRenderer
 * → carruseles; la fila se reconoce por su título ("Quick picks" o
 * "Selección rápida"). Sus ítems son musicTwoRowItemRenderer con el
 * videoId en navigationEndpoint.watchEndpoint. Si no hay fila (sin
 * login a veces no hay), la lista sale vacía.
 */
fun parseQuickPicks(json: String): List<QuickPick> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val sections = root.obj("contents")?.obj("singleColumnBrowseResultsRenderer")
        ?.obj("tabs")?.arr()?.firstOrNull()
        ?.obj("tabRenderer")?.obj("content")?.obj("sectionListRenderer")
        ?.obj("contents")?.arr() ?: return emptyList()
    for (section in sections) {
        val shelf = section.obj("musicCarouselShelfRenderer") ?: continue
        val headerTitle = shelf.obj("header")
            ?.obj("musicCarouselShelfBasicHeaderRenderer")
            ?.obj("title")?.obj("runs")?.arr()?.firstOrNull()?.obj("text")?.str()
            ?: continue
        val lower = headerTitle.lowercase()
        if (!lower.contains("quick") && !lower.contains("rápida")) continue
        val items = shelf.obj("contents")?.arr() ?: return emptyList()
        val picks = mutableListOf<QuickPick>()
        for (item in items) {
            val twoRow = item.obj("musicTwoRowItemRenderer") ?: continue
            val videoId = twoRow.obj("navigationEndpoint")?.obj("watchEndpoint")
                ?.obj("videoId")?.str() ?: continue
            val title = twoRow.obj("title")?.obj("runs")?.arr()
                ?.firstOrNull()?.obj("text")?.str() ?: continue
            val subtitle = twoRow.obj("subtitle")?.obj("runs")?.arr()
                ?.mapNotNull { it.obj("text")?.str() }
                ?.filter { it != " • " } ?: emptyList()
            val artist = subtitle.firstOrNull() ?: ""
            val thumbs = twoRow.obj("thumbnailRenderer")?.obj("musicThumbnailRenderer")
                ?.obj("thumbnail")?.obj("thumbnails")?.arr()
            val thumbnail = thumbs?.lastOrNull()?.obj("url")?.str()
            picks += QuickPick(videoId, title, artist, thumbnail)
        }
        return picks
    }
    return emptyList()
}

/** Elige el formato de audio segun la calidad pedida: se
 * recorre su escalera de itags; si ninguno esta, cae al de
 * mayor bitrate. Sin formatos no hay audio (null). */
fun selectAudioFormat(
    formats: List<AudioFormat>,
    preferredItags: List<Long> = listOf(251L, 140L, 139L),
): AudioFormat? {
    if (formats.isEmpty()) return null
    for (itag in preferredItags) {
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
class InnerTubeClient(
    private val authCookies: String? = null,
    private val authToken: String? = null,
) {
    suspend fun search(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val url = "https://${SEARCH_CLIENT.host}/youtubei/v1/search" +
            "?key=${SEARCH_CLIENT.apiKey}&prettyPrint=false"
        val body = postJson(url, buildSearchBody(query), SEARCH_CLIENT) ?: return@withContext emptyList()
        parseSearchResults(body)
    }

    /** Charts de YouTube Music (Para ti): listas de vídeos top y artistas top, globales. */
    suspend fun getCharts(): ChartsResult = withContext(Dispatchers.IO) {
        val url = "https://${SEARCH_CLIENT.host}/youtubei/v1/browse" +
            "?key=${SEARCH_CLIENT.apiKey}&prettyPrint=false"
        val body = "{\"context\":{${clientContextJson(SEARCH_CLIENT)}},\"browseId\":\"FEmusic_charts\"}"
        val response = postJson(url, body, SEARCH_CLIENT)
            ?: return@withContext ChartsResult(emptyList(), emptyList())
        parseCharts(response)
    }

    /** Selección rápida de YouTube Music (Para ti): canciones sugeridas del inicio. */
    suspend fun getQuickPicks(): List<QuickPick> = withContext(Dispatchers.IO) {
        val url = "https://${SEARCH_CLIENT.host}/youtubei/v1/browse" +
            "?key=${SEARCH_CLIENT.apiKey}&prettyPrint=false"
        val body = "{\"context\":{${clientContextJson(SEARCH_CLIENT)}},\"browseId\":\"FEmusic_home\"}"
        val response = postJson(url, body, SEARCH_CLIENT) ?: return@withContext emptyList()
        parseQuickPicks(response)
    }

    /** Resuelve el audio probando la escalera de clientes; en cada
     * uno, el formato sigue la calidad pedida. Devuelve la URL y el
     * itag elegido (la caché lo necesita para nombrar el archivo). */
    suspend fun resolveStream(
        videoId: String,
        quality: StreamQuality = StreamQuality.HIGH,
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        for (client in PLAYER_CLIENTS) {
            val url = "https://${client.host}/youtubei/v1/player?prettyPrint=false"
            val body = postJson(url, buildPlayerBody(client, videoId), client) ?: continue
            val formats = parsePlayerFormats(body) ?: continue
            selectAudioFormat(formats, quality.preferredItags)?.let {
                return@withContext ResolvedStream(it.url, it.itag.toInt())
            }
        }
        null
    }

    /** Solo la URL (atajo para quien no necesita el itag). */
    suspend fun resolveStreamUrl(
        videoId: String,
        quality: StreamQuality = StreamQuality.HIGH,
    ): String? = resolveStream(videoId, quality)?.url

    /**
     * Resuelve el VÍDEO (Fase 2 app): el progresivo con audio y las
     * pistas de subtítulos, probando la escalera de clientes. Null
     * si no hay progresivo (entonces solo queda el audio).
     */
    suspend fun resolveVideo(videoId: String): ResolvedVideo? = withContext(Dispatchers.IO) {
        for (client in PLAYER_CLIENTS) {
            val url = "https://${client.host}/youtubei/v1/player?prettyPrint=false"
            val body = postJson(url, buildPlayerBody(client, videoId), client) ?: continue
            val video = parsePlayerVideoFormats(body)?.let { selectVideoFormat(it) } ?: continue
            val captions = parseCaptionTracks(body)
            return@withContext ResolvedVideo(video.url, video.itag.toInt(), captions)
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
                // Cuenta de Google del teléfono (2026-11-10): el token
                // OAuth va como Bearer y tiene prioridad; sin token,
                // las cookies del WebView, como siempre. Un token en
                // blanco es como no tenerlo (nunca se manda "Bearer ").
                if (!authToken.isNullOrBlank()) {
                    GoogleAccountAuth.buildBearerHeaders(authToken).forEach { (name, value) ->
                        conn.setRequestProperty(name, value)
                    }
                } else if (authCookies != null) {
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
