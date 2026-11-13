package com.aurora.player

import com.aurora.player.model.StreamQuality
import com.aurora.player.util.effectiveStreamQuality
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Calidad por conexión (copia BitChord, slice 3): el resolvedor puro
 * elige la calidad de Wi-Fi con Wi-Fi y la de móvil sin ella.
 */
class NetworkQualityTest {

    @Test
    fun `con wifi usa la calidad de wifi`() {
        assertEquals(
            StreamQuality.HIGH,
            effectiveStreamQuality(true, StreamQuality.HIGH, StreamQuality.LOW),
        )
    }

    @Test
    fun `con datos usa la calidad de movil`() {
        assertEquals(
            StreamQuality.LOW,
            effectiveStreamQuality(false, StreamQuality.HIGH, StreamQuality.LOW),
        )
    }

    @Test
    fun `con wifi y media usa media`() {
        assertEquals(
            StreamQuality.MEDIUM,
            effectiveStreamQuality(true, StreamQuality.MEDIUM, StreamQuality.LOW),
        )
    }

    @Test
    fun `con datos y alta en movil usa alta`() {
        assertEquals(
            StreamQuality.HIGH,
            effectiveStreamQuality(false, StreamQuality.LOW, StreamQuality.HIGH),
        )
    }
}
