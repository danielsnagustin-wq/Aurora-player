package com.aurora.player

import com.aurora.player.lyrics.nextIndexToPrefetch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Precarga de letras (idea de BitChord): qué canción sigue.
 * Lógica pura, sin Android, para probarla en la JVM.
 */
class LyricsPrefetchTest {

    @Test
    fun `en medio de la cola sigue la siguiente`() {
        assertEquals(1, nextIndexToPrefetch(0, 5, false))
        assertEquals(4, nextIndexToPrefetch(3, 5, false))
    }

    @Test
    fun `al final sin repetir no hay siguiente`() {
        assertNull(nextIndexToPrefetch(4, 5, false))
    }

    @Test
    fun `al final repitiendo todo vuelve al principio`() {
        assertEquals(0, nextIndexToPrefetch(4, 5, true))
    }

    @Test
    fun `una sola cancion no se precarga a si misma`() {
        assertNull(nextIndexToPrefetch(0, 1, true))
        assertNull(nextIndexToPrefetch(0, 1, false))
    }

    @Test
    fun `cola vacia o indice invalido no precargan`() {
        assertNull(nextIndexToPrefetch(0, 0, false))
        assertNull(nextIndexToPrefetch(-1, 5, false))
        assertNull(nextIndexToPrefetch(5, 5, false))
    }
}
