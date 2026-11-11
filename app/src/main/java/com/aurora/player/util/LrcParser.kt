package com.aurora.player.util

/**
 * Aurora Player — letras LRC (ETAPA 5).
 * Solo letras reales: el parser lee el .lrc que acompaña a la
 * canción (misma carpeta, mismo nombre). Nunca se inventan letras ni
 * se usan las de otra canción. Sin .lrc, la UI dice "Sin letras".
 * Formato: [mm:ss.xx]texto por línea; las etiquetas [ti:], [ar:]
 * y demás sin tiempo se ignoran.
 * Karaoke palabra por palabra (Fase 1b, formato mejorado A2):
 * [mm:ss.xx]palabra1 <mm:ss.xx>palabra2 <mm:ss.xx>palabra3.
 * Las etiquetas de palabra son tiempos ABSOLUTOS; la primera palabra
 * toma el tiempo de la etiqueta de línea. Sin etiquetas de palabra,
 * la línea es plana (words vacío) y se resalta por línea, como antes.
 */
data class LrcWord(val timeMs: Long, val text: String)

data class LrcLine(
    val timeMs: Long,
    val text: String,
    val words: List<LrcWord> = emptyList(),
    // Duración en ms, solo para subtítulos de vídeo (srv3 `d`): sin ella
    // la línea se queda visible hasta que empiece la siguiente. Las
    // letras .lrc no tienen duración y dejan null (comportamiento de
    // siempre).
    val durationMs: Long? = null,
)

object LrcParser {

    private val TAG_REGEX = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?\]""")
    private val WORD_TAG_REGEX = Regex("""<(\d+):(\d+)(?:[.:](\d+))?>""")
    private val SPACES_REGEX = Regex("""\s+""")

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
            val rawText = line.substring(tags.last().range.last + 1).trim()
            if (rawText.isEmpty()) continue
            // Texto visible: las etiquetas de palabra no se muestran.
            val lineText = rawText.replace(WORD_TAG_REGEX, " ")
                .replace(SPACES_REGEX, " ").trim()
            if (lineText.isEmpty()) continue
            for (match in tags) {
                val timeMs = tagToMs(match) ?: continue
                lines += LrcLine(timeMs, lineText, parseWords(rawText, timeMs))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun tagToMs(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val frac = match.groupValues[3]
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100L
            2 -> frac.toLong() * 10L
            else -> frac.take(3).toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + fracMs
    }

    /**
     * Palabras con su tiempo de una línea mejorada. La primera palabra
     * es el texto antes de la primera etiqueta de palabra y toma
     * firstWordTimeMs (el tiempo de la etiqueta de línea); cada
     * palabra siguiente es el texto tras su etiqueta. Sin etiquetas
     * de palabra, lista vacía (línea plana).
     */
    fun parseWords(rawText: String, firstWordTimeMs: Long): List<LrcWord> {
        val tags = WORD_TAG_REGEX.findAll(rawText).toList()
        if (tags.isEmpty()) return emptyList()
        val words = mutableListOf<LrcWord>()
        val first = rawText.substring(0, tags.first().range.first).trim()
        if (first.isNotEmpty()) words += LrcWord(firstWordTimeMs, first)
        for ((i, tag) in tags.withIndex()) {
            val timeMs = tagToMs(tag) ?: continue
            val end = if (i + 1 < tags.size) tags[i + 1].range.first else rawText.length
            val word = rawText.substring(tag.range.last + 1, end).trim()
            if (word.isNotEmpty()) words += LrcWord(timeMs, word)
        }
        return words.sortedBy { it.timeMs }
    }

    /** Índice de la palabra que suena en positionMs (-1 si aún no empieza). */
    fun currentWordIndex(line: LrcLine, positionMs: Long): Int {
        var idx = -1
        for ((i, word) in line.words.withIndex()) {
            if (word.timeMs <= positionMs) idx = i else break
        }
        return idx
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
