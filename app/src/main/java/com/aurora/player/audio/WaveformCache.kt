package com.aurora.player.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Forma de onda (Fase 2 app): caché de los picos por canción local,
 * mismo patrón que LufsCache (filesDir/waveform-cache, clave = id de
 * la canción, nunca tira). Los picos se guardan como texto separado
 * por comas (200 valores, ~1-2 KB). Las pistas online ("yt:") no
 * entran aquí. Si el archivo cambia con el mismo id, la onda queda
 * vieja hasta borrar los datos de la app, como el LUFS.
 */
object WaveformCache {
    private fun dir(context: Context): File =
        File(context.filesDir, "waveform-cache").apply { mkdirs() }

    private fun fileFor(context: Context, key: String): File {
        val safe = key.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
        return File(dir(context), "$safe.txt")
    }

    suspend fun read(context: Context, key: String): List<Float>? = withContext(Dispatchers.IO) {
        return@withContext try {
            val text = fileFor(context, key).takeIf { it.exists() }
                ?.readText()?.trim() ?: return@withContext null
            if (text.isEmpty()) return@withContext null
            val values = text.split(",").mapNotNull { it.toFloatOrNull() }
            if (values.isEmpty()) null else values
        } catch (_: Exception) {
            null
        }
    }

    suspend fun write(context: Context, key: String, peaks: List<Float>) {
        withContext(Dispatchers.IO) {
            try {
                fileFor(context, key).writeText(peaks.joinToString(","))
            } catch (_: Exception) {
                // Sin caché no pasa nada: la próxima vez se decodifica otra vez.
            }
        }
    }
}
