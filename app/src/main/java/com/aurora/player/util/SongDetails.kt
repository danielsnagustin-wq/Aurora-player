package com.aurora.player.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
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
suspend fun loadLyricsFor(song: Song, context: Context): List<LrcLine> =
    loadLyricsForPath(context, song.path)

/**
 * AUDITORÍA (2026-11-09): buscar las letras pedía escanear TODA la
 * biblioteca (1471 canciones) en cada cambio de pista, solo para
 * encontrar la ruta de la canción actual. Ahora la ruta viaja en el
 * MediaItem y las letras se leen directas, sin escaneo.
 */
suspend fun loadLyricsForPath(context: Context, path: String): List<LrcLine> =
    withContext(Dispatchers.IO) {
        try {
            if (path.isBlank()) return@withContext emptyList()
            val audio = java.io.File(path)
            val lrcName = audio.nameWithoutExtension + ".lrc"
            // 1) Ruta directa (Android antiguo o acceso permitido).
            val lrc = java.io.File(audio.parentFile, lrcName)
            if (lrc.exists()) return@withContext LrcParser.parse(lrc.readText())
            // 2) MediaStore (RONDA 2, 2026-11-09): en Android 13+ el
            // File directo a carpetas ajenas falla en silencio y el
            // .lrc nunca se encontraba aunque existiera junto a la
            // canción. Se busca por nombre de archivo y se lee por
            // contentResolver; si hay varios, manda el de la misma
            // carpeta que el audio.
            val resolver = context.contentResolver
            val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.RELATIVE_PATH,
            )
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?"
            val folder = audio.parentFile?.name.orEmpty()
            var chosenId: Long? = null
            resolver.query(uri, projection, selection, arrayOf(lrcName), null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                var firstId: Long? = null
                while (cursor.moveToNext()) {
                    if (idCol < 0) break
                    val id = cursor.getLong(idCol)
                    if (firstId == null) firstId = id
                    val rel = if (pathCol >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getString(pathCol).orEmpty()
                    } else {
                        ""
                    }
                    if (folder.isNotEmpty() && rel.trimEnd('/').endsWith(folder)) {
                        chosenId = id
                        break
                    }
                }
                if (chosenId == null) chosenId = firstId
            }
            val lrcUri = chosenId?.let {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL, it)
            } ?: return@withContext emptyList()
            val text = resolver.openInputStream(lrcUri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext emptyList()
            LrcParser.parse(text)
        } catch (_: Exception) {
            emptyList()
        }
    }
