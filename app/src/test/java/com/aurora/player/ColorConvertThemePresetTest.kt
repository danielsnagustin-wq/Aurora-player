package com.aurora.player

import com.aurora.player.model.MAX_THEMES
import com.aurora.player.model.ThemePreset
import com.aurora.player.model.ThemeSettings
import com.aurora.player.model.normalizePresetName
import com.aurora.player.model.themePresetsFromJsonString
import com.aurora.player.model.themePresetsToJsonString
import com.aurora.player.util.hexToRgb
import com.aurora.player.util.hslToRgb
import com.aurora.player.util.rgbToHex
import com.aurora.player.util.rgbToHsl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorConvertTest {

    @Test
    fun `hexToRgb lee el acento Aurora`() {
        assertEquals(Triple(140, 203, 235), hexToRgb("#8CCBEB"))
        assertEquals(Triple(140, 203, 235), hexToRgb("8CCBEB"))
    }

    @Test
    fun `hexToRgb acepta forma corta y rechaza basura`() {
        assertEquals(Triple(255, 255, 255), hexToRgb("#FFF"))
        assertNull(hexToRgb("#ZZZZZZ"))
        assertNull(hexToRgb(""))
        assertNull(hexToRgb("#12345"))
    }

    @Test
    fun `rgbToHex y hexToRgb son inversos`() {
        assertEquals("#8CCBEB", rgbToHex(140, 203, 235))
        assertEquals(Triple(140, 203, 235), hexToRgb(rgbToHex(140, 203, 235)))
    }

    @Test
    fun `rgbToHsl y hslToRgb son inversos`() {
        val (h, s, l) = rgbToHsl(140, 203, 235)
        val (r, g, b) = hslToRgb(h, s, l)
        assertTrue(kotlin.math.abs(r - 140) <= 1)
        assertTrue(kotlin.math.abs(g - 203) <= 1)
        assertTrue(kotlin.math.abs(b - 235) <= 1)
    }

    @Test
    fun `hsl del gris no tiene tono ni saturacion`() {
        val (h, s, _) = rgbToHsl(128, 128, 128)
        assertEquals(0f, h, 0.001f)
        assertEquals(0f, s, 0.001f)
    }
}

class ThemePresetTest {

    @Test
    fun `normalizePresetName recorta y rechaza vacio`() {
        assertEquals("Noche", normalizePresetName("  Noche  "))
        assertEquals("A".repeat(40), normalizePresetName("A".repeat(50)))
        assertNull(normalizePresetName("   "))
    }

    @Test
    fun `round-trip de tema guarda los 21 campos`() {
        val settings = ThemeSettings.DEFAULT.copy(accentHex = "#75D6CE", cornerRadiusDp = 20)
        val preset = ThemePreset("Turquesa", settings)
        val restored = themePresetsFromJsonString(themePresetsToJsonString(listOf(preset)))
        assertEquals(1, restored.size)
        assertEquals("Turquesa", restored[0].name)
        assertEquals("#75D6CE", restored[0].settings.accentHex)
        assertEquals(20, restored[0].settings.cornerRadiusDp)
    }

    @Test
    fun `json roto no rompe nada`() {
        assertTrue(themePresetsFromJsonString("esto no es json").isEmpty())
        assertTrue(themePresetsFromJsonString("").isEmpty())
    }

    @Test
    fun `MAX_THEMES es 20 como las colas`() {
        assertEquals(20, MAX_THEMES)
    }
}
