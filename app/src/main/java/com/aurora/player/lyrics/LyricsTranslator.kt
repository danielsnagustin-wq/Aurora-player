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
        // RONDA 2 (2026-11-13, P2): el endpoint gtx recibe el texto
        // en la URL; una canción larga (todas las líneas en una sola
        // petición) superaba el límite de longitud y fallaba. Las
        // líneas se traducen en lotes de como mucho 2000 caracteres,
        // en orden; un lote que falla tumba la traducción entera,
        // como antes (nunca se guarda una traducción a medias).
        val translatedAll = mutableListOf<String>()
        for ((index, chunk) in chunkLines(lines).withIndex()) {
            val (segments, detected) = translateChunk(chunk.joinToString("\n"))
                ?: return@withContext null
            // El idioma se detecta en el primer lote: si la canción
            // ya está en español no hay nada que traducir.
            if (index == 0 && detected == "es") return@withContext null
            val translated = segments.joinToString("").split("\n")
            // Si Google partió o unió líneas, se alinean las que se
            // pueda; las que falten se quedan sin traducción (se
            // muestra solo la original). Nunca se inventa una.
            translatedAll += chunk.indices.map { i -> translated.getOrNull(i) ?: "" }
        }
        if (translatedAll.all { it.isBlank() }) return@withContext null
        LyricsCache.write(context, "$cacheKey:es", translatedAll.joinToString("\n"))
        translatedAll
    }

    /** Una petición gtx con el texto ya unido; null si falla. */
    private fun translateChunk(joined: String): Pair<List<String>, String?>? {
        val url = "$ENDPOINT?client=gtx&sl=auto&tl=es&dt=t&q=" +
            URLEncoder.encode(joined, "UTF-8")
        val body = try {
            val connection = URL(url).openConnection() as HttpURLConnection
            // La conexión se desconecta en finally: sin esto quedaba
            // retenida tras cada traducción (auditoría 2026-11-12).
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            return null
        }
        return parseGtxResponse(body)
    }

    /**
     * Puro (internal para poder probarlo): corta las líneas en lotes
     * cuyo texto unido mide como mucho [maxChars] caracteres, sin
     * partir nunca una línea por la mitad. Una línea sola más larga
     * que el límite va sola en su lote.
     */
    internal fun chunkLines(lines: List<String>, maxChars: Int = 2000): List<List<String>> {
        val chunks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var currentLen = 0
        for (line in lines) {
            val nextLen = if (current.isEmpty()) line.length else currentLen + 1 + line.length
            if (current.isNotEmpty() && nextLen > maxChars) {
                chunks += current
                current = mutableListOf()
                currentLen = 0
            }
            current += line
            currentLen = if (current.size == 1) line.length else nextLen
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }
}
