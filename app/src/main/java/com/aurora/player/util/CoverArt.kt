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
suspend fun loadEmbeddedCover(context: Context, songUri: Uri): Bitmap? =
    withContext(Dispatchers.IO) { loadEmbeddedCoverSync(context, songUri) }

/** Versión síncrona (la usa el cargador de portadas de la notificación). */
fun loadEmbeddedCoverSync(context: Context, songUri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, songUri)
        val bytes = retriever.embeddedPicture ?: return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
