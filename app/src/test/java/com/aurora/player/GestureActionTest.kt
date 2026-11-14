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
    fun minusculasYMixtas_tambienParsean() {
        // RONDA 2 (2026-11-13, P2): pin de n=881 — parseGestureAction
        // es ignoreCase: los valores viejos guardados en minúscula
        // ("next", "favorite") casan con su enum en vez de caer al
        // defecto en silencio.
        assertEquals(GestureAction.NEXT, parseGestureAction("next", GestureAction.FAVORITE))
        assertEquals(GestureAction.FAVORITE, parseGestureAction("favorite", GestureAction.NEXT))
        assertEquals(GestureAction.PLAY_PAUSE, parseGestureAction("play_pause", GestureAction.NEXT))
        assertEquals(GestureAction.FAST_FORWARD, parseGestureAction("Fast_Forward", GestureAction.NEXT))
        assertEquals(GestureAction.TOGGLE_MUTE, parseGestureAction("toggle_mute", GestureAction.NEXT))
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
