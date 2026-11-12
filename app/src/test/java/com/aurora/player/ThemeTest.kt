package com.aurora.player

import androidx.compose.ui.graphics.Color
import com.aurora.player.model.CoverShape
import com.aurora.player.model.ListDensity
import com.aurora.player.model.ThemeSettings
import com.aurora.player.ui.player.SleepTimerOption
import com.aurora.player.ui.theme.brightnessOf
import com.aurora.player.ui.theme.contrastOn
import com.aurora.player.ui.theme.coverShapeToCornerPercent
import com.aurora.player.ui.theme.darkenForLight
import com.aurora.player.ui.theme.listDensityToRowHeightDp
import com.aurora.player.ui.theme.parseHexOrNull
import com.aurora.player.ui.theme.readableTextOn
import com.aurora.player.ui.theme.resolveBackgroundColor
import com.aurora.player.ui.theme.resolvePlayerBarColor
import com.aurora.player.ui.theme.resolveSurfaceColor
import com.aurora.player.ui.theme.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aurora Player — tests de tema y personalización (ETAPA 7).
 * Lógica pura sin Android: HEX, forma de portada, densidad de lista,
 * presets de acento y temporizador. Nada de esto se finge en la UI.
 */
class ThemeTest {

    @Test
    fun `HEX valido se parsea y vuelve al mismo texto`() {
        assertEquals(Color(0xFF8CCBEB), parseHexOrNull("#8CCBEB"))
        assertEquals("#8CCBEB", parseHexOrNull("#8CCBEB")?.toHex())
        assertEquals(Color(0xFF6578B8), parseHexOrNull("6578B8"))
        assertEquals("#6578B8", parseHexOrNull("6578B8")?.toHex())
    }

    @Test
    fun `HEX invalido da null y no rompe el tema`() {
        assertNull(parseHexOrNull("ZZZZZZ"))
        assertNull(parseHexOrNull("#12345"))
        assertNull(parseHexOrNull("#1234567"))
        assertNull(parseHexOrNull(""))
        assertFalse(ThemeSettings.isValidHex("dorado"))
        assertNull(ThemeSettings.normalizeHex("#12G456"))
    }

    @Test
    fun `normalizeHex pone mayusculas y almohadilla`() {
        assertEquals("#8CCBEB", ThemeSettings.normalizeHex("8ccbeb"))
        assertEquals("#8CCBEB", ThemeSettings.normalizeHex("#8ccbeb"))
        assertTrue(ThemeSettings.isValidHex("#A99ACD"))
    }

    @Test
    fun `forma de portada - cuadrada 0, redondeada 12, circular 50`() {
        assertEquals(0, coverShapeToCornerPercent(CoverShape.SQUARE))
        assertEquals(12, coverShapeToCornerPercent(CoverShape.ROUNDED))
        assertEquals(50, coverShapeToCornerPercent(CoverShape.CIRCLE))
    }

    @Test
    fun `densidad de lista - compacta 52 dp, comoda 72 dp`() {
        assertEquals(52, listDensityToRowHeightDp(ListDensity.COMPACT))
        assertEquals(72, listDensityToRowHeightDp(ListDensity.COMFORTABLE))
    }

    @Test
    fun `presets de acento son HEX validos y ninguno es dorado ni amarillo`() {
        val prohibidos = setOf("#F2B705", "#FFD700", "#FFC107", "#FFFF00", "#FFEB3B")
        for ((name, hex) in ThemeSettings.ACCENT_PRESETS) {
            assertTrue("preset $name sin HEX válido", ThemeSettings.isValidHex(hex))
            assertFalse("preset $name es dorado/amarillo", hex.uppercase() in prohibidos)
            assertTrue(parseHexOrNull(hex) != null)
        }
        assertEquals("#8CCBEB", ThemeSettings.DEFAULT.accentHex)
    }

    @Test
    fun `temporizador de apagado - valores de minutos reales`() {
        assertEquals(0, SleepTimerOption.OFF.minutes)
        assertEquals(15, SleepTimerOption.MIN_15.minutes)
        assertEquals(30, SleepTimerOption.MIN_30.minutes)
        assertEquals(60, SleepTimerOption.MIN_60.minutes)
        assertEquals(-1, SleepTimerOption.END_OF_SONG.minutes)
    }

