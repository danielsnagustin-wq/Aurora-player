package com.aurora.player.util

import android.content.Context
import android.content.Intent

/**
 * Compartir verso (Fase 2 app, Apple Music): compartir la línea
 * que suena como texto plano. Formato: el verso entre comillas y
 * debajo quién lo canta. Sin artista ni título, solo el verso.
 */
fun formatSharedVerse(lineText: String, title: String?, artist: String?): String {
    val line = lineText.trim()
    val song = listOfNotNull(
        artist?.takeIf { it.isNotBlank() },
        title?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    return if (song.isEmpty()) "«$line»" else "«$line»\n— $song"
}

/** Abre el menú de compartir del teléfono con el verso formateado. */
fun shareVerse(context: Context, lineText: String, title: String?, artist: String?) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, formatSharedVerse(lineText, title, artist))
    }
    context.startActivity(Intent.createChooser(intent, "Compartir verso"))
}
