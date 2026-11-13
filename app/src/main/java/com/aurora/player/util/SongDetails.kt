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
    /**
     * Dolby Atmos real: el archivo es E-AC-3 con JOC (Joint Object
     * Coding), detectado en la cabecera del primer frame. False
     * también cuando no se pudo comprobar: el badge exige prueba
     * positiva, nunca se adivina.
     */
    val isAtmosJoc: Boolean = false,
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

/**
 * Slice 5 paso 2 — Dolby Atmos real: ¿lleva este archivo E-AC-3
 * con JOC (Joint Object Coding)? Layout de bits según ETSI TS 102
 * 366 Anexo E, verificado contra el recorrido de cabecera de
 * ffmpeg (libavcodec/ac3_parser.c, LGPL; el código aquí es propio,
 * solo se sigue el mismo orden de bits — las posiciones de bits
 * son hechos de la especificación, no código copiado):
 *
 * syncword 0x0B77 (16 bits) → strmtyp(2) + substreamid(3) +
 * frmsiz(11) + fscod(2) + numblkscod(2) + acmod(3) + lfeon(1) +
 * bsid(5, en el bit 40; 16 = E-AC-3) → resto de la cabecera
 * E-AC-3 (longitud variable según acmod/strmtyp/bloques) → al
 * final, "additional bitstream info": el LSB del primer byte es
 * flag_ec3_extension_type_a (1 = JOC/Atmos) y el byte siguiente
 * es el índice de complejidad (el "Complexity index" de MediaInfo).
 *
 * parseEac3JocAt devuelve true (JOC), false (frame E-AC-3 válido
 * sin JOC) o null (no es un frame E-AC-3 válido o está truncado).
 * Solo un frame E-AC-3 válido decide: un AC-3 normal (bsid <= 10)
 * o un syncword casual en otros datos no cuentan como prueba en
 * ningún sentido y el escaneo sigue buscando. Sin frame válido no
 * hay badge.
 */
private class Eac3BitReader(private val bytes: ByteArray, startBit: Int) {
    private var pos = startBit

    /** Lee n bits (1..32) MSB-first; null si no quedan bits. */
    fun bits(n: Int): Int? {
        var value = 0
        for (i in 0 until n) {
            val byteIndex = pos / 8
            if (byteIndex >= bytes.size) return null
            val bit = (bytes[byteIndex].toInt() ushr (7 - (pos % 8))) and 1
            value = (value shl 1) or bit
            pos++
        }
        return value
    }

    fun bit(): Int? = bits(1)

    fun skip(n: Int): Boolean {
        if (n < 0 || pos + n > bytes.size * 8) return false
        pos += n
        return true
    }
}