    @Test
    fun `readableTextOn - el color elegido manda y el texto se adapta`() {
        val darkText = Color(0xFF1A1C1E)
        val lightText = Color(0xFFDCE8F5)
        // Fondo claro + texto claro del usuario -> texto oscuro.
        assertEquals(darkText, readableTextOn(Color.White, "#DCE8F5", darkText, lightText))
        // Fondo claro + texto oscuro del usuario -> el del usuario.
        assertEquals(darkText, readableTextOn(Color.White, "#1A1C1E", darkText, lightText))
        // Fondo oscuro + texto claro del usuario -> el del usuario.
        assertEquals(lightText, readableTextOn(Color.Black, "#DCE8F5", darkText, lightText))
        // Fondo oscuro + texto oscuro del usuario -> texto claro.
        assertEquals(lightText, readableTextOn(Color.Black, "#1A1C1E", darkText, lightText))
    }

    @Test
    fun `darkenForLight oscurece el acento para leerlo en claro`() {
        val ice = Color(0xFF8CCBEB)
        val dark = darkenForLight(ice)
        assertTrue(brightnessOf(dark) < brightnessOf(ice))
        assertTrue(brightnessOf(dark) < 0.5f)
        assertEquals(ice.alpha, dark.alpha)
        assertEquals(Color.Black, darkenForLight(Color.Black))
        assertEquals(Color.White, contrastOn(dark))
    }

    @Test
    fun `contrastOn - un gris medio lleva texto negro, no blanco`() {
        // Gris medio #808080: luminancia ~0.216; el negro contrasta
        // 5.3:1 y el blanco solo 3.9:1. El umbral viejo (brillo 0.5)
        // ponía blanco y se leía mal.
        assertEquals(Color.Black, contrastOn(Color(0xFF808080)))
        assertEquals(Color.White, contrastOn(Color(0xFF1A1C1E)))
        assertEquals(Color.Black, contrastOn(Color(0xFFDCE8F5)))
        assertEquals(Color.White, contrastOn(Color.Black))
        assertEquals(Color.Black, contrastOn(Color.White))
    }

    @Test
    fun `readableTextOn - fondo de tono medio lleva texto oscuro`() {
        val darkText = Color(0xFF1A1C1E)
        val lightText = Color(0xFFDCE8F5)
        assertEquals(darkText, readableTextOn(Color(0xFF808080), "#DCE8F5", darkText, lightText))
        assertEquals(lightText, readableTextOn(Color(0xFF1A1C1E), "#DCE8F5", darkText, lightText))
    }

    @Test
    fun `Claro con fondo de fabrica pinta blanco, no negro`() {
        val def = ThemeSettings.DEFAULT
        assertEquals(Color.White, resolveBackgroundColor(def, useDark = false, isAmoled = false))
        assertEquals(Color.White, resolveBackgroundColor(def, useDark = false, isAmoled = true))
    }

    @Test
    fun `Claro con fondo personalizado respeta el HEX elegido`() {
        val custom = ThemeSettings.DEFAULT.copy(backgroundHex = "#202020")
        assertEquals(Color(0xFF202020), resolveBackgroundColor(custom, useDark = false, isAmoled = false))
    }

    @Test
    fun `Oscuro con fondo de fabrica es gris, distinto de AMOLED`() {
        val def = ThemeSettings.DEFAULT
        assertEquals(Color(0xFF1E1E26), resolveBackgroundColor(def, useDark = true, isAmoled = false))
        assertEquals(Color.Black, resolveBackgroundColor(def, useDark = true, isAmoled = true))
    }

    @Test
    fun `Claro con tarjeta de fabrica es clara, no negra`() {
        val def = ThemeSettings.DEFAULT
        assertEquals(Color(0xFFF2F2F2), resolveSurfaceColor(def, useDark = false))
        assertEquals(Color(0xFF17171B), resolveSurfaceColor(def, useDark = true))
        val custom = def.copy(cardHex = "#223344")
        assertEquals(Color(0xFF223344), resolveSurfaceColor(custom, useDark = false))
    }

    @Test
    fun `Claro con barra de fabrica usa la superficie`() {
        val def = ThemeSettings.DEFAULT
        val surface = Color(0xFFF2F2F2)
        assertEquals(surface, resolvePlayerBarColor(def, useDark = false, surface))
        val custom = def.copy(playerBarHex = "#445566")
        assertEquals(Color(0xFF445566), resolvePlayerBarColor(custom, useDark = false, surface))
    }
}
