package com.aurora.player.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import com.aurora.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Aurora Player — detalles reales de una canción (ETAPA 5).
 * MediaStore da título/artista/álbum/duración/tamaño/fecha; el
 * extractor del archivo (MediaMetadataRetriever) añade género,
 * compositor, letrista, bitrate y sample rate; la cabecera del
 * archivo añade la profundidad de bits real (FLAC/WAV). Si un
 * campo no existe, es null y la UI no muestra esa fila: nunca se
 * inventa un dato.
 */
data class SongDetails(
    val genre: String?,
    val composer: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val mimeType: String,
    /** Canales de audio (1 = mono, 2 = estéreo), null si no se pudo leer. */
    val channelCount: Int? = null,
    /**
     * Profundidad de bits real del archivo: FLAC desde el bloque
     * STREAMINFO, WAV desde el chunk "fmt ". Null si no es FLAC/WAV
     * o no se pudo leer; nunca se inventa.
     */
    val bitDepth: Int? = null,
    /** Letrista (MediaMetadataRetriever METADATA_KEY_WRITER). Null = sin dato. */
    val writer: String? = null,
)

/** Nombre legible del códec ("audio/flac" -> "FLAC"). Función pura, testeable. */
/** Formato que se ve en el reproductor. Si MediaStore no guardó el
 * mime (pasa con archivos de extensiones raras), se deduce de la
 * extensión del archivo; nunca sale "—". */
fun codecDisplayName(mimeType: String, filePath: String = ""): String = when (mimeType.lowercase(Locale.ROOT)) {
    "audio/flac", "audio/x-flac" -> "FLAC"
    "audio/mpeg", "audio/mp3" -> "MP3"
    "audio/mp4", "audio/x-m4a", "audio/aac" -> "AAC"
    "audio/x-wav", "audio/wav" -> "WAV"
    "audio/ogg" -> "OGG"
    "audio/opus" -> "Opus"
    "audio/x-ms-wma" -> "WMA"
    "audio/alac" -> "ALAC"
    else -> mimeType.ifBlank {
        when (filePath.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "flac" -> "FLAC"
            "mp3" -> "MP3"
            "m4a", "aac" -> "AAC"
            "wav" -> "WAV"
            "ogg" -> "OGG"
            "opus" -> "Opus"
            "wma" -> "WMA"
            "mka" -> "MKA"
            "aif", "aiff" -> "AIFF"
            else -> "Audio"
        }
    }
}

/**
 * Profundidad de bits de un FLAC desde sus primeros bytes.
 * Formato verificado contra la especificación FLAC: "fLaC" + cabecera
 * de bloque (tipo 0 = STREAMINFO, longitud 34) y dentro de STREAMINFO
 * el campo de 64 bits (20 bits frecuencia, 3 bits canales-1, 5 bits
 * profundidad-1, 36 bits muestras). Null si no es FLAC o no se lee.
 */
fun flacBitDepth(bytes: ByteArray): Int? {
    if (bytes.size < 42) return null
    if (!(bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
        bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte())
    ) return null
    if ((bytes[4].toInt() and 0x7F) != 0) return null // primer bloque = STREAMINFO
    val length = ((bytes[5].toInt() and 0xFF) shl 16) or
        ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
    if (length != 34) return null
    // STREAMINFO empieza en el byte 8; el campo de 64 bits en data[10]:
    // data[12] = byte 20, data[13] = byte 21.
    val bpsMinusOne = ((bytes[20].toInt() and 0x01) shl 4) or
        ((bytes[21].toInt() and 0xFF) ushr 4)
    return (bpsMinusOne + 1).takeIf { it in 4..32 }
}

/**
 * Profundidad de bits de un WAV desde sus primeros bytes (chunk "fmt ",
 * bits por muestra en little-endian en el offset 14 del chunk). Los
 * chunks van alineados a par. Null si no es WAV o no se encuentra.
 */
