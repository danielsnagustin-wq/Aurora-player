package com.aurora.player.online

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data class representing a Last.fm artist.
 */
data class LastFmArtist(
    val name: String,
    val listeners: Long? = null,
    val playcount: Long? = null,
    val bioSummary: String? = null,
    val imageUrl: String? = null
)

/**
 * Data class representing a similar artist from Last.fm.
 */
data class LastFmSimilarArtist(
    val name: String,
    val imageUrl: String? = null
)

/**
 * Data class representing a top track from Last.fm.
 */
data class LastFmTopTrack(
    val name: String,
    val artist: String,
    val playcount: Long? = null,
    val listeners: Long? = null,
    val imageUrl: String? = null
)

/**
 * Data class representing a top artist from Last.fm charts.
 */
data class LastFmTopArtist(
    val name: String,
    val playcount: Long? = null,
    val listeners: Long? = null,
    val imageUrl: String? = null
)

/**
 * Build a Last.fm API URL.
 *
 * @param method API method name (e.g., "artist.getInfo").
 * @param apiKey Your Last.fm API key.
 * @param params Additional query parameters.
 * @return Fully-encoded URL string.
 */
fun buildLastFmUrl(
    method: String,
    apiKey: String,
    params: Map<String, String> = emptyMap()
): String {
    val sb = StringBuilder()
    sb.append("https://ws.audioscrobbler.com/2.0/?")
    sb.append("method=").append(URLEncoder.encode(method, "UTF-8"))
    sb.append("&api_key=").append(URLEncoder.encode(apiKey, "UTF-8"))
    sb.append("&format=json")
    for ((k, v) in params) {
        sb.append('&')
            .append(URLEncoder.encode(k, "UTF-8"))
            .append('=')
            .append(URLEncoder.encode(v, "UTF-8"))
    }
    return sb.toString()
}

/**
 * Extract the best image URL from a Last.fm "image" array.
 *
 * The array contains objects with "#text" (the URL) and "size".
 * Preference order: mega > extralarge > large > medium > small.
 */
/**
 * Lee un Long de un número JSON o de un string numérico.
 * Last.fm devuelve listeners/playcount como STRINGS ("123456"), no como
 * números, así que .long() solo (que solo lee JsonValue.Num) daría null.
 */
internal fun JsonValue?.longOrStr(): Long? = long() ?: str()?.toLongOrNull()

internal fun lastFmImageUrl(images: JsonValue?): String? {
    val order = listOf("mega", "extralarge", "large", "medium", "small")
    val arr = images?.arr() ?: return null
    var bestUrl: String? = null
    var bestRank = order.size
    for (item in arr) {
        val url = item.obj("#text")?.str()
        val size = item.obj("size")?.str()
        if (url.isNullOrBlank() || size == null) continue
        val rank = order.indexOf(size)
        if (rank != -1 && rank < bestRank) {
            bestRank = rank
            bestUrl = url
        }
    }
    return bestUrl
}

/**
 * Parse an artist info JSON response.
 *
 * Returns null if required fields are missing or JSON is malformed.
 */
fun parseArtistInfo(json: String): LastFmArtist? {
    return try {
        val root = JsonParser.parse(json)
        val artistObj = root?.obj("artist") ?: return null
        val name = artistObj.obj("name")?.str() ?: return null
        val stats = artistObj.obj("stats")
        val listeners = stats?.obj("listeners")?.longOrStr()
        val playcount = stats?.obj("playcount")?.longOrStr()
        val bioSummaryRaw = artistObj.obj("bio")?.obj("summary")?.str()
        val bioSummary = bioSummaryRaw?.substringBefore("<a ")?.trim()?.takeIf { it.isNotEmpty() }
        val imageUrl = lastFmImageUrl(artistObj.obj("image"))
        LastFmArtist(
            name = name,
            listeners = listeners,
            playcount = playcount,
            bioSummary = bioSummary,
            imageUrl = imageUrl
        )
    } catch (e: JsonParseException) {
        null
    }
}

/**
 * Parse similar artists JSON response.
 */
fun parseSimilarArtists(json: String): List<LastFmSimilarArtist> {
    return try {
        val root = JsonParser.parse(json)
        val arr = root?.obj("similarartists")?.obj("artist")?.arr() ?: return emptyList()
        arr.mapNotNull { item ->
            val name = item?.obj("name")?.str() ?: return@mapNotNull null
            val imageUrl = lastFmImageUrl(item.obj("image"))
            LastFmSimilarArtist(name = name, imageUrl = imageUrl)
        }
    } catch (e: JsonParseException) {
        emptyList()
    }
}

/**
 * Parse top tracks JSON response (artist.getTopTracks or chart.getTopTracks).
 */
