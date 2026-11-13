package com.aurora.player.online

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data class representing an iTunes track.
 */
data class ItunesTrack(
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl100: String?,
    val artworkUrl600: String?
)

/**
 * Convert a 100x100 artwork URL to a 600x600 URL if possible.
 */
fun itunesArtwork600(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return if (url.contains("100x100bb")) {
        url.replace("100x100bb", "600x600bb")
    } else {
        url
    }
}

/**
 * Build the iTunes search URL.
 */
fun buildItunesSearchUrl(query: String, limit: Int = 25): String {
    val encoded = URLEncoder.encode(query, "UTF-8")
    return "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=$limit"
}

/**
 * Parse an iTunes JSON response into a list of ItunesTrack.
 * Returns an empty list if the JSON is invalid or if no results are present.
 */
fun parseItunesResponse(json: String): List<ItunesTrack> {
    return try {
        val root = JsonParser.parse(json)
        val results = root?.obj("results")?.arr() ?: return emptyList()
        results.mapNotNull { item ->
            val trackId = item?.obj("trackId")?.long() ?: return@mapNotNull null
            val title = item.obj("trackName")?.str() ?: return@mapNotNull null
            val artist = item.obj("artistName")?.str() ?: return@mapNotNull null
            val album = item.obj("collectionName")?.str()
            val artworkUrl100 = item.obj("artworkUrl100")?.str()
            val artworkUrl600 = itunesArtwork600(artworkUrl100)

            ItunesTrack(
                trackId = trackId,
                title = title,
                artist = artist,
                album = album,
                artworkUrl100 = artworkUrl100,
                artworkUrl600 = artworkUrl600
            )
        }
    } catch (e: JsonParseException) {
        emptyList()
    }
}

/**
 * Perform an iTunes search.
 *
 * Returns null on non‑200 HTTP responses or on JSON parsing errors.
 * Propagates CancellationException unchanged.
 * IOException is not caught and will propagate to the caller.
 */
suspend fun searchItunes(query: String, limit: Int = 25): List<ItunesTrack>? {
    val url = buildItunesSearchUrl(query, limit)
    return withContext(Dispatchers.IO) {
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

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            parseItunesResponse(response)
        } catch (e: JsonParseException) {
            null
        } catch (e: CancellationException) {
            throw e
        } finally {
            conn?.disconnect()
        }
    }
}
