package com.aurora.player

import com.aurora.player.model.ThemeSettings
import com.aurora.player.ui.theme.parseHexOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aurora Player — tests de personalización (Etapa 1, lógica pura). */
class ThemeSettingsTest {

    @Test
    fun `por defecto es AMOLED negro puro`() {
        assertEquals("#000000", ThemeSettings.DEFAULT.backgroundHex)
        assertEquals("#111111", ThemeSettings.DEFAULT.cardHex)
        assertEquals("#8CCBEB", ThemeSettings.DEFAULT.accentHex)
    }

    @Test
    fun `ningun preset de acento es dorado ni amarillo`() {
        val forbidden = setOf("#F2B705", "#FFD700", "#FFFF00", "#FFC107")
        for ((_, hex) in ThemeSettings.ACCENT_PRESETS) {
            assertTrue("$hex no debe ser dorado/amarillo", hex.uppercase() !in forbidden)
        }
    }

    @Test
    fun `HEX valido se acepta y se normaliza`() {
        assertTrue(ThemeSettings.isValidHex("#8CCBEB"))
        assertTrue(ThemeSettings.isValidHex("8ccbeb"))
        assertEquals("#8CCBEB", ThemeSettings.normalizeHex("8ccbeb"))
    }

    @Test
    fun `HEX invalido se rechaza`() {
        assertTrue(!ThemeSettings.isValidHex("#8CCB"))
        assertTrue(!ThemeSettings.isValidHex("#GGGGGG"))
        assertTrue(!ThemeSettings.isValidHex(""))
        assertNull(ThemeSettings.normalizeHex("no-es-hex"))
    }

    @Test
    fun `parseHexOrNull devuelve null con HEX roto`() {
        assertNull(parseHexOrNull("#ZZZZZZ"))
        assertNull(parseHexOrNull(""))
        assertTrue(parseHexOrNull("#8CCBEB") != null)
    }
}
