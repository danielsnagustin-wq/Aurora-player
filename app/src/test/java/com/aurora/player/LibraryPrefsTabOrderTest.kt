package com.aurora.player

import com.aurora.player.data.normalizeTabOrderList
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Orden de pestañas (copia BitChord, slice 2): la normalización pura
 * de LibraryPrefsStore. El orden resultante siempre es completo
 * (0..7), sin repetidos y sin índices fuera de rango.
 */
class LibraryPrefsTabOrderTest {

    @Test
    fun `vacio da el orden natural`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), normalizeTabOrderList(emptyList()))
    }

    @Test
    fun `orden parcial conserva su orden y completa al final`() {
        assertEquals(
            listOf(3, 1, 0, 2, 4, 5, 6, 7),
            normalizeTabOrderList(listOf(3, 1)),
        )
    }

    @Test
    fun `repetidos se quitan quedandose el primero`() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 6, 7),
            normalizeTabOrderList(listOf(0, 0, 1, 1)),
        )
    }

    @Test
    fun `indices fuera de rango se ignoran`() {
        assertEquals(
            listOf(2, 0, 1, 3, 4, 5, 6, 7),
            normalizeTabOrderList(listOf(9, -1, 2)),
        )
    }

    @Test
    fun `orden completo se respeta tal cual`() {
        val reversed = listOf(7, 6, 5, 4, 3, 2, 1, 0)
        assertEquals(reversed, normalizeTabOrderList(reversed))
    }

    @Test
    fun `una sola pestana va primera y el resto sigue`() {
        assertEquals(
            listOf(6, 0, 1, 2, 3, 4, 5, 7),
            normalizeTabOrderList(listOf(6)),
        )
    }
}
