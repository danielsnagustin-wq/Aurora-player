package com.aurora.player

import com.aurora.player.ui.player.volumeStepsForDrag
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeGestureTest {

    @Test
    fun `sin recorrido no hay pasos`() {
        assertEquals(0, volumeStepsForDrag(0f, 56f))
        assertEquals(0, volumeStepsForDrag(30f, 56f))
        assertEquals(0, volumeStepsForDrag(-30f, 56f))
    }

    @Test
    fun `bajar el dedo baja el volumen (pasos positivos)`() {
        assertEquals(1, volumeStepsForDrag(60f, 56f))
        assertEquals(2, volumeStepsForDrag(120f, 56f))
    }

    @Test
    fun `subir el dedo sube el volumen (pasos negativos)`() {
        assertEquals(-1, volumeStepsForDrag(-60f, 56f))
        assertEquals(-2, volumeStepsForDrag(-120f, 56f))
    }
}