fun parseEac3JocAt(bytes: ByteArray, offset: Int): Boolean? {
    if (offset < 0 || offset + 2 > bytes.size) return null
    if (bytes[offset] != 0x0B.toByte() || bytes[offset + 1] != 0x77.toByte()) return null
    val r = Eac3BitReader(bytes, (offset + 2) * 8)
    val frameType = r.bits(2) ?: return null
    if (frameType == 3) return null // RESERVED
    val substreamId = r.bits(3) ?: return null
    if (substreamId != 0) return null
    val frameSize = ((r.bits(11) ?: return null) + 1) * 2
    if (frameSize < 7) return null
    val fscod = r.bits(2) ?: return null
    val reducedRate = fscod == 3
    var numBlocks = 6
    if (reducedRate) {
        val fscod2 = r.bits(2) ?: return null
        if (fscod2 == 3) return null
    } else {
        numBlocks = when (r.bits(2) ?: return null) {
            0 -> 1
            1 -> 2
            2 -> 3
            else -> 6
        }
    }
    val channelMode = r.bits(3) ?: return null
    val lfeOn = (r.bit() ?: return null) == 1
    // bsid, en el bit 40: 16 = E-AC-3. Cualquier otro valor no es un
    // frame E-AC-3 (un AC-3 normal tiene bsid <= 10) y no decide.
    val bsid = r.bits(5) ?: return null
    if (bsid != 16) return null
    // Control de volumen: dialnorm(5) + compr(1) [+ dynrng(8)];
    // dos rondas en dual-mono (acmod 0), una en el resto.
    val dialNormRounds = if (channelMode == 0) 2 else 1
    repeat(dialNormRounds) {
        if (r.bits(5) == null) return null
        if ((r.bit() ?: return null) == 1) {
            if (r.bits(8) == null) return null
        }
    }
    // Mapa de canales del stream dependiente.
    if (frameType == 1) {
        if ((r.bit() ?: return null) == 1) {
            if (r.bits(16) == null) return null
        }
    }
    // Metadatos de mezcla.
    if ((r.bit() ?: return null) == 1) {
        if (channelMode > 2) {
            if (r.bits(2) == null) return null // preferred downmix
            if (channelMode and 1 != 0) {
                if (r.bits(3) == null || r.bits(3) == null) return null // cmixlev
            }
            if (channelMode and 4 != 0) {
                if (r.bits(3) == null || r.bits(3) == null) return null // surmixlev
            }
        }
        if (lfeOn && (r.bit() ?: return null) == 1) {
            if (r.bits(5) == null) return null // lfe mix level
        }
        if (frameType == 0) {
            repeat(dialNormRounds) {
                if ((r.bit() ?: return null) == 1) {
                    if (!r.skip(6)) return null // program scale factor
                }
            }
            if ((r.bit() ?: return null) == 1) {
                if (!r.skip(6)) return null // external program scale factor
            }
            when (r.bits(2) ?: return null) {
                1 -> if (!r.skip(5)) return null
                2 -> if (!r.skip(12)) return null
                3 -> {
                    val mixDataSize = ((r.bits(5) ?: return null) + 2) shl 3
                    if (!r.skip(mixDataSize)) return null
                }
            }
            if (channelMode < 2) {
                val panRounds = if (channelMode == 0) 2 else 1
                repeat(panRounds) {
                    if ((r.bit() ?: return null) == 1) {
                        if (!r.skip(8) || !r.skip(6)) return null
                    }
                }
            }
            if ((r.bit() ?: return null) == 1) {
                for (i in 0 until numBlocks) {
                    if (numBlocks == 1 || (r.bit() ?: return null) == 1) {
                        if (!r.skip(5)) return null
                    }
                }
            }
        }
    }
    // Metadatos informativos.
    if ((r.bit() ?: return null) == 1) {
        if (r.bits(3) == null) return null // bitstream mode
        if (!r.skip(2)) return null // copyright + original
        if (channelMode == 2) {
            if (r.bits(2) == null || r.bits(2) == null) return null // dsurmod + dhpmod
        }
        if (channelMode >= 6) {
            if (r.bits(2) == null) return null // dsurexmod
        }
        repeat(dialNormRounds) {
            if ((r.bit() ?: return null) == 1) {
                if (!r.skip(8)) return null
            }
        }
        if (!reducedRate) {
            if (!r.skip(1)) return null // source sample rate code
        }
    }
    // Flag de sincronización del conversor.
    if (frameType == 0 && numBlocks != 6) {
        if (!r.skip(1)) return null
    }
    // Código de tamaño original (solo AC-3 convertido).
    if (frameType == 2 && (numBlocks == 6 || (r.bit() ?: return null) == 1)) {
        if (!r.skip(6)) return null
    }
    // Additional bitstream info: el LSB del primer byte es el flag JOC.
    if ((r.bit() ?: return null) == 1) {
        r.bits(6) ?: return null // addbsil
        if (!r.skip(7)) return null
        val extensionTypeA = r.bit() ?: return null
        if (extensionTypeA == 1) {
            if (r.bits(8) == null) return null // complexity index
        }
        return extensionTypeA == 1
    }
    return false
}

/**
 * Busca el primer frame E-AC-3 válido en los bytes dados y dice si
 * lleva JOC. En un .mka/.mkv el frame no está al principio (antes
 * van las cabeceras EBML), así que se escanea buscando el syncword
 * 0x0B77; en un .eac3 crudo está en el offset 0. Sin frame válido,
 * false: sin prueba no hay badge.
 */
fun findEac3Joc(bytes: ByteArray): Boolean {
    var offset = 0
    while (offset + 1 < bytes.size) {
        if (bytes[offset] == 0x0B.toByte() && bytes[offset + 1] == 0x77.toByte()) {
            val result = parseEac3JocAt(bytes, offset)
            if (result != null) return result
        }
        offset++
    }
    return false
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
        var audioMime: String? = null
        try {
            context.contentResolver.openFileDescriptor(song.uri, "r")?.use { pfd ->
                val extractor = android.media.MediaExtractor()
                try {
                    extractor.setDataSource(pfd.fileDescriptor)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            audioMime = mime
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
        // Dolby Atmos real (slice 5 paso 2): solo si la pista de audio
        // es E-AC-3 (lo dice el extractor) o el mime/extensión apunta
        // a E-AC-3 o a un contenedor que lo lleva (.mka/.mkv/.mp4).
        // Se escanean los primeros 64 KB buscando el primer frame
        // E-AC-3 válido: en un .mka el frame no está al principio
        // (antes van las cabeceras EBML). Sin frame válido, false.
        var isAtmosJoc = false
        try {
            val ext = song.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val probeEac3 = audioMime?.equals("audio/eac3", ignoreCase = true) == true ||
                song.mimeType.equals("audio/eac3", ignoreCase = true) ||
                ext in setOf("eac3", "ec3", "mka", "mkv", "mp4", "m4a")
            if (probeEac3) {
                context.contentResolver.openInputStream(song.uri)?.use { input ->
                    val window = ByteArray(64 * 1024)
                    var read = 0
                    while (read < window.size) {
                        val n = input.read(window, read, window.size - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read > 0) isAtmosJoc = findEac3Joc(window.copyOf(read))
                }
            }
        } catch (_: Exception) {
            isAtmosJoc = false
        }
        SongDetails(genre, composer, bitrate, sampleRate, song.mimeType, channels, bitDepth, writer, isAtmosJoc)
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
