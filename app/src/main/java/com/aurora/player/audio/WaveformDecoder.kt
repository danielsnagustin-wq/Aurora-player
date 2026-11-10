package com.aurora.player.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
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
        val stride = (sampleRate / 1000).coerceAtLeast(1)
        var sampleCounter = 0
        val bufferInfo = MediaCodec.BufferInfo()
        var inputEos = false
        var outputEos = false
        while (!outputEos) {
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
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputEos = true
                }
            }
        }
        if (mono.isEmpty()) return@withContext null
        waveformPeaks(mono.toFloatArray(), columns)
    } catch (_: Exception) {
        null
    } finally {
        try { codec?.stop() } catch (_: Exception) { }
        try { codec?.release() } catch (_: Exception) { }
        try { extractor?.release() } catch (_: Exception) { }
    }
}
