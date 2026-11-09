package com.aurora.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aurora.player.audio.EqualizerManager
import com.aurora.player.audio.ReplayGainProcessor
import com.aurora.player.audio.SilenceDetectorAudioProcessor
import com.aurora.player.audio.replayGainFromMetadata
import com.aurora.player.audio.selectReplayGainDb
import com.aurora.player.data.EqStateStore
import com.aurora.player.data.PlayerSettingsStore
import com.aurora.player.util.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
 * - Crossfade: implementado de verdad en ETAPA 4 (fade-in al cambiar
 *   de pista + fade-out al final de la pista, con un solo ExoPlayer).
 *   El crossfade real de mezcla con solape (Retro, hasta 12 s) va en
 *   Fase 1b con dos instancias o con audio processor, y se prueba en
 *   el teléfono.
 */
/** Puente UI <-> efectos: el servicio publica aquí su EqualizerManager real. */
object AudioEffects {
    // Estado de Compose: la pantalla del EQ se entera cuando el
    // manager aparece (al sonar la primera canción) sin reabrirla.
    var equalizerManager: EqualizerManager? by mutableStateOf(null)
        private set
    /** true si el dispositivo rechazó el EQ con una sesión real. */
    var equalizerUnsupported: Boolean by mutableStateOf(false)
        private set

    internal fun publish(manager: EqualizerManager?) {
        equalizerManager = manager
    }

    internal fun markUnsupported() {
        equalizerUnsupported = true
    }

