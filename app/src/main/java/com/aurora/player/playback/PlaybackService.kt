package com.aurora.player.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aurora.player.MainActivity
import com.aurora.player.alarm.AlarmScheduler
import com.aurora.player.alarm.AlarmSettings
import com.aurora.player.alarm.AlarmSource
import com.aurora.player.audio.ChannelBalanceAudioProcessor
import com.aurora.player.audio.EqualizerManager
import com.aurora.player.audio.currentOutputRoute
import com.aurora.player.audio.routeKey
import com.aurora.player.audio.ReplayGainProcessor
import com.aurora.player.audio.SilenceDetectorAudioProcessor
import com.aurora.player.audio.replayGainFromMetadata
import com.aurora.player.audio.selectReplayGainDb
import com.aurora.player.data.AlarmStore
import com.aurora.player.data.AuroraDatabase
import com.aurora.player.data.EqStateStore
import com.aurora.player.data.MusicRepository
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
import kotlinx.coroutines.withContext

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
    /** Fase 1b: reproducir al conectar auriculares/BT (opt-in, OFF). */
    private var autoplayOnConnect = false
    private var headphoneReceiver: BroadcastReceiver? = null
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
    /** Balance L/R (SPEC 005 FR-001): procesador en la cadena. */
    private var balanceProcessor: ChannelBalanceAudioProcessor? = null
    /** Balance L/R vivo (para que B nazca con el mismo). */
    private var channelBalance: Float = 0f
    /** Fase 1b — crossfade de 2 reproductores: B solo vive durante
     * la ventana de solape; fuera de ella está liberado. */
    private var overlapPlayer: ExoPlayer? = null
    private var overlapJob: Job? = null
    private var overlapping: Boolean = false
    private var overlapHandoff: Boolean = false
    private var crossfadeGaplessAlbum: Boolean = true
    private var overlapGainProcessor: ReplayGainProcessor? = null
    private var overlapBalanceProcessor: ChannelBalanceAudioProcessor? = null
    private var overlapEqualizer: EqualizerManager? = null
    private var overlapEqualizerSessionId: Int = 0
    private var overlapTrackGainDb: Float? = null
    private var overlapAlbumGainDb: Float? = null

    companion object {
        const val CHANNEL_ID = "aurora_player_playback"
        const val NOTIFICATION_ID = 1
        /** RONDA 7 P4: la alarma la dispara el receptor con estas acciones. */
        const val ACTION_PLAY_ALARM = "com.aurora.player.PLAY_ALARM"
        const val ACTION_STOP_ALARM = "com.aurora.player.STOP_ALARM"
        const val ALARM_CHANNEL_ID = "aurora_player_alarm"
        const val ALARM_NOTIFICATION_ID = 2
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createAlarmChannel()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val gainProcessor = ReplayGainProcessor().also { replayGainProcessor = it }
        val silenceDetector = SilenceDetectorAudioProcessor(
            onLongSilence = { onLongSilenceDetected() },
        ).also { silenceProcessor = it }
        // SPEC 005 FR-001: balance L/R, tercer procesador de la cadena.
        val balance = ChannelBalanceAudioProcessor().also { balanceProcessor = it }
        // RONDA 6 (fix run #68, VERIFICADO EN EL CÓDIGO FUENTE 1.4.1):
        // en 1.4.1 buildAudioSink tiene TRES parámetros (context,
        // enableFloatOutput, enableAudioTrackPlaybackParams) y el
        // Builder de DefaultAudioSink NO tiene setOffloadMode (ese
        // método solo existe en DefaultAudioSink, no en su Builder).
        // El override replica el cuerpo por defecto de 1.4.1 y solo
        // añade los procesadores.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(gainProcessor, silenceDetector, balance))
                    .build()
            }
        }
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // auto-pausa al quitar auriculares
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
            // Sin esto, tocar la notificación no abre la app
            // (2026-11-10): Media3 usa la actividad de la sesión
            // como destino del toque.
            .setSessionActivity(openAppPendingIntent())
            .build()
        // Fase 1b: reproducir al conectar auriculares/BT (opt-in,
        // OFF por defecto). El receptor es dinámico: solo vive
        // mientras el servicio está vivo; si la app se cerró del
        // todo, no resucita la reproducción.
        val autoplayReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val connected = isAudioOutputConnected(
                    intent.action,
                    intent.getIntExtra("state", 0),
                    intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1),
                )
                if (connected) maybeAutoplayOnConnect()
                // EQ por salida: al cambiar la salida (conectar o
                // quitar auriculares/BT) se aplica el EQ de la nueva.
                applyRouteEq()
            }
        }
        headphoneReceiver = autoplayReceiver
        val autoplayFilter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(autoplayReceiver, autoplayFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(autoplayReceiver, autoplayFilter)
        }
        // Ajustes vivos (ETAPA 4): crossfade y auto-pausa se aplican
        // cuando cambian en Ajustes, sin reiniciar nada.
        serviceScope.launch {
            PlayerSettingsStore(this@PlaybackService).settingsFlow.collect { settings ->
                crossfadeSeconds = settings.crossfadeSeconds
                crossfadeGaplessAlbum = settings.crossfadeGaplessAlbumException
                exoPlayer.setHandleAudioBecomingNoisy(settings.autoPauseOnDisconnect)
                autoplayOnConnect = settings.autoplayOnConnect
                if (crossfadeSeconds == 0) {
                    fadeJob?.cancel()
                    fadingIn = false
                    cancelOverlap()
                    exoPlayer.volume = 1f
                }
                // RONDA 6: el modo/preamp de ReplayGain se aplica al
                // cambiar en Ajustes, con la etiqueta de la pista que
                // suena (si la hay).
                replayGainMode = settings.replayGainMode
                replayGainPreAmpDb = settings.replayGainPreAmpDb
                applyReplayGain()
                applyOverlapReplayGain()
                // RONDA 6: saltar silencio se enciende/apaga en vivo.
                skipSilenceEnabled = settings.skipSilence
                silenceProcessor?.enabled = settings.skipSilence
                // SPEC 005 FR-001: balance L/R en vivo; en 0 el
                // procesador pasa el audio sin tocarlo.
                channelBalance = settings.channelBalance
                balanceProcessor?.setBalance(settings.channelBalance)
                balanceProcessor?.enabled = settings.channelBalance != 0f
                overlapBalanceProcessor?.setBalance(settings.channelBalance)
                overlapBalanceProcessor?.enabled = settings.channelBalance != 0f
            }
        }
        // Crossfade real (ETAPA 4): fade-in al cambiar de pista y
        // fade-out vigilado por posición al final de la pista.
        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (overlapHandoff) {
                    // Traspaso del solape: A ya está en la pista
                    // siguiente; el volumen lo pone el traspaso, no
                    // el fade-in. La ganancia se resetea igual.
                    trackGainDb = null
                    albumGainDb = null
                    applyReplayGain()
                    return
                }
                // El usuario cambió de pista a mano durante un
                // solape: el solape ya no vale.
                if (overlapping) cancelOverlap()
                if (crossfadeSeconds > 0) startFadeIn() else exoPlayer.volume = 1f
                // RONDA 6: pista nueva, etiquetas nuevas: se resetea
                // la ganancia a 0 hasta que llegue su metadata.
                trackGainDb = null
                albumGainDb = null
                applyReplayGain()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Pausa/reanudación durante el solape: B sigue a A.
                if (overlapping) overlapPlayer?.playWhenReady = isPlaying
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // Buscar a mano durante un solape: las posiciones
                // divergen y el solape ya no vale. El traspaso hace
                // sus propios seeks con overlapHandoff activo.
                if (overlapping && !overlapHandoff &&
                    (reason == Player.DISCONTINUITY_REASON_SEEK ||
                        reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)
                ) {
                    cancelOverlap()
                }
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
                        if (!overlapping) {
                            // Fase 1b: solape real si aplica; si no,
                            // el fade-out de siempre.
                            if (!tryStartOverlap(p)) {
                                p.volume = (remaining.toFloat() / windowMs.toFloat()).coerceIn(0.05f, 1f)
                            }
                        }
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
                applyRouteEq()
            }
        }
    }

    /**
     * EQ por salida: si está activado y la salida actual tiene un EQ
     * guardado, se aplica sobre el EQ global (también al de solape,
     * para que el tono no cambie a mitad del fundido).
     */
    private fun applyRouteEq() {
        val eq = equalizer ?: return
        serviceScope.launch {
            val store = EqStateStore(this@PlaybackService)
            if (!store.perOutputFlow.first()) return@launch
            val (route, name) = currentOutputRoute(this@PlaybackService)
            val saved = store.routeStateFlow(routeKey(route, name)).first() ?: return@launch
            eq.setEnabled(saved.enabled)
            eq.applyPreset(EqualizerManager.Preset("Salida", saved.levelsDb))
            eq.setBassBoost(saved.bassBoost.toShort())
            eq.setVirtualizer(saved.virtualizer.toShort())
            overlapEqualizer?.let { overlap ->
                overlap.setEnabled(saved.enabled)
                overlap.applyPreset(EqualizerManager.Preset("Salida", saved.levelsDb))
                overlap.setBassBoost(saved.bassBoost.toShort())
                overlap.setVirtualizer(saved.virtualizer.toShort())
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

    /**
     * Fase 1b — crossfade de 2 reproductores: arranca el solape si
     * aplica (decisión pura en CrossfadeOverlap.kt). B prepara la
     * pista siguiente y los dos funden a la vez. Devuelve si arrancó.
     */
    private fun tryStartOverlap(p: ExoPlayer): Boolean {
        if (overlapping) return true
        val count = p.mediaItemCount
        if (count == 0) return false
        val index = p.currentMediaItemIndex
        val repeatOne = p.repeatMode == Player.REPEAT_MODE_ONE
        val nextIndex = when {
            index < count - 1 -> index + 1
            p.repeatMode == Player.REPEAT_MODE_ALL -> 0
            else -> return false
        }
        val nextItem = p.getMediaItemAt(nextIndex)
        val currentAlbum = p.currentMediaItem?.mediaMetadata?.albumTitle?.toString()
        val nextAlbum = nextItem.mediaMetadata.albumTitle?.toString()
        if (!shouldOverlapCrossfade(
                crossfadeSeconds, true, repeatOne,
                currentAlbum, nextAlbum, crossfadeGaplessAlbum,
            )
        ) return false
        // Duración de la pista siguiente (Timeline; TIME_UNSET si
        // aún no se conoce): la ventana se recorta a ella.
        var nextDurationMs = 0L
        val timeline = p.currentTimeline
        if (timeline.windowCount > nextIndex) {
            val w = Timeline.Window()
            timeline.getWindow(nextIndex, w)
            if (w.durationMs != C.TIME_UNSET) nextDurationMs = w.durationMs
        }
        val window = overlapWindowMs(crossfadeSeconds, nextDurationMs)
        // B lleva su propia cadena: ReplayGain (ganancia de la pista
        // siguiente, por sus metadatos) + Balance. Sin detector de
        // silencio: el silencio al empezar la pista entrante es
        // normal y su callback actúa sobre A, no sobre B.
        val gainB = ReplayGainProcessor().also { overlapGainProcessor = it }
        val balanceB = ChannelBalanceAudioProcessor().also { overlapBalanceProcessor = it }
        balanceB.setBalance(channelBalance)
        balanceB.enabled = channelBalance != 0f
        val factoryB = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(gainB, balanceB))
                    .build()
            }
        }
        val audioAttributesB = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val b = ExoPlayer.Builder(this, factoryB)
            .setAudioAttributes(audioAttributesB, true)
            .build()
        b.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupOverlapEqualizer(audioSessionId)
            }

            override fun onMetadata(metadata: Metadata) {
                val (track, album) = replayGainFromMetadata(metadata)
                overlapTrackGainDb = track
                overlapAlbumGainDb = album
                applyOverlapReplayGain()
            }
        })
        b.setMediaItem(nextItem)
        b.prepare()
        b.volume = 0f
        b.play()
        overlapPlayer = b
        overlapping = true
        // Rampa: A baja 1→0 y B sube 0→1 en la misma ventana.
        overlapJob = serviceScope.launch {
            val steps = 20
            val stepMs = window / steps
            for (i in 1..steps) {
                delay(stepMs)
                val f = i / steps.toFloat()
                p.volume = (1f - f).coerceIn(0f, 1f)
                b.volume = f
            }
            finishOverlapHandoff(p)
        }
        return true
    }

    /**
     * Traspaso: A salta a la pista siguiente en la posición por la
     * que va B (misma pista, mismo punto) y recupera el volumen; B
     * cubre el audio durante el salto porque sigue sonando hasta
     * que A vuelve a 1. A está a volumen 0 durante el salto: no se
     * escucha el principio de la pista.
     */
    private fun finishOverlapHandoff(p: ExoPlayer) {
        val b = overlapPlayer ?: return
        overlapHandoff = true
        p.volume = 0f
        p.seekToNextMediaItem()
        p.seekTo(b.currentPosition)
        p.volume = 1f
        overlapHandoff = false
        releaseOverlapPlayer()
        overlapping = false
    }

    private fun cancelOverlap() {
        overlapJob?.cancel()
        overlapJob = null
        releaseOverlapPlayer()
        overlapping = false
        val p = player
        if (p != null && p.isPlaying) p.volume = 1f
    }

    private fun releaseOverlapPlayer() {
        overlapPlayer?.release()
        overlapPlayer = null
        overlapEqualizer?.release()
        overlapEqualizer = null
        overlapEqualizerSessionId = 0
        overlapGainProcessor = null
        overlapBalanceProcessor = null
        overlapTrackGainDb = null
        overlapAlbumGainDb = null
    }

    /** EQ del solape: segundo manager sobre la sesión de B con el
     * MISMO estado guardado que A (si no, el tono cambiaría a mitad
     * del fundido). No se publica en AudioEffects: la UI edita el
     * estado guardado, que B lee al crearse. */
    private fun setupOverlapEqualizer(sessionId: Int) {
        if (sessionId == 0 || sessionId == overlapEqualizerSessionId) return
        overlapEqualizer?.release()
        overlapEqualizerSessionId = sessionId
        val manager = try {
            EqualizerManager(sessionId)
        } catch (e: Exception) {
            ErrorLog.log(this, "EQ solape no disponible", e)
            null
        }
        overlapEqualizer = manager
        manager?.let { eq ->
            serviceScope.launch {
                val saved = EqStateStore(this@PlaybackService).stateFlow.first()
                eq.setEnabled(saved.enabled)
                eq.applyPreset(EqualizerManager.Preset("Guardado", saved.levelsDb))
                eq.setBassBoost(saved.bassBoost.toShort())
                eq.setVirtualizer(saved.virtualizer.toShort())
                applyRouteEq()
            }
        }
    }

    private fun applyOverlapReplayGain() {
        val processor = overlapGainProcessor ?: return
        val gainDb = selectReplayGainDb(replayGainMode, overlapTrackGainDb, overlapAlbumGainDb, replayGainPreAmpDb)
        processor.enabled = replayGainMode != "off" && gainDb != 0f
        processor.setTargetGainDb(gainDb)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reproducción",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Controles de reproducción de Aurora Player" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** RONDA 7 P4: canal de la alarma (alta importancia, sin sonido
     * propio: el sonido ES la música, por el canal de alarma). */
    private fun createAlarmChannel() {
        val channel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarma",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alarma musical de Aurora Player"
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * RONDA 7 P4: la alarma llega como intent al servicio.
     * PLAY_ALARM suena la música elegida; STOP_ALARM la apaga.
     * El resto de intents sigue su camino normal de MediaSession.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_ALARM -> serviceScope.launch { playAlarm() }
            ACTION_STOP_ALARM -> stopAlarm()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Suena la alarma: resuelve qué música, la pone por el canal de
     * alarma (se oye aunque el teléfono esté en silencio) y publica
     * la notificación con el botón "Apagar". */
    private suspend fun playAlarm() {
        val settings = withContext(Dispatchers.IO) {
            AlarmStore(this@PlaybackService).settingsFlow.first()
        }
        if (!settings.enabled) return
        val items = withContext(Dispatchers.IO) { resolveAlarmItems(settings) }
        if (items.isEmpty()) {
            ErrorLog.log(this, "Alarma sin música", null)
            return
        }
        val p = player ?: return
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_ALARM)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        p.setMediaItems(items)
        p.prepare()
        p.play()
        showAlarmNotification(settings)
    }

    /** Canción / playlist / mezcla de la alarma; si lo elegido ya no
     * existe (canción borrada, playlist vacía), mezcla todo: la
     * alarma suena igual, nunca en silencio. */
    private suspend fun resolveAlarmItems(settings: AlarmSettings): List<MediaItem> {
        val minDurationMs = PlayerSettingsStore(this).settingsFlow.first().minDurationMs
        val songs = MusicRepository(this).loadSongs(minDurationMs)
        if (songs.isEmpty()) return emptyList()
        return when (val source = settings.source) {
            AlarmSource.ShuffleAll -> songs.shuffled().map { songToMediaItem(this, it) }
            is AlarmSource.Song -> {
                val song = songs.firstOrNull { it.id == source.songId }
                    ?: return songs.shuffled().map { songToMediaItem(this, it) }
                listOf(songToMediaItem(this, song))
            }
            is AlarmSource.Playlist -> {
                val ids = AuroraDatabase.getInstance(this).playlistDao()
                    .songsOf(source.playlistId).map { it.songId }.toSet()
                val picked = songs.filter { it.id in ids }
                if (picked.isEmpty()) songs.shuffled().map { songToMediaItem(this, it) }
                else picked.map { songToMediaItem(this, it) }
            }
        }
    }

    /** Apaga la alarma: pausa, vuelve al canal multimedia y quita la
     * notificación; si no suena nada, el servicio se detiene solo. */
    private fun stopAlarm() {
        val p = player
        p?.pause()
        p?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        getSystemService(NotificationManager::class.java)?.cancel(ALARM_NOTIFICATION_ID)
        if (p == null || !p.playWhenReady) stopSelf()
    }

    private fun showAlarmNotification(settings: AlarmSettings) {
        val what = when (val source = settings.source) {
            is AlarmSource.Song -> source.title
            is AlarmSource.Playlist -> source.name
            AlarmSource.ShuffleAll -> "Mezcla de tu biblioteca"
        }
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("Alarma · Aurora Player")
            .setContentText(what)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Apagar",
                    AlarmScheduler.stopPendingIntent(this),
                ).build(),
            )
            .build()
        getSystemService(NotificationManager::class.java)?.notify(ALARM_NOTIFICATION_ID, notification)
    }

    /** Tocar cualquier notificación de Aurora abre la app (mismo
     * PendingIntent que ya usa la notificación de la alarma). */
    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildPlaceholderNotification(): Notification {
        // Media3 sustituye esta notificación por la de MediaSession en
        // cuanto hay un MediaItem; este placeholder solo cubre el arranque.
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Aurora Player")
            .setContentText("Listo para reproducir")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent())
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

    /**
     * Fase 1b: si el usuario activó "Reproducir al conectar" y hay
     * una canción cargada en pausa, reanuda al conectar auriculares
     * o BT. Sin canción cargada no hace nada (no se inventa
     * reproducción) y si ya suena, tampoco.
     */
    private fun maybeAutoplayOnConnect() {
        val p = player ?: return
        if (!autoplayOnConnect) return
        if (p.isPlaying) return
        if (p.currentMediaItem == null) return
        p.play()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try { headphoneReceiver?.let { unregisterReceiver(it) } } catch (_: IllegalArgumentException) { }
        headphoneReceiver = null
        fadeJob?.cancel()
        overlapJob?.cancel()
        releaseOverlapPlayer()
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
    /** Velocidad y tono juntos (fix run #67: PlaybackParameters
     * NO tiene withPitch en Media3 1.4.1; se construye directo
     * con los dos valores, como dice la referencia oficial). */
    fun setSpeedAndPitch(player: Player, speed: Float, pitch: Float) {
        player.playbackParameters = PlaybackParameters(
            speed.coerceIn(0.5f, 2.0f),
            pitch.coerceIn(0.5f, 2.0f),
        )
    }
}
