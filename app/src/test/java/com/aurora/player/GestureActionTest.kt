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
    fun sieteAccionesConEtiqueta() {
        assertEquals(7, GestureAction.entries.size)
        assertEquals(7, GestureAction.entries.map { it.label }.toSet().size)
        assertEquals(GestureAction.FAST_FORWARD, parseGestureAction("FAST_FORWARD", GestureAction.NEXT))
        assertEquals(GestureAction.REWIND, parseGestureAction("REWIND", GestureAction.NEXT))
        assertEquals(GestureAction.TOGGLE_MUTE, parseGestureAction("TOGGLE_MUTE", GestureAction.NEXT))
    }
}
