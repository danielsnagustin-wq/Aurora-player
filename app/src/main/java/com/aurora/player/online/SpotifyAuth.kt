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
    return root.obj("accessToken")?.str()?.takeIf { it.isNotBlank() }
}

/** GET /v1/me/playlists -> tus listas. */
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

    /** Tus listas (necesita el token de fetchAccessToken). */
    suspend fun fetchPlaylists(token: String): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val body = httpGet(
            "https://api.spotify.com/v1/me/playlists?limit=50",
            mapOf("Authorization" to "Bearer $token"),
        ) ?: return@withContext emptyList()
        parseSpotifyPlaylists(body)
    }

    /** Canciones de una lista (para importarlas por YouTube Music). */
    suspend fun fetchPlaylistTracks(token: String, playlistId: String): List<SpotifyTrack> =
        withContext(Dispatchers.IO) {
            val body = httpGet(
                "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100&fields=items(track(name,artists(name),album(name)))",
                mapOf("Authorization" to "Bearer $token"),
            ) ?: return@withContext emptyList()
            parseSpotifyPlaylistTracks(body)
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
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
