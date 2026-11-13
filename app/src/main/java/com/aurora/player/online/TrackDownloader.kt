package com.aurora.player.online

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.aurora.player.model.StreamQuality
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Descarga una pista de YouTube Music y la exporta a la carpeta
 * pública de descargas (MediaStore.Downloads/AuroraPlayer). Usa la
 * caché de audio de [StreamCacheStore] y la resolución de streams
 * de [InnerTubeClient].
 *
 * No lanza excepciones de I/O: cualquier error se devuelve como
 * [Result.failure]. Sólo relanza [CancellationException] para
 * respetar la cancelación de corutinas.
 */
class TrackDownloader(
    private val context: Context,
    private val cacheStore: StreamCacheStore = StreamCacheStore(context),
) {

    /**
     * Descarga la pista con la calidad solicitada y la copia a
     * MediaStore.Downloads. Devuelve el [Uri] del archivo creado.
     *
     * @param videoId  ID del vídeo en YouTube.
     * @param title    Título de la canción.
     * @param artist   Artista de la canción.
     * @param quality  Calidad deseada.
     * @param resolveStream Función que resuelve el stream (p.ej.
     *        `InnerTubeClient::resolveStream`).
     */
    suspend fun downloadTrack(
        videoId: String,
        title: String,
        artist: String,
        quality: StreamQuality,
        resolveStream: suspend (String, StreamQuality) -> ResolvedStream?,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // 1. Resolver el stream.
            val resolved = resolveStream(videoId, quality)
                ?: return@withContext Result.failure<Uri>(Exception("No stream resolved"))

            // 2. Intentar obtener el archivo de caché.
            val cachedFile = cacheStore.cachedFile(videoId, quality)
                ?: cacheStore.download(videoId, resolved.itag, resolved.url, quality)
                ?: return@withContext Result.failure<Uri>(Exception("Failed to obtain cached file"))

            // 3. Copiar a Descargas.
            val ext = extensionForItag(resolved.itag)
            val fileName = buildDownloadFileName(artist, title, ext)
            val mime = mimeTypeForItag(resolved.itag)

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/AuroraPlayer")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return@withContext Result.failure<Uri>(Exception("Failed to create MediaStore entry"))

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    cachedFile.inputStream().use { it.copyTo(out) }
                } ?: return@withContext Result.failure<Uri>(Exception("Failed to write file"))

                Result.success(uri)
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "AuroraPlayer",
                ).apply { mkdirs() }
                val file = File(dir, fileName)
                cachedFile.inputStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                Result.success(Uri.fromFile(file))
            }
        } catch (e: CancellationException) {
            // Propagar cancelación.
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Prueba las calidades HIGH → MEDIUM → LOW y devuelve el primer
     * éxito. Si todas fallan, devuelve el último error.
     */
    suspend fun downloadBestAvailable(
        videoId: String,
        title: String,
        artist: String,
        resolveStream: suspend (String, StreamQuality) -> ResolvedStream?,
    ): Result<Uri> {
        var lastError: Exception? = null
        for (q in listOf(StreamQuality.HIGH, StreamQuality.MEDIUM, StreamQuality.LOW)) {
            val result = downloadTrack(videoId, title, artist, q, resolveStream)
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull() as? Exception
        }
        return Result.failure(lastError ?: Exception("All qualities failed"))
    }

    /** Construye el nombre de archivo: "Artista - Título.ext". */
    internal fun buildDownloadFileName(artist: String, title: String, extension: String): String {
        val safeArtist = if (artist.isBlank()) "Sin artista" else sanitize(artist)
        val safeTitle = if (title.isBlank()) "Sin título" else sanitize(title)
        return "$safeArtist - $safeTitle.$extension"
    }

    /** Sanitiza una cadena: elimina caracteres prohibidos y colapsa espacios. */
    private fun sanitize(input: String): String {
        // Elimina [/\\:*?"<>|] y caracteres de control.
        val cleaned = input.replace(Regex("[/\\\\:*?\"<>|\\p{C}]"), " ")
        // Colapsa espacios múltiples y recorta.
        val collapsed = cleaned.replace(Regex("\\s+"), " ").trim()
        // Limita a 200 caracteres por parte.
        return if (collapsed.length > 200) collapsed.substring(0, 200) else collapsed
    }

    /** Extensión de archivo según itag. */
    internal fun extensionForItag(itag: Int): String = when (itag) {
        140, 139 -> "m4a"
        251 -> "webm"
        else -> "bin"
    }

    /** MIME type según itag. */
    internal fun mimeTypeForItag(itag: Int): String = when (itag) {
        140, 139 -> "audio/mp4"
        251 -> "audio/webm"
        else -> "application/octet-stream"
    }
}
