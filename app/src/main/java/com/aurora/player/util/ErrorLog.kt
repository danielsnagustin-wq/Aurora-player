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

    // RONDA CAZA 2026-11-13 (P2): log() y trimIfOver() no estaban
    // sincronizados: dos hilos escribiendo a la vez podían pisarse
    // (recortar mientras el otro añade) y perder entradas o dejar el
    // archivo por encima del tope. Todo el ciclo recortar→añadir→
    // recortar va bajo un solo candado.
    // RONDA 2 (2026-11-13, P2): leer (readAll/hasEntries) y borrar
    // (clear) también van bajo el mismo candado: sin él, una lectura
    // a medias de una escritura, o un borrado mientras se añade,
    // dejaban el archivo a medias o resucitaban un registro borrado.
    private val lock = Any()

    fun file(context: Context): File = File(context.cacheDir, FILE_NAME)

    /** Recorta el archivo a la mitad si pasa de ~128 KB. */
    private fun trimIfOver(f: File) {
        var guard = 0
        while (f.exists() && f.length() > MAX_BYTES && guard < 10) {
            val lines = f.readLines()
            if (lines.size <= 1) {
                // RONDA 2 (2026-11-13, P2): una sola línea gigante
                // (un mensaje enorme sin saltos) nunca se recortaba:
                // el archivo crecía sin tope. Se queda la segunda
                // mitad del texto.
                val text = f.readText()
                if (text.length <= 1) break
                f.writeText(text.drop(text.length / 2))
            } else {
                f.writeText(lines.drop(lines.size / 2).joinToString("\n"))
            }
            guard++
        }
    }

    /** Escribe una entrada sanitizada; nunca lanza. */
    fun log(context: Context, tag: String, throwable: Throwable?) {
        try {
            synchronized(lock) {
                val f = file(context)
                trimIfOver(f)
                val time = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.ROOT,
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
                // El tope se vuelve a comprobar DESPUÉS de añadir: una
                // entrada muy larga dejaba el archivo por encima de 128 KB
                // hasta la siguiente escritura (ronda 11).
                trimIfOver(f)
            }
        } catch (_: Exception) {
            // El registro nunca puede romper la app.
        }
    }

    fun hasEntries(context: Context): Boolean {
        return try {
            synchronized(lock) {
                val f = file(context)
                f.exists() && f.length() > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    fun readAll(context: Context): String {
        return try {
            synchronized(lock) {
                val f = file(context)
                if (f.exists()) f.readText() else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun clear(context: Context) {
        try {
            synchronized(lock) {
                file(context).delete()
            }
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