fun parseTopTracks(json: String): List<LastFmTopTrack> {
    return try {
        val root = JsonParser.parse(json)
        val arr = root?.obj("toptracks")?.obj("track")?.arr()
            ?: root?.obj("tracks")?.obj("track")?.arr()
            ?: return emptyList()
        arr.mapNotNull { item ->
            val name = item?.obj("name")?.str() ?: return@mapNotNull null
            val artistObj = item.obj("artist")
            val artistName = artistObj?.obj("name")?.str()
                ?: artistObj?.str()
                ?: return@mapNotNull null
            val playcount = item.obj("playcount")?.longOrStr()
            val listeners = item.obj("listeners")?.longOrStr()
            val imageUrl = lastFmImageUrl(item.obj("image"))
            LastFmTopTrack(
                name = name,
                artist = artistName,
                playcount = playcount,
                listeners = listeners,
                imageUrl = imageUrl
            )
        }
    } catch (e: JsonParseException) {
        emptyList()
    }
}

/**
 * Parse chart top artists JSON response.
 */
fun parseChartTopArtists(json: String): List<LastFmTopArtist> {
    return try {
        val root = JsonParser.parse(json)
        val arr = root?.obj("artists")?.obj("artist")?.arr() ?: return emptyList()
        arr.mapNotNull { item ->
            val name = item?.obj("name")?.str() ?: return@mapNotNull null
            val playcount = item.obj("playcount")?.longOrStr()
            val listeners = item.obj("listeners")?.longOrStr()
            val imageUrl = lastFmImageUrl(item.obj("image"))
            LastFmTopArtist(
                name = name,
                playcount = playcount,
                listeners = listeners,
                imageUrl = imageUrl
            )
        }
    } catch (e: JsonParseException) {
        emptyList()
    }
}

/**
 * Parse chart top tracks JSON response.
 */
fun parseChartTopTracks(json: String): List<LastFmTopTrack> {
    return try {
        val root = JsonParser.parse(json)
        val arr = root?.obj("tracks")?.obj("track")?.arr() ?: return emptyList()
        arr.mapNotNull { item ->
            val name = item?.obj("name")?.str() ?: return@mapNotNull null
            val artistObj = item.obj("artist")
            val artistName = artistObj?.obj("name")?.str()
                ?: artistObj?.str()
                ?: return@mapNotNull null
            val playcount = item.obj("playcount")?.longOrStr()
            val listeners = item.obj("listeners")?.longOrStr()
            val imageUrl = lastFmImageUrl(item.obj("image"))
            LastFmTopTrack(
                name = name,
                artist = artistName,
                playcount = playcount,
                listeners = listeners,
                imageUrl = imageUrl
            )
        }
    } catch (e: JsonParseException) {
        emptyList()
    }
}

/**
 * Perform a GET request to a Last.fm endpoint.
 *
 * Returns the response body as a UTF-8 string, or null on non-200 responses.
 * CancellationException is re-thrown; IOException propagates.
 */
private suspend fun lastFmGet(url: String): String? = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            return@withContext null
        }

        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: CancellationException) {
        throw e
    } finally {
        conn?.disconnect()
    }
}

/**
 * Fetch artist information.
 */
suspend fun getArtistInfo(apiKey: String, artist: String): LastFmArtist? {
    if (apiKey.isBlank()) return null
    val url = buildLastFmUrl("artist.getInfo", apiKey, mapOf("artist" to artist))
    val json = lastFmGet(url) ?: return null
    return parseArtistInfo(json)
}

/**
 * Fetch similar artists.
 */
suspend fun getSimilarArtists(apiKey: String, artist: String): List<LastFmSimilarArtist> {
    if (apiKey.isBlank()) return emptyList()
    val url = buildLastFmUrl("artist.getSimilar", apiKey, mapOf("artist" to artist))
    val json = lastFmGet(url) ?: return emptyList()
    return parseSimilarArtists(json)
}

/**
 * Fetch top tracks for an artist.
 */
suspend fun getArtistTopTracks(apiKey: String, artist: String): List<LastFmTopTrack> {
    if (apiKey.isBlank()) return emptyList()
    val url = buildLastFmUrl("artist.getTopTracks", apiKey, mapOf("artist" to artist))
    val json = lastFmGet(url) ?: return emptyList()
    return parseTopTracks(json)
}

/**
 * Fetch chart top artists.
 */
suspend fun getChartTopArtists(apiKey: String): List<LastFmTopArtist> {
    if (apiKey.isBlank()) return emptyList()
    val url = buildLastFmUrl("chart.getTopArtists", apiKey)
    val json = lastFmGet(url) ?: return emptyList()
    return parseChartTopArtists(json)
}

/**
 * Fetch chart top tracks.
 */
suspend fun getChartTopTracks(apiKey: String): List<LastFmTopTrack> {
    if (apiKey.isBlank()) return emptyList()
    val url = buildLastFmUrl("chart.getTopTracks", apiKey)
    val json = lastFmGet(url) ?: return emptyList()
    return parseChartTopTracks(json)
}
