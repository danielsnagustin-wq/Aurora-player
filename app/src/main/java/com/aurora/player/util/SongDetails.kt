package com.aurora.player.util

import android.content.Context
import android.media.MediaMetadataRetriever
import com.aurora.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aurora Player — detalles reales de una canción (ETAPA 5).
 * MediaStore da título/artista/álbum/duración/tamaño/fecha; el
 * extractor del archivo (MediaMetadataRetriever) añade género,
 * compositor, bitrate y sample rate. Si un campo no existe, es null
 * y la UI muestra "—": nunca se inventa un dato.
 */
data class SongDetails(
    val genre: String?,
    val composer: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val mimeType: String,
    /** Canales de audio (1 = mono, 2 = estéreo), null si no se pudo leer. */
    val channelCount: Int? = null,
)

/** Nombre legible del códec ("audio/flac" -> "FLAC"). Función pura, testeable. */
fun codecDisplayName(mimeType: String): String = when (mimeType.lowercase()) {
    "audio/flac", "audio/x-flac" -> "FLAC"
    "audio/mpeg", "audio/mp3" -> "MP3"
    "audio/mp4", "audio/x-m4a", "audio/aac" -> "AAC"
    "audio/x-wav", "audio/wav" -> "WAV"
    "audio/ogg" -> "OGG"
    "audio/opus" -> "Opus"
    "audio/x-ms-wma" -> "WMA"
    "audio/alac" -> "ALAC"
    else -> mimeType.ifBlank { "—" }
}

suspend fun loadSongDetails(context: Context, song: Song): SongDetails =
    withContext(Dispatchers.IO) {
        var genre: String? = null
        var composer: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, song.uri)
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()?.div(1000)
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull()
            }
        } catch (_: Exception) {
            // Sin detalles del extractor: se muestran los de MediaStore.
        }
        // RONDA 6: el número de canales no lo da MediaMetadataRetriever;
        // se lee del formato de la pista de audio con MediaExtractor
        // (descriptor de archivo, válido en todas las API: la ruta
        // directa del archivo está bloqueada por el almacenamiento
        // con ámbito en Android 10+). Si falla, es null y se muestra "—".
        var channels: Int? = null
        try {
            context.contentResolver.openFileDescriptor(song.uri, "r")?.use { pfd ->
                val extractor = android.media.MediaExtractor()
                try {
                    extractor.setDataSource(pfd.fileDescriptor)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            channels = try {
                                format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                            } catch (_: Exception) {
                                null
                            }
                            break
                        }
                    }
                } finally {
                    extractor.release()
                }
            }
        } catch (_: Exception) {
            channels = null
        }
        SongDetails(genre, composer, bitrate, sampleRate, song.mimeType, channels)
    }

/**
 * Lee el .lrc que acompaña a la canción (misma carpeta, mismo nombre).
 * Si no existe o no se puede leer (p. ej. por el acceso a archivos en
 * Android 13+), devuelve lista vacía y la UI dice "Sin letras".
 */
suspend fun loadLyricsFor(song: Song): List<LrcLine> = loadLyricsForPath(song.path)

/**
 * AUDITORÍA (2026-11-09): buscar las letras pedía escanear TODA la
 * biblioteca (1471 canciones) en cada cambio de pista, solo para
 * encontrar la ruta de la canción actual. Ahora la ruta viaja en el
 * MediaItem y las letras se leen directas, sin escaneo.
 */
suspend fun loadLyricsForPath(path: String): List<LrcLine> = withContext(Dispatchers.IO) {
    try {
        if (path.isBlank()) return@withContext emptyList()
        val audio = java.io.File(path)
        val lrc = java.io.File(audio.parentFile, audio.nameWithoutExtension + ".lrc")
        if (!lrc.exists()) return@withContext emptyList()
        LrcParser.parse(lrc.readText())
    } catch (_: Exception) {
        emptyList()
    }
}
