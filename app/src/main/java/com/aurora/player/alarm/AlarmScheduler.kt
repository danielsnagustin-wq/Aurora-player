package com.aurora.player.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aurora.player.util.ErrorLog

/**
 * RONDA 7 P4: programa/cancela la alarma en AlarmManager.
 * setExactAndAllowWhileIdle: suena a la hora aunque el teléfono
 * esté en reposo (Doze). En Android 12+ necesita el permiso de
 * alarmas exactas (SCHEDULE_EXACT_ALARM): si no está concedido,
 * programar lanza SecurityException y se registra, no se finge.
 */
object AlarmScheduler {
    const val ACTION_ALARM = "com.aurora.player.ALARM"
    const val ACTION_STOP_ALARM = "com.aurora.player.STOP_ALARM"
    private const val REQUEST_CODE_ALARM = 1001
    private const val REQUEST_CODE_STOP = 1002

    fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun schedule(context: Context, settings: AlarmSettings) {
        if (!settings.enabled) {
            cancel(context)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = nextAlarmTime(settings.hour, settings.minute, System.currentTimeMillis())
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmPendingIntent(context))
        } catch (e: SecurityException) {
            ErrorLog.log(context, "Programar alarma", e)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(alarmPendingIntent(context))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MusicAlarmReceiver::class.java).setAction(ACTION_ALARM)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Acción "Apagar" de la notificación de la alarma. */
    fun stopPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MusicAlarmReceiver::class.java).setAction(ACTION_STOP_ALARM)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STOP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
