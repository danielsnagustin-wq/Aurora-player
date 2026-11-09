package com.aurora.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aurora.player.audio.EqualizerManager
import com.aurora.player.model.RepeatMode

/**
 * Aurora Player — servicio de reproducción (Etapa 1).
 *
 * ANTI-BUG (lección del gamepad): startForegroundService() sin
 * startForeground() inmediato => Android mata la app en ~5-10 s con
 * ForegroundServiceDidNotStartInTimeException. Aquí:
 * - onCreate() crea el reproductor, la MediaSession y la notificación,
 *   y llama a startForeground() ANTES de reproducir nada.
 * - La notificación la gestiona MediaSession (MediaStyle) vía
 *   MediaNotificationManager de media3-session; el canal se crea aquí.
 * - onTaskRemoved(): no se mata la reproducción a traición; si no suena
 *   nada, el servicio se detiene solo (stopSelf).
 * - Gapless: ExoPlayer con cola continua (sin recrear el player).
 * - Crossfade (Fase 1): no se finge con dos players; en Etapa 1 el
 *   crossfade es un fundido de volumen al cambiar de pista (0 s = OFF).
 *   El crossfade real de mezcla (Retro, hasta 12 s) va en Fase 1b con
 *   dos instancias o con audio processor, y se prueba en el teléfono.
 */
/** Puente UI <-> efectos: el servicio publica aquí su EqualizerManager real. */
object AudioEffects {
    var equalizerManager: EqualizerManager? = null
}

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var equalizer: EqualizerManager? = null

    companion object {
        const val CHANNEL_ID = "aurora_player_playback"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // auto-pausa al quitar auriculares
            .build()
        player = exoPlayer
        equalizer = try {
            EqualizerManager(exoPlayer.audioSessionId)
        } catch (_: Exception) {
            null // sin EQ en este dispositivo: la UI lo dice, no se finge
        }
        AudioEffects.equalizerManager = equalizer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        // startForeground() INMEDIATO: la notificación de MediaSession
        // se publica al arrancar el servicio, antes de cualquier play().
        startForeground(NOTIFICATION_ID, buildPlaceholderNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reproducción",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Controles de reproducción de Aurora Player" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildPlaceholderNotification(): Notification {
        // Media3 sustituye esta notificación por la de MediaSession en
        // cuanto hay un MediaItem; este placeholder solo cubre el arranque.
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Aurora Player")
            .setContentText("Listo para reproducir")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        equalizer?.release()
        AudioEffects.equalizerManager = null
        mediaSession = null
        player = null
        equalizer = null
        super.onDestroy()
    }
}

/** Ajustes de reproducción con efecto real sobre el ExoPlayer. */
object PlayerControls {
    fun setSpeed(player: Player, speed: Float) {
        player.playbackParameters = player.playbackParameters.withSpeed(speed.coerceIn(0.5f, 2.0f))
    }

    fun setRepeatMode(player: Player, mode: RepeatMode) {
        player.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun setShuffle(player: Player, enabled: Boolean) {
        player.shuffleModeEnabled = enabled
    }

    fun playSongs(player: Player, songs: List<MediaItem>, startIndex: Int = 0) {
        player.setMediaItems(songs, startIndex, 0L)
        player.prepare()
        player.play()
    }
}
