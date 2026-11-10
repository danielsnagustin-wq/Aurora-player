package com.aurora.player.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aurora Player — carátula incrustada en el archivo (idea de
 * Gramophone/Auxio: muchos MP3/FLAC llevan la portada dentro del
 * archivo y MediaStore no la devuelve). Segundo intento cuando
 * ContentResolver.loadThumbnail no da nada: se lee el embeddedPicture
 * del archivo con MediaMetadataRetriever. Solo en el teléfono, en IO.
 * Si el archivo no lleva portada, devuelve null y la UI muestra la
 * inicial grande, nunca la portada de otra canción.
 */
suspend fun loadEmbeddedCover(context: Context, songUri: Uri, maxPx: Int = 512): Bitmap? =
    withContext(Dispatchers.IO) { loadEmbeddedCoverSync(context, songUri, maxPx) }

/** Versión síncrona (la usa el cargador de portadas de la notificación). */
fun loadEmbeddedCoverSync(context: Context, songUri: Uri, maxPx: Int = 512): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, songUri)
        val bytes = retriever.embeddedPicture ?: return null
        // AUDITORÍA (2026-11-09): la portada incrustada se decodificaba
        // a tamaño completo (una de 2000x2000 son 16 MB) y la caché
        // guardaba 32 sin límite de bytes. Ahora se reduce a 512 px
        // de lado mayor por defecto; el reproductor pide 1024 para
        // que la portada grande no se vea borrosa (ronda pulido).
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (_: OutOfMemoryError) {
        // Sin memoria para la portada: null, la UI pone la inicial.
        null
    } catch (_: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
            // Nada que liberar.
        }
    }
}
