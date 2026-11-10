package com.aurora.player

import com.aurora.player.model.GestureAction
import com.aurora.player.model.parseGestureAction
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureActionTest {

    @Test
    fun parseaAccionesConocidas() {
        assertEquals(GestureAction.NEXT, parseGestureAction("NEXT", GestureAction.FAVORITE))
        assertEquals(GestureAction.PLAY_PAUSE, parseGestureAction("PLAY_PAUSE", GestureAction.NEXT))
    }

    @Test
    fun desconocidoONulo_usaDefecto() {
        assertEquals(GestureAction.FAVORITE, parseGestureAction("VOLUMEN", GestureAction.FAVORITE))
        assertEquals(GestureAction.NEXT, parseGestureAction(null, GestureAction.NEXT))
        assertEquals(GestureAction.NEXT, parseGestureAction("", GestureAction.NEXT))
    }

    @Test
    fun cuatroAccionesConEtiqueta() {
        assertEquals(4, GestureAction.entries.size)
        assertEquals(4, GestureAction.entries.map { it.label }.toSet().size)
    }
}