    internal fun clear() {
        equalizerManager = null
        equalizerUnsupported = false
    }
}

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var equalizer: EqualizerManager? = null

    /** Ajustes vivos: crossfade y auto-pausa se leen de DataStore. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var crossfadeSeconds = 0
    private var fadeJob: Job? = null
    private var fadingIn = false
    /** Sesión de audio a la que está atado el EQ actual (0 = ninguna). */
    private var equalizerSessionId: Int = 0
    /** ReplayGain (RONDA 6): procesador en la cadena de audio + la
     * ganancia de la pista que suena (de su etiqueta, null = sin
     * etiqueta) + el modo y el preamp elegidos en Ajustes. */
    private var replayGainProcessor: ReplayGainProcessor? = null
    private var replayGainMode = "off"
    private var replayGainPreAmpDb = 0f
    private var trackGainDb: Float? = null
    private var albumGainDb: Float? = null
    /** Saltar silencio (RONDA 6): procesador en la cadena + ajuste. */
    private var silenceProcessor: SilenceDetectorAudioProcessor? = null
    private var skipSilenceEnabled = false

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
        val gainProcessor = ReplayGainProcessor().also { replayGainProcessor = it }
        val silenceDetector = SilenceDetectorAudioProcessor(
            onLongSilence = { onLongSilenceDetected() },
        ).also { silenceProcessor = it }
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // auto-pausa al quitar auriculares
            // RONDA 6: ReplayGain real en la cadena de audio (antes el
            // ajuste existía en el modelo pero nada lo aplicaba) y
            // detector de silencio (pasa el audio sin tocarlo).
            .setAudioProcessors(gainProcessor, silenceDetector)
            .build()
        player = exoPlayer
        // RONDA 2 (2026-11-09): el EQ NO se crea aquí. Al arrancar,
        // exoPlayer.audioSessionId aún es 0 (la sesión se crea al
        // preparar la primera pista) y Equalizer(0, 0) se engancha a
        // la MEZCLA GLOBAL: el EQ cambiaba el sonido de TODAS las
        // apps mientras Aurora corría, y no estaba atado a su sesión.
        // Se crea en onAudioSessionIdChanged, con la sesión real.
        AudioEffects.clear()
        mediaSession = MediaSession.Builder(this, exoPlayer)
            // Portadas de la notificación: MediaStore, incrustada en el
            // archivo o inicial de Aurora; nunca sin carátula.
            .setBitmapLoader(AuroraBitmapLoader(this))
            .build()
        // Ajustes vivos (ETAPA 4): crossfade y auto-pausa se aplican
        // cuando cambian en Ajustes, sin reiniciar nada.
        serviceScope.launch {
            PlayerSettingsStore(this@PlaybackService).settingsFlow.collect { settings ->
                crossfadeSeconds = settings.crossfadeSeconds
                exoPlayer.setHandleAudioBecomingNoisy(settings.autoPauseOnDisconnect)
                if (crossfadeSeconds == 0) {
                    fadeJob?.cancel()
                    fadingIn = false
                    exoPlayer.volume = 1f
                }
                // RONDA 6: el modo/preamp de ReplayGain se aplica al
                // cambiar en Ajustes, con la etiqueta de la pista que
                // suena (si la hay).
                replayGainMode = settings.replayGainMode
                replayGainPreAmpDb = settings.replayGainPreAmpDb
                applyReplayGain()
                // RONDA 6: saltar silencio se enciende/apaga en vivo.
                skipSilenceEnabled = settings.skipSilence
                silenceProcessor?.enabled = settings.skipSilence
            }
        }
        // Crossfade real (ETAPA 4): fade-in al cambiar de pista y
        // fade-out vigilado por posición al final de la pista.
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (crossfadeSeconds > 0) startFadeIn() else exoPlayer.volume = 1f
                // RONDA 6: pista nueva, etiquetas nuevas: se resetea
                // la ganancia a 0 hasta que llegue su metadata.
                trackGainDb = null
                albumGainDb = null
                applyReplayGain()
            }

            override fun onMetadata(metadata: Metadata) {
                // RONDA 6: la etiqueta ReplayGain llega con los
                // metadatos del archivo (FLAC/MP3). Sin etiqueta, la
                // ganancia queda en 0 y no se finge nada.
                val (track, album) = replayGainFromMetadata(metadata)
                trackGainDb = track
                albumGainDb = album
                applyReplayGain()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupEqualizer(audioSessionId)
            }
        })
        serviceScope.launch {
            while (true) {
                val p = player
                if (p != null && p.isPlaying && !fadingIn && crossfadeSeconds > 0) {
                    val duration = p.duration
                    val remaining = duration - p.currentPosition
                    val windowMs = crossfadeSeconds * 1000L
                    if (duration > 0 && p.currentPosition > 5000 && remaining in 0..windowMs) {
                        p.volume = (remaining.toFloat() / windowMs.toFloat()).coerceIn(0.05f, 1f)
                    } else if (duration > 0 && remaining > windowMs && p.volume < 1f) {
                        p.volume = 1f
                    }
                }
                // Refinamiento: en pausa el monitor duerme 2 s en vez de
                // despertar 4 veces por segundo sin nada que vigilar.
                delay(if (p?.isPlaying == true) 250 else 2000)
            }
        }
        // startForeground() INMEDIATO: la notificación de MediaSession
        // se publica al arrancar el servicio, antes de cualquier play().
        startForeground(NOTIFICATION_ID, buildPlaceholderNotification())
    }

    /**
     * RONDA 6: aplica la ganancia ReplayGain según el modo, las
     * etiquetas de la pista que suena y el preamp. Sin modo o sin
     * etiqueta aplicable, el procesador queda en ganancia 0 (apagado).
     */
    private fun applyReplayGain() {
        val processor = replayGainProcessor ?: return
        val gainDb = selectReplayGainDb(replayGainMode, trackGainDb, albumGainDb, replayGainPreAmpDb)
        processor.enabled = replayGainMode != "off" && gainDb != 0f
        processor.setTargetGainDb(gainDb)
    }

    /**
     * RONDA 6: el detector encontró 2 s de silencio. Al final de la
     * pista (últimos 30 s) pasa a la siguiente; en mitad de la pista
     * salta 3 s hacia adelante (el detector necesita otros 2 s de
     * silencio para volver a avisar, así que no se encadena solo).
     * Se llama desde el hilo de audio: los métodos de ExoPlayer son
     * seguros desde cualquier hilo.
     */
    private fun onLongSilenceDetected() {
        val p = player ?: return
        if (!skipSilenceEnabled) return
        val duration = p.duration
        if (duration <= 0) return
        val remaining = duration - p.currentPosition
        if (remaining <= 30_000L) {
            if (p.currentMediaItemIndex < p.mediaItemCount - 1) {
                p.seekToNextMediaItem()
            }
        } else {
            p.seekTo((p.currentPosition + 3000).coerceAtMost(duration))
        }
    }

    /**
     * Crea el EQ sobre la sesión de audio REAL del reproductor, cuando
     * ExoPlayer la anuncia (al preparar la primera pista). Si la sesión
     * cambia, se suelta el manager viejo y se crea uno nuevo con el
     * estado guardado del usuario, no en cero.
     */
    private fun setupEqualizer(sessionId: Int) {
        if (sessionId == 0 || sessionId == equalizerSessionId) return
        equalizer?.release()
        equalizerSessionId = sessionId
        val manager = try {
            EqualizerManager(sessionId)
        } catch (e: Exception) {
            // Sin EQ en este dispositivo: se registra y la UI lo dice.
            ErrorLog.log(this, "EQ no disponible", e)
            AudioEffects.markUnsupported()
            null
        }
        equalizer = manager
        AudioEffects.publish(manager)
        manager?.let { eq ->
            serviceScope.launch {
                val saved = EqStateStore(this@PlaybackService).stateFlow.first()
                eq.setEnabled(saved.enabled)
                eq.applyPreset(EqualizerManager.Preset("Guardado", saved.levelsDb))
                eq.setBassBoost(saved.bassBoost.toShort())
                eq.setVirtualizer(saved.virtualizer.toShort())
            }
        }
    }

    /** Fade-in de 0 a 1 durante los segundos de crossfade. */
    private fun startFadeIn() {
        val p = player ?: return
        val seconds = crossfadeSeconds.coerceIn(1, 12)
        fadeJob?.cancel()
        fadingIn = true
        p.volume = 0f
        fadeJob = serviceScope.launch {
            val steps = 20
            val stepMs = seconds * 1000L / steps
            for (i in 1..steps) {
                delay(stepMs)
                p.volume = i / steps.toFloat()
            }
            fadingIn = false
        }
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
        serviceScope.cancel()
        fadeJob?.cancel()
        mediaSession?.release()
        player?.release()
        equalizer?.release()
        AudioEffects.clear()
        equalizerSessionId = 0
        replayGainProcessor = null
        silenceProcessor = null
        mediaSession = null
        player = null
        equalizer = null
        super.onDestroy()
    }
}

/** Ajustes de reproducción con efecto real sobre el ExoPlayer. */
object PlayerControls {
    /** Velocidad y tono juntos: ExoPlayer conserva el tono al cambiar
     * la velocidad (pitch 1.0) y el tono se cambia aparte (RONDA 6). */
    fun setSpeedAndPitch(player: Player, speed: Float, pitch: Float) {
        player.playbackParameters = player.playbackParameters
            .withSpeed(speed.coerceIn(0.5f, 2.0f))
            .withPitch(pitch.coerceIn(0.5f, 2.0f))
    }
}
