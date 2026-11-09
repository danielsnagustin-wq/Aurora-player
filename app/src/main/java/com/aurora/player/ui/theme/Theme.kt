package com.aurora.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aurora.player.model.CoverShape
import com.aurora.player.model.ListDensity
import com.aurora.player.model.ThemeMode
import com.aurora.player.model.ThemeSettings

/**
 * Aurora Player — tema (actualizado 2026-11-08 ~16:43: AMOLED TOTAL).
 *
 * - Fondo negro PURO #000000 en todas las pantallas (AMOLED: el píxel
 *   negro está apagado y ahorra batería de verdad). Tarjetas #111111 o
 *   transparentes. SIN grises azulados: el fondo anterior #10131D queda
 *   eliminado. En tema claro el fondo es blanco, pero el modo por
 *   defecto es AMOLED.
 * - Todo color personalizable: el tema se construye desde ThemeSettings
 *   (acento, fondo, tarjetas, texto, botones, barra, visualizador).
 *   Si un color guardado no es un HEX válido, se ignora y se usa el
 *   valor por defecto (nunca se rompe la UI por un valor roto).
 * - Material You (colores del sistema) es Fase 1b, apagado por defecto.
 */
val IceBlue = Color(0xFF8CCBEB)
val LavenderBlue = Color(0xFF6578B8)
val Lavender = Color(0xFFA99ACD)
val Turquoise = Color(0xFF75D6CE)
val Silver = Color(0xFFDCE8F5)
val PureBlack = Color(0xFF000000)
val CardBlack = Color(0xFF111111)

/** Convierte "#RRGGBB" en Color; null si no es un HEX válido. */
fun parseHexOrNull(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (!Regex("^[0-9a-fA-F]{6}$").matches(clean)) return null
    return try {
        Color(("FF$clean").toLong(16))
    } catch (_: NumberFormatException) {
        null
    }
}

fun Color.toHex(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

/** Movimiento premium (PLAN §16): transiciones de 250 ms, press scale 0.97. */
object AuroraMotion {
    /** Duración estándar de transición, en ms. */
    const val TRANSITION_MS = 250
    /** Escala al presionar una tarjeta o un botón. */
    const val PRESS_SCALE = 0.97f
    /** Retraso entre filas al aparecer la lista, en ms (con límite en listas largas). */
    const val STAGGER_MS = 30
}

/** Tipografía cuidada (PLAN §16): jerarquía título > artista > álbum, sin textos amontonados. */
val AuroraTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

@Composable
fun AuroraPlayerTheme(
    settings: ThemeSettings = ThemeSettings.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val useDark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> darkTheme
    }
    val isAmoled = settings.themeMode == ThemeMode.AMOLED ||
        (settings.themeMode == ThemeMode.SYSTEM && darkTheme && settings.amoledWhenSystemDark)

    val accent = parseHexOrNull(settings.accentHex) ?: IceBlue
    val background = when {
        !useDark -> Color.White
        isAmoled -> parseHexOrNull(settings.backgroundHex) ?: PureBlack
        else -> Color(0xFF121212)
    }
    val surface = when {
        !useDark -> Color(0xFFF2F2F2)
        else -> parseHexOrNull(settings.cardHex) ?: CardBlack
    }
    val onBackground = parseHexOrNull(settings.textHex) ?: if (useDark) Silver else Color.Black
    val onSurface = onBackground
    // ETAPA 2 (renovación): colores guardados que el tema no usaba y
    // ahora tienen efecto real: texto secundario -> onSurfaceVariant,
    // barra del reproductor -> surfaceVariant (oscuro/AMOLED). El color
    // de botones se cablea en ETAPA 6 (necesita su propio canal).
    val secondaryText = parseHexOrNull(settings.secondaryTextHex)
        ?: if (useDark) Color(0xFF9AA7B8) else Color(0xFF5A6470)
    val playerBar = if (useDark) parseHexOrNull(settings.playerBarHex) ?: surface else surface
    // Contraste real del texto sobre el color principal: acento claro
    // -> texto negro; acento oscuro -> texto blanco (antes negro fijo).
    val onPrimary = if (accent.luminance() > 0.5f) PureBlack else Color.White

    val colors = if (useDark) {
        darkColorScheme(
            primary = accent,
            secondary = parseHexOrNull(settings.visualizerHex) ?: Lavender,
            tertiary = Turquoise,
            background = background,
            surface = surface,
            surfaceVariant = playerBar,
            onBackground = onBackground,
            onSurface = onSurface,
            onSurfaceVariant = secondaryText,
            onPrimary = onPrimary,
        )
    } else {
        lightColorScheme(
            primary = accent,
            secondary = Lavender,
            tertiary = Turquoise,
            background = background,
            surface = surface,
            onBackground = onBackground,
            onSurface = onSurface,
            onSurfaceVariant = secondaryText,
            onPrimary = onPrimary,
        )
    }
    MaterialTheme(colorScheme = colors, typography = AuroraTypography, content = content)
}

/** Forma de portada real (Fase 1b): se aplica en la lista y en el reproductor. */
fun coverShapeToCornerPercent(shape: CoverShape): Int = when (shape) {
    CoverShape.SQUARE -> 0
    CoverShape.ROUNDED -> 12
    CoverShape.CIRCLE -> 50
}

/** Densidad de lista real (Fase 1b): altura de fila en dp. */
fun listDensityToRowHeightDp(density: ListDensity): Int = when (density) {
    ListDensity.COMPACT -> 52
    ListDensity.COMFORTABLE -> 72
}
