package com.aurora.player.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spotify como BitChord (usuario 2026-11-09: "usemos lo mismo que
 * usa BitChord"). Verificado en los strings de BitChord v1.9.1 con
 * REA (35 claves spotify):
 *
 * - NO usa OAuth con client_id ni app registrada. Usa la cookie
 *   sp_dc: el usuario entra a open.spotify.com en el navegador,
 *   copia la cookie sp_dc de DevTools (Application -> Cookies) y
 *   la pega en la app ("Set up Spotify Canvas", "Use sp_dc token").
 * - "Sign in to load your playlists. Playback uses YouTube Music":
 *   el login solo carga TUS listas; la reproducción es por YouTube
 *   Music (cada canción se busca allí) — por eso la calidad varía.
 * - Importar por enlace público de playlist + fallback local.
 * - Canvas: portadas animadas con la misma cookie sp_dc.
 *
 * La cookie vive solo en el teléfono (SpotifyStore, DataStore),
 * nunca sale del dispositivo. El token de acceso se pide a
 * open.spotify.com con esa cookie (endpoint del reproductor web
 * de Spotify) y solo se guarda en memoria, nunca en disco.
 */

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String?,
)

data class SpotifyTrack(
    val name: String,
    val artist: String,
    val album: String?,
)

/** Token del reproductor web a partir de la cookie sp_dc. */
fun parseSpotifyAccessToken(json: String): String? {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return null
    }
    // RONDA CAZA 2026-11-13 (P0): con sp_dc inválida o ausente Spotify
    // devuelve 200 con accessToken Y "isAnonymous": true. Ese token
    // anónimo no sirve para /v1/me: se guardaba como "Conectado", el
    // perfil daba null ("Sesión caducada") y el diálogo volvía a
    // auto-conectar la misma cookie en bucle. Sin el campo se acepta
    // como antes; si viene true, se rechaza.
    if (root.obj("isAnonymous")?.bool() == true) return null
    return root.obj("accessToken")?.str()?.takeIf { it.isNotBlank() }
}

/** GET /v1/me/playlists -> tus listas. */
/**
 * Enlace publico de una lista de Spotify -> su id. Acepta la URL
 * (con o sin ?si=...) y la URI spotify:playlist:<id>. Cualquier
 * otra cosa devuelve null (nunca se inventa un id).
 */
fun parseSpotifyPlaylistId(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    if (t.startsWith("spotify:playlist:")) {
        return t.removePrefix("spotify:playlist:").trim().takeIf { it.isNotEmpty() }
    }
    val m = Regex("open\\.spotify\\.com/playlist/([A-Za-z0-9]+)").find(t)
    return m?.groupValues?.getOrNull(1)
}

fun parseSpotifyPlaylists(json: String): List<SpotifyPlaylist> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val items = root.obj("items")?.arr() ?: return emptyList()
    return items.mapNotNull { item ->
        val id = item.obj("id")?.str() ?: return@mapNotNull null
        val name = item.obj("name")?.str() ?: return@mapNotNull null
        val total = item.obj("tracks")?.obj("total")?.long()?.toInt() ?: 0
        val image = item.obj("images")?.arr()?.firstOrNull()?.obj("url")?.str()
        SpotifyPlaylist(id, name, total, image)
    }
}

/** GET /v1/playlists/{id}/tracks -> canciones de una lista. */
fun parseSpotifyPlaylistTracks(json: String): List<SpotifyTrack> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val items = root.obj("items")?.arr() ?: return emptyList()
    return items.mapNotNull { item ->
        val track = item.obj("track") ?: return@mapNotNull null
        val name = track.obj("name")?.str() ?: return@mapNotNull null
        val artist = track.obj("artists")?.arr()?.firstOrNull()
            ?.obj("name")?.str() ?: return@mapNotNull null
        SpotifyTrack(name, artist, track.obj("album")?.obj("name")?.str())
    }
}

/** Perfil de la cuenta: GET /v1/me -> nombre e imagen. */
data class SpotifyProfile(
    val displayName: String,
    val imageUrl: String?,
)

/** Un lanzamiento nuevo de Spotify (GET /v1/browse/new-releases). */
data class SpotifyRelease(
    val id: String,
    val name: String,
    val artistId: String?,
    val artistName: String,
    val releaseDate: String?,
    val imageUrl: String?,
)

/** Un artista que sigues en Spotify (GET /v1/me/following). */
data class SpotifyFollowedArtist(
    val id: String,
    val name: String,
)

/** GET /v1/browse/new-releases -> lanzamientos nuevos (globales). */
fun parseSpotifyNewReleases(json: String): List<SpotifyRelease> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val items = root.obj("albums")?.obj("items")?.arr() ?: return emptyList()
    return items.mapNotNull { item ->
        val id = item.obj("id")?.str() ?: return@mapNotNull null
        val name = item.obj("name")?.str() ?: return@mapNotNull null
        val firstArtist = item.obj("artists")?.arr()?.firstOrNull()
        val artistName = firstArtist?.obj("name")?.str() ?: return@mapNotNull null
        val artistId = firstArtist.obj("id")?.str()
        val releaseDate = item.obj("release_date")?.str()
        val image = item.obj("images")?.arr()?.firstOrNull()?.obj("url")?.str()
        SpotifyRelease(id, name, artistId, artistName, releaseDate, image)
    }
}

