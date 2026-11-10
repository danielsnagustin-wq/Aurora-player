package com.aurora.player.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.aurora.player.model.Song
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fase 1b — editor de letras (.lrc).
 * Serializa líneas a texto LRC ([mm:ss.xx]texto) y guarda el archivo
 * junto a la canción, donde loadLyricsForPath lo busca: en Android
 * 10+ por MediaStore (misma carpeta que el audio), en Android 9 o
 * menos por File directo. Las líneas con palabras conservan sus
 * etiquetas <mm:ss.xx>; una línea editada pierde sus palabras (el
 * texto nuevo no tiene tiempos de palabra).
 */
fun formatLrcTime(timeMs: Long): String {
    val totalSeconds = (timeMs.coerceAtLeast(0L)) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val centis = (timeMs.coerceAtLeast(0L) % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, centis)
}

fun lrcLineToText(line: LrcLine): String {
    val out = StringBuilder("[${formatLrcTime(line.timeMs)}]")
    if (line.words.isEmpty()) {
        out.append(line.text)
    } else {
        line.words.forEachIndexed { i, word ->
            if (i > 0) out.append(" <${formatLrcTime(word.timeMs)}>")
            out.append(word.text)
        }
    }
    return out.toString()
}

fun lrcLinesToText(lines: List<LrcLine>): String =
    lines.sortedBy { it.timeMs }.joinToString("\n", postfix = "\n") { lrcLineToText(it) }

/**
 * Guarda las letras como <canción>.lrc junto al audio. Borra antes
 * cualquier .lrc con el mismo nombre en la misma carpeta, para no
 * dejar duplicados. Devuelve la ruta visible del archivo guardado.
 */
suspend fun saveLyricsForSong(context: Context, song: Song, lines: List<LrcLine>): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val audio = File(song.path)
            val lrcName = audio.nameWithoutExtension + ".lrc"
            val text = lrcLinesToText(lines)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val folder = songFolderRelativePath(context, song)
                val resolver = context.contentResolver
                val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                // Borrar .lrc previos con el mismo nombre en la carpeta.
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.RELATIVE_PATH,
                )
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
                val ids = mutableListOf<Long>()
                resolver.query(uri, projection, selection, arrayOf(lrcName), null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                    val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    while (cursor.moveToNext()) {
                        if (idCol < 0) break
                        val rel = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                        if (rel.trimEnd('/') == folder.trimEnd('/')) {
                            ids += cursor.getLong(idCol)
                        }
                    }
                }
                for (id in ids) {
                    resolver.delete(
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, id),
                        null, null,
                    )
                }
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, lrcName)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, folder)
                }
                val lrcUri = resolver.insert(uri, values)
                    ?: throw IllegalStateException("No se pudo crear el archivo .lrc")
                resolver.openOutputStream(lrcUri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("No se pudo escribir el archivo .lrc")
                "$folder$lrcName"
            } else {
                val lrcFile = File(audio.parentFile, lrcName)
                lrcFile.writeText(text, Charsets.UTF_8)
                lrcFile.absolutePath
            }
        }
    }

/**
 * Carpeta de la canción en términos de MediaStore (p. ej.
 * "Music/Artista/Álbum/"). Se lee del URI de la canción; si no se
 * puede, se deduce de la ruta del archivo.
 */
private fun songFolderRelativePath(context: Context, song: Song): String {
    try {
        val projection = arrayOf(MediaStore.Files.FileColumns.RELATIVE_PATH)
        context.contentResolver.query(song.uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val col = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                if (col >= 0) {
                    val rel = cursor.getString(col).orEmpty()
                    if (rel.isNotBlank()) return rel
                }
            }
        }
    } catch (_: Exception) {
    }
    val path = song.path.replace("\\", "/")
    val marker = "/storage/emulated/0/"
    val relative = if (path.startsWith(marker)) path.removePrefix(marker) else path
    val parent = relative.substringBeforeLast("/", "")
    return if (parent.isBlank()) "Music/" else "$parent/"
}
