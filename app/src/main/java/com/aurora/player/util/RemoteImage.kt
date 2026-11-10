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

/** Descarga un bitmap de una URL (10 s de límite); null si falla. */
suspend fun loadRemoteBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "AuroraPlayer/0.1")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
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
