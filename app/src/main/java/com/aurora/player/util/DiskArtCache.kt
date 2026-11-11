package com.aurora.player.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Aurora Player — carátulas en DISCO (ronda 5, 2026-11-11).
 *
 * Pedido del usuario: "que las carátulas se carguen solo una vez para
 * siempre como lo hacen otros reproductores". Hasta ahora la caché era
 * solo en memoria (AlbumArtCache 32 MB + LargeArtCache 12 MB): al
 * cerrar la app se vaciaba y cada arranque volvía a decodificar todas
 * las portadas. Otros reproductores guardan la miniatura decodificada
 * en disco, así que la segunda vez es instantánea.
 *
 * Cómo funciona (código propio, sin librerías):
 * - Dos tamaños por álbum: 144 px (lista) y 1024 px (reproductor),
 *   en JPEG 85, bajo cacheDir/album-art/ ("<albumId>_<sizePx>.jpg").
 * - La escritura es atómica: se escribe a "<nombre>.tmp" y se renombra;
 *   una lectura nunca ve un archivo a medias.
 * - Tope 200 MB con desalojo LRU por último acceso: al escribir, si el
 *   total pasa el tope se borran los más viejos sin usar.
 * - Nada tira excepciones: cualquier fallo devuelve null/false y la
 *   llamada sigue su camino normal (MediaStore o incrustada).
 *
 * Límites honestos:
 * - Es cacheDir: Android puede purgarlo si le falta espacio. "Para
 *   siempre" es mientras el sistema no lo limpie, como en cualquier
 *   reproductor.
 * - La clave es el albumId: si el usuario cambia la portada de un
 *   álbum, la guardada queda vieja hasta "Borrar caché de imágenes".
 */
object DiskArtCache {
    const val DIR_NAME = "album-art"

    /** Tope total del disco para carátulas. */
    const val MAX_BYTES = 200L * 1024 * 1024

    const val JPEG_QUALITY = 85

    const val THUMB_PX = 144
    const val LARGE_PX = 1024

    /** Nombre de archivo puro y testeable. */
    fun fileName(albumId: Long, sizePx: Int): String = "${albumId}_$sizePx.jpg"

    fun dir(context: Context): File = File(context.cacheDir, DIR_NAME)

    /** Lee la portada guardada, o null si no está. Toca el archivo para el LRU. */
    suspend fun read(context: Context, albumId: Long, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(dir(context), fileName(albumId, sizePx))
                if (!file.exists() || file.length() == 0L) return@runCatching null
                BitmapFactory.decodeFile(file.absolutePath)?.also {
                    file.setLastModified(System.currentTimeMillis())
                }
            }.getOrNull()
        }

    /** Guarda la portada; true si quedó escrita (o ya estaba). Nunca tira. */
    suspend fun write(context: Context, albumId: Long, sizePx: Int, bitmap: Bitmap): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val directory = dir(context).apply { if (!exists()) mkdirs() }
                val target = File(directory, fileName(albumId, sizePx))
                if (target.exists()) return@runCatching true
                // Las portadas de hardware no se pueden comprimir: copia blanda.
                val soft = if (bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching false
                } else {
                    bitmap
                }
                val tmp = File(directory, fileName(albumId, sizePx) + ".tmp")
                FileOutputStream(tmp).use { out ->
                    if (!soft.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                        tmp.delete()
                        return@runCatching false
                    }
                }
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return@runCatching false
                }
                evictIfOver(directory)
                true
            }.getOrDefault(false)
        }

    /** Vacía el disco de carátulas (botón "Borrar caché de imágenes"). */
    suspend fun clear(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching { dir(context).deleteRecursively() }
        }
    }

    /** Un archivo del disco, para el desalojo puro y testeable. */
    data class DiskFile(val name: String, val bytes: Long, val lastModifiedMs: Long)

    /**
     * Pura: de la lista de archivos, elige qué borrar (del más viejo al
     * más nuevo) hasta quedar bajo el tope. No toca el disco.
     */
    fun pickEviction(files: List<DiskFile>, maxBytes: Long): List<String> {
        val sorted = files.sortedBy { it.lastModifiedMs }
        var total = files.sumOf { it.bytes }
        val doomed = mutableListOf<String>()
        for (file in sorted) {
            if (total <= maxBytes) break
            total -= file.bytes
            doomed.add(file.name)
        }
        return doomed
    }

    private fun evictIfOver(directory: File) {
        runCatching {
            val files = directory.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".tmp") }
                ?.map { DiskFile(it.name, it.length(), it.lastModified()) }
                .orEmpty()
            for (name in pickEviction(files, MAX_BYTES)) {
                File(directory, name).delete()
            }
        }
    }
}
