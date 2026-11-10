package com.aurora.player.util

/**
 * TTML -> LrcLine (2026-11-09, proveedores de letras Fase 2).
 *
 * Los proveedores nuevos (BetterLyrics, BiniLyrics/lrc.red, PaxSenix)
 * devuelven la letra en TTML, el formato de Apple Music: líneas
 * <p begin="..." end="..."> con palabras <span begin="..." end="...">.
 * Este parser puro (sin Android, probado en la JVM) lo convierte a
 * las LrcLine que ya renderiza el reproductor (karaoke por palabra).
 *
 * Tiempos aceptados: "00:01:23.456", "01:23.456", "83.5s", "83.5".
 * Las líneas sin begin quedan como Long.MAX_VALUE (misma convención
 * que plainToLrcLines): se muestran sin sincronizar.
 */
object TtmlParser {

    private val P_REGEX = Regex("""(?s)<p\b([^>]*)>(.*?)</p>""")
    private val SPAN_REGEX = Regex("""(?s)<span\b([^>]*)>(.*?)</span>""")
    private val BEGIN_REGEX = Regex("""begin\s*=\s*"([^"]+)"""")
    private val TAG_REGEX = Regex("""<[^>]+>""")

    /** "00:01:23.456" | "01:23.456" | "83.5s" | "83.5" -> ms, o null. */
    fun parseTime(raw: String): Long? {
        val text = raw.trim().removeSuffix("s").trim()
        if (text.isEmpty()) return null
        val parts = text.split(":")
        return try {
            when (parts.size) {
                1 -> (parts[0].toDouble() * 1000).toLong()
                2 -> ((parts[0].toLong() * 60 + parts[1].toDouble()) * 1000).toLong()
                3 -> ((parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toDouble()) * 1000).toLong()
                else -> null // formato con frames (00:00:05:12): no soportado
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun unescape(text: String): String =
        text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&amp;", "&")

    fun parse(ttml: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        for (match in P_REGEX.findAll(ttml)) {
            val attrs = match.groupValues[1]
            val inner = match.groupValues[2]
            val begin = BEGIN_REGEX.find(attrs)?.groupValues?.get(1)?.let { parseTime(it) }
            val words = mutableListOf<LrcWord>()
            for (span in SPAN_REGEX.findAll(inner)) {
                val wordTime = BEGIN_REGEX.find(span.groupValues[1])?.groupValues?.get(1)
                    ?.let { parseTime(it) }
                val wordText = unescape(TAG_REGEX.replace(span.groupValues[2], "")).trim()
                if (wordText.isNotEmpty() && wordTime != null) {
                    words.add(LrcWord(wordTime, wordText))
                }
            }
            val text = unescape(TAG_REGEX.replace(inner, "")).trim()
            if (text.isEmpty()) continue
            lines.add(LrcLine(begin ?: Long.MAX_VALUE, text, words))
        }
        return lines
    }
}
