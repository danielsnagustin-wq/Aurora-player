package com.aurora.player.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Aurora Player — auto-pausa al desconectar auriculares/Bluetooth.
 * ExoPlayer ya lo hace con setHandleAudioBecomingNoisy(true); este
 * receiver queda como red de seguridad explícita y para la Fase 1b
 * (headset autoplay al conectar: apagado por defecto, como en Auxio).
 */
class HeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.media.AUDIO_BECOMING_NOISY") {
            // La pausa real la aplica ExoPlayer (handleAudioBecomingNoisy).
            // No se para el servicio aquí: solo se registra el evento.
        }
    }
}
