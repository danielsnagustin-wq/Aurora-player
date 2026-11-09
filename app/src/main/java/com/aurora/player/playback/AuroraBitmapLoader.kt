package com.aurora.player.playback

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.session.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.aurora.player.util.loadEmbeddedCoverSync
import java.util.concurrent.Executors

/**
 * Aurora Player — portadas de la notificación (MediaSession).
 *
 * El cargador por defecto de Media3 solo lee la portada de MediaStore
 * y, si el álbum no tiene, la notificación sale sin carátula (el
 * mismo bug que tenía el reproductor). Este cargador prueba, en orden:
 * 1) la portada de MediaStore, como antes;
 * 2) la portada incrustada en un archivo del álbum (MediaMetadataRetriever);
 * 3) una inicial grande con los colores de Aurora, nunca vacía y nunca
 *    la portada de otra canción.
 * Todo corre en un hilo propio, nunca en el hilo principal del servicio.
 */
class AuroraBitmapLoader(private val context: Context) : BitmapLoader {

    private val executor = Executors.newSingleThreadExecutor()

    override fun supportsMimeType(mimeType: String): Boolean =
        mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(data, 0, data.size)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        executor.execute {
            try {
                // SettableFuture.set acepta null (contrato de Media3:
                // null = no se pudo cargar la portada).
                future.set(loadSync(uri))
            } catch (_: Exception) {
                future.set(null)
            }
        }
        return future
    }

    private fun loadSync(uri: Uri): Bitmap? {
        // 1) Portada de MediaStore.
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { return it }
            }
        } catch (_: Exception) {
            // Siguiente intento.
        }
        // 2) Portada incrustada en un archivo del álbum.
        val albumId = try {
            ContentUris.parseId(uri)
        } catch (_: Exception) {
            return null
        }
        val song = findAlbumSong(albumId) ?: return null
        loadEmbeddedCoverSync(context, song.uri)?.let { return it }
        // 3) Inicial grande con los colores de Aurora.
        return letterTile(song.title)
    }

    private data class AlbumSong(val uri: Uri, val title: String)

    private fun findAlbumSong(albumId: Long): AlbumSong? {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
        )
        val selection = "${MediaStore.Audio.Media.ALBUM_ID} = ?" +
            " AND ${MediaStore.Audio.Media.IS_MUSIC} = 1"
        return try {
            context.contentResolver.query(
                uri, projection, selection, arrayOf(albumId.toString()), null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val title = cursor.getString(1) ?: ""
                    AlbumSong(ContentUris.withAppendedId(uri, id), title)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun letterTile(title: String): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#111111"))
        val letter = title.trim().take(1).uppercase().ifEmpty { "♪" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8CCBEB")
            textSize = size * 0.45f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, y, paint)
        return bitmap
    }
}
