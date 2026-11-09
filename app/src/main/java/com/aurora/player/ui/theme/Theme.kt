package com.aurora.player.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.player.model.CardStyle
import com.aurora.player.model.CoverShape
import com.aurora.player.model.ListDensity
import com.aurora.player.model.PlayerBackground
import com.aurora.player.model.TextSize
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

/** Brillo percibido 0..1 (0.299 R + 0.587 G + 0.114 B). */
fun brightnessOf(color: Color): Float =
    color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f

/**
 * Texto que se lee sobre un color: negro si es claro, blanco si es
 * oscuro. Antes vivía solo dentro del tema; ahora es de nivel superior
 * porque el reproductor también lo necesita para el acento dinámico.
 */
fun contrastOn(color: Color): Color =
    if (brightnessOf(color) > 0.5f) PureBlack else Color.White

/**
 * Canales de personalización (ETAPA 6): valores que no caben en el
 * colorScheme pero tienen efecto real en la UI.
 */
val LocalButtonColor = staticCompositionLocalOf { IceBlue }
val LocalOnButtonColor = staticCompositionLocalOf { PureBlack }
val LocalCoverShape = staticCompositionLocalOf { CoverShape.ROUNDED }
val LocalListDensity = staticCompositionLocalOf { ListDensity.COMFORTABLE }

/** Diseño premium (spec 003): canales con efecto real en la UI. */
val LocalCornerRadiusDp = staticCompositionLocalOf { 16 }
val LocalCardStyle = staticCompositionLocalOf { CardStyle.SOLID }
val LocalDynamicColor = staticCompositionLocalOf { true }
val LocalPlayerBackground = staticCompositionLocalOf { PlayerBackground.COVER }
val LocalAlbumGrid = staticCompositionLocalOf { false }

/** Rendimiento (spec 004): animaciones activas o no (efectivas). */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/** Colores de botón reales: fondo = buttonHex, texto con contraste. */
@Composable
fun auroraButtonColors() = ButtonDefaults.buttonColors(
    containerColor = LocalButtonColor.current,
    contentColor = LocalOnButtonColor.current,
)

