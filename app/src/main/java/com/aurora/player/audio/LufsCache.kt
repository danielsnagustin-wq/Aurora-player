package com.aurora.player.audio

import android.content.Context
import java.io.File

/**
 * LUFS (Fase 2 audio): caché del LUFS medido por canción local, en
 * el almacenamiento privado de la app (sin carpeta visible, sin base
 * de datos). Medir un archivo cuesta decodificarlo entero: una vez
 * medido no se vuelve a medir. La clave es el id de la canción
 * (MediaStore); las pistas online ("yt:") no se miden ni entran aquí.
 * Si el archivo cambia por dentro con el mismo id, el valor queda
 * viejo: la caché se borra con los datos de la app, como las letras.
 */
object LufsCache {
    private fun dir(context: Context): File =
        File(context.filesDir, "lufs-cache").apply { mkdirs() }

    private fun fileFor(context: Context, key: String): File {
        val safe = key.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
        return File(dir(context), "$safe.txt")
    }

    fun read(context: Context, key: String): Double? {
        return try {
            fileFor(context, key).takeIf { it.exists() }
                ?.readText()?.trim()?.toDoubleOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun write(context: Context, key: String, lufs: Double) {
        try {
            fileFor(context, key).writeText(lufs.toString())
        } catch (_: Exception) {
            // Sin caché no pasa nada: la próxima vez se mide otra vez.
        }
    }
}
