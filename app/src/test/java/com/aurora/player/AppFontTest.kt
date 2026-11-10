package com.aurora.player

import com.aurora.player.model.AppFont
import com.aurora.player.model.parseAppFont
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontTest {

    @Test
    fun parseaFuentesConocidas() {
        assertEquals(AppFont.SERIF, parseAppFont("SERIF", AppFont.DEFAULT))
        assertEquals(AppFont.MONO, parseAppFont("MONO", AppFont.SANS))
    }

    @Test
    fun desconocidoONulo_usaDefecto() {
        assertEquals(AppFont.DEFAULT, parseAppFont("COMIC", AppFont.DEFAULT))
        assertEquals(AppFont.SANS, parseAppFont(null, AppFont.SANS))
        assertEquals(AppFont.DEFAULT, parseAppFont("", AppFont.DEFAULT))
    }

    @Test
    fun sieteFuentesConEtiquetaYFamilia() {
        assertEquals(7, AppFont.entries.size)
        assertEquals(7, AppFont.entries.map { it.label }.toSet().size)
        // Solo la predeterminada no tiene familia del sistema.
        assertEquals(1, AppFont.entries.count { it.family.isEmpty() })
        assertEquals(6, AppFont.entries.count { it.family.isNotEmpty() })
    }
}
