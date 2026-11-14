package com.aurora.player.online

import com.aurora.player.model.StreamQuality

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // RONDA 175: el vídeo que suena ahora mismo, para que la poda
    // no lo borre en mitad de la reproducción. Lo marca cachedFile
    // (el archivo que devuelve está a punto de sonar) y lo lee
    // prune. AtomicReference porque cachedFile corre en el hilo de
    // la UI y prune en IO. Solo se protege un vídeo: el último que
    // se pidió de caché (retener uno de más es despreciable).
    private val protectedVideoId = AtomicReference<String?>(null)

    companion object {
        // RONDA 69: un candado por archivo, de PROCESO. Online y
        // Spotify crean cada una su propio StreamCacheStore, así que
        // un candado de instancia no bastaba: dos descargas del mismo
        // vídeo escribían el mismo .part y el archivo final salía
        // corrupto (o una borraba el .part de la otra).
        private val downloadLocks = ConcurrentHashMap<String, Mutex>()
    }

    /** Archivo en caché para este video con la calidad pedida, o null. */
    fun cachedFile(videoId: String, quality: StreamQuality): File? {
        for (itag in quality.preferredItags) {
            val file = File(dir, streamCacheFileName(videoId, itag.toInt()))
            if (file.exists() && file.length() > 0L) {
                runCatching { file.setLastModified(System.currentTimeMillis()) }
                // RONDA 175: este archivo está a punto de sonar; la
                // poda no puede llevárselo mientras tanto.
                protectedVideoId.set(videoId)
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
     * MENÚ ⋮ (2026-11-11, como BitChord): borra todos los archivos
     * en caché de este vídeo (cualquier itag, incluidos los .part
     * de una descarga a medias). Nunca lanza.
     *
     * RONDA 175: suspende y toma el candado por archivo (el mismo
     * que download). Antes corría sin candado y podía borrar el
     * .part mientras se escribía o el archivo final justo tras el
     * renameTo de una descarga en curso.
     */
    suspend fun delete(videoId: String) {
        val safe = Regex.escape(videoId)
        val pattern = Regex("^${safe}_\\d+\\.(webm|m4a|bin)$")
        val partPattern = Regex("^${safe}_\\d+\\.(webm|m4a|bin)\\.part$")
        val files = dir.listFiles()?.filter { file ->
            pattern.matchEntire(file.name) != null ||
                partPattern.matchEntire(file.name) != null
        } ?: return
        for (file in files) {
            val key = file.name.removeSuffix(".part")
            downloadLocks.getOrPut(key) { Mutex() }.withLock {
                runCatching { file.delete() }
            }
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
        val key = streamCacheFileName(videoId, itag)
        downloadLocks.getOrPut(key) { Mutex() }.withLock {
            downloadLocked(videoId, itag, url, quality, maxBytes)
        }
    }

    private fun downloadLocked(
        videoId: String,
        itag: Int,
        url: String,
        quality: StreamQuality,
        maxBytes: Long,
    ): File? {
        try {
            val final = File(dir, streamCacheFileName(videoId, itag))
            if (final.exists() && final.length() > 0L) return final
            val part = File(dir, streamCachePartFileName(videoId, itag))
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            // La conexión se desconecta en finally: sin esto quedaba
            // retenida en los retornos anticipados (auditoría 2026-11-12).
            try {
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.connect()
                if (conn.responseCode !in 200..299) return null
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
                    return null
                }
                // RONDA 175: una respuesta truncada que cierra limpia
                // pasaba el filtro (part no vacío) y se renombraba como
                // final corrupto. Si el servidor dio longitud, tiene que
                // coincidir; y la magia del contenedor tiene que ser la
                // del itag (WebM = EBML, M4A = ftyp). Sin longitud ni
                // magia conocida no hay nada que comprobar.
                val expected = conn.contentLengthLong
                if (expected > 0L && total != expected) {
                    part.delete()
                    return null
                }
                if (!streamCachePartHasMagic(part, itag)) {
                    part.delete()
                    return null
                }
                if (part.length() == 0L || !part.renameTo(final)) {
                    part.delete()
                    return null
                }
                prune(maxBytes)
                return final
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            runCatching { File(dir, streamCachePartFileName(videoId, itag)).delete() }
            return null
        }
    }

    private fun prune(maxBytes: Long) {
        // RONDA 175: el vídeo que suena no se poda.
        val protected = setOfNotNull(protectedVideoId.get())
        filesToEvict(listCached(), maxBytes, protected).forEach { cached ->
            runCatching { File(dir, cached.fileName).delete() }
        }
    }
}

/**
 * RONDA 2 (2026-11-13, P1, n=925): lee exactamente 12 bytes de un
 * flujo, en bucle (un solo read() puede devolver menos aunque queden
 * más — RONDA CAZA 2026-11-13 P1). Null si el flujo termina antes de
 * 12 bytes. Extraído de streamCachePartHasMagic para poder probarlo
 * con flujos que entregan 1 byte por lectura o se cortan (n=926).
 */
internal fun readHeader12(input: InputStream): ByteArray? {
    val header = ByteArray(12)
    var read = 0
    while (read < 12) {
        val n = input.read(header, read, 12 - read)
        if (n < 0) break
        read += n
    }
    return if (read < 12) null else header
}

/** Magia del contenedor de un .part (ronda 175): WebM empieza con EBML, M4A lleva 'ftyp' en los bytes 4-7. */
private fun streamCachePartHasMagic(part: File, itag: Int): Boolean {
    val ext = streamExtensionForItag(itag)
    if (ext == "bin") return true
    val header = try {
        part.inputStream().use { input -> readHeader12(input) }
    } catch (_: Exception) {
        return false
    } ?: return false
    return when (ext) {
        "webm" -> header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        "m4a" -> header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray(Charsets.US_ASCII))
        else -> true
    }
}
