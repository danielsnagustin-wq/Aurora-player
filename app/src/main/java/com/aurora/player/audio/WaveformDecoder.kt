package com.aurora.player.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Forma de onda (Fase 2 app): decodifica un archivo a mono y devuelve
 * los picos por columna (ver Waveform.kt). Reutiliza el bucle del
 * decodificador LUFS (MediaExtractor + MediaCodec, streaming por
 * trozos, nunca carga el archivo entero), pero mezcla los canales a
 * mono y baja la tasa a ~1 kHz efectiva (un sample de cada N) para
 * no guardar millones de muestras: una canción de 5 min son ~300k
 * floats. Cualquier fallo -> null (sin onda, nunca una inventada).
 */
suspend fun measureWaveform(
    context: Context,
    uri: Uri,
    columns: Int = 200,
): List<Float>? = withContext(Dispatchers.IO) {
    var extractor: MediaExtractor? = null
    var codec: MediaCodec? = null
    try {
        extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var trackIndex = -1
        var mime: String? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val m = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (m.startsWith("audio/")) {
                trackIndex = i
                mime = m
                break
            }
        }
        if (trackIndex < 0 || mime == null) return@withContext null
        val format = extractor.getTrackFormat(trackIndex)
        extractor.selectTrack(trackIndex)
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else {
            return@withContext null
        }
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 8)
        } else {
            2
        }
        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val mono = ArrayList<Float>(300_000)
        // Un sample de cada `stride`: ~1 kHz efectiva.
        // RONDA 2 (2026-11-13, P1, n=903): la tasa y la codificación
        // PCM son las del formato de SALIDA y pueden cambiar: se
        // actualizan en INFO_OUTPUT_FORMAT_CHANGED (como el LUFS,
        // ronda 9). Antes todo se leía como 16-bit y con un
        // decodificador que entrega float la onda salía basura.
        var stride = (sampleRate / 1000).coerceAtLeast(1)
        var outEncoding = 2 // ENCODING_PCM_16BIT
        var sampleCounter = 0
        val bufferInfo = MediaCodec.BufferInfo()
        var inputEos = false
        var outputEos = false
        while (!outputEos) {
            // RONDA 2 (n=903): como el LUFS (n=902), el bucle no
            // tiene puntos de suspensión: sin esto, cancelar la
            // medida no la paraba nunca.
            ensureActive()
            if (!inputEos) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    val read = extractor.readSampleData(inBuffer!!, 0)
                    if (read < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEos = true
                    } else {
                        codec.queueInputBuffer(
                            inIndex, 0, read,
                            extractor.sampleTime, extractor.sampleFlags,
                        )
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIndex >= 0) {
                val outBuffer = codec.getOutputBuffer(outIndex)
                if (outBuffer != null && bufferInfo.size > 0) {
                    val bytes = ByteArray(bufferInfo.size)
                    outBuffer.position(bufferInfo.offset)
                    outBuffer.get(bytes)
                    // Codificaciones (literales de AudioFormat, igual
                    // que el LUFS): 2 = PCM 16-bit, 4 = PCM float,
                    // 21 = PCM 24-bit empaquetado. Cualquier otra se
                    // salta: mejor un hueco en la onda que basura.
                    when (outEncoding) {
                        2 -> {
                            val shorts = ByteBuffer.wrap(bytes)
                                .order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val frameCount = bytes.size / 2 / channelCount
                            for (f in 0 until frameCount) {
                                var sum = 0f
                                for (ch in 0 until channelCount) {
                                    sum += shorts[f * channelCount + ch] / 32768f
                                }
                                if (sampleCounter % stride == 0) {
                                    mono.add(sum / channelCount)
                                }
                                sampleCounter++
                            }
                        }
                        4 -> {
                            val floats = ByteBuffer.wrap(bytes)
                                .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                            val frameCount = bytes.size / 4 / channelCount
                            for (f in 0 until frameCount) {
                                var sum = 0f
                                for (ch in 0 until channelCount) {
                                    sum += floats[f * channelCount + ch]
                                }
                                if (sampleCounter % stride == 0) {
                                    mono.add(sum / channelCount)
                                }
                                sampleCounter++
                            }
                        }
                        21 -> {
                            val frameCount = bytes.size / 3 / channelCount
                            for (f in 0 until frameCount) {
                                var sum = 0f
                                for (ch in 0 until channelCount) {
                                    val o = (f * channelCount + ch) * 3
                                    // El byte alto ya viene con signo
                                    // (toInt() lo extiende): el OR deja
                                    // el valor de 24 bits con su signo
                                    // en 32 bits, sin arreglo manual.
                                    val v = (bytes[o].toInt() and 0xFF) or
                                        ((bytes[o + 1].toInt() and 0xFF) shl 8) or
                                        (bytes[o + 2].toInt() shl 16)
                                    sum += v / 8388608f
                                }
                                if (sampleCounter % stride == 0) {
                                    mono.add(sum / channelCount)
                                }
                                sampleCounter++
                            }
                        }
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputEos = true
                }
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                try {
                    val outFormat = codec.outputFormat
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        val rate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (rate > 0) stride = (rate / 1000).coerceAtLeast(1)
                    }
                    if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outEncoding = outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (mono.isEmpty()) return@withContext null
        waveformPeaks(mono.toFloatArray(), columns)
    } catch (e: CancellationException) {
        // RONDA 2 (2026-11-13, P1, n=903): como el LUFS (n=902), la
        // cancelación se relanza, no se traga como "sin onda".
        throw e
    } catch (_: Exception) {
        null
    } finally {
        try { codec?.stop() } catch (_: Exception) { }
        try { codec?.release() } catch (_: Exception) { }
        try { extractor?.release() } catch (_: Exception) { }
    }
}
