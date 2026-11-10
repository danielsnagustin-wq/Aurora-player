package com.aurora.player

import com.aurora.player.model.AppFont
import com.aurora.player.model.TextSize
import com.aurora.player.model.ThemePreset
import com.aurora.player.model.ThemeSettings
import com.aurora.player.model.themePresetFromJsonValue
import com.aurora.player.model.themePresetToJsonValue
import com.aurora.player.online.JsonParser
import com.aurora.player.util.toJsonString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Skins por archivo (Fase 2 app, AIMP): un tema es un archivo JSON
 * con nombre + los ajustes. La vuelta completa (escribir el archivo
 * y leerlo) tiene que devolver el mismo tema.
 */
class SkinFileTest {

    private fun presetDePrueba() = ThemePreset(
        "Noche azul",
        ThemeSettings(
            accentHex = "#8CCBEB",
            backgroundHex = "#000000",
            textSize = TextSize.LARGE,
            fontFamily = AppFont.SERIF,
        ),
    )

    @Test
    fun vueltaCompleta_devuelveElMismoTema() {
        val original = presetDePrueba()
        val text = themePresetToJsonValue(original).toJsonString()
        val leido = themePresetFromJsonValue(JsonParser.parse(text))
        assertEquals(original, leido)
    }

    @Test
    fun jsonRoto_noEsTema() {
        // El parser estricto tira con JSON roto: el archivo no se
        // lee, y el llamador (Ajustes) muestra "no es un tema".
        assertNull(runCatching { JsonParser.parse("{no es json") }.getOrNull())
        assertNull(themePresetFromJsonValue(JsonParser.parse("[]")))
        assertNull(themePresetFromJsonValue(JsonParser.parse("{\"name\": \"\"}")))
    }

    @Test
    fun nombreSeRecortaA40() {
        val largo = ThemePreset("x".repeat(100), ThemeSettings())
        val text = themePresetToJsonValue(largo).toJsonString()
        val leido = themePresetFromJsonValue(JsonParser.parse(text))
        assertEquals(40, leido?.name?.length)
    }
}