/** GET /v1/me/following?type=artist -> artistas que sigues. */
fun parseSpotifyFollowedArtists(json: String): List<SpotifyFollowedArtist> {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val items = root.obj("artists")?.obj("items")?.arr() ?: return emptyList()
    return items.mapNotNull { item ->
        val id = item.obj("id")?.str() ?: return@mapNotNull null
        val name = item.obj("name")?.str() ?: return@mapNotNull null
        SpotifyFollowedArtist(id, name)
    }
}

/**
 * Solo lo nuevo DE TUS artistas: cruza los lanzamientos con los ids
 * de los artistas que sigues. Sin id de artista no hay cruce.
 */
fun filterSpotifyReleasesByFollowed(
    releases: List<SpotifyRelease>,
    followedIds: Set<String>,
): List<SpotifyRelease> =
    releases.filter { it.artistId != null && it.artistId in followedIds }

/** GET /v1/me -> tu nombre y tu foto de Spotify. */
fun parseSpotifyProfile(json: String): SpotifyProfile? {
    val root = try {
        JsonParser.parse(json)
    } catch (_: Exception) {
        return null
    }
    val name = root.obj("display_name")?.str()?.takeIf { it.isNotBlank() } ?: return null
    val image = root.obj("images")?.arr()?.firstOrNull()?.obj("url")?.str()
    return SpotifyProfile(name, image)
}

class SpotifyClient {
    /**
     * Pide el token de acceso con la cookie sp_dc (reproductor web
     * de Spotify). Devuelve el token o null si la cookie no sirve.
     */
    suspend fun fetchAccessToken(spDc: String): String? = withContext(Dispatchers.IO) {
        if (spDc.isBlank()) return@withContext null
        val body = httpGet(
            "https://open.spotify.com/get_access_token?reason=transport&productType=web-player",
            mapOf("Cookie" to "sp_dc=$spDc"),
        ) ?: return@withContext null
        parseSpotifyAccessToken(body)
    }

    /** Nombre e imagen de tu cuenta (para "Cuenta e integraciones"). */
    suspend fun fetchProfile(token: String): SpotifyProfile? = withContext(Dispatchers.IO) {
        val body = httpGet(
            "https://api.spotify.com/v1/me",
            mapOf("Authorization" to "Bearer $token"),
        ) ?: return@withContext null
        parseSpotifyProfile(body)
    }

    /** Tus listas (necesita el token de fetchAccessToken). Null si Spotify no respondió. */
    suspend fun fetchPlaylists(token: String): List<SpotifyPlaylist>? = withContext(Dispatchers.IO) {
        val body = httpGet(
            "https://api.spotify.com/v1/me/playlists?limit=50",
            mapOf("Authorization" to "Bearer $token"),
        ) ?: return@withContext null
        parseSpotifyPlaylists(body)
    }

    /** Canciones de una lista (para importarlas por YouTube Music). Null si Spotify no respondió. */
    suspend fun fetchPlaylistTracks(token: String, playlistId: String): List<SpotifyTrack>? =
        withContext(Dispatchers.IO) {
            val body = httpGet(
                "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100&fields=items(track(name,artists(name),album(name)))",
                mapOf("Authorization" to "Bearer $token"),
            ) ?: return@withContext null
            parseSpotifyPlaylistTracks(body)
        }

    /** Lanzamientos nuevos (para el panel de Nuevos lanzamientos). Null si Spotify no respondió. */
    suspend fun fetchNewReleases(token: String): List<SpotifyRelease>? =
        withContext(Dispatchers.IO) {
            val body = httpGet(
                "https://api.spotify.com/v1/browse/new-releases?limit=20",
                mapOf("Authorization" to "Bearer $token"),
            ) ?: return@withContext null
            parseSpotifyNewReleases(body)
        }

    /** Artistas que sigues (para filtrar los lanzamientos). Null si Spotify no respondió. */
    suspend fun fetchFollowedArtists(token: String): List<SpotifyFollowedArtist>? =
        withContext(Dispatchers.IO) {
            val body = httpGet(
                "https://api.spotify.com/v1/me/following?type=artist&limit=50",
                mapOf("Authorization" to "Bearer $token"),
            ) ?: return@withContext null
            parseSpotifyFollowedArtists(body)
        }

    private fun httpGet(url: String, headers: Map<String, String>): String? {
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
            for ((key, value) in headers) {
                conn.setRequestProperty(key, value)
            }
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            try {
                if (conn.responseCode != 200) return null
                // Tope de 2 MB: la respuesta es JSON (KBs). Sin tope,
                // una respuesta gigante tumba la app por memoria
                // (misma familia del crash, auditoría 2026-11-10).
                readCappedText(conn.inputStream)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Lee el cuerpo con tope; null si supera [maxBytes]. Lo usan también InnerTube y Canvas. */
internal fun readCappedText(stream: java.io.InputStream, maxBytes: Int = 2 * 1024 * 1024): String? {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (true) {
        val n = stream.read(buf)
        if (n <= 0) break
        total += n
        if (total > maxBytes) return null
        out.write(buf, 0, n)
    }
    return String(out.toByteArray(), Charsets.UTF_8)
}
