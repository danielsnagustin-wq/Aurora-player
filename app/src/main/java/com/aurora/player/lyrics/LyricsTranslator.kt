package com.aurora.player.lyrics

import android.content.Context
import com.aurora.player.online.JsonParser
import com.aurora.player.online.arr
import com.aurora.player.online.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Traductor de letras a español (2026-11-10, como BitChord: la
 * traducción va en letra pequeña bajo cada línea original).
 * Usa el endpoint público sin clave de Google Translate (client=gtx),
 * verificado en vivo 2026-11-10: traduce bien sin API key.
 * LibreTranslate público ya exige API key: no se usa.
 * Solo con modo online; la traducción se guarda en LyricsCache con
 * la clave "<cacheKey>:es" (una línea traducida por línea original).
 * Si la letra ya está en español (detectado "es"), no hay nada que
 * traducir y se devuelve null.
 */
object LyricsTranslator {
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val TIMEOUT_MS = 10_000

    /**
     * Puro: extrae (textos traducidos, idioma detectado) de una
     * respuesta gtx. La respuesta es [[["trad","orig",...],...],null,"en",...]:
     * [0] son los segmentos, [2] el idioma detectado.
     */
    fun parseGtxResponse(json: String): Pair<List<String>, String?>? {
        return try {
            val root = JsonParser.parse(json).arr() ?: return null
            val segments = root.getOrNull(0)?.arr() ?: return null
            val texts = segments.mapNotNull { it.arr()?.getOrNull(0)?.str() }
            if (texts.isEmpty()) return null
            val detected = root.getOrNull(2)?.str()
            Pair(texts, detected)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Traduce las líneas en orden (una petición con todas las líneas
     * unidas por salto de línea). Devuelve null si no se pudo, si no
     * hay líneas o si el original ya está en español. Lo traducido
     * queda en caché para no volver a pedirlo.
     */
    suspend fun translateLines(
        context: Context,
        cacheKey: String,
        lines: List<String>,
    ): List<String>? = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext null
        val cached = LyricsCache.read(context, "$cacheKey:es")
        if (!cached.isNullOrBlank()) {
            val cachedLines = cached.split("\n")
            if (cachedLines.size == lines.size) return@withContext cachedLines
        }
        val joined = lines.joinToString("\n")
        val url = "$ENDPOINT?client=gtx&sl=auto&tl=es&dt=t&q=" +
            URLEncoder.encode(joined, "UTF-8")
        val body = try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return@withContext null
        }
        val (segments, detected) = parseGtxResponse(body) ?: return@withContext null
        if (detected == "es") return@withContext null
        val translated = segments.joinToString("").split("\n")
        // Si Google partió o unió líneas, se alinean las que se pueda;
        // las que falten se quedan sin traducción (se muestra solo la
        // original). Nunca se inventa una traducción.
        val aligned = lines.indices.map { i -> translated.getOrNull(i) ?: "" }
        if (aligned.all { it.isBlank() }) return@withContext null
        LyricsCache.write(context, "$cacheKey:es", aligned.joinToString("\n"))
        aligned
    }
}
