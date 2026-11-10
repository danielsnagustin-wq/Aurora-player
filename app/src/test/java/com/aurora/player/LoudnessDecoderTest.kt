package com.aurora.player

import com.aurora.player.audio.LufsAccumulator
import com.aurora.player.audio.resampleLinear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessDecoderTest {

    @Test
    fun remuestreo_mismaTasa_noToca() {
        val input = floatArrayOf(0.1f, -0.2f, 0.3f)
        assertEquals(input.toList(), resampleLinear(input, 48000).toList())
    }

    @Test
    fun remuestreo_bajaTasa_alarga() {
        // 24000 -> 48000 duplica las muestras (interpoladas).
        val input = FloatArray(100) { 0.5f }
        val out = resampleLinear(input, 24000)
        assertEquals(200, out.size)
        assertTrue(out.all { it == 0.5f })
    }

    @Test
    fun acumulador_silencio_daNull() {
        val acc = LufsAccumulator(2)
        acc.add(FloatArray(48000 * 2)) // 1 s de silencio estéreo
        assertNull(acc.finish())
    }

    @Test
    fun acumulador_tonoConstante_enRangoSano() {
        // 2 s de seno 1 kHz a -20 dBFS en estéreo a 48 kHz.
        val acc = LufsAccumulator(2)
        val frames = 96000
        val interleaved = FloatArray(frames * 2) { i ->
            val frame = i / 2
            (0.1 * sin(2.0 * PI * 1000.0 * frame / 48000.0)).toFloat()
        }
        acc.add(interleaved)
        val lufs = acc.finish()
        assertTrue(lufs != null)
        // Dos canales suman +3 LU: -19.99 LUFS según la misma simulación.
        assertTrue(lufs!! > -21.5 && lufs < -18.5)
    }
}
