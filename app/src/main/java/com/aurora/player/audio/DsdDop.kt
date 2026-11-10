package com.aurora.player.audio

/**
 * Ronda USB Serie B — DSD vía DoP (DSD over PCM): leer archivos
 * DSF (Sony) y DFF (Philips DSDIFF) y empaquetar su flujo de 1 bit
 * en tramas PCM de 24 bits con marcador, tal como lo define el
 * estándar abierto DoP (dCS, v1.1).
 *
 * Trama DoP 24-bit (verificado en la spec y en varias fuentes):
 *   [marcador 8 bits][16 bits de DSD][—] — el marcador va en el
 *   byte alto de la palabra; los 16 bits de DSD son 2 bytes del
 *   flujo, el primero (más antiguo en el tiempo) en el byte alto
 *   del campo de 16 bits. El marcador alterna 0x05 / 0xFA por
 *   trama, igual para L y R dentro de una trama.
 * Tasas portadoras: DSD64 (2.8224 MHz) -> 176.4 kHz, DSD128 ->
 * 352.8 kHz, DSD256 -> 705.6 kHz (tasa DSD / 16).
 *
 * DSF (spec oficial Sony v1.01): números little-endian; chunk
 * "DSD " + "fmt " (52 bytes) + "data". Con bits-per-sample = 1
 * los bits van LSB-first dentro de cada byte; los datos vienen
 * por bloques de blockSize bytes POR CANAL (primero el bloque
 * entero del canal 0, luego el del 1...).
 * DFF (spec DSDIFF 1.5): TODO big-endian; "FRM8" + chunks
 * ("FVER", "PROP" -> "SND " -> "FS  " tasa, "CHNL" canales,
 * "DSD " datos). Channel Byte = 8 muestras de 1 canal, el bit
 * más significativo es el más antiguo (MSB-first). Los datos van
 * por tramas: 1 byte por canal, canales intercalados.
 *
 * LÍMITE HONESTO: el empaquetado es real y se verifica con
 * tests de ida y vuelta, pero sin la salida USB directa (driver
 * USB propio, Serie C) el DoP no llega al DAC: por el mezclador
 * de Android los marcadores se rompen y el DAC suena a ruido.
 * Un DFF con chunk "DST " (comprimido DST) se detecta y NO se
 * empaqueta: no hay decodificador DST.
 */
enum class DsdFormat { DSF, DFF }

data class DsdInfo(
    val format: DsdFormat,
    val sampleRateHz: Int,
    val channels: Int,
    val sampleCountPerChannel: Long?,
    val dataOffset: Int,
    val dataSize: Long,
    /** DFF con audio comprimido DST: detectado, sin soporte. */
    val compressedDst: Boolean = false,
    /** DSF: LSB-first por byte; DFF: MSB-first por byte. */
    val bitOrderLsbFirst: Boolean,
)

/** "DSD64" / "DSD128" / "DSD256" / "DSD512", o "DSD" si la tasa no es estándar. */
fun dopLabel(dsdRateHz: Int): String = when (dsdRateHz) {
    2822400 -> "DSD64"
    5644800 -> "DSD128"
    11289600 -> "DSD256"
    22579200 -> "DSD512"
    else -> "DSD"
}

/** Tasa PCM portadora del DoP: la tasa DSD entre 16. */
fun dopCarrierRateHz(dsdRateHz: Int): Int = dsdRateHz / 16

/** DSD por extensión (.dsf/.dff) o por tipo MIME. */
fun isDsdFile(path: String?, mimeType: String?): Boolean {
    val lowerPath = path?.lowercase() ?: ""
    if (lowerPath.endsWith(".dsf") || lowerPath.endsWith(".dff")) return true
    val mime = mimeType?.lowercase() ?: ""
    return mime == "audio/dsf" || mime == "audio/x-dsf" ||
        mime == "audio/dff" || mime == "audio/x-dff" ||
        mime == "audio/dsd" || mime == "audio/x-dsd"
}

