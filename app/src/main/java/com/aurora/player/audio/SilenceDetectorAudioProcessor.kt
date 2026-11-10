package com.aurora.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Aurora Player — saltar silencio (RONDA 6).
 *
 * Idea de Metrolist (SilenceDetectorAudioProcessor, GPL — solo la
 * idea, código propio): un AudioProcessor que pasa el audio sin
 * tocarlo y avisa cuando detecta un silencio largo. La diferencia
 * con Metrolist: este NO lanza si el formato no es PCM 16 bits —
 * en 24/32 bits o float pasa sin detectar (romper la reproducción
 * de un FLAC 24 bits por el detector sería peor que no saltar nada).
 *
 * - Silencio = muestras con |valor| < 256 durante 2 s seguidos.
 * - [onLongSilence] se llama UNA vez por segmento silencioso.
 * - Al buscar (seek) el estado se resetea: el silencio que sigue a
 *   un salto es un segmento nuevo.
 * - Solo detecta PCM 16 bits (lo que saca ExoPlayer por defecto);
 *   otros formatos pasan sin tocarse.
 */
@UnstableApi
class SilenceDetectorAudioProcessor(
    private val minSilenceDurationUs: Long = 2_000_000L,
    private val silenceThreshold: Int = 256,
    private val onLongSilence: () -> Unit,
) : BaseAudioProcessor() {

    @Volatile
    var enabled = false

    private var sampleRate = 0
    private var channelCount = 0
    private var detectThisFormat = false

    @Volatile
    private var consecutiveSilentFrames = 0L

    @Volatile
    private var notifiedThisSilence = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        // Solo 16 bits se detecta; el resto pasa sin tocarse.
        detectThisFormat = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        clearSilenceState()
        return inputAudioFormat
    }

    override fun onFlush() {
        clearSilenceState()
    }

    private fun clearSilenceState() {
        consecutiveSilentFrames = 0L
        notifiedThisSilence = false
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        // La detección se hace sobre una copia: el búfer original se
        // consume al copiarlo a la salida (si no se consume, el sink
        // cree que no se procesó y se atasca).
        val probe = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
        if (!enabled || !detectThisFormat || sampleRate <= 0 || channelCount <= 0) {
            return
        }
        val silentFramesNeeded = minSilenceDurationUs * sampleRate / 1_000_000L
        while (probe.remaining() >= 2 * channelCount) {
            var frameSilent = true
            for (ch in 0 until channelCount) {
                if (abs(probe.short.toInt()) >= silenceThreshold) {
                    frameSilent = false
                    // Consumir el resto de canales de este frame.
                    repeat(channelCount - ch - 1) { probe.short }
                    break
                }
            }
            if (frameSilent) {
                consecutiveSilentFrames++
                if (!notifiedThisSilence && consecutiveSilentFrames >= silentFramesNeeded) {
                    notifiedThisSilence = true
                    // El callback corre en el hilo de audio: si tira, el
                    // proceso muere con él. Nunca se deja escapar nada.
                    try {
                        onLongSilence()
                    } catch (_: Exception) {
                        // El servicio registra su propio error; aquí solo
                        // se protege el hilo de audio.
                    }
                }
            } else {
                consecutiveSilentFrames = 0L
                notifiedThisSilence = false
            }
        }
    }
}
