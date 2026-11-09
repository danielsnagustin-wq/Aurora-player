package com.aurora.player.model

/**
 * Aurora Player — ajustes de tema y personalización (actualizado 16:43).
 *
 * Todo color es un HEX "#RRGGBB" guardado en DataStore. Reglas:
 * - Cada campo tiene efecto real: el tema se construye desde aquí
 *   (ver ui/theme/Theme.kt). Si un campo no se aplica en ningún sitio,
 *   no existe.
 * - Aplicar al tocar: cada cambio se guarda en DataStore al instante,
 *   sin botón "Aplicar" que bloquee nada. La vista previa muestra los
 *   valores aplicados en ese momento.
 * - Restablecer: DEFAULT devuelve todos los valores de fábrica.
 * - Sin dorado/amarillo en presets: solo si el usuario escribe el HEX
 *   a mano es su elección y se aplica.
 */
enum class ThemeMode(val label: String) {
    LIGHT("Claro"),
    DARK("Oscuro"),
    AMOLED("AMOLED"),
    SYSTEM("Sistema"),
}
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
    val crossfadeSeconds: Int = 0, // 0 = OFF, 1..12
    val playbackSpeed: Float = 1.0f,
    // Audio (RONDA 6): tono independiente de la velocidad (0.5..2.0,
    // 1.0 = sin cambio) y saltar silencios largos (OFF por defecto).
    val playbackPitch: Float = 1.0f,
    val skipSilence: Boolean = false,
    // Audio (RONDA 6): ReplayGain de verdad — modo off | track |
    // album | smart (track si hay etiqueta, si no album) + preamp
    // en dB (-12..+12). Sin etiqueta ReplayGain en el archivo, la
    // ganancia es 0 y no se finge ningún cambio de volumen.
    val replayGainMode: String = "off",
    val replayGainPreAmpDb: Float = 0f,
    // Auriculares / Bluetooth (Etapa 1)
    val autoPauseOnDisconnect: Boolean = true,
    // Biblioteca (Etapa 1: minDuration + rescan; 1b: carpetas)
    val minDurationMs: Long = 5_000L,
    // Reproducción (Etapa 1: temporizador; RONDA 6: radio local)
    val resumeWhereLeft: Boolean = true,
    // RONDA 6: radio local (OFF por defecto) — al acabar la cola,
    // seguir con canciones parecidas (mismo artista/género) en vez
    // de parar. Sin internet, solo con la biblioteca del teléfono.
    val localRadio: Boolean = false,
    // RONDA 7 P2: letras online (OFF por defecto) — con el
    // interruptor encendido, si la canción no tiene .lrc local, se
    // busca la letra en LRCLIB. Es el ÚNICO uso de internet del
    // reproductor; apagado, la app no toca la red jamás.
    val onlineLyrics: Boolean = false,
    // Gestos (Etapa 1: ya existen en el reproductor; el interruptor
    // decide si están activos. El doble toque viene activado porque
    // así suena hoy: apagarlo por defecto cambiaría el gesto actual.)
    val swipeToChangeTrack: Boolean = true,
    val doubleTapToFavorite: Boolean = true,
) {
    companion object {
        val DEFAULT = PlayerSettings()
    }
}
