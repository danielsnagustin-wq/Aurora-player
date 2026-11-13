package com.aurora.player.util

/**
 * Aurora Player — reporte de errores SANITIZADO por diseño (Etapa 1).
 *
 * Principio de privacidad del usuario (2026-11-08):
 * "Que sea 100 libre y no guardemos los datos de las personas que la usen,
 * mucho menos en reporte de errores".
 *
 * Reglas:
 * - Un reporte solo contiene: versión de la app, versión de Android,
 *   tipo de error y stack trace. NADA más.
 * - NUNCA: rutas de archivos (/storage/... con nombre de usuario),
 *   nombres/títulos de canciones, nombres de carpetas personales,
 *   package con nombre personal, tokens (YouTube/SoundCloud),
 *   emails ni identificadores de dispositivo.
 * - El reporte nunca se construye con datos de canciones: [buildReport]
 *   solo recibe los 4 campos permitidos. [sanitize] es la segunda capa:
 *   si un stack trace trae una ruta o un token pegado, se reemplaza
 *   antes de exportar. Antes de exportar, cualquier ruta pasa a <ruta>
 *   y cualquier nombre de archivo pasa a <archivo>.
 * - Sin cuenta, sin nombre, sin email, sin identificadores: todo queda
 *   SOLO en el teléfono (Room/DataStore local) y se borra con
 *   "Borrar datos" o al desinstalar.
 */
object BugReport {

    fun buildReport(
        appVersion: String,
        androidVersion: String,
        errorType: String,
        stackTrace: String,
    ): String {
        val body = buildString {
            appendLine("App: Aurora Player $appVersion")
            appendLine("Android: $androidVersion")
            appendLine("Error: $errorType")
            appendLine("Stack trace:")
            appendLine(stackTrace)
        }
        return sanitize(body)
    }

    /**
     * Sanitiza un texto libre (normalmente un stack trace) antes de
     * exportarlo. No inventa datos: solo reemplaza lo sensible.
     */
    fun sanitize(text: String): String {
        var out = text
        // Rutas absolutas Unix/Android y Windows -> <ruta>
        out = out.replace(Regex("/[A-Za-z0-9_\\-.]+(/[A-Za-z0-9_\\-. ]+)+"), "<ruta>")
        out = out.replace(Regex("[A-Za-z]:\\\\[^\\s\"']+"), "<ruta>")
        out = out.replace(Regex("content://[^\\s\"']+"), "<ruta>")
        out = out.replace(Regex("file://[^\\s\"']+"), "<ruta>")
        // Nombres de archivo de audio / letra / lista -> <archivo>
        out = out.replace(
            Regex("[A-Za-z0-9_\\- .()\\[\\]]+\\.(mp3|flac|wav|m4a|aac|ogg|opus|mka|alac|lrc|ttml|srt|m3u|xspf|wpl|pls|jpg|jpeg|png)", RegexOption.IGNORE_CASE),
            "<archivo>",
        )
        // Tokens y credenciales -> <token> (valor incluido, no solo la clave)
        out = out.replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9_\\-.~+/=]+"), "$1<token>")
        out = out.replace(Regex("(?i)(access_token|refresh_token|id_token|api_key|apikey|client_secret|client_id)\\s*[:=]\\s*[^\\s\"',;]+"), "$1=<token>")
        out = out.replace(Regex("ya29\\.[A-Za-z0-9_\\-]+"), "<token>")
        // Sesiones propias de la app (YouTube Music / Spotify /
        // SoundCloud): SAPISID, sp_dc, oauth_token y cabeceras Cookie:
        // completas. Antes un reporte de error podía llevar la sesión
        // entera dentro del mensaje de una excepción.
        out = out.replace(Regex("(?i)(SAPISIDHASH|SAPISID|sp_dc|oauth_token|__Secure-[A-Za-z0-9_-]+)\\s*[:=]\\s*[^\\s\"',;]+"), "$1=<token>")
        out = out.replace(Regex("(?i)(Cookie:\\s*)[^\\n]+"), "$1<token>")
        // Emails -> <email>
        out = out.replace(Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"), "<email>")
        // Nombre personal del desarrollador original del launcher (nunca
        // debe aparecer: el package neutro es com.aurora.player).
        out = out.replace("danielsnagustin", "<usuario>", ignoreCase = true)
        // Identificadores de dispositivo habituales -> <id>
        out = out.replace(Regex("(?i)(android_id|serial|imei|device_id)\\s*[:=]\\s*[^\\s\"',;]+"), "$1=<id>")
        return out
    }
}
