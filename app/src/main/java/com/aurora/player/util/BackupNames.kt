package com.aurora.player.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nombres del respaldo (Fase 2 app — respaldo a carpeta): puros y
 * comprobables. El archivo lleva la fecha y hora de creación; la
 * carpeta elegida se muestra por su último segmento, sin inventar
 * una ruta que el sistema no nos da.
 */
fun backupFileName(createdAtMs: Long): String {
    // Precisión de SEGUNDO: con solo minuto, dos respaldos creados
    // en el mismo minuto colisionaban y el segundo reemplazaba al
    // primero en la ruta File (Android 8-9, ronda 11).
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(createdAtMs))
    return "aurora-player-respaldo-$stamp.json"
}

/**
 * Nombre visible de una carpeta elegida por SAF (Uri en texto).
 * "content://.../tree/primary%3AMusic" -> "Music". Sin ":" o sin
 * segmento, se muestra el último segmento tal cual; sin nada, "".
 */
fun backupFolderDisplayName(uriString: String): String {
    val segment = uriString.substringAfterLast('/').substringAfterLast("%3A").substringAfterLast(':')
    return try {
        java.net.URLDecoder.decode(segment, "UTF-8")
    } catch (_: Exception) {
        segment
    }
}
