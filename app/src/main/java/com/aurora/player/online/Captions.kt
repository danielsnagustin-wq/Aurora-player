package com.aurora.player.online

import com.aurora.player.util.LrcLine
import com.aurora.player.util.TtmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Subtítulos de YouTube (Fase 2 app — YouTube con vídeo, Namida),
 * Serie B.
 *
 * El timedtext de YouTube (fmt=srv3) es XML con líneas
 * `<p t="1234" d="2000">texto</p>`: `t` son los milisegundos de
 * inicio. Se convierte a las LrcLine que ya usa el reproductor, así
 * el subtítulo en pantalla es la misma maquinaria que las letras.
 */
private val CAPTION_P_REGEX = Regex("""(?s)<p\b([^>]*)>(.*?)</p>""")
private val CAPTION_T_REGEX = Regex("""\bt\s*=\s*"(\d+)"""")
private val CAPTION_TAG_REGEX = Regex("""<[^>]+>""")

/** XML timedtext (srv3) -> líneas con su tiempo en ms. Vacía si no hay nada. */
fun parseTimedText(xml: String): List<LrcLine> {
    val lines = mutableListOf<LrcLine>()
    for (match in CAPTION_P_REGEX.findAll(xml)) {
        val attrs = match.groupValues[1]
        val inner = match.groupValues[2]
        val startMs = CAPTION_T_REGEX.find(attrs)?.groupValues?.get(1)?.toLongOrNull()
            ?: TtmlParser.parseTime(
                Regex("""begin\s*=\s*"([^"]+)"""").find(attrs)?.groupValues?.get(1) ?: "",
            )
            ?: continue
        val text = TtmlParser.unescape(CAPTION_TAG_REGEX.replace(inner, "")).trim()
        if (text.isEmpty()) continue
        lines.add(LrcLine(startMs, text))
    }
    return lines
}

/** La línea que toca a esta posición: la última que ya empezó. Null si aún no empezó ninguna. */
fun captionLineAt(lines: List<LrcLine>, positionMs: Long): LrcLine? =
    lines.lastOrNull { it.timeMs <= positionMs }

/**
 * Baja el timedtext de una pista y lo parsea. Pide srv3 si la URL
 * no trae formato. Cualquier fallo (sin red, sin subtítulos, XML
 * raro) -> lista vacía: el vídeo se ve sin subtítulos, nunca falla.
 */
suspend fun fetchCaptions(baseUrl: String): List<LrcLine> = withContext(Dispatchers.IO) {
    try {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val url = if (baseUrl.contains("fmt=")) baseUrl else "$baseUrl${separator}fmt=srv3"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        )
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        parseTimedText(text)
    } catch (_: Exception) {
        emptyList()
    }
}
