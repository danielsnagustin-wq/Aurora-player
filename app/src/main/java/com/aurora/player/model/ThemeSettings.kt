package com.aurora.player.model

/**
 * Aurora Player — ajustes de tema y personalización (actualizado 16:43).
 *
 * Todo color es un HEX "#RRGGBB" guardado en DataStore. Reglas:
 * - Cada campo tiene efecto real: el tema se construye desde aquí
 *   (ver ui/theme/Theme.kt). Si un campo no se aplica en ningún sitio,
 *   no existe.
 * - Vista previa antes de aplicar: la pantalla de ajustes trabaja con
 *   una COPIA (draft); solo "Aplicar" guarda. "Descartar" no toca nada.
 * - Restablecer: DEFAULT devuelve todos los valores de fábrica.
 * - Sin dorado/amarillo en presets: solo si el usuario escribe el HEX
 *   a mano es su elección y se aplica.
 */
enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM }
enum class CoverShape { SQUARE, ROUNDED, CIRCLE }
enum class ListDensity { COMPACT, COMFORTABLE }
enum class TextSize { SMALL, MEDIUM, LARGE }

data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    /** AMOLED también cuando el sistema está en oscuro y el modo es SYSTEM. */
    val amoledWhenSystemDark: Boolean = true,
    val accentHex: String = "#8CCBEB", // Aurora hielo
    val backgroundHex: String = "#000000", // negro puro AMOLED
    val cardHex: String = "#111111",
    val textHex: String = "#DCE8F5", // plata
    val secondaryTextHex: String = "#9AA7B8",
    val buttonHex: String = "#8CCBEB",
    val playerBarHex: String = "#111111",
    val visualizerHex: String = "#A99ACD", // lavanda
    val useMaterialYou: Boolean = false, // Fase 1b, apagado por defecto
    val textSize: TextSize = TextSize.MEDIUM,
    val coverShape: CoverShape = CoverShape.ROUNDED,
    val listDensity: ListDensity = ListDensity.COMFORTABLE,
) {
    companion object {
        val DEFAULT = ThemeSettings()

        /** Presets de acento (sin dorado/amarillo). Nombre -> HEX. */
        val ACCENT_PRESETS: List<Pair<String, String>> = listOf(
            "Aurora hielo" to "#8CCBEB",
            "Lavanda" to "#A99ACD",
            "Turquesa" to "#75D6CE",
            "Plata" to "#DCE8F5",
            "Azul lavanda" to "#6578B8",
        )

        /** ¿Es un HEX "#RRGGBB" válido? */
        fun isValidHex(hex: String): Boolean {
            val clean = hex.trim().removePrefix("#")
            return Regex("^[0-9a-fA-F]{6}$").matches(clean)
        }

        /** Normaliza a "#RRGGBB" en mayúsculas; null si inválido. */
        fun normalizeHex(hex: String): String? {
            if (!isValidHex(hex)) return null
            return "#" + hex.trim().removePrefix("#").uppercase()
        }
    }
}

/**
 * Todos los ajustes de reproductores modernos en un solo sitio
 * (PLAN §7.2). Cada campo con efecto real; lo que aún no tiene efecto
 * (Fase 1b/2) lleva su fase en el comentario y NO aparece en la UI
 * hasta que exista su efecto.
 */
data class PlayerSettings(
    // Audio (Etapa 1)
    val gapless: Boolean = true,
    val crossfadeSeconds: Int = 0, // 0 = OFF, 1..12
    val playbackSpeed: Float = 1.0f,
    val equalizerEnabled: Boolean = false, // Plano = sin efecto; OFF por defecto
    // Audio (Fase 1b)
    val replayGainMode: String = "off", // off | track | album | smart
    val replayGainPreAmpDb: Float = 0f,
    val normalizeVolume: Boolean = false,
    // Auriculares / Bluetooth (Fase 1b)
    val autoPauseOnDisconnect: Boolean = true,
    val autoplayOnConnect: Boolean = false,
    // Biblioteca (Etapa 1: minDuration + rescan; 1b: carpetas)
    val minDurationMs: Long = 5_000L,
    val excludedFolders: List<String> = emptyList(),
    // Reproducción (Etapa 1: temporizador; 1b: resto)
    val resumeWhereLeft: Boolean = true,
    val persistentQueue: Boolean = true,
    // Gestos (Fase 1b)
    val swipeToChangeTrack: Boolean = true,
    val doubleTapToFavorite: Boolean = false,
    val verticalSwipeVolume: Boolean = false,
) {
    companion object {
        val DEFAULT = PlayerSettings()
    }
}
