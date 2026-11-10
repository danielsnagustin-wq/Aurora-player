package com.aurora.player.online

import com.aurora.player.model.StreamQuality

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tienda en disco de la caché online (Fase 2, 2026-11-10).
 *
 * Guarda el audio de YouTube en cacheDir/stream-cache (el sistema
 * puede limpiarlo si le falta espacio: es caché, no descargas del
 * usuario). Nunca lanza: cualquier fallo devuelve null y la canción
 * suena por la red como siempre. La poda LRU usa [filesToEvict].
 */
class StreamCacheStore(context: Context) {
    private val dir = File(context.cacheDir, "stream-cache").apply { mkdirs() }

    /** Archivo en caché para este video con la calidad pedida, o null. */
    fun cachedFile(videoId: String, quality: StreamQuality): File? {
        for (itag in quality.preferredItags) {
            val file = File(dir, streamCacheFileName(videoId, itag.toInt()))
            if (file.exists() && file.length() > 0L) {
                runCatching { file.setLastModified(System.currentTimeMillis()) }
                return file
            }
        }
        return null
    }

    /** Todos los archivos de la caché (para podar y para mostrar). */
    fun listCached(): List<CachedStream> {
        val files = dir.listFiles() ?: return emptyList()
        val namePattern = Regex("^(.+)_(\\d+)\\.(webm|m4a|bin)$")
        return files.mapNotNull { file ->
            val m = namePattern.matchEntire(file.name) ?: return@mapNotNull null
            val itag = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val quality = StreamQuality.entries
                .find { it.preferredItags.firstOrNull()?.toInt() == itag } ?: StreamQuality.HIGH
            CachedStream(
                videoId = m.groupValues[1],
                itag = itag,
                quality = quality,
                sizeBytes = file.length(),
                lastUsedMs = file.lastModified(),
                fileName = file.name,
            )
        }
    }

    /**
     * Baja el audio a caché en segundo plano. Devuelve el archivo
     * final, o null si algo falló (la canción ya está sonando por la
     * red; la caché es solo un ahorro para la próxima vez).
     */
    suspend fun download(
        videoId: String,
        itag: Int,
        url: String,
        quality: StreamQuality,
        maxBytes: Long = DEFAULT_STREAM_CACHE_MAX_BYTES,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val final = File(dir, streamCacheFileName(videoId, itag))
            if (final.exists() && final.length() > 0L) return@withContext final
            val part = File(dir, streamCachePartFileName(videoId, itag))
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.connect()
            if (conn.responseCode !in 200..299) return@withContext null
            // Copia con tope por archivo: la poda LRU solo corre al
            // final, así que un flujo infinito llenaría el disco
            // antes de podar (auditoría 2026-11-10).
            var total = 0L
            var capped = false
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    while (!capped) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_SINGLE_STREAM_BYTES) {
                            capped = true
                            break
                        }
                        output.write(buf, 0, n)
                    }
                }
            }
            if (capped) {
                part.delete()
                return@withContext null
            }
            if (part.length() == 0L || !part.renameTo(final)) {
                part.delete()
                return@withContext null
            }
            prune(maxBytes)
            final
        } catch (_: Exception) {
            runCatching { File(dir, streamCachePartFileName(videoId, itag)).delete() }
            null
        }
    }

    private fun prune(maxBytes: Long) {
        filesToEvict(listCached(), maxBytes).forEach { cached ->
            runCatching { File(dir, cached.fileName).delete() }
        }
    }
}
