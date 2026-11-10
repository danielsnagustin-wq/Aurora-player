package com.aurora.player

import com.aurora.player.model.LAB_THEME_PRESETS
import com.aurora.player.model.ThemeSettings
import com.aurora.player.model.labThemePreset
import com.aurora.player.model.themePresetFromJsonValue
import com.aurora.player.model.themePresetToJsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Laboratorio de diseño — las 3 propuestas son temas reales, no maquetas. */
class LabThemePresetsTest {

    @Test
    fun `hay exactamente tres propuestas con nombres distintos`() {
        assertEquals(3, LAB_THEME_PRESETS.size)
        assertEquals(3, LAB_THEME_PRESETS.map { it.name }.toSet().size)
        assertTrue(LAB_THEME_PRESETS.any { it.name == "Aurora Hielo" })
        assertTrue(LAB_THEME_PRESETS.any { it.name == "Medianoche Lavanda" })
        assertTrue(LAB_THEME_PRESETS.any { it.name == "Turquesa Vivo" })
    }

    @Test
    fun `todos los colores son HEX validos y ninguno es dorado ni amarillo`() {
        val forbidden = setOf("#F2B705", "#FFD700", "#FFFF00", "#FFC107", "#FFEB3B")
        for (preset in LAB_THEME_PRESETS) {
            val s = preset.settings
            val hexes = listOf(
                s.accentHex, s.backgroundHex, s.cardHex, s.textHex,
                s.secondaryTextHex, s.buttonHex, s.playerBarHex, s.visualizerHex,
            )
            for (hex in hexes) {
                assertTrue("${preset.name}: $hex debe ser HEX válido", ThemeSettings.isValidHex(hex))
                assertTrue("${preset.name}: $hex no debe ser dorado/amarillo", hex.uppercase() !in forbidden)
            }
        }
    }

    @Test
    fun `los tres acentos son distintos entre si`() {
        val accents = LAB_THEME_PRESETS.map { it.settings.accentHex.uppercase() }
        assertEquals(3, accents.toSet().size)
        assertEquals("#8CCBEB", accents[0])
        assertEquals("#A99ACD", accents[1])
        assertEquals("#75D6CE", accents[2])
    }

    @Test
    fun `cada propuesta usa su fondo de reproductor y su forma de biblioteca`() {
        val hielo = labThemePreset("Aurora Hielo")!!
        assertEquals(com.aurora.player.model.PlayerBackground.COVER, hielo.settings.playerBackground)
        assertTrue(!hielo.settings.albumGrid)
        val lavanda = labThemePreset("Medianoche Lavanda")!!
        assertEquals(com.aurora.player.model.PlayerBackground.GRADIENT, lavanda.settings.playerBackground)
        assertEquals(20, lavanda.settings.cornerRadiusDp)
        val turquesa = labThemePreset("Turquesa Vivo")!!
        assertTrue(turquesa.settings.albumGrid)
        assertEquals(2, turquesa.settings.albumGridColumns)
    }

    @Test
    fun `la busqueda por nombre ignora mayusculas y espacios`() {
        assertNotNull(labThemePreset("aurora hielo"))
        assertNotNull(labThemePreset("  Medianoche Lavanda "))
        assertNull(labThemePreset("No existe"))
        assertNull(labThemePreset(""))
        assertNull(labThemePreset("   "))
    }

    @Test
    fun `cada propuesta sobrevive a guardar y cargar como JSON`() {
        for (preset in LAB_THEME_PRESETS) {
            val restored = themePresetFromJsonValue(themePresetToJsonValue(preset))
            assertNotNull("${preset.name} debe restaurarse", restored)
            assertEquals(preset.name, restored!!.name)
            assertEquals(preset.settings, restored.settings)
        }
    }
}
