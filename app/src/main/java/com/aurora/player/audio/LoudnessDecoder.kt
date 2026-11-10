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
 * LUFS (Fase 2 audio, parte 2): decodificador que mide la sonoridad
 * integrada real de un archivo local. MediaExtractor + MediaCodec
 * sacan PCM, se remuestrea linealmente a 48 kHz (el filtro K solo
 * vale a 48 kHz), se pasa por el filtro K y se acumulan bloques de
 * 400 ms con solape del 75% hasta [integratedLufs].
 *
 * Todo en streaming y en Dispatchers.IO: nunca carga el archivo
 * entero en memoria ni toca la reproducción. Cualquier fallo
 * (formato sin decodificador, archivo corrupto) devuelve null:
 * sin LUFS medido no se muestra nada y el ReplayGain de la
 * etiqueta sigue mandando. El remuestreo lineal es una
 * aproximación honesta, documentada, no un medidor de laboratorio.
 */

private const val BLOCK_SAMPLES = 19200 // 400 ms a 48 kHz
private const val HOP_SAMPLES = 4800 // solape del 75%

/** Remuestreo lineal mono. Si la tasa ya es 48 kHz, devuelve tal cual. */
fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int = 48000): FloatArray {
    if (input.isEmpty()) return input
    if (fromRate == toRate) return input
    if (fromRate <= 0 || toRate <= 0) return input
    val ratio = fromRate.toDouble() / toRate.toDouble()
    val outSize = (input.size / ratio).toInt().coerceAtLeast(1)
    return FloatArray(outSize) { j ->
        val pos = j * ratio
        val i = pos.toInt().coerceIn(0, input.size - 1)
        val next = (i + 1).coerceAtMost(input.size - 1)
        val frac = (pos - i).toFloat()
        input[i] * (1f - frac) + input[next] * frac
    }
}

/**
 * Acumulador de bloques para LUFS: recibe PCM 48 kHz entrelazado,
 * filtra por canal y emite potencias por bloque. Puro y testeable.
 */
class LufsAccumulator(private val channelCount: Int) {
    private val filters = List(channelCount) { KWeighting48k() }
    private val windows = List(channelCount) { ArrayList<Double>(BLOCK_SAMPLES) }
    private val blockPowers = ArrayList<Double>()

    /** Peso BS.1770: 1.0 los dos primeros canales, 1.41 el resto. */
    private fun channelWeight(channel: Int): Double = if (channel < 2) 1.0 else 1.41

    /** PCM 48 kHz entrelazado (canales = channelCount). */
    fun add(interleaved48k: FloatArray) {
        val frames = interleaved48k.size / channelCount
        for (frame in 0 until frames) {
            for (ch in 0 until channelCount) {
                val y = filters[ch].process(interleaved48k[frame * channelCount + ch].toDouble())
                windows[ch].add(y)
            }
        }
        while (windows[0].size >= BLOCK_SAMPLES) {
            var blockPower = 0.0
            for (ch in 0 until channelCount) {
                val window = windows[ch]
                var sum = 0.0
                for (i in 0 until BLOCK_SAMPLES) {
                    val v = window[i]
                    sum += v * v
                }
                blockPower += (sum / BLOCK_SAMPLES) * channelWeight(ch)
            }
            blockPowers.add(blockPower)
            for (ch in 0 until channelCount) {
                windows[ch].subList(0, HOP_SAMPLES).clear()
            }
        }
    }

    /** Sonoridad integrada de lo medido, o null si no hubo audio audible. */
    fun finish(): Double? = integratedLufs(blockPowers)
}

/** Mide el LUFS integrado de un archivo local, o null si no se pudo medir. */
suspend fun measureFileLufs(context: Context, uri: Uri): Double? =
    withContext(Dispatchers.IO) {
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

            val accumulator = LufsAccumulator(channelCount)
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
                        decodePcmChunk(
                            outBuffer, bufferInfo, channelCount,
                            sampleRate, accumulator,
                        )
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputEos = true
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // El formato de salida puede cambiar la tasa: se lee
                    // en cada trozo desde bufferInfo, no se cachea.
                }
            }
            accumulator.finish()
        } catch (_: Exception) {
            null
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            try { extractor?.release() } catch (_: Exception) { }
        }
    }

/**
 * Un trozo PCM 16-bit del decodificador a muestras 48 kHz en el
 * acumulador. El remuestreo se hace por trozo: la discontinuidad en
 * el borde es despreciable para una sonoridad integrada.
 */
private fun decodePcmChunk(
    buffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    channelCount: Int,
    sampleRate: Int,
    accumulator: LufsAccumulator,
) {
    val bytes = ByteArray(info.size)
    buffer.position(info.offset)
    buffer.get(bytes)
    val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    val frameCount = bytes.size / 2 / channelCount
    if (frameCount <= 0) return
    val resampledChannels = List(channelCount) { ch ->
        val mono = FloatArray(frameCount) { f ->
            shorts[f * channelCount + ch] / 32768f
        }
        resampleLinear(mono, sampleRate)
    }
    val frames = resampledChannels[0].size
    val interleaved = FloatArray(frames * channelCount)
    for (f in 0 until frames) {
        for (ch in 0 until channelCount) {
            interleaved[f * channelCount + ch] =
                resampledChannels[ch].getOrElse(f) { 0f }
        }
    }
    accumulator.add(interleaved)
}
