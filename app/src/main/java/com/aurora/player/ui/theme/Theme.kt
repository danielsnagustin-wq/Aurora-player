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

/**
 * Resolución de colores por modo (2026-11-10, bug verificado en
 * teléfono: elegir Claro dejaba la app negra). El HEX de fábrica
 * (#000000 fondo, #111111 tarjeta/barra) significa "sin
 * personalizar": al cambiar de modo manda el color DEL MODO. Un HEX
 * distinto elegido por el usuario SIEMPRE se aplica, en cualquier
 * modo. Puras y testeables: la UI solo las llama.
 */
fun resolveBackgroundColor(
    settings: ThemeSettings,
    useDark: Boolean,
    isAmoled: Boolean,
): Color {
    val isFactory = settings.backgroundHex.equals(
        ThemeSettings.DEFAULT.backgroundHex, ignoreCase = true,
    )
    return when {
        !useDark && isFactory -> Color.White
        !useDark -> parseHexOrNull(settings.backgroundHex) ?: Color.White
        isAmoled -> parseHexOrNull(settings.backgroundHex) ?: PureBlack
        isFactory -> Color(0xFF1E1E26)
        else -> parseHexOrNull(settings.backgroundHex) ?: Color(0xFF1E1E26)
    }
}

fun resolveSurfaceColor(settings: ThemeSettings, useDark: Boolean): Color {
    val isFactory = settings.cardHex.equals(
        ThemeSettings.DEFAULT.cardHex, ignoreCase = true,
    )
    return when {
        !useDark && isFactory -> Color(0xFFF2F2F2)
        else -> parseHexOrNull(settings.cardHex)
            ?: if (useDark) CardBlack else Color(0xFFF2F2F2)
    }
}

fun resolvePlayerBarColor(
    settings: ThemeSettings,
    useDark: Boolean,
    surface: Color,
): Color {
    val isFactory = settings.playerBarHex.equals(
        ThemeSettings.DEFAULT.playerBarHex, ignoreCase = true,
    )
    return when {
        !useDark && isFactory -> surface
        else -> parseHexOrNull(settings.playerBarHex) ?: surface
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
 * Texto que se lee sobre un color (2026-11-10): el del usuario si
 * contrasta con el fondo, si no el que toca. Así el color elegido
 * SIEMPRE se aplica y lo que se adapta es el texto, en vez de
 * descartar el color en silencio (eso hacía creer que "ningún
 * color funciona").
 */
fun readableTextOn(
    color: Color,
    userHex: String,
    darkDefault: Color,
    lightDefault: Color,
): Color {
    val user = parseHexOrNull(userHex)
    return if (brightnessOf(color) > 0.5f) {
        // Fondo claro: texto oscuro (el del usuario si es oscuro).
        if (user != null && brightnessOf(user) < 0.5f) user else darkDefault
    } else {
        // Fondo oscuro: texto claro (el del usuario si es claro).
        if (user != null && brightnessOf(user) > 0.5f) user else lightDefault
    }
}

/**
 * RONDA PULIDO (2026-11-09): en modo claro el acento se usa como
 * color de TEXTO sobre blanco (chips sin elegir, iconos, enlaces).
 * El azul hielo #8CCBEB sobre blanco casi no se lee. Se oscurece
 * a la mitad para que el texto se lea; el color elegido sigue
 * valiendo como relleno de botones y como acento en oscuro.
 */
fun darkenForLight(color: Color): Color {
    val factor = 0.5f
    return Color(color.red * factor, color.green * factor, color.blue * factor, color.alpha)
}

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
    // COLORES (2026-11-10): el color elegido SIEMPRE se aplica; lo que
    // se adapta es el texto, para que se siga leyendo. El HEX de
    // fábrica significa "sin personalizar" y manda el color del modo
    // (ver resolveBackgroundColor): sin esto, Claro pintaba negro
    // porque el fondo guardado era #000000, el valor de AMOLED.
    val background = resolveBackgroundColor(settings, useDark, isAmoled)
    val surface = resolveSurfaceColor(settings, useDark)
    // El texto se adapta al fondo que haya quedado (ver readableTextOn):
    // fondo claro -> texto oscuro; fondo oscuro -> texto claro (el del
    // usuario cuando contrasta).
    val onBackground = readableTextOn(
        background, settings.textHex, Color(0xFF1A1C1E), Silver,
    )
    val onSurface = readableTextOn(
        surface, settings.textHex, Color(0xFF1A1C1E), Silver,
    )
    val secondaryText = readableTextOn(
        surface, settings.secondaryTextHex, Color(0xFF5A6470), Color(0xFF9AA7B8),
    )
    val playerBar = resolvePlayerBarColor(settings, useDark, surface)
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
        // RONDA PULIDO (2026-11-09): en claro el primary es color de
        // texto sobre blanco: el acento claro no se leía. Se usa la
        // versión oscurecida; el acento elegido sigue como relleno.
        val lightPrimary = darkenForLight(accent)
        lightColorScheme(
            primary = lightPrimary,
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
            onPrimary = contrastOn(lightPrimary),
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
