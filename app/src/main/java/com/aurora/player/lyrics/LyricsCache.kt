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

    // Ronda 8 (OpenCode): la sanitización ingenua ("Song A" y
    // "Song_A" daban el mismo archivo) podía colisionar si alguna
    // clave futura llevara espacios u otros caracteres: hoy las
    // claves son ids numéricos y "yt:<videoId>" y no colisionan,
    // pero el nombre lleva ahora un sufijo hash de la clave
    // original, así que dos claves distintas nunca comparten
    // archivo. Las letras guardadas con el nombre viejo se
    // vuelven a buscar una vez; los archivos viejos quedan
    // huérfanos (son .lrc diminutos) y no se leen más.
    private fun fileFor(context: Context, key: String): File {
        val safe = key.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
        val hash = try {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(key.toByteArray(Charsets.UTF_8))
                .take(6).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            key.hashCode().toUInt().toString(16)
        }
        return File(dir(context), "$safe-$hash.lrc")
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
