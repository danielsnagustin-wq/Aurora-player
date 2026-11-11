package com.aurora.player.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fase 1b — exportar estadísticas a CSV (idea de Musicolet).
 * Una fila por canción con historial real (30 s por escucha): título,
 * artista, álbum, reproducciones, minutos escuchados y última
 * reproducción. Nada inventado: solo lo que está en play_history.
 */
data class StatsCsvRow(
    val title: String,
    val artist: String,
    val album: String,
    val plays: Int,
    val listenedMinutes: Long,
    val lastPlayedAtMs: Long,
)

/** Campo CSV (RFC 4180): comillas si lleva coma, comilla o salto. */
fun csvField(value: String): String =
    if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

fun formatCsvDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

fun buildStatsCsv(rows: List<StatsCsvRow>): String {
    val out = StringBuilder()
    // RONDA 64: BOM UTF-8 al principio. Sin él, Excel abre el CSV
    // como ANSI en algunas configuraciones y los títulos/artistas
    // con tildes se ven rotos. Google Sheets y LibreOffice lo
    // ignoran; el texto sigue siendo UTF-8 válido.
    out.append("\uFEFF")
    out.append("titulo,artista,album,reproducciones,minutos_escuchados,ultima_reproduccion\n")
    for (row in rows) {
        out.append(csvField(row.title)).append(',')
            .append(csvField(row.artist)).append(',')
            .append(csvField(row.album)).append(',')
            .append(row.plays).append(',')
            .append(row.listenedMinutes).append(',')
            .append(formatCsvDate(row.lastPlayedAtMs)).append('\n')
    }
    return out.toString()
}

/** Escribe el CSV en Descargas/AuroraPlayer; devuelve su ruta visible. */
suspend fun exportStatsCsv(context: Context, rows: List<StatsCsvRow>): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
            val fileName = "aurora-estadisticas-$stamp.csv"
            val text = buildStatsCsv(rows)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/AuroraPlayer")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
                ) ?: throw IllegalStateException("No se pudo crear el archivo")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("No se pudo escribir el archivo")
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "AuroraPlayer",
                ).apply { mkdirs() }
                File(dir, fileName).writeText(text, Charsets.UTF_8)
            }
            "Descargas/AuroraPlayer/$fileName"
        }
    }
