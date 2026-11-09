package com.aurora.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Aurora Player — balance izquierda/derecha (SPEC 005, FR-001).
 *
 * Idea de BlackPlayer/Musicolet (ideas solo, código propio): un
 * AudioProcessor de ExoPlayer que escala cada canal por separado.
 * El balance es -1 (todo a la izquierda) .. 0 (centro) .. +1 (todo
 * a la derecha). Ley lineal sin refuerzo: el canal que se aleja
 * baja, el otro queda en 1.0; nunca se sube el volumen ni se
 * distorsiona. Solo estéreo (2 canales): mono o multicanal pasan
 * sin tocarse, nunca se adivina qué canal es cuál.
 *
 * PCM 16/24/32 bits y float. Otros formatos pasan sin tocarse
 * (onConfigure lanza y ExoPlayer lo saca de la cadena, como hace
 * ReplayGainProcessor).
 */

/**
 * Ganancias (izquierda, derecha) para un balance en [-1, 1].
 * 0 -> (1, 1); +1 -> (0, 1); -1 -> (1, 0); +0.5 -> (0.5, 1).
 * Función pura, testeable.
 */
fun balanceGains(balance: Float): Pair<Double, Double> {
    val b = balance.coerceIn(-1f, 1f).toDouble()
    val left = if (b <= 0.0) 1.0 else 1.0 - b
    val right = if (b >= 0.0) 1.0 else 1.0 + b
    return left to right
}

@UnstableApi
class ChannelBalanceAudioProcessor : BaseAudioProcessor() {

    private var encoding = C.ENCODING_INVALID
    private var channelCount = 0

    @Volatile
    var enabled = false

    @Volatile
    private var leftGain = 1.0

    @Volatile
    private var rightGain = 1.0

    /** Balance objetivo (-1 = izquierda, 0 = centro, +1 = derecha). */
    @Synchronized
    fun setBalance(balance: Float) {
        val (left, right) = balanceGains(balance)
        leftGain = left
        rightGain = right
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        channelCount = inputAudioFormat.channelCount
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
        val left = leftGain
        val right = rightGain
        if (!enabled || channelCount != 2 || (left == 1.0 && right == 1.0)) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.remaining() >= 4) {
                    val l = inputBuffer.short.toInt()
                    val r = inputBuffer.short.toInt()
                    out.putShort(((l * left).toInt().coerceIn(-32768, 32767)).toShort())
                    out.putShort(((r * right).toInt().coerceIn(-32768, 32767)).toShort())
                }
                // Muestra suelta sin pareja (no debería pasar): se copia.
                if (inputBuffer.hasRemaining()) out.put(inputBuffer)
            }
            C.ENCODING_PCM_24BIT -> {
                while (inputBuffer.remaining() >= 6) {
                    val l = read24(inputBuffer)
                    val r = read24(inputBuffer)
                    write24(out, (l * left).toInt().coerceIn(-8388608, 8388607))
                    write24(out, (r * right).toInt().coerceIn(-8388608, 8388607))
                }
                if (inputBuffer.hasRemaining()) out.put(inputBuffer)
            }
            C.ENCODING_PCM_32BIT -> {
                while (inputBuffer.remaining() >= 8) {
                    val l = inputBuffer.int.toLong()
                    val r = inputBuffer.int.toLong()
                    out.putInt((l * left).toLong().coerceIn(-2147483648L, 2147483647L).toInt())
                    out.putInt((r * right).toLong().coerceIn(-2147483648L, 2147483647L).toInt())
                }
                if (inputBuffer.hasRemaining()) out.put(inputBuffer)
            }
            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.remaining() >= 8) {
                    val l = inputBuffer.float
                    val r = inputBuffer.float
                    out.putFloat((l * left).toFloat().coerceIn(-1f, 1f))
                    out.putFloat((r * right).toFloat().coerceIn(-1f, 1f))
                }
                if (inputBuffer.hasRemaining()) out.put(inputBuffer)
            }
            else -> {
                // Codificación desconocida: pasa sin tocarse.
                out.put(inputBuffer)
            }
        }
        out.flip()
    }

    private fun read24(buffer: ByteBuffer): Int {
        val b0 = buffer.get().toInt() and 0xFF
        val b1 = buffer.get().toInt() and 0xFF
        val b2 = buffer.get().toInt() and 0xFF
        var sample = b0 or (b1 shl 8) or (b2 shl 16)
        if (sample and 0x800000 != 0) sample -= 0x1000000
        return sample
    }

    private fun write24(buffer: ByteBuffer, sample: Int) {
        buffer.put((sample and 0xFF).toByte())
        buffer.put((sample shr 8 and 0xFF).toByte())
        buffer.put((sample shr 16 and 0xFF).toByte())
    }
}