fun wavBitDepth(bytes: ByteArray): Int? {
    if (bytes.size < 12) return null
    if (!(bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte())
    ) return null
    var offset = 12
    while (offset + 8 <= bytes.size) {
        val id = String(bytes, offset, 4, Charsets.US_ASCII)
        val size = (bytes[offset + 4].toInt() and 0xFF) or
            ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 7].toInt() and 0xFF) shl 24)
        if (size < 0) return null
        if (id == "fmt " && size >= 16 && offset + 8 + 16 <= bytes.size) {
            val bps = (bytes[offset + 8 + 14].toInt() and 0xFF) or
                ((bytes[offset + 8 + 15].toInt() and 0xFF) shl 8)
            return bps.takeIf { it in 1..32 }
        }
        offset += 8 + size + (size and 1)
    }
    return null
}

suspend fun loadSongDetails(context: Context, song: Song): SongDetails =
    withContext(Dispatchers.IO) {
        var genre: String? = null
        var composer: String? = null
        var writer: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, song.uri)
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                writer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
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
        // Profundidad de bits real: se leen los primeros bytes del
        // archivo y se parsea la cabecera (FLAC STREAMINFO, WAV fmt).
        // Apertura propia: el extractor de arriba ya soltó su descriptor.
        var bitDepth: Int? = null
        try {
            context.contentResolver.openInputStream(song.uri)?.use { input ->
                // InputStream.read puede devolver MENOS bytes de los
                // pedidos: se lee en bucle hasta llenar la cabecera o EOF.
                val header = ByteArray(512)
                var read = 0
                while (read < header.size) {
                    val n = input.read(header, read, header.size - read)
                    if (n <= 0) break
                    read += n
                }
                if (read > 0) {
                    val head = header.copyOf(read)
                    val mime = song.mimeType.lowercase(Locale.ROOT)
                    bitDepth = when {
                        mime in setOf("audio/flac", "audio/x-flac") ||
                            song.path.endsWith(".flac", ignoreCase = true) -> flacBitDepth(head)
                        mime in setOf("audio/x-wav", "audio/wav") ||
                            song.path.endsWith(".wav", ignoreCase = true) -> wavBitDepth(head)
                        else -> null
                    }
                }
            }
        } catch (_: Exception) {
            bitDepth = null
        }
        SongDetails(genre, composer, bitrate, sampleRate, song.mimeType, channels, bitDepth, writer)
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
            // RONDA 67: comparar los dos últimos segmentos de la
            // carpeta ("Música/Álbum") antes que solo el último
            // ("Álbum"): dos carpetas con el mismo nombre final en
            // rutas distintas ya no se confunden. Si no hay dos
            // segmentos útiles, se usa el nombre final como antes.
            val twoSeg = audio.parentFile?.path.orEmpty()
                .split('/').filter { it.isNotEmpty() }.takeLast(2)
                .takeIf { it.size == 2 }?.joinToString("/") ?: ""
            var chosenId: Long? = null
            var oneSegId: Long? = null
            resolver.query(uri, projection, selection, arrayOf(lrcName), null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                var firstId: Long? = null
                // RONDA 75: si MediaStore SÍ da la carpeta y ninguna
                // coincide, no se adivina: el primero podría ser la
                // letra de OTRA canción con el mismo nombre. Solo se
                // usa el primero cuando no hay info de carpeta (Android
                // viejo, sin RELATIVE_PATH) y no hay nada con qué elegir.
                var sawFolderInfo = false
                while (cursor.moveToNext()) {
                    if (idCol < 0) break
                    val id = cursor.getLong(idCol)
                    if (firstId == null) firstId = id
                    val rel = if (pathCol >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getString(pathCol).orEmpty()
                    } else {
                        ""
                    }
                    val relNorm = rel.trimEnd('/')
                    if (relNorm.isNotEmpty()) sawFolderInfo = true
                    if (twoSeg.isNotEmpty() && relNorm.endsWith(twoSeg)) {
                        chosenId = id
                        break
                    }
                    if (oneSegId == null && folder.isNotEmpty() && relNorm.endsWith(folder)) {
                        oneSegId = id
                    }
                }
                if (chosenId == null) chosenId = oneSegId ?: if (sawFolderInfo) null else firstId
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
