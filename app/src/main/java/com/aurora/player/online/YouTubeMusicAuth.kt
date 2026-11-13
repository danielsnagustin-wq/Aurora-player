package com.aurora.player.online

import java.security.MessageDigest

/**
 * Inicio de sesión de YouTube Music (2026-11-10, como BitChord: la
 * cuenta de Google inicia sesión en music.youtube.com dentro de la
 * app y Aurora usa esa sesión para buscar en YouTube Music).
 *
 * La sesión son las cookies del WebView (SAPISID y compañía), que
 * solo viven en el teléfono (YouTubeMusicStore). Para que InnerTube
 * acepte la sesión hay que firmar cada petición con SAPISIDHASH:
 * `Authorization: SAPISIDHASH <segundos>_<sha1("<segundos> <sapisid>
 * https://music.youtube.com")>` + la cookie + `X-Origin`. Es el
 * mismo esquema que usan los clientes de YouTube (yt-dlp lo
 * documenta); aquí es código propio, sin librerías de Google.
 * Sin SAPISID no hay sesión: la búsqueda sigue anónima, como hoy.
 */
object YouTubeMusicAuth {
    const val ORIGIN = "https://music.youtube.com"
    const val MUSIC_HOST = "music.youtube.com"

    /** Valor de una cookie en la cadena "a=b; c=d" del CookieManager. */
    fun extractCookie(cookies: String, name: String): String? {
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }
            ?.removePrefix("$name=")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Pegado manual de sesión (RONDA ARREGLOS 2026-11-13; idea de
     * BitChord "Universal Smart Paste", código propio): acepta el
     * header crudo de cookies ("a=b; c=d") o el valor suelto de una
     * clave. Devuelve el valor de [key] o null si el texto no trae
     * esa clave. Quita el prefijo "Cookie:" y comillas envolventes.
     * Sin esto, pegar un header crudo se guardaba entero como si
     * fuera el valor y la sesión se rompía en silencio.
     */
    fun parseManualSession(input: String, key: String): String? {
        val clean = input.trim()
            .removePrefix("Cookie:").removePrefix("cookie:")
            .trim().removeSurrounding("\"").trim()
            .takeIf { it.isNotBlank() } ?: return null
        if (!clean.contains("=")) return clean // valor suelto, sin clave
        return extractCookie(clean, key)
    }

    /** SHA-1 en minúsculas (40 hex); null si el algoritmo no existe. */
    fun sha1Hex(text: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Firma SAPISIDHASH para una petición. Pura y testeable: con
     * segundos fijos el resultado es siempre el mismo.
     */
    fun buildSapisidHash(
        sapisid: String,
        timestampSec: Long = System.currentTimeMillis() / 1000,
        origin: String = ORIGIN,
    ): String? {
        if (sapisid.isBlank()) return null
        val hash = sha1Hex("$timestampSec $sapisid $origin") ?: return null
        return "SAPISIDHASH ${timestampSec}_$hash"
    }

    /**
     * Cabeceras de autenticación para InnerTube (solo WEB_REMIX, el
     * cliente de music.youtube.com). Null si no hay sesión: el
     * llamador sigue sin autenticar.
     */
    fun buildAuthHeaders(cookies: String): Map<String, String>? {
        val sapisid = extractCookie(cookies, "SAPISID") ?: return null
        val auth = buildSapisidHash(sapisid) ?: return null
        return mapOf(
            "Cookie" to cookies,
            "Authorization" to auth,
            "X-Origin" to ORIGIN,
        )
    }
}
