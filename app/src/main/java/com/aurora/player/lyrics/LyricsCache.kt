package com.aurora.player.lyrics

import android.content.Context
import java.io.File

/**
 * RONDA 7 P2: caché de letras online, en el almacenamiento privado
 * de la app (sin carpeta visible, sin base de datos). Una letra
 * buscada no se vuelve a pedir: se lee de aquí la próxima vez.
 */
object LyricsCache {
    private fun dir(context: Context): File =
        File(context.filesDir, "lyrics-cache").apply { mkdirs() }

    fun read(context: Context, songId: Long): String? {
        return try {
            File(dir(context), "$songId.lrc").takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }
    }

    fun write(context: Context, songId: Long, text: String) {
        try {
            File(dir(context), "$songId.lrc").writeText(text)
        } catch (_: Exception) {
            // Sin caché no pasa nada: la próxima vez se busca otra vez.
        }
    }
}
