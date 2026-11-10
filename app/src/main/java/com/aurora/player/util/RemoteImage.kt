package com.aurora.player.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Imágenes remotas (portadas de listas de Spotify, avatar de la
 * cuenta) sin librería externa: descarga + caché en memoria.
 * Solo se usa con modo online; sin internet devuelve null y la UI
 * muestra el icono, nunca se cuelga.
 */
object RemoteImageCache {
    private const val MAX_KB = 8 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(url: String): Bitmap? = cache.get(url)
    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }
}

private const val MAX_PX = 512
private const val MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024

/** Descarga un bitmap de una URL (10 s de límite); null si falla.
 * Muestreado a 512 px como máximo y con tope de 4 MB descargados:
 * un decode sin muestrear de una imagen remota grande (una de
 * 3000x3000 son 36 MB) puede tumbar la app por memoria, igual que
 * la portada de la notificación (2026-11-10, caza del crash).
 * El catch no caza OutOfMemoryError, así que el tope va ANTES del
 * decode, no después. */
suspend fun loadRemoteBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
        val bytes = connection.inputStream.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_DOWNLOAD_BYTES) return@withContext null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return@withContext null
        var sample = 1
        while (longest / sample > MAX_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (_: Exception) {
        null
    }
}

/** Bitmap remoto con caché; null mientras carga o si no hay. */
@Composable
fun rememberRemoteBitmap(url: String?): Bitmap? {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = RemoteImageCache.get(url) ?: loadRemoteBitmap(url)?.also {
            RemoteImageCache.put(url, it)
        }
    }
    return bitmap
}