private fun u32le(b: ByteArray, off: Int): Int =
    (b[off].toInt() and 0xFF) or
        ((b[off + 1].toInt() and 0xFF) shl 8) or
        ((b[off + 2].toInt() and 0xFF) shl 16) or
        ((b[off + 3].toInt() and 0xFF) shl 24)

private fun u64le(b: ByteArray, off: Int): Long {
    var v = 0L
    for (i in 0 until 8) v = v or ((b[off + i].toLong() and 0xFF) shl (8 * i))
    return v
}

private fun u32be(b: ByteArray, off: Int): Int =
    ((b[off].toInt() and 0xFF) shl 24) or
        ((b[off + 1].toInt() and 0xFF) shl 16) or
        ((b[off + 2].toInt() and 0xFF) shl 8) or
        (b[off + 3].toInt() and 0xFF)

private fun u64be(b: ByteArray, off: Int): Long {
    var v = 0L
    for (i in 0 until 8) v = v or ((b[off + i].toLong() and 0xFF) shl (8 * (7 - i)))
    return v
}

private fun fourCc(b: ByteArray, off: Int): String =
    String(b, off, 4, Charsets.US_ASCII)

/**
 * Cabecera DSF: necesita al menos los 28 bytes del chunk DSD +
 * los 52 del fmt. Devuelve null si no es un DSF válido.
 */
fun parseDsfHeader(bytes: ByteArray): DsdInfo? {
    if (bytes.size < 28 + 52) return null
    if (fourCc(bytes, 0) != "DSD ") return null
    if (u64le(bytes, 4) != 28L) return null
    if (fourCc(bytes, 28) != "fmt ") return null
    val fmtSize = u64le(bytes, 32)
    if (fmtSize < 52L) return null
    // fmt: version(4) + formatID(4) + channelType(4) + channelNum(4)
    //   + sampleRate(4) + bitsPerSample(4) + sampleCount(8)
    //   + blockSizePerChannel(4) + reserved(4). Offsets desde 36.
    val version = u32le(bytes, 40)
    val formatId = u32le(bytes, 44)
    if (version != 1 || formatId != 0) return null
    val channels = u32le(bytes, 52)
    val sampleRate = u32le(bytes, 56)
    val bitsPerSample = u32le(bytes, 60)
    if (channels < 1 || channels > 6) return null
    if (sampleRate <= 0 || bitsPerSample != 1) return null
    val sampleCount = u64le(bytes, 64)
    // El chunk data sigue al fmt (28 + fmtSize). Su cabecera son
    // 12 bytes: "data" + size(8). Los datos empiezan después.
    val dataChunkOff = (28 + fmtSize).toInt()
    if (bytes.size < dataChunkOff + 12) return null
    if (fourCc(bytes, dataChunkOff) != "data") return null
    val dataSize = u64le(bytes, dataChunkOff + 4)
    return DsdInfo(
        format = DsdFormat.DSF,
        sampleRateHz = sampleRate,
        channels = channels,
        sampleCountPerChannel = sampleCount,
        dataOffset = dataChunkOff + 12,
        dataSize = dataSize,
        bitOrderLsbFirst = true,
    )
}

/**
 * Cabecera DFF (DSDIFF): recorre los chunks hasta el de datos.
 * Devuelve null si no es un DFF válido; si el audio es DST
 * comprimido, devuelve la info con compressedDst = true.
 */
