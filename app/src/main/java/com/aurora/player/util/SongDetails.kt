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
)

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
        SongDetails(genre, composer, bitrate, sampleRate, song.mimeType)
    }

/**
 * Lee el .lrc que acompaña a la canción (misma carpeta, mismo nombre).
 * Si no existe o no se puede leer (p. ej. por el acceso a archivos en
 * Android 13+), devuelve lista vacía y la UI dice "Sin letras".
 */
suspend fun loadLyricsFor(song: Song): List<LrcLine> = withContext(Dispatchers.IO) {
    try {
        val path = song.path
        if (path.isBlank()) return@withContext emptyList()
        val audio = java.io.File(path)
        val lrc = java.io.File(audio.parentFile, audio.nameWithoutExtension + ".lrc")
        if (!lrc.exists()) return@withContext emptyList()
        LrcParser.parse(lrc.readText())
    } catch (_: Exception) {
        emptyList()
    }
}
