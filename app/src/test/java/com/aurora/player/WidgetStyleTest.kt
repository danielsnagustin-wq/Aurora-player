package com.aurora.player

import com.aurora.player.widget.WidgetStyle
import com.aurora.player.widget.parseWidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStyleTest {

    @Test
    fun parseaEstilosConocidos() {
        assertEquals(WidgetStyle.COVER, parseWidgetStyle("COVER", WidgetStyle.CLASSIC))
        assertEquals(WidgetStyle.COMPACT, parseWidgetStyle("COMPACT", WidgetStyle.LARGE))
    }

    @Test
    fun desconocidoONulo_usaDefecto() {
        assertEquals(WidgetStyle.CLASSIC, parseWidgetStyle("ENORME", WidgetStyle.CLASSIC))
        assertEquals(WidgetStyle.LARGE, parseWidgetStyle(null, WidgetStyle.LARGE))
        assertEquals(WidgetStyle.CLASSIC, parseWidgetStyle("", WidgetStyle.CLASSIC))
    }

    @Test
    fun cuatroEstilosConEtiqueta() {
        assertEquals(4, WidgetStyle.entries.size)
        assertEquals(4, WidgetStyle.entries.map { it.label }.toSet().size)
    }
}
