package com.aurora.player

import com.aurora.player.audio.balanceGains
import org.junit.Assert.assertEquals
import org.junit.Test

/** Aurora Player — tests de balance L/R (spec 005, FR-001, lógica pura). */
class ChannelBalanceTest {

    private fun assertGains(balance: Float, left: Double, right: Double) {
        val (l, r) = balanceGains(balance)
        assertEquals(left, l, 0.0001)
        assertEquals(right, r, 0.0001)
    }

    @Test
    fun `centro no toca ningun canal`() {
        assertGains(0f, 1.0, 1.0)
    }

    @Test
    fun `todo a la derecha apaga la izquierda`() {
        assertGains(1f, 0.0, 1.0)
    }

    @Test
    fun `todo a la izquierda apaga la derecha`() {
        assertGains(-1f, 1.0, 0.0)
    }

    @Test
    fun `mitad a la derecha baja la izquierda a la mitad`() {
        assertGains(0.5f, 0.5, 1.0)
    }

    @Test
    fun `mitad a la izquierda baja la derecha a la mitad`() {
        assertGains(-0.5f, 1.0, 0.5)
    }

    @Test
    fun `fuera de rango se recorta`() {
        assertGains(2f, 0.0, 1.0)
        assertGains(-2f, 1.0, 0.0)
    }

    @Test
    fun `ningun canal sube nunca por encima de 1`() {
        for (b in listOf(-1f, -0.7f, -0.2f, 0f, 0.3f, 0.8f, 1f)) {
            val (l, r) = balanceGains(b)
            assert(l in 0.0..1.0) { "izquierda fuera de rango con balance $b: $l" }
            assert(r in 0.0..1.0) { "derecha fuera de rango con balance $b: $r" }
        }
    }
}
