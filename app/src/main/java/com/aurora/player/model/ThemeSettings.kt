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
enum class ThemeMode(val label: String, val description: String) {
    LIGHT("Claro", "Fondo claro, texto oscuro"),
    DARK("Oscuro", "Gris oscuro, distinto del negro AMOLED"),
    AMOLED("AMOLED", "Negro puro, ahorra batería en pantallas AMOLED"),
    SYSTEM("Sistema", "Sigue al sistema del teléfono"),
}
enum class CoverShape { SQUARE, ROUNDED, CIRCLE }
enum class ListDensity { COMPACT, COMFORTABLE }
enum class TextSize { SMALL, MEDIUM, LARGE }

/**
 * Fuente de la app (Fase 2 app — selector de fuentes): solo
 * familias del sistema Android, sin descargar nada ni engordar la
 * APK. Si un teléfono no trae una familia, Android cae a la
 * predeterminada: nunca se rompe, como mucho se ve igual que antes.
 */
enum class AppFont(val label: String, val family: String) {
    DEFAULT("Predeterminada", ""),
    SANS("Sans", "sans-serif"),
    SERIF("Serif", "serif"),
    MONO("Monoespaciada", "monospace"),
    LIGHT("Ligera", "sans-serif-light"),
    CONDENSED("Condensada", "sans-serif-condensed"),
    MEDIUM("Mediana", "sans-serif-medium"),
}

/** Lee una fuente guardada como texto; un valor desconocido o vacío usa el defecto. */
fun parseAppFont(value: String?, default: AppFont): AppFont =
    AppFont.entries.find { it.name == value } ?: default

/**
 * Modo de karaoke por palabra (Fase 2 app, Apple Music: ALWAYS_ON /
 * ONLY_WHEN_VA_ON / OFF). El modo del medio de Apple se activa con la
 * atenuación de voz (Sing), que no tenemos y no se va a fingir: aquí
 * el medio es "solo a pantalla completa", la vista de cantar. Por
 * defecto es ese modo, que es como se ve hoy.
 */
enum class KaraokeMode(val label: String) {
    ALWAYS("Siempre"),
    FULLSCREEN_ONLY("Solo a pantalla completa"),
    OFF("Apagado"),
}

/** Lee un modo guardado como texto; un valor desconocido o vacío usa el defecto. */
fun parseKaraokeMode(value: String?, default: KaraokeMode): KaraokeMode =
    KaraokeMode.entries.find { it.name == value } ?: default

/** ¿Se colorean las palabras en esta vista? Puro, con tabla de verdad en los tests. */
fun karaokeWordsActive(mode: KaraokeMode, fullscreen: Boolean): Boolean = when (mode) {
    KaraokeMode.ALWAYS -> true
    KaraokeMode.FULLSCREEN_ONLY -> fullscreen
    KaraokeMode.OFF -> false
}

/** Estilo de tarjetas (spec 003): sólido, cristal o solo contorno. */
enum class CardStyle { SOLID, GLASS, OUTLINE }

/** Fondo del reproductor (spec 003): plano, portada gigante o degradado. */
enum class PlayerBackground { SOLID, COVER, GRADIENT }

