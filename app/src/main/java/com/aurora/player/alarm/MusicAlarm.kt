package com.aurora.player.alarm

import java.util.Calendar

/**
 * RONDA 7 P4: alarma musical — lógica pura (sin Android), testeable.
 * La alarma suena con la música local aunque la app esté cerrada
 * (AlarmManager + BroadcastReceiver + servicio en primer plano) y
 * sobrevive a un reinicio (BOOT_COMPLETED la vuelve a programar).
 */
sealed interface AlarmSource {
    data object ShuffleAll : AlarmSource
    data class Song(val songId: Long, val title: String) : AlarmSource
    data class Playlist(val playlistId: Long, val name: String) : AlarmSource
}

data class AlarmSettings(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 30,
    val daily: Boolean = true,
    val source: AlarmSource = AlarmSource.ShuffleAll,
)

/** Próxima vez que suena la alarma (milisegundos), desde nowMs. */
fun nextAlarmTime(hour: Int, minute: Int, nowMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMs
    cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
    cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    var next = cal.timeInMillis
    if (next <= nowMs) {
        // Sumar un DÍA DE CALENDARIO, no 24 h exactas: al cruzar un
        // cambio de horario de verano el día dura 23 o 25 h y la
        // alarma sonaría una hora antes o después (ronda 13).
        cal.add(Calendar.DAY_OF_YEAR, 1)
        next = cal.timeInMillis
    }
    return next
}

/** "07:30" con cero a la izquierda. */
fun formatAlarmTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

/** "Sonará hoy a las 07:30" o "Sonará mañana a las 07:30". */
fun describeNextAlarm(hour: Int, minute: Int, nowMs: Long): String {
    val next = nextAlarmTime(hour, minute, nowMs)
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    // La medianoche de mañana es un día de calendario después, no
    // 24 h exactas (mismo horario de verano que nextAlarmTime).
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val tomorrowStart = cal.timeInMillis
    val day = if (next < tomorrowStart) "hoy" else "mañana"
    return "Sonará $day a las ${formatAlarmTime(hour, minute)}"
}

/** Codificación en un string para DataStore. */
fun encodeAlarmSource(source: AlarmSource): String = when (source) {
    AlarmSource.ShuffleAll -> "shuffle"
    is AlarmSource.Song -> "song:${source.songId}:${source.title}"
    is AlarmSource.Playlist -> "playlist:${source.playlistId}:${source.name}"
}

/** Lo contrario; un valor roto o vacío es "mezclar todo", nunca un fallo. */
fun decodeAlarmSource(raw: String?): AlarmSource {
    if (raw.isNullOrBlank()) return AlarmSource.ShuffleAll
    if (raw == "shuffle") return AlarmSource.ShuffleAll
    val parts = raw.split(":", limit = 3)
    return when {
        parts.size == 3 && parts[0] == "song" -> {
            val id = parts[1].toLongOrNull() ?: return AlarmSource.ShuffleAll
            AlarmSource.Song(id, parts[2])
        }
        parts.size == 3 && parts[0] == "playlist" -> {
            val id = parts[1].toLongOrNull() ?: return AlarmSource.ShuffleAll
            AlarmSource.Playlist(id, parts[2])
        }
        else -> AlarmSource.ShuffleAll
    }
}