fun parseDffHeader(bytes: ByteArray): DsdInfo? {
    if (bytes.size < 16) return null
    if (fourCc(bytes, 0) != "FRM8") return null
    if (fourCc(bytes, 8) != "DSD ") return null
    var sampleRate = 0
    var channels = 0
    // Cabecera FRM8: "FRM8"(4) + size(8) + tipo "DSD "(4) = 16;
    // el primer chunk (FVER) empieza en 16, no en 12.
    var pos = 16
    var dataOffset = -1
    var dataSize = 0L
    var compressedDst = false
    while (pos + 12 <= bytes.size) {
        val id = fourCc(bytes, pos)
        val size = u64be(bytes, pos + 4)
        val body = pos + 12
        if (size < 0 || body > bytes.size) return null
        when (id) {
            "PROP" -> {
                if (body + 4 > bytes.size || fourCc(bytes, body) != "SND ") return null
                var p = body + 4
                val end = (body + size).toInt().coerceAtMost(bytes.size)
                while (p + 12 <= end) {
                    val subId = fourCc(bytes, p)
                    val subSize = u64be(bytes, p + 4)
                    val subBody = p + 12
                    if (subBody > bytes.size) return null
                    when (subId) {
                        "FS  " -> if (subSize >= 4) sampleRate = u32be(bytes, subBody)
                        "CHNL" -> if (subSize >= 2) {
                            channels = ((bytes[subBody].toInt() and 0xFF) shl 8) or
                                (bytes[subBody + 1].toInt() and 0xFF)
                        }
                    }
                    p = (subBody + subSize).toInt()
                    if (subSize % 2L == 1L) p += 1
                }
            }
            "DSD " -> {
                dataOffset = body
                dataSize = size
            }
            "DST " -> {
                compressedDst = true
                dataOffset = body
                dataSize = size
            }
        }
        pos = (body + size).toInt()
        if (size % 2L == 1L) pos += 1
        if (dataOffset >= 0 && sampleRate > 0 && channels > 0) break
    }
    if (sampleRate <= 0 || channels < 1 || dataOffset < 0) return null
    return DsdInfo(
        format = DsdFormat.DFF,
        sampleRateHz = sampleRate,
        channels = channels,
        sampleCountPerChannel = null,
        dataOffset = dataOffset,
        dataSize = dataSize,
        compressedDst = compressedDst,
        bitOrderLsbFirst = false,
    )
}

/**
 * Empaqueta DSD estéreo a DoP 24-bit (little-endian por palabra).
 * Cada trama: 2 bytes de DSD por canal (16 bits) + marcador.
 * La palabra de 24 bits se guarda LE: [byte DSD bajo][byte DSD
 * alto][marcador]. El marcador alterna 0x05/0xFA por trama, igual
 * para L y R. Se empaquetan floor(min(lenL, lenR) / 2) tramas; los
 * bytes sueltos sobrantes se descartan (un DSD bien formado no
 * los tiene). El orden de bits dentro de cada byte es el del
 * archivo (DSF LSB-first, DFF MSB-first): no se reordena nada.
 */
fun dopPackStereo24(leftDsd: ByteArray, rightDsd: ByteArray): ByteArray {
    val frames = minOf(leftDsd.size, rightDsd.size) / 2
    val out = ByteArray(frames * 6)
    var marker = 0x05
    for (i in 0 until frames) {
        val l0 = leftDsd[2 * i]
        val l1 = leftDsd[2 * i + 1]
        val r0 = rightDsd[2 * i]
        val r1 = rightDsd[2 * i + 1]
        val base = i * 6
        out[base] = l1
        out[base + 1] = l0
        out[base + 2] = marker.toByte()
        out[base + 3] = r1
        out[base + 4] = r0
        out[base + 5] = marker.toByte()
        marker = if (marker == 0x05) 0xFA else 0x05
    }
    return out
}

/** Desempaqueta DoP 24-bit (solo para verificar el empaquetado en tests). */
fun dopUnpackStereo24(dop: ByteArray): Pair<ByteArray, ByteArray> {
    val frames = dop.size / 6
    val left = ByteArray(frames * 2)
    val right = ByteArray(frames * 2)
    for (i in 0 until frames) {
        val base = i * 6
        left[2 * i] = dop[base + 1]
        left[2 * i + 1] = dop[base]
        right[2 * i] = dop[base + 4]
        right[2 * i + 1] = dop[base + 3]
    }
    return left to right
}