data class ThemeSettings(
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    /** AMOLED también cuando el sistema está en oscuro y el modo es SYSTEM. */
    val amoledWhenSystemDark: Boolean = true,
    val accentHex: String = "#8CCBEB", // Aurora hielo
    val backgroundHex: String = "#000000", // negro puro AMOLED
    // UI-3 (2026-11-11): #111111 sobre #000000 aplana las capas;
    // #17171B separa tarjetas y barra sin perder el AMOLED.
    val cardHex: String = "#17171B",
    val textHex: String = "#DCE8F5", // plata
    val secondaryTextHex: String = "#B0BAC8",
    val buttonHex: String = "#8CCBEB",
    val playerBarHex: String = "#17171B",
    val visualizerHex: String = "#A99ACD", // lavanda
    val useMaterialYou: Boolean = false, // Fase 1b, apagado por defecto
    val textSize: TextSize = TextSize.MEDIUM,
    // Fase 2 — selector de fuentes: la familia del sistema que usa
    // toda la app (títulos, listas y reproductor).
    val fontFamily: AppFont = AppFont.DEFAULT,
    val coverShape: CoverShape = CoverShape.ROUNDED,
    val listDensity: ListDensity = ListDensity.COMFORTABLE,
    // DISEÑO PREMIUM (spec 003): todo personalizable, todo con efecto.
    /** El acento del reproductor sigue el color de la portada actual. */
    val dynamicColor: Boolean = true,
    /** Radio de esquinas de tarjetas y mini-barra, en dp (0..28). */
    val cornerRadiusDp: Int = 16,
    val cardStyle: CardStyle = CardStyle.SOLID,
    val playerBackground: PlayerBackground = PlayerBackground.COVER,
    /** Álbumes en cuadrícula en vez de lista. */
    val albumGrid: Boolean = false,
    /** Columnas de la cuadrícula de álbumes (1..4; 2 como hasta ahora). */
    val albumGridColumns: Int = 2,
    // RENDIMIENTO (spec 004): perfiles + interruptor de animaciones.
    // Solo gobiernan efectos reales (ver PerformanceProfile.kt).
    val performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    /** Animaciones: inicio (3 s) y desplazamiento animado de letras. */
    val animationsEnabled: Boolean = true,
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
    // Fase 1b: no solapar dentro del mismo álbum (gapless).
    val crossfadeGaplessAlbumException: Boolean = true,
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
    // Audio (SPEC 005 FR-001): balance izquierda/derecha (-1 = todo
    // a la izquierda, 0 = centro, +1 = todo a la derecha). Solo
    // estéreo; mono o multicanal no se tocan.
    val channelBalance: Float = 0f,
    // Audio (Fase 2, Salida Directa): "standard" | "direct". En
    // Directa la app no toca el audio: sin ReplayGain, sin saltar
    // silencios, sin balance, sin EQ del sistema, sin crossfade y
    // con el volumen a 1.0. No se promete bit-perfect total: Android
    // puede resamplear en su mezclador y sin DAC USB no lo hay.
    val audioOutputMode: String = "standard",
    // Auriculares / Bluetooth (Etapa 1)
    val autoPauseOnDisconnect: Boolean = true,
    // Fase 1b: reproducir al conectar auriculares/BT (OFF por
    // defecto) — al conectar una salida de audio, reanuda la
    // reproducción si hay una canción cargada y está en pausa.
    val autoplayOnConnect: Boolean = false,
    // AUTO-CERRAR (2026-11-11, como Musicolet): minutos sin música
    // tras los que la app se cierra sola, solo si no está visible.
    // 0 = nunca. Por defecto 2, como pidió el usuario.
    val autoCloseMinutes: Int = 2,
    // Biblioteca (Etapa 1: minDuration + rescan; 1b: carpetas)
    val minDurationMs: Long = 5_000L,
    // Reproducción (Etapa 1: temporizador; RONDA 6: radio local)
    val resumeWhereLeft: Boolean = true,
    // RONDA 6: radio local (OFF por defecto) — al acabar la cola,
    // seguir con canciones parecidas (mismo artista/género) en vez
    // de parar. Sin internet, solo con la biblioteca del teléfono.
    val localRadio: Boolean = false,
    // RONDA 7 P2 + 002-online-youtube: modo online (OFF por
    // defecto) — con el interruptor encendido, la app puede usar
    // internet: letras online (LRCLIB) y escuchar de YouTube. La
    // música local nunca usa la red. Apagado, nada sale a la red.
    val onlineMode: Boolean = false,
    // Calidad de streaming (BitChord, 2026-11-10): Alta es lo
    // mejor que da YouTube (Opus ~160 kbps); no hay tier sin
    // perdida porque YouTube no lo ofrece.
    val streamQuality: StreamQuality = StreamQuality.HIGH,
    // Calidad por conexión (copia BitChord, slice 3): una calidad
    // para Wi-Fi y otra para datos móviles. La única se migra en
    // SettingsStore/BackupData y se quita después (paso D).
    val wifiStreamQuality: StreamQuality = StreamQuality.HIGH,
    val mobileStreamQuality: StreamQuality = StreamQuality.MEDIUM,
    // Gestos (Etapa 1: ya existen en el reproductor; el interruptor
    // decide si están activos. El doble toque viene activado porque
    // así suena hoy: apagarlo por defecto cambiaría el gesto actual.)
    val swipeToChangeTrack: Boolean = true,
    // Fase 1b: agitar para pasar a la siguiente (OFF por defecto).
    // RONDA 119: funciona también en pausa (antes solo sonando).
    val shakeToNext: Boolean = false,
    // Fase 2 — gestos configurables (GoneMAD): qué hace agitar el
    // teléfono. NEXT por defecto, como hasta ahora. Guardado como
    // texto, validado al leer (un valor desconocido usa NEXT).
    val shakeAction: String = "next",
    val doubleTapToFavorite: Boolean = true,
    // Fase 2 — gestos configurables: qué hace el doble toque en la
    // portada. FAVORITE por defecto, como hasta ahora.
    val doubleTapAction: String = "favorite",
    // Fase 2 — modos de karaoke: cuándo se colorean las palabras.
    // FULLSCREEN_ONLY por defecto: como se ve hoy (solo a pantalla
    // completa); "Siempre" lo lleva también a la vista previa del
    // reproductor; "Apagado" deja solo el color de la línea.
    val karaokeMode: KaraokeMode = KaraokeMode.FULLSCREEN_ONLY,
) {
    companion object {
        val DEFAULT = PlayerSettings()
    }
}
