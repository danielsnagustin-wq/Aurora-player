package com.aurora.player.lyrics

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RONDA LETRAS (2026-11-11): Lyrics.ovh — letras en texto plano,
 * gratis y sin clave. El usuario lo pidió ("busca más proveedores"):
 * su captura muestra que BitChord TIENE la letra de Blue Foundation
 * "Eyes on Fire" mientras la nuestra decía "Sin letras". Verificado
 * en vivo 2026-11-11: GET https://api.lyrics.ovh/v1/Blue%20Foundation/Eyes%20on%20Fire
 * devuelve la letra completa. Coincidencia exacta de artista+título;
 * sin letras sincronizadas (el texto plano se muestra sin resaltar,
 * vía plainToLrcLines). Servicio de aficionado: puede caerse; como
 * los demás proveedores, un fallo de red se propaga (RONDA 153) y
 * un código distinto de 200 es "sin letra", no un fallo.
 */

/** URL de Lyrics.ovh (artista y título van en la ruta, con %20). */
fun buildLyricsOvhUrl(artist: String, track: String): String =
    "https://api.lyrics.ovh/v1/" +
        URLEncoder.encode(artist.trim(), "UTF-8").replace("+", "%20") + "/" +
        URLEncoder.encode(track.trim(), "UTF-8").replace("+", "%20")

/** Parsea la respuesta {"lyrics": "..."} de Lyrics.ovh. */
fun parseLyricsOvhResponse(json: String): OnlineLyrics? {
    val lyrics = extractJsonString(json, "lyrics")?.takeIf { it.isNotBlank() }
        ?: return null
    return OnlineLyrics(syncedLrc = null, plainText = lyrics)
}

class LyricsOvhProvider : LyricsProvider {
    override val name: String = "LyricsOvh"

    override suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationSec: Long,
    ): OnlineLyrics? = withContext(Dispatchers.IO) {
        val body = httpGet(buildLyricsOvhUrl(artist, track)) ?: return@withContext null
        parseLyricsOvhResponse(body)
    }

    // RONDA 153: un error de red NO es "sin letra": se propaga (el
    // que busca cuenta los fallos) y solo un código distinto de 200
    // devuelve null. Igual que LrclibProvider.
    private fun httpGet(url: String): String? {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        try {
            if (conn.responseCode != 200) return null
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
