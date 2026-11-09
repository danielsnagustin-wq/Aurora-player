package com.aurora.player.util

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Aurora Player — registro de errores en el teléfono (depuración).
 *
 * Solo para encontrar errores mientras desarrollamos: esta sección se
 * quitará en el release. Todo lo que se registra pasa por
 * BugReport.sanitize: sin rutas, sin nombres de canciones, sin tokens,
 * sin emails, sin identificadores. Nada sale del teléfono salvo que el
 * usuario lo comparta desde Ajustes. El archivo vive en caché (se
 * borra con "Borrar datos" o al desinstalar) y se limita a ~128 KB.
 * Ninguna de estas funciones lanza nunca: el registro no puede romper
 * la app que intenta diagnosticar.
 */
object ErrorLog {

    private const val FILE_NAME = "aurora-error-log.txt"
    private const val MAX_BYTES = 128 * 1024L
    private const val SEPARATOR = "---"

    fun file(context: Context): File = File(context.cacheDir, FILE_NAME)

    /** Escribe una entrada sanitizada; nunca lanza. */
    fun log(context: Context, tag: String, throwable: Throwable?) {
        try {
            val f = file(context)
            if (f.exists() && f.length() > MAX_BYTES) {
                val lines = f.readLines()
                f.writeText(lines.drop(lines.size / 2).joinToString("\n"))
            }
            val time = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault(),
            ).format(java.util.Date())
            val entry = buildString {
                appendLine(SEPARATOR)
                appendLine("[$time] $tag")
                if (throwable != null) {
                    appendLine("${throwable.javaClass.name}: ${throwable.message ?: ""}")
                    throwable.stackTrace.take(12).forEach { appendLine("    at $it") }
                    var cause = throwable.cause
                    var depth = 0
                    while (cause != null && depth < 3) {
                        appendLine("Caused by: ${cause.javaClass.name}: ${cause.message ?: ""}")
                        cause = cause.cause
                        depth++
                    }
                } else {
                    appendLine("(sin detalle)")
                }
            }
            f.appendText(BugReport.sanitize(entry))
        } catch (_: Exception) {
            // El registro nunca puede romper la app.
        }
    }

    fun hasEntries(context: Context): Boolean {
        return try {
            val f = file(context)
            f.exists() && f.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    fun readAll(context: Context): String {
        return try {
            val f = file(context)
            if (f.exists()) f.readText() else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Exception) {
            // Nada que borrar.
        }
    }

    /** Texto listo para ver o compartir: cabecera + entradas, sanitizado. */
    fun buildReportText(context: Context): String {
        // La versión se lee del paquete instalado: BuildConfig no se
        // genera en este proyecto (AGP 8 la tiene apagada por defecto).
        val versionName = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
        val header = "Aurora Player $versionName · " +
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
            "Reporte de errores (depuración, se quitará en el release). " +
            "Sanitizado: sin rutas, sin nombres de canciones, sin datos personales.\n\n"
        val body = readAll(context)
        return if (body.isBlank()) {
            header + "Sin errores registrados todavía."
        } else {
            header + body
        }
    }
}
