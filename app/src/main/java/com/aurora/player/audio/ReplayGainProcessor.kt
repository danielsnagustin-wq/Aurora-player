package com.aurora.player.audio

import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.pow

/**
 * Aurora Player — normalización de volumen con ReplayGain (RONDA 6).
 *
 * Idea de Metrolist (VolumeNormalizationAudioProcessor, GPL — solo la
 * idea, código propio): un AudioProcessor de ExoPlayer que multiplica
 * cada muestra por una ganancia lineal. La ganancia viene de la
 * etiqueta ReplayGain del archivo (FLAC: comentario Vorbis;
 * MP3: marco ID3 TXXX), nunca inventada: sin etiqueta no hay cambio.
 *
 * - Modo "track": ganancia de la pista; "album": la del álbum;
 *   "smart": la de la pista si existe, si no la del álbum.
 * - Modo "lufs" (Fase 2): sonoridad medida del archivo (BS.1770)
 *   hacia el objetivo -14 LUFS: ganancia = -14 - medido. Sin
 *   medición todavía, no cambia el volumen.
 * - Preamp del usuario (-12..+12 dB) se suma a la ganancia de la
 *   etiqueta. La suma se recorta a [-30, +12] dB para no distorsionar.
 * - PCM 16/24/32 bits y float. Otros formatos pasan sin tocarse
 *   (el procesador no se activa para ellos: onConfigure lanza y
 *   ExoPlayer lo saca de la cadena, como hace Metrolist).
 */

/** dB -> ganancia lineal (10^(dB/20)). Función pura, testeable. */
fun dbToLinear(gainDb: Float): Double = 10.0.pow(gainDb / 20.0)

/** Recorta la ganancia total para no distorsionar: nunca más de +12 dB. */
fun clampGainDb(gainDb: Float): Float = gainDb.coerceIn(-30f, 12f)

/** Primer número con decimales de un texto ("-6.23 dB" -> -6.23f). */
fun parseReplayGainDb(text: String): Float? {
    val match = Regex("""(-?\d+(?:\.\d+)?)""").find(text) ?: return null
    return match.value.toFloatOrNull()
}

/** Objetivo de sonoridad del modo LUFS (estándar de streaming). */
const val LUFS_TARGET_DB = -14f

/**
 * Ganancia final en dB según el modo y las etiquetas leídas.
 * "off" o sin etiqueta aplicable -> 0 (sin cambio de volumen).
 * Modo "lufs": objetivo -14 LUFS menos lo medido; sin medición,
 * 0 hasta que llegue (nunca se inventa).
 * Función pura, testeable.
 */
fun selectReplayGainDb(
    mode: String,
    trackDb: Float?,
    albumDb: Float?,
    preAmpDb: Float,
    lufsDb: Float? = null,
): Float {
    val base = when (mode) {
        "track" -> trackDb
        "album" -> albumDb
        "smart" -> trackDb ?: albumDb
        "lufs" -> lufsDb?.let { LUFS_TARGET_DB - it }
        else -> null
    } ?: return 0f
    return clampGainDb(base + preAmpDb)
}

/**
 * Lee las ganancias ReplayGain de los metadatos que ExoPlayer extrae
 * del archivo: comentarios Vorbis (FLAC) y TXXX de ID3 (MP3).
 * Devuelve (ganancia de pista, ganancia de álbum), null donde no hay.
 */
fun replayGainFromMetadata(metadata: Metadata): Pair<Float?, Float?> {
    var trackDb: Float? = null
    var albumDb: Float? = null
    for (i in 0 until metadata.length()) {
        when (val entry = metadata[i]) {
            is VorbisComment -> {
                // RONDA 167: uppercase() sin locale usa el locale
                // del teléfono: en turco, "replaygain_track_gain"
                // sube como "REPLAYGAİN_TRACK_GAİN" (İ con punto) y
                // la etiqueta no se reconoce. Locale.ROOT es
                // independiente del idioma del teléfono.
                when (entry.key.uppercase(Locale.ROOT)) {
                    "REPLAYGAIN_TRACK_GAIN" -> trackDb = parseReplayGainDb(entry.value)
                    "REPLAYGAIN_ALBUM_GAIN" -> albumDb = parseReplayGainDb(entry.value)
                }
            }
            is TextInformationFrame -> {
                if (entry.id == "TXXX") {
                    // TXXX: "descripción\0valor" (p. ej.
                    // "REPLAYGAIN_TRACK_GAIN\0-6.23 dB").
                    val value = entry.value
                    if (value.contains("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
                        trackDb = parseReplayGainDb(value.substringAfter("REPLAYGAIN_TRACK_GAIN", ""))
                            ?: parseReplayGainDb(value)
                    } else if (value.contains("REPLAYGAIN_ALBUM_GAIN", ignoreCase = true)) {
                        albumDb = parseReplayGainDb(value.substringAfter("REPLAYGAIN_ALBUM_GAIN", ""))
                            ?: parseReplayGainDb(value)
                    }
                }
            }
            else -> Unit
        }
    }
    return trackDb to albumDb
}

@UnstableApi
class ReplayGainProcessor : BaseAudioProcessor() {

    private var encoding = C.ENCODING_INVALID

    @Volatile
    var enabled = false

    @Volatile
    private var linearGain = 1.0

    /** Ganancia objetivo en dB (ya con preamp y recorte). */
    @Synchronized
    fun setTargetGainDb(gainDb: Float) {
        linearGain = dbToLinear(clampGainDb(gainDb))
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        when (encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> Unit
            else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        // El audio PCM en Android es little-endian; se fija el orden
        // en los dos búferes para no depender del orden por defecto.
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.order(ByteOrder.LITTLE_ENDIAN)
        val gain = linearGain
        if (!enabled || gain == 1.0) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        // Cada rama exige los bytes de una muestra completa (como la
        // de 24 bits ya hacía con >= 3): un búfer truncado pierde su
        // cola en vez de lanzar BufferUnderflowException en el hilo
        // de audio (auditoría OpenCode, ronda 2).
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.remaining() >= 2) {
                    val sample = inputBuffer.short.toInt()
                    val scaled = (sample * gain).toInt().coerceIn(-32768, 32767)
                    out.putShort(scaled.toShort())
                }
            }
            C.ENCODING_PCM_24BIT -> {
                while (inputBuffer.remaining() >= 3) {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt() and 0xFF
                    var sample = b0 or (b1 shl 8) or (b2 shl 16)
                    if (sample and 0x800000 != 0) sample -= 0x1000000
                    val scaled = (sample * gain).toInt().coerceIn(-8388608, 8388607)
                    out.put((scaled and 0xFF).toByte())
                    out.put((scaled shr 8 and 0xFF).toByte())
                    out.put((scaled shr 16 and 0xFF).toByte())
                }
            }
            C.ENCODING_PCM_32BIT -> {
                while (inputBuffer.remaining() >= 4) {
                    val sample = inputBuffer.int.toLong()
                    val scaled = (sample * gain).toLong().coerceIn(-2147483648L, 2147483647L)
                    out.putInt(scaled.toInt())
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.remaining() >= 4) {
                    val sample = inputBuffer.float
                    out.putFloat((sample * gain).toFloat().coerceIn(-1f, 1f))
                }
            }
            else -> {
                // Codificación desconocida: pasa sin tocarse.
                out.put(inputBuffer)
            }
        }
        out.flip()
    }
}
