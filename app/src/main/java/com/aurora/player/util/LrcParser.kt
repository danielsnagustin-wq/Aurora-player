package com.aurora.player.util

/**
 * Aurora Player — letras LRC (ETAPA 5).
 * Solo letras reales: el parser lee el .lrc que acompaña a la
 * canción (misma carpeta, mismo nombre). Nunca se inventan letras ni
 * se usan las de otra canción. Sin .lrc, la UI dice "Sin letras".
 * Formato: [mm:ss.xx]texto por línea; las etiquetas [ti:], [ar:]
 * y demás sin tiempo se ignoran. Karaoke palabra por palabra: Fase 1b.
 */
data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {

    private val TAG_REGEX = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?\]""")

    /**
     * Parsea texto LRC; ignora etiquetas sin tiempo ([ti:], [ar:]…).
     * RONDA 6: una línea puede llevar VARIAS etiquetas de tiempo
     * ([00:01.00][00:05.00]coro): antes la segunda etiqueta salía
     * como texto de la letra; ahora cada etiqueta da su propia línea
     * con el mismo texto.
     */
    fun parse(text: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val tags = TAG_REGEX.findAll(line).toList()
            if (tags.isEmpty()) continue
            // El texto es lo que queda tras la última etiqueta.
            val lineText = line.substring(tags.last().range.last + 1).trim()
            if (lineText.isEmpty()) continue
            for (match in tags) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                val frac = match.groupValues[3]
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100L
                    2 -> frac.toLong() * 10L
                    else -> frac.take(3).toLong()
                }
                val timeMs = minutes * 60_000L + seconds * 1_000L + fracMs
                lines += LrcLine(timeMs, lineText)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** Índice de la línea que suena en positionMs (-1 si aún no empieza). */
    fun currentLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        var idx = -1
        for ((i, line) in lines.withIndex()) {
            if (line.timeMs <= positionMs) idx = i else break
        }
        return idx
    }
}
