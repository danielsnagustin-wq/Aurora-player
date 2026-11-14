package com.aurora.player.util

import java.util.Locale

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

    /**
     * Resuelve una ruta de entrada M3U contra la biblioteca por
     * sufijo (ronda 18). Las rutas relativas no se pueden resolver
     * contra la ubicación del archivo M3U (el selector del sistema
     * no da una ruta real), así que la cola de la ruta de entrada
     * tiene que coincidir con la cola de la ruta de la biblioteca,
     * con límite de directorio: "rock/song.mp3" casa con
     * "/storage/.../Música/rock/song.mp3"; "../Música/rock/song.mp3"
     * también (se quitan los ".." iniciales); "song.mp3" casa con
     * cualquier canción de ese nombre. Las barras "\" de Windows
     * se normalizan a "/". Devuelve la ruta de biblioteca que casa,
     * o null.
     */
    fun matchBySuffix(entryPath: String, libraryPaths: List<String>): String? {
        val tail = entryPath.replace('\\', '/').trim()
            .split('/')
            .dropWhile { it == "." || it == ".." }
            .joinToString("/")
            .trimStart('/')
        if (tail.isEmpty()) return null
        val suffix = "/$tail"
        // RONDA 166: solo si la coincidencia es ÚNICA. Antes
        // devolvía la primera que casaba y un nombre repetido en
        // dos carpetas importaba la canción equivocada en silencio
        // — lo mismo que la ronda 32 prohibió en los niveles de
        // nombre y título. Si varias casan, null: la entrada queda
        // sin coincidir y se cuenta como tal, nunca se adivina.
        return libraryPaths.filter { it.endsWith(suffix) || it == tail }.singleOrNull()
    }
}

/** Formato de duraciones y contadores. Sin inventar: 0 = "0:00". */
object TimeFormat {
    // RONDA CAZA 2026-11-13 (P2): String.format sin Locale usa el
    // del teléfono y en locales con dígitos no latinos las duraciones
    // salían con otros dígitos. Locale.ROOT siempre.
    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }

    fun formatTotalListened(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        return if (hours > 0) String.format(Locale.ROOT, "%dh %02dmin", hours, minutes) else String.format(Locale.ROOT, "%d min", minutes)
    }
}
