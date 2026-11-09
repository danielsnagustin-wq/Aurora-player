package com.aurora.player.lyrics

import android.content.Context
import java.io.File

/**
 * RONDA 7 P2: caché de letras online, en el almacenamiento privado
 * de la app (sin carpeta visible, sin base de datos). Una letra
 * buscada no se vuelve a pedir: se lee de aquí la próxima vez.
 * La clave es el id de la canción local o "yt:<videoId>" para las
 * pistas online (el videoId es único: nunca se mezclan letras).
 */
object LyricsCache {
    private fun dir(context: Context): File =
        File(context.filesDir, "lyrics-cache").apply { mkdirs() }

    private fun fileFor(context: Context, key: String): File {
        val safe = key.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
        return File(dir(context), "$safe.lrc")
    }

    fun read(context: Context, key: String): String? {
        return try {
            fileFor(context, key).takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }
    }

    fun write(context: Context, key: String, text: String) {
        try {
            fileFor(context, key).writeText(text)
        } catch (_: Exception) {
            // Sin caché no pasa nada: la próxima vez se busca otra vez.
        }
    }
}
