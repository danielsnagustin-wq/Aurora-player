package com.aurora.player

import com.aurora.player.util.BugReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aurora Player — test de privacidad del reporte de errores (Etapa 1).
 *
 * Principio del usuario (2026-11-08): "Que sea 100 libre y no guardemos
 * los datos de las personas que la usen, mucho menos en reporte de errores".
 *
 * Un reporte de ejemplo con rutas, títulos de canciones, carpetas
 * personales, nombre personal, tokens y email debe salir sanitizado:
 * sin ninguno de esos datos, solo versión, Android, tipo de error
 * y stack trace limpio.
 */
class BugReportTest {

    private val dirtyStackTrace = """
        java.lang.RuntimeException: fallo al reproducir
            at com.aurora.player.playback.PlaybackService.play(PlaybackService.kt:42)
            at /storage/emulated/0/Music/Mi Canción Favorita.mp3
            at /storage/emulated/0/Mi Carpeta Secreta/tema.flac
            at canción suelta.mp3
            at content://media/external/audio/media/123
            Caused by: token ya29.AHES6ZRnXyZ9kQwerty1234567890
            Authorization: Bearer abcdef1234567890
            client_id=1234567890-abcdef.apps.googleusercontent.com
            user: danielsnagustin@example.com
            android_id=abcdef1234567890
    """.trimIndent()

    // RONDA 2 (2026-11-14, P2#27-fix): la línea "canción suelta.mp3"
    // del fixture es un nombre SUELTO, sin ruta. Con \p{L} la regex
    // de rutas se traga TAMBIÉN el nombre dentro de una ruta (todo
    // -> <ruta>), así que sin esta línea el assert de <archivo> no
    // tendría nada que probar: el run 43598929079 falló justo ahí.
    @Test
    fun `el reporte no contiene rutas ni nombres de archivos`() {
        val report = BugReport.buildReport("0.1.0", "14", "RuntimeException", dirtyStackTrace)
        assertFalse(report.contains("/storage"))
        assertFalse(report.contains("Mi Canción Favorita"))
        assertFalse(report.contains("Mi Carpeta Secreta"))
        assertFalse(report.contains(".mp3"))
        assertFalse(report.contains(".flac"))
        assertFalse(report.contains("content://"))
        assertTrue(report.contains("<ruta>"))
        assertTrue(report.contains("<archivo>"))
    }

    @Test
    fun `el reporte no contiene tokens ni datos personales`() {
        val report = BugReport.buildReport("0.1.0", "14", "RuntimeException", dirtyStackTrace)
        assertFalse(report.contains("ya29."))
        assertFalse(report.contains("abcdef1234567890"))
        assertFalse(report.contains("danielsnagustin"))
        assertFalse(report.contains("@example.com"))
        assertTrue(report.contains("<token>"))
        assertTrue(report.contains("<email>") || report.contains("<usuario>"))
    }

    @Test
    fun `el reporte si contiene lo permitido`() {
        val report = BugReport.buildReport("0.1.0", "14", "RuntimeException", dirtyStackTrace)
        assertTrue(report.contains("Aurora Player 0.1.0"))
        assertTrue(report.contains("Android: 14"))
        assertTrue(report.contains("RuntimeException"))
        assertTrue(report.contains("PlaybackService"))
    }

    @Test
    fun `rutas con acentos no dejan fragmentos`() {
        // RONDA 2 (2026-11-13, P2): las regex eran solo ASCII y el
        // reporte dejaba trozos ("úsica/José Pérez - Canció",
        // "canció"). Ahora no queda ni una letra de la ruta.
        val report = BugReport.buildReport(
            "0.1.0", "14", "RuntimeException",
            "fallo en /storage/emulated/0/Música/José Pérez - Canción.mp3",
        )
        assertFalse(report.contains("Música"))
        assertFalse(report.contains("José"))
        assertFalse(report.contains("Canción"))
        assertFalse(report.contains("Pérez"))
        assertFalse(report.contains("canción"))
        assertFalse(report.contains("Canció"))
        assertTrue(report.contains("<ruta>"))
        val out = BugReport.sanitize("fallo al leer Álbum de Björk.flac y canción.mp3")
        assertFalse(out.contains("Björk"))
        assertFalse(out.contains("canción"))
        assertFalse(out.contains("Álbum"))
        assertTrue(out.contains("<archivo>"))
    }

    @Test
    fun `sesiones de YouTube y Spotify no salen en el reporte`() {
        // RONDA 2 (2026-11-13, P2): pin de n=847 — las sesiones
        // (SAPISIDHASH, sp_dc, oauth_token, Cookie) salían intactas
        // en el reporte cuando venían entrecomilladas en JSON.
        // El valor no debe aparecer ni en claro ni entre comillas.
        val out = BugReport.sanitize(
            "fallo con \"SAPISIDHASH\": \"abc123XYZ\" y " +
                "\"sp_dc\": \"def456\" y oauth_token=\"ghi789\" " +
                "y Cookie: SAPISID=zzz111; sp_dc=yyy222",
        )
        assertFalse(out.contains("abc123XYZ"))
        assertFalse(out.contains("def456"))
        assertFalse(out.contains("ghi789"))
        assertFalse(out.contains("zzz111"))
        assertFalse(out.contains("yyy222"))
        assertTrue(out.contains("<token>"))
    }

    @Test
    fun `sanitize reemplaza rutas windows y nombres de lista`() {
        val out = BugReport.sanitize("fallo en C:\\Users\\Ana\\Music\\lista.m3u y en foto.jpg")
        assertFalse(out.contains("C:\\Users"))
        assertFalse(out.contains("lista.m3u"))
        assertFalse(out.contains("Ana"))
        assertTrue(out.contains("<ruta>"))
        assertTrue(out.contains("<archivo>"))
    }
}
