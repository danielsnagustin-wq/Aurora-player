package com.aurora.player.model

/**
 * Laboratorio de diseño UI/UX — las 3 propuestas visuales como temas
 * reales de Aurora Player (Fase 4 del AI DEVELOPMENT STUDIO,
 * 2026-11-10). No son diseños estáticos: cada propuesta es un
 * [ThemePreset] completo que la app puede aplicar, guardar y
 * exportar con la maquinaria que ya existe (ThemePresetStore,
 * skins por archivo). Prototipos con capturas reales en
 * `AI-DEVELOPMENT-STUDIO/design-system/propuestas/`.
 *
 * - Aurora Hielo: evolución de la actual (negro AMOLED + azul hielo).
 * - Medianoche Lavanda: degradado lavanda, tipografía grande, onda.
 * - Turquesa Vivo: cuadrícula de álbumes, espectro, cola en fichas.
 *
 * Reglas: ningún preset usa dorado ni amarillo; todos los HEX son
 * "#RRGGBB" válidos; los 3 acentos son distintos entre sí.
 */
val LAB_THEME_PRESETS: List<ThemePreset> = listOf(
    ThemePreset(
        name = "Aurora Hielo",
        settings = ThemeSettings(
            themeMode = ThemeMode.AMOLED,
            accentHex = "#8CCBEB",
            backgroundHex = "#000000",
            cardHex = "#17171B",
            textHex = "#DCE8F5",
            secondaryTextHex = "#B0BAC8",
            buttonHex = "#8CCBEB",
            playerBarHex = "#17171B",
            visualizerHex = "#A99ACD",
            dynamicColor = true,
            cornerRadiusDp = 16,
            cardStyle = CardStyle.SOLID,
            playerBackground = PlayerBackground.COVER,
            albumGrid = false,
            albumGridColumns = 2,
        ),
    ),
    ThemePreset(
        name = "Medianoche Lavanda",
        settings = ThemeSettings(
            themeMode = ThemeMode.AMOLED,
            accentHex = "#A99ACD",
            backgroundHex = "#14101F",
            cardHex = "#241D38",
            textHex = "#EDE8F5",
            secondaryTextHex = "#9A93AD",
            buttonHex = "#A99ACD",
            playerBarHex = "#241D38",
            visualizerHex = "#75D6CE",
            dynamicColor = true,
            cornerRadiusDp = 20,
            cardStyle = CardStyle.SOLID,
            playerBackground = PlayerBackground.GRADIENT,
            albumGrid = false,
            albumGridColumns = 2,
        ),
    ),
    ThemePreset(
        name = "Turquesa Vivo",
        settings = ThemeSettings(
            themeMode = ThemeMode.AMOLED,
            accentHex = "#75D6CE",
            backgroundHex = "#000000",
            cardHex = "#122019",
            textHex = "#DCE8F5",
            secondaryTextHex = "#8AA39D",
            buttonHex = "#75D6CE",
            playerBarHex = "#122019",
            visualizerHex = "#8CCBEB",
            dynamicColor = true,
            cornerRadiusDp = 16,
            cardStyle = CardStyle.SOLID,
            playerBackground = PlayerBackground.COVER,
            albumGrid = true,
            albumGridColumns = 2,
        ),
    ),
)

/** Busca una propuesta del laboratorio por nombre (sin distinción de mayúsculas); null si no existe. */
fun labThemePreset(name: String): ThemePreset? {
    val clean = name.trim()
    if (clean.isEmpty()) return null
    return LAB_THEME_PRESETS.find { it.name.equals(clean, ignoreCase = true) }
}
