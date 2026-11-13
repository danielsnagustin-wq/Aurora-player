package com.aurora.player.online

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data class representing a possible tag source.
 */
data class TagCandidate(
    val title: String,
    val artist: String,
    val album: String?,
    val year: Int?,
    val trackTotal: Int?,
    val coverUrl: String?,
    val source: String,
    val mbid: String?
)

/**
 * Escape characters that have special meaning in Lucene query syntax.
 * The list of characters is taken from the specification:
 * + - = && || > < ! ( ) { } [ ] ^ " ~ * ? : \ /
 */
private fun escapeLucene(input: String): String {
    val special = setOf(
        '+', '-', '=', '&', '|', '>', '<', '!', '(', ')', '{', '}', '[', ']',
        '^', '"', '~', '*', '?', ':', '\\', '/'
    )
    val sb = StringBuilder()
    for (ch in input) {
        if (ch in special) {
            sb.append('\\')
        }
        sb.append(ch)
    }
    return sb.toString()
}

/**
 * Build the Lucene query for MusicBrainz.
 */
fun buildMbQuery(title: String, artist: String): String {
    val escTitle = escapeLucene(title)
    val escArtist = escapeLucene(artist)
    return """recording:"$escTitle" AND artist:"$escArtist""""
}

/**
 * Build the full URL for a MusicBrainz request.
 */
fun buildMbUrl(title: String, artist: String): String {
    val query = buildMbQuery(title, artist)
    val encoded = URLEncoder.encode(query, "UTF-8")
    return "https://musicbrainz.org/ws/2/recording?query=$encoded&fmt=json"
}

/**
 * Parse a MusicBrainz JSON response into a list of TagCandidate.
 * Returns an empty list if the JSON is invalid or if no recordings are present.
 */
fun parseMbResponse(json: String): List<TagCandidate> {
    return try {
        val root = JsonParser.parse(json)
        val recordings = root?.obj("recordings")?.arr() ?: return emptyList()
        recordings.mapNotNull { rec ->
            val title = rec?.obj("title")?.str() ?: return@mapNotNull null
            val artist = rec?.obj("artist-credit")?.arr()
                ?.firstOrNull()
                ?.obj("name")
                ?.str()
                ?: return@mapNotNull null

            val release = rec?.obj("releases")?.arr()?.firstOrNull()
            val album = release?.obj("title")?.str()
            val date = release?.obj("date")?.str()
            val year = date?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
            val mbid = release?.obj("id")?.str()
            val coverUrl = mbid?.let { "https://coverartarchive.org/release/$it/front-250" }

            TagCandidate(
                title = title,
                artist = artist,
                album = album,
                year = year,
                trackTotal = null,
                coverUrl = coverUrl,
                source = "MusicBrainz",
                mbid = mbid
            )
        }
    } catch (e: JsonParseException) {
        emptyList()
    }
}

/**
 * Build the query string for Deezer.
 */
fun buildDzQuery(title: String, artist: String): String {
    val escTitle = escapeLucene(title)
    val escArtist = escapeLucene(artist)
    return """artist:"$escArtist" track:"$escTitle""""
}

/**
 * Build the full URL for a Deezer request.
 */
fun buildDzUrl(title: String, artist: String): String {
    val query = buildDzQuery(title, artist)
    val encoded = URLEncoder.encode(query, "UTF-8")
    return "https://api.deezer.com/search/track?q=$encoded"
}

/**
 * Parse a Deezer JSON response into a list of TagCandidate.
 * Returns an empty list if the JSON is invalid or if no data entries are present.
 */
fun parseDzResponse(json: String): List<TagCandidate> {
    return try {
        val root = JsonParser.parse(json)
        val data = root?.obj("data")?.arr() ?: return emptyList()
        data.mapNotNull { item ->
            val title = item?.obj("title")?.str() ?: return@mapNotNull null
            val artist = item?.obj("artist")?.obj("name")?.str() ?: return@mapNotNull null
            val album = item?.obj("album")?.obj("title")?.str()
            val coverUrl = item?.obj("album")?.obj("cover_medium")?.str()

            TagCandidate(
                title = title,
                artist = artist,
                album = album,
                year = null,
                trackTotal = null,
                coverUrl = coverUrl,
                source = "Deezer",
                mbid = null
            )
        }
    } catch (e: JsonParseException) {
        emptyList()
    }
}

/**
 * Perform a MusicBrainz search.
 *
 * MusicBrainz allows roughly 1 request per second, so this function should only be
 * called in response to explicit user actions (e.g., pressing a "search" button) and
 * never inside tight loops.
 *
 * Returns null on non‑200 HTTP responses or on JSON parsing errors.
 * Propagates CancellationException unchanged.
 */
suspend fun searchMusicBrainz(title: String, artist: String): List<TagCandidate>? {
    val url = buildMbUrl(title, artist)
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
            parseMbResponse(response)
        } catch (e: JsonParseException) {
            null
        } catch (e: CancellationException) {
            throw e
        } finally {
            conn?.disconnect()
        }
    }
}

/**
 * Perform a Deezer search.
 *
 * Returns null on non‑200 HTTP responses or on JSON parsing errors.
 * Propagates CancellationException unchanged.
 */
suspend fun searchDeezer(title: String, artist: String): List<TagCandidate>? {
    val url = buildDzUrl(title, artist)
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
            parseDzResponse(response)
        } catch (e: JsonParseException) {
            null
        } catch (e: CancellationException) {
            throw e
        } finally {
            conn?.disconnect()
        }
    }
}