/** Tipografía escalada por el tamaño de texto elegido (ETAPA 6). */
private fun scaledTypography(scale: Float): Typography {
    fun TextStyle.scaled(): TextStyle = copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
    return Typography(
        titleLarge = AuroraTypography.titleLarge.scaled(),
        titleMedium = AuroraTypography.titleMedium.scaled(),
        bodyLarge = AuroraTypography.bodyLarge.scaled(),
        bodyMedium = AuroraTypography.bodyMedium.scaled(),
        labelMedium = AuroraTypography.labelMedium.scaled(),
    )
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
    val context = LocalContext.current
    val useDark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> darkTheme
    }
    val isAmoled = settings.themeMode == ThemeMode.AMOLED ||
        (settings.themeMode == ThemeMode.SYSTEM && darkTheme && settings.amoledWhenSystemDark)

    val accent = parseHexOrNull(settings.accentHex) ?: IceBlue
    // AUDITORÍA (2026-11-09): en modo claro el fondo, las tarjetas,
    // la barra y el visualizador IGNORABAN el color elegido por el
    // usuario (blanco y grises fijos): esos ajustes no hacían nada en
    // claro. Ahora se aplica el color del usuario cuando es claro
    // (brillo > 0,5, para que el texto oscuro se siga leyendo); si es
    // oscuro, se usa el valor claro por defecto, nunca se rompe la
    // lectura.
    fun lightOrDefault(hex: String, default: Color): Color {
        val custom = parseHexOrNull(hex)
        return if (custom != null && brightnessOf(custom) > 0.5f) custom else default
    }
    // RONDA 3 (2026-11-09): en modo oscuro el fondo IGNORABA el
    // color elegido (fijo #121212) y en AMOLED se aceptaba hasta un
    // fondo claro con texto claro encima, ilegible. Ahora el fondo
    // elegido vale cuando es oscuro; si es claro, cae al oscuro por
    // defecto para que el texto se siga leyendo.
    fun darkOrDefault(hex: String, default: Color): Color {
        val custom = parseHexOrNull(hex)
        return if (custom != null && brightnessOf(custom) < 0.5f) custom else default
    }
    val background = when {
        !useDark -> lightOrDefault(settings.backgroundHex, Color.White)
        isAmoled -> darkOrDefault(settings.backgroundHex, PureBlack)
        else -> darkOrDefault(settings.backgroundHex, Color(0xFF121212))
    }
    val surface = when {
        !useDark -> lightOrDefault(settings.cardHex, Color(0xFFF2F2F2))
        else -> parseHexOrNull(settings.cardHex) ?: CardBlack
    }
    // BUG REAL (prueba en teléfono 2026-11-08): el color de texto
    // guardado (#DCE8F5, claro) se usaba también en modo claro, así que
    // el texto quedaba claro sobre fondo blanco, ilegible. En modo
    // claro el texto es oscuro siempre, salvo que el usuario haya
    // elegido un color oscuro que sí se lea; en oscuro/AMOLED manda el
    // color elegido por el usuario.
    val onBackground = if (useDark) {
        parseHexOrNull(settings.textHex) ?: Silver
    } else {
        val custom = parseHexOrNull(settings.textHex)
        if (custom != null && brightnessOf(custom) < 0.6f) custom else Color(0xFF1A1C1E)
    }
    val onSurface = onBackground
    // ETAPA 2 (renovación): colores guardados que el tema no usaba y
    // ahora tienen efecto real: texto secundario -> onSurfaceVariant,
    // barra del reproductor -> surfaceVariant (oscuro/AMOLED). El color
    // de botones se cablea en ETAPA 6 (necesita su propio canal).
    val secondaryText = if (useDark) {
        parseHexOrNull(settings.secondaryTextHex) ?: Color(0xFF9AA7B8)
    } else {
        val custom = parseHexOrNull(settings.secondaryTextHex)
        if (custom != null && brightnessOf(custom) < 0.6f) custom else Color(0xFF5A6470)
    }
    val playerBar = if (useDark) {
        parseHexOrNull(settings.playerBarHex) ?: surface
    } else {
        lightOrDefault(settings.playerBarHex, surface)
    }
    // Contraste real del texto sobre el color principal: acento claro
    // -> texto negro; acento oscuro -> texto blanco (antes negro fijo).
    // La luminancia se calcula a mano (Color.luminance() no existe en
    // esta versión de Compose).
    val onPrimary = contrastOn(accent)
    // ETAPA 6: color de botones con su propio canal (buttonHex; si no
    // es válido, cae al acento). El texto del botón usa contraste real.
    val buttonColor = parseHexOrNull(settings.buttonHex) ?: accent
    val onButton = contrastOn(buttonColor)
    val textScale = when (settings.textSize) {
        TextSize.SMALL -> 0.85f
        TextSize.MEDIUM -> 1f
        TextSize.LARGE -> 1.15f
    }

    // ETAPA 6: Material You (Android 12+): el esquema viene del fondo
    // de pantalla del sistema. En Android 11 o menos el interruptor no
    // tiene efecto y se usan los colores de Aurora. El color de botones
    // elegido por el usuario sigue valiendo en ambos casos.
    val useDynamic = settings.useMaterialYou &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = if (useDynamic) {
        if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (useDark) {
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
            // El visualizador también vale en claro (es un acento: un
            // color oscuro sobre fondo claro se lee bien).
            secondary = parseHexOrNull(settings.visualizerHex) ?: Lavender,
            tertiary = Turquoise,
            background = background,
            surface = surface,
            // RONDA 3 (2026-11-09): era `surface` y la barra del
            // reproductor IGNORABA playerBarHex en modo claro (el
            // ajuste no hacía nada en claro, igual que en la ronda 1
            // pasaba con el fondo). Ahora manda el color de la barra.
            surfaceVariant = playerBar,
            onBackground = onBackground,
            onSurface = onSurface,
            onSurfaceVariant = secondaryText,
            onPrimary = onPrimary,
        )
    }
    CompositionLocalProvider(
        // BUG REAL (prueba en teléfono 2026-11-08): las pantallas que
        // se dibujan fuera del Scaffold (reproductor, ecualizador,
        // inicio) no reciben el color de contenido del Scaffold y
        // Compose pone negro por defecto: título, botones y textos
        // quedaban negros sobre negro, invisibles. Se fija aquí, para
        // todas las pantallas a la vez.
        LocalContentColor provides colors.onBackground,
        LocalButtonColor provides buttonColor,
        LocalOnButtonColor provides onButton,
        LocalCoverShape provides settings.coverShape,
        LocalListDensity provides settings.listDensity,
        LocalCornerRadiusDp provides settings.cornerRadiusDp.coerceIn(0, 28),
        LocalCardStyle provides settings.cardStyle,
        LocalDynamicColor provides settings.dynamicColor,
        LocalPlayerBackground provides settings.playerBackground,
        LocalAlbumGrid provides settings.albumGrid,
        LocalAnimationsEnabled provides settings.animationsEnabled,
    ) {
        MaterialTheme(colorScheme = colors, typography = scaledTypography(textScale), content = content)
    }
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

/** Radio de esquinas de tarjetas (spec 003): 0..28 dp, desde Ajustes. */
fun cornerRadiusShape(cornerDp: Int): RoundedCornerShape =
    RoundedCornerShape(cornerDp.coerceIn(0, 28).dp)

/** Fondo de tarjeta según el estilo elegido (spec 003). */
fun cardBackground(style: CardStyle, surface: Color): Color = when (style) {
    CardStyle.SOLID -> surface
    CardStyle.GLASS -> surface.copy(alpha = 0.45f)
    CardStyle.OUTLINE -> Color.Transparent
}

/** Borde de tarjeta según el estilo; null en sólido (sin borde). */
fun cardBorder(style: CardStyle, outline: Color): BorderStroke? = when (style) {
    CardStyle.SOLID -> null
    CardStyle.GLASS -> BorderStroke(1.dp, outline.copy(alpha = 0.25f))
    CardStyle.OUTLINE -> BorderStroke(1.dp, outline.copy(alpha = 0.45f))
}
