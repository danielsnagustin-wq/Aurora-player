package com.aurora.player.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aurora.player.data.AlarmStore
import com.aurora.player.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * RONDA 7 P4: recibe la alarma, el apagado y el arranque del teléfono.
 * - Llega la hora: si es diaria, la próxima ya queda programada; si
 *   es de una vez, se apaga. Luego arranca el servicio en primer plano
 *   para que suene la música.
 * - El teléfono se reinicia: la alarma se vuelve a programar sola.
 * - Cambia el permiso de alarmas exactas: si la alarma estaba guardada
 *   como activa pero sin permiso (RONDA 43), al concederlo desde los
 *   ajustes del sistema se programa sola sin tener que reabrir la
 *   pantalla ni pulsar "Actualizar".
 * - "Apagar": le dice al servicio que pare la música.
 */
class MusicAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (intent.action) {
                    AlarmScheduler.ACTION_ALARM -> onAlarm(context)
                    AlarmScheduler.ACTION_STOP_ALARM -> onStopAlarm(context)
                    Intent.ACTION_BOOT_COMPLETED -> onBoot(context)
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                        onExactAlarmPermissionChanged(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onAlarm(context: Context) {
        val store = AlarmStore(context)
        val settings = store.settingsFlow.first()
        if (!settings.enabled) return
        if (settings.daily) {
            AlarmScheduler.schedule(context, settings)
        } else {
            store.save(settings.copy(enabled = false))
        }
        val serviceIntent = Intent(context, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_PLAY_ALARM)
        context.startForegroundService(serviceIntent)
    }

    private fun onStopAlarm(context: Context) {
        val serviceIntent = Intent(context, PlaybackService::class.java)
            .setAction(PlaybackService.ACTION_STOP_ALARM)
        context.startService(serviceIntent)
    }

    private suspend fun onBoot(context: Context) {
        val settings = AlarmStore(context).settingsFlow.first()
        if (settings.enabled) AlarmScheduler.schedule(context, settings)
    }

    private suspend fun onExactAlarmPermissionChanged(context: Context) {
        val settings = AlarmStore(context).settingsFlow.first()
        if (settings.enabled && AlarmScheduler.canScheduleExactAlarms(context)) {
            AlarmScheduler.schedule(context, settings)
        }
    }
}
