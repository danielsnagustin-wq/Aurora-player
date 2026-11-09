package com.aurora.player.util

/**
 * Aurora Player — M3U (Etapa 1: export; import en Etapa 1, XSPF/WPL/PLS en 1b).
 * Formato real: líneas #EXTINF:<segundos>,<artista> - <título> + ruta.
 * Las rutas se escriben ABSOLUTAS (la del archivo en el teléfono):
 * al exportar con el selector del sistema no se conoce la carpeta
 * destino, así que no se pueden escribir relativas. Al importar,
 * solo entran las pistas que existen de verdad.
 */
object M3u {
    data class Entry(val seconds: Long, val title: String, val path: String)

    fun build(entries: List<Entry>): String = buildString {
        appendLine("#EXTM3U")
        for (e in entries) {
            appendLine("#EXTINF:${e.seconds},${e.title}")
            appendLine(e.path)
        }
    }

    /** Parser tolerante: ignora #EXTM3U, directivas desconocidas y líneas vacías. */
    fun parse(text: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        var pendingTitle = ""
        var pendingSeconds = 0L
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line == "#EXTM3U") continue
            if (line.startsWith("#EXTINF:")) {
                val body = line.removePrefix("#EXTINF:")
                val seconds = body.substringBefore(",").trim().toLongOrNull() ?: 0L
                pendingSeconds = seconds
                pendingTitle = body.substringAfter(",", "")
                continue
            }
            if (line.startsWith("#")) continue // directiva desconocida: se ignora
            entries += Entry(pendingSeconds, pendingTitle, line)
            pendingTitle = ""
            pendingSeconds = 0L
        }
        return entries
    }
}

/** Formato de duraciones y contadores. Sin inventar: 0 = "0:00". */
object TimeFormat {
    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun formatTotalListened(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        return if (hours > 0) "%dh %02dmin".format(hours, minutes) else "%d min".format(minutes)
    }
}
