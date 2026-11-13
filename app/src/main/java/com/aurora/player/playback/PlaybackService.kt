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
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.aurora.player.MainActivity
import com.aurora.player.alarm.AlarmScheduler
import com.aurora.player.alarm.AlarmSettings
import com.aurora.player.alarm.AlarmSource
import com.aurora.player.audio.AtmosSupport
import com.aurora.player.audio.ChannelBalanceAudioProcessor
import com.aurora.player.audio.EqualizerManager
import com.aurora.player.audio.isDirectMode
import com.aurora.player.audio.currentOutputRoute
import com.aurora.player.audio.detectAtmosSupport
import com.aurora.player.audio.findUsbDac
import com.aurora.player.audio.routeKey
import com.aurora.player.audio.ReplayGainProcessor
import com.aurora.player.audio.LufsCache
import com.aurora.player.audio.VisualizerCapture
import com.aurora.player.audio.measureFileLufs
import com.aurora.player.audio.applyTone2d
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

    /** Barras del visualizador real (FFT de la sesión que suena). */
    var visualizerBars: List<Float> by mutableStateOf(emptyList())
        private set

    internal fun publish(manager: EqualizerManager?) {
        equalizerManager = manager
    }

    internal fun publishBars(bars: List<Float>) {
        visualizerBars = bars
    }

    internal fun markUnsupported() {
        equalizerUnsupported = true
    }

    internal fun clear() {
        equalizerManager = null
        equalizerUnsupported = false
        visualizerBars = emptyList()
    }
}

class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibraryService.MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    /** Cargador de portadas de la notificación (su hilo se libera en onDestroy). */
    private var bitmapLoader: AuroraBitmapLoader? = null
    private var equalizer: EqualizerManager? = null
    private var audioOutputDirect: Boolean = false
    // Ronda USB Serie A: los sumideros de audio (principal + solape)
    // para fijarles el DAC USB como dispositivo preferido.
    private val audioSinks = mutableListOf<DefaultAudioSink>()
    // Slice 5 paso 4: ¿este dispositivo pasa Atmos por offload?
    // Se decide una vez al arrancar (paso 5: el interruptor
    // "Dolby Atmos (passthrough)" re-aplica el modo con
    // applyAtmosOffloadMode). El modo vive en el sink y el sink
    // decide POR FORMATO: con offload activado, un E-AC-3 que el
    // dispositivo soporta va directo al hardware (passthrough) y
    // el resto se decodifica a PCM como siempre. La distinción JOC
    // (badge y aviso) vive en la UI con SongDetails.isAtmosJoc: el
    // sink no distingue JOC de E-AC-3 normal, y un E-AC-3 sin JOC
    // también pasa en offload cuando el dispositivo lo soporta (es
    // el mismo passthrough DD+). En offload los procesadores (EQ,
    // ReplayGain, balance, silencio) NO se aplican: es el sink
    // quien los salta, no los apagamos nosotros. Si el dispositivo
    // no lo soporta, la pista se decodifica normal (núcleo DD+ sin
    // Atmos): nunca silencio ni crash.
    private var atmosOffloadOn: Boolean = false

    /**
     * Fija el modo offload de un sink según la decisión actual.
     * setOffloadMode exige API 29+ (Media3 lo comprueba con
     * checkState): por debajo de Q no se toca, aunque la detección
     * ya devuelva NONE. El modo se lee en el próximo configure(),
     * así que vale para la siguiente pista.
     */
    private fun applyOffloadModeToSink(sink: DefaultAudioSink) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        sink.setOffloadMode(
            if (atmosOffloadOn) AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED
            else AudioSink.OFFLOAD_MODE_DISABLED,
        )
    }

    /** Reaplica el modo offload a todos los sinks conocidos. */
    private fun applyAtmosOffloadMode() {
        for (sink in audioSinks) {
            try {
                applyOffloadModeToSink(sink)
            } catch (e: Exception) {
                ErrorLog.log(this@PlaybackService, "Atmos", e)
            }
        }
    }
    private var visualizerCapture: VisualizerCapture? = null

    /** Ajustes vivos: crossfade y auto-pausa se leen de DataStore. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var crossfadeSeconds = 0
    /** Fase 1b: reproducir al conectar auriculares/BT (opt-in, OFF). */
    private var autoplayOnConnect = false
    // AUTO-CERRAR (2026-11-11, como Musicolet): la app se cierra
    // sola si la música lleva parada autoCloseMinutes minutos y la
    // app no está visible. appVisible la avisa la actividad por
    // broadcast (ACTION_APP_VISIBILITY).
    private var appVisible = true
    private var autoCloseMinutes = 0
    private var autoCloseJob: Job? = null
    private var visibilityReceiver: BroadcastReceiver? = null
    // RONDA 179: la alarma suena por el mismo reproductor, pero
    // antes de sonar tarda en resolver (dos lecturas IO) con el
    // reproductor en pausa: sin esta bandera, el temporizador
    // podría vencer en ese hueco y parar la alarma antes de sonar.
    private var alarmActive = false
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
    /** LUFS medido de la pista que suena (modo "lufs"), null = sin medir. */
    private var trackLufsDb: Float? = null
    private var overlapLufsDb: Float? = null
    private var lufsJob: Job? = null
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
        /** Botón X de la notificación (2026-11-11): cierra la música y la app. */
        const val ACTION_CLOSE_APP = "com.aurora.player.ACTION_CLOSE_APP"
        const val ACTION_STOP_ALL = "aurora_stop_all"
        /** AUTO-CERRAR (2026-11-11): la actividad avisa si se ve o no. */
        const val ACTION_APP_VISIBILITY = "com.aurora.player.ACTION_APP_VISIBILITY"
        const val EXTRA_APP_VISIBLE = "app_visible"
        const val ALARM_CHANNEL_ID = "aurora_player_alarm"
        const val ALARM_NOTIFICATION_ID = 2
    }

    override fun onCreate() {
        super.onCreate()
        // Botón X de la notificación (2026-11-11): proveedor propio
        // para que la compacta sea [anterior, reproducir/pausa, X].
        // Va antes de crear la sesión: el manager se crea perezoso
        // al añadirla y ya no se puede cambiar el proveedor.
        setMediaNotificationProvider(AuroraNotificationProvider(this))
        createNotificationChannel()
        createAlarmChannel()
        // startForeground() INMEDIATO (auditoría Codex, ronda 2):
        // la notificación se publica al arrancar el servicio, ANTES
        // de crear el player y la sesión: si algo de eso fallara, el
        // servicio no moriría por ForegroundServiceDidNotStartInTime.
        startForeground(NOTIFICATION_ID, buildPlaceholderNotification())
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
        // Slice 5 paso 4: ¿este dispositivo pasa Atmos por offload?
        // detectAtmosSupport ya caza Throwable y devuelve NONE si
        // algo falla, así que aquí no hace falta otro try/catch.
        atmosOffloadOn = detectAtmosSupport(this) == AtmosSupport.ATMOS
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
                val sink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(gainProcessor, silenceDetector, balance))
                    .build()
                audioSinks.add(sink)
                // Slice 5 paso 4: offload Atmos cuando el dispositivo
                // lo soporta (el sink decide por formato).
                applyOffloadModeToSink(sink)
                return sink
            }
        }
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true) // auto-pausa al quitar auriculares
            .build()
        player = exoPlayer
        // Ronda USB Serie A: si ya hay un DAC conectado, el
        // AudioTrack va directo a él desde la primera pista.
        applyUsbRouting()
        // RONDA 2 (2026-11-09): el EQ NO se crea aquí. Al arrancar,
        // exoPlayer.audioSessionId aún es 0 (la sesión se crea al
        // preparar la primera pista) y Equalizer(0, 0) se engancha a
        // la MEZCLA GLOBAL: el EQ cambiaba el sonido de TODAS las
        // apps mientras Aurora corría, y no estaba atado a su sesión.
        // Se crea en onAudioSessionIdChanged, con la sesión real.
        AudioEffects.clear()
        // Android Auto (Fase 2 app): MediaLibrarySession con el
        // árbol de AutoBrowse; el coche navega por carpetas y toca
        // canciones reales de MediaStore.
        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(
            this,
            exoPlayer,
            AuroraLibraryCallback(
                context = this,
                scope = serviceScope,
                loadSongs = { MusicRepository(this).loadSongs() },
                loadFavoriteIds = {
                    AuroraDatabase.getInstance(this).libraryDao().favoriteIds().toSet()
                },
                // Pulido notificación: los botones de aleatorio y
                // bucle de la notificación llegan como comandos
                // personalizados y se aplican al reproductor.
                onNotificationCommand = { action ->
                    val p = player
                    if (p == null) {
                        false
                    } else when (action) {
                        "aurora_toggle_shuffle" -> {
                            p.shuffleModeEnabled = !p.shuffleModeEnabled
                            true
                        }
                        "aurora_cycle_repeat" -> {
                            p.repeatMode = when (p.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            true
                        }
                        // Botón X de la notificación (2026-11-11):
                        // para la música del todo y cierra la app.
                        ACTION_STOP_ALL -> {
                            stopAllAndCloseApp()
                            true
                        }
                        else -> false
                    }
                },
            ),
        )
            // Portadas de la notificación: MediaStore, incrustada en el
            // archivo o inicial de Aurora; nunca sin carátula.
            .setBitmapLoader(AuroraBitmapLoader(this).also { bitmapLoader = it })
            // Sin esto, tocar la notificación no abre la app
            // (2026-11-10): Media3 usa la actividad de la sesión
            // como destino del toque.
            .setSessionActivity(openAppPendingIntent())
            .build()
        // Pulido notificación: botones de aleatorio y bucle en la
        // notificación del panel. Media3 1.4.1 solo añade al layout
        // personalizado los botones con comando personalizado
        // (COMMAND_CODE_CUSTOM): los de reproductor se ignoran.
        updateNotificationCustomLayout()
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
                applyUsbRouting()
            }
        }
        headphoneReceiver = autoplayReceiver
        val autoplayFilter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            // Ronda 16: conectar un DAC USB no dispara ni HEADSET_PLUG
            // ni A2DP: sin estas acciones el DAC no se buscaba hasta
            // otro evento y el audio se quedaba en la ruta anterior.
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(autoplayReceiver, autoplayFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(autoplayReceiver, autoplayFilter)
        }
        // AUTO-CERRAR (2026-11-11): la actividad avisa de si se ve
        // o no. El broadcast no arranca el servicio: si está muerto,
        // no hay nada que cerrar y no llega a ningún lado.
        visibilityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                appVisible = intent?.getBooleanExtra(EXTRA_APP_VISIBLE, true) ?: true
                updateAutoCloseTimer()
            }
        }.also { receiver ->
            val filter = IntentFilter(ACTION_APP_VISIBILITY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
        }
        // Ajustes vivos (ETAPA 4): crossfade y auto-pausa se aplican
        // cuando cambian en Ajustes, sin reiniciar nada.
        serviceScope.launch {
            PlayerSettingsStore(this@PlaybackService).settingsFlow.collect { settings ->
                // Salida Directa (Fase 2): todo el procesado se apaga
                // — ReplayGain, silencio, balance, EQ del sistema,
                // crossfade — y el volumen queda a 1.0.
                val direct = isDirectMode(settings.audioOutputMode)
                audioOutputDirect = direct
                crossfadeSeconds = if (direct) 0 else settings.crossfadeSeconds
                crossfadeGaplessAlbum = settings.crossfadeGaplessAlbumException
                exoPlayer.setHandleAudioBecomingNoisy(settings.autoPauseOnDisconnect)
                autoplayOnConnect = settings.autoplayOnConnect
                // AUTO-CERRAR (2026-11-11): cada cambio del ajuste
                // reevalúa el temporizador.
                autoCloseMinutes = settings.autoCloseMinutes
                updateAutoCloseTimer()
                if (crossfadeSeconds == 0) {
                    fadeJob?.cancel()
                    fadingIn = false
                    cancelOverlap()
                    exoPlayer.volume = 1f
                }
                // RONDA 6: el modo/preamp de ReplayGain se aplica al
                // cambiar en Ajustes, con la etiqueta de la pista que
                // suena (si la hay).
                replayGainMode = if (direct) "off" else settings.replayGainMode
                replayGainPreAmpDb = settings.replayGainPreAmpDb
                applyReplayGain()
                applyOverlapReplayGain()
                // RONDA 6: saltar silencio se enciende/apaga en vivo.
                skipSilenceEnabled = if (direct) false else settings.skipSilence
                silenceProcessor?.enabled = skipSilenceEnabled
                // SPEC 005 FR-001: balance L/R en vivo; en 0 el
                // procesador pasa el audio sin tocarlo.
                channelBalance = if (direct) 0f else settings.channelBalance
                balanceProcessor?.setBalance(channelBalance)
                balanceProcessor?.enabled = channelBalance != 0f
                overlapBalanceProcessor?.setBalance(channelBalance)
                overlapBalanceProcessor?.enabled = channelBalance != 0f
                if (direct) {
                    equalizer?.setEnabled(false)
                    overlapEqualizer?.setEnabled(false)
                } else {
                    // Ronda 3 (Codex): al volver a estándar se restaura
                    // el EQ guardado; antes quedaba apagado hasta
                    // reiniciar el servicio.
                    val saved = EqStateStore(this@PlaybackService).stateFlow.first()
                    equalizer?.setEnabled(saved.enabled)
                    overlapEqualizer?.setEnabled(saved.enabled)
                    applyRouteEq()
                }
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
                    // Traspaso del solape: B ya midió el LUFS de esta
                    // pista; A lo hereda para no medir dos veces.
                    trackLufsDb = overlapLufsDb
                    applyReplayGain()
                    // Ronda 8 (Codex): el flag se limpia AQUÍ, no en
                    // finishOverlapHandoff: la transición es asíncrona
                    // y si se limpiaba en síncrono el callback llegaba
                    // con el flag ya en false y startFadeIn() bajaba
                    // el volumen a 0 al terminar cada solape.
                    overlapHandoff = false
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
                trackLufsDb = null
                applyReplayGain()
                measureLufsFor(mediaItem, isOverlap = false)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Pausa/reanudación durante el solape: B sigue a A.
                if (overlapping) {
                    if (isPlaying) {
                        overlapPlayer?.playWhenReady = true
                    } else {
                        // RONDA 149: pausar NO cancelaba la rampa: la
                        // corrutina seguía bajando/subiendo volúmenes
                        // y el traspaso avanzaba a la pista siguiente
                        // con la música en pausa. Pausar congela todo:
                        // se cancela el solape y A vuelve a volumen 1
                        // (cancelOverlap solo lo restaura si suena).
                        cancelOverlap()
                        player?.volume = 1f
                    }
                }
                // AUTO-CERRAR (2026-11-11): al parar empieza el
                // conteo; al sonar se cancela.
                updateAutoCloseTimer()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // RONDA 179: la alarma terminó sola al acabarse su
                // cola (sin Apagar): bajar la bandera para que el
                // auto-cierre vuelva a aplicar. Con repetición no
                // llega STATE_ENDED: la alarma sigue activa y la
                // bandera se queda, como toca.
                if (playbackState == Player.STATE_ENDED && alarmActive) {
                    alarmActive = false
                    updateAutoCloseTimer()
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateNotificationCustomLayout()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateNotificationCustomLayout()
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
                setupVisualizer(audioSessionId)
            }
        })
        serviceScope.launch {
            while (true) {
                val p = player
                // Ronda 4 (optimización): fuera de la ventana de
                // crossfade no hay nada que vigilar 4 veces por
                // segundo: dormir hasta su entrada (máx. 2 s por
                // pasada, para seguir restaurando el volumen).
                var sleepMs = if (p?.isPlaying == true) 250L else 2000L
                if (p != null && p.isPlaying && !fadingIn && crossfadeSeconds > 0) {
                    val duration = p.duration
                    val remaining = duration - p.currentPosition
                    val windowMs = crossfadeSeconds * 1000L
                    // Ronda 8 (Codex): en pistas de 5 s o menos el
                    // umbral de 5000 ms nunca se cumplía con remaining
                    // >= 0 y el solape no arrancaba nunca; el umbral
                    // baja a la mitad de la duración en pistas cortas,
                    // en las demás queda igual.
                    if (duration > 0 && p.currentPosition > minOf(5000L, duration / 2) && remaining in 0..windowMs) {
                        if (!overlapping) {
                            // Fase 1b: solape real si aplica; si no,
                            // el fade-out de siempre.
                            if (!tryStartOverlap(p)) {
                                p.volume = (remaining.toFloat() / windowMs.toFloat()).coerceIn(0.05f, 1f)
                            }
                        }
                        sleepMs = 250L
                    } else if (duration > 0 && remaining > windowMs) {
                        if (p.volume < 1f) p.volume = 1f
                        sleepMs = minOf(remaining - windowMs, 2000L).coerceAtLeast(250L)
                    }
                }
                delay(sleepMs)
            }
        }
    }

    /**
     * RONDA 6: aplica la ganancia ReplayGain según el modo, las
     * etiquetas de la pista que suena y el preamp. Sin modo o sin
     * etiqueta aplicable, el procesador queda en ganancia 0 (apagado).
     */
    private fun applyReplayGain() {
        val processor = replayGainProcessor ?: return
        val gainDb = selectReplayGainDb(
            replayGainMode, trackGainDb, albumGainDb,
            replayGainPreAmpDb, trackLufsDb,
        )
        processor.enabled = replayGainMode != "off" && gainDb != 0f
        processor.setTargetGainDb(gainDb)
    }

    /**
     * LUFS (Fase 2): mide la sonoridad de la pista en segundo plano
     * cuando el modo es "lufs". Primero la caché; si no está, se
     * decodifica el archivo entero (puede tardar segundos) y se
     * aplica solo si la pista sigue siendo la actual. Las pistas
     * online ("yt:") no se miden: su audio es un stream, no un archivo.
     */
    private fun measureLufsFor(item: MediaItem?, isOverlap: Boolean) {
        if (replayGainMode != "lufs") return
        val id = item?.mediaId ?: return
        if (id.startsWith("yt:")) return
        val uri = item.localConfiguration?.uri ?: return
        LufsCache.read(this, id)?.let { cached ->
            if (isOverlap) {
                overlapLufsDb = cached.toFloat()
                applyOverlapReplayGain()
            } else {
                trackLufsDb = cached.toFloat()
                applyReplayGain()
            }
            return
        }
        lufsJob?.cancel()
        lufsJob = serviceScope.launch {
            val measured = measureFileLufs(this@PlaybackService, uri) ?: return@launch
            LufsCache.write(this@PlaybackService, id, measured)
            val currentId = if (isOverlap) {
                overlapPlayer?.currentMediaItem?.mediaId
            } else {
                player?.currentMediaItem?.mediaId
            }
            if (currentId != id) return@launch
            if (isOverlap) {
                overlapLufsDb = measured.toFloat()
                applyOverlapReplayGain()
            } else {
                trackLufsDb = measured.toFloat()
                applyReplayGain()
            }
        }
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
        try {
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
        } catch (e: Exception) {
            // Se llama desde el hilo de audio: un fallo aquí no puede
            // tumbar el proceso (auditoría Codex, 2026-11-10).
            ErrorLog.log(this, "Silencio", e)
        }
    }

    /**
     * Crea el EQ sobre la sesión de audio REAL del reproductor, cuando
     * ExoPlayer la anuncia (al preparar la primera pista). Si la sesión
     * cambia, se suelta el manager viejo y se crea uno nuevo con el
     * estado guardado del usuario, no en cero.
     */
    /**
     * Pulido notificación: los botones de aleatorio y bucle que salen
     * en la notificación del panel. El icono y el texto muestran el
     * estado actual (aleatorio sí/no; repetir no/todo/una) y se
     * actualizan solos al cambiar desde la app o desde la notificación.
     */
    private fun updateNotificationCustomLayout() {
        val session = mediaSession ?: return
        val p = player ?: return
        val shuffleButton = CommandButton.Builder(
            if (p.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
        )
            .setSessionCommand(SessionCommand("aurora_toggle_shuffle", Bundle.EMPTY))
            .setDisplayName(if (p.shuffleModeEnabled) "Aleatorio: sí" else "Aleatorio: no")
            .build()
        val repeatIcon = when (p.repeatMode) {
            Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
            Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
            else -> CommandButton.ICON_REPEAT_OFF
        }
        val repeatName = when (p.repeatMode) {
            Player.REPEAT_MODE_ONE -> "Repetir: una"
            Player.REPEAT_MODE_ALL -> "Repetir: todo"
            else -> "Repetir: no"
        }
        val repeatButton = CommandButton.Builder(repeatIcon)
            .setSessionCommand(SessionCommand("aurora_cycle_repeat", Bundle.EMPTY))
            .setDisplayName(repeatName)
            .build()
        // Botón X de la notificación (2026-11-11): cierra la música
        // y la app. En la compacta sale siempre (AuroraNotificationProvider);
        // aquí solo se añade al layout, como aleatorio y repetir.
        val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
            .setSessionCommand(SessionCommand(ACTION_STOP_ALL, Bundle.EMPTY))
            .setDisplayName("Cerrar")
            .build()
        session.setCustomLayout(listOf(shuffleButton, repeatButton, stopButton))
    }

    /**
     * AUTO-CERRAR (2026-11-11, como Musicolet): si la música lleva
     * parada [autoCloseMinutes] minutos y la app no está visible, la
     * app se cierra del todo (servicio + actividad). Cada cambio de
     * reproducción, visibilidad o ajuste reinicia el conteo; al
     * sonar, se cancela. La cola guardada NO se borra: al volver a
     * abrir, reanuda donde iba.
     */
    private fun updateAutoCloseTimer() {
        autoCloseJob?.cancel()
        autoCloseJob = null
        val minutes = autoCloseMinutes
        // RONDA 179: con la alarma activa no hay temporizador
        // (suena por este mismo reproductor).
        if (minutes <= 0 || appVisible || alarmActive || player?.isPlaying == true) return
        autoCloseJob = serviceScope.launch {
            delay(minutes * 60_000L)
            // Re-comprobar al despertar: por si sonó, se abrió la
            // app o empezó una alarma mientras corría el conteo.
            if (player?.isPlaying != true && !appVisible && !alarmActive) {
                stopAllAndCloseApp()
            }
        }
    }

    /**
     * Botón X de la notificación (2026-11-11): para la música del
     * todo, quita la notificación, detiene el servicio y le pide a la
     * actividad que se cierre. La cola guardada NO se borra: al volver
     * a abrir, la música sigue donde iba (eso es reanudar, no sonar).
     */
    private fun stopAllAndCloseApp() {
        try {
            val p = player
            p?.stop()
            p?.clearMediaItems()
        } catch (e: Exception) {
            ErrorLog.log(this, "Cerrar todo", e)
        }
        // RONDA 179: al cerrar del todo, la notificación de la
        // alarma tampoco puede quedarse colgada con el servicio
        // muerto detrás (le pasaba igual al botón X).
        getSystemService(NotificationManager::class.java)?.cancel(ALARM_NOTIFICATION_ID)
        sendBroadcast(Intent(ACTION_CLOSE_APP).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

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
                val direct = isDirectMode(
                    PlayerSettingsStore(this@PlaybackService).settingsFlow.first().audioOutputMode,
                )
                audioOutputDirect = direct
                // RONDA EQ: si el efecto no se creó o no tiene bandas,
                // queda escrito en el registro (antes era mudo: la
                // pantalla ofrecía barras que no hacían nada).
                if (!eq.isAvailable) {
                    ErrorLog.log(this@PlaybackService, "EQ no disponible en este dispositivo (el efecto no se creó o no tiene bandas)", null)
                }
                val wantEnabled = if (direct) false else saved.enabled
                if (wantEnabled && eq.isAvailable && !eq.setEnabled(true)) {
                    ErrorLog.log(this@PlaybackService, "EQ: el teléfono rechazó activar el ecualizador", null)
                }
                if (!wantEnabled) eq.setEnabled(false)
                if (!eq.applyPreset(
                    EqualizerManager.Preset(
                        "Guardado",
                        applyTone2d(saved.levelsDb, saved.toneBassDb, saved.toneTrebleDb),
                    ),
                )) {
                    ErrorLog.log(this@PlaybackService, "EQ: no se pudo aplicar el preset guardado", null)
                }
                eq.setBassBoost(saved.bassBoost.toShort())
                eq.setVirtualizer(saved.virtualizer.toShort())
                if (!direct) applyRouteEq()
                applyUsbRouting()
            }
        }
    }

    /**
     * Visualizador real: captura la FFT de la sesión que suena y
     * publica las barras en AudioEffects. Al cambiar de sesión se
     * suelta la captura vieja; si el dispositivo no lo soporta, no
     * se publica nada y el reproductor no muestra espectro.
     */
    private fun setupVisualizer(sessionId: Int) {
        visualizerCapture?.release()
        visualizerCapture = null
        if (sessionId == 0) {
            AudioEffects.publishBars(emptyList())
            return
        }
        val capture = VisualizerCapture(sessionId) { bars ->
            AudioEffects.publishBars(bars)
        }
        if (!capture.isAvailable) {
            capture.release()
            AudioEffects.publishBars(emptyList())
            return
        }
        visualizerCapture = capture
    }

    /**
     * Ronda USB Serie A: el AudioTrack del reproductor va directo
     * al DAC USB cuando lo hay (setPreferredDevice, Media3 1.4.1 —
     * null lo limpia, verificado en el bytecode del AAR). Sin DAC,
     * se limpia la preferencia y Android elige la salida normal.
     * Se llama en los mismos sitios que applyRouteEq: al cambiar
     * la salida y al cambiar los ajustes.
     */
    private fun applyUsbRouting() {
        val dac = try {
            findUsbDac(this@PlaybackService)?.first
        } catch (e: Exception) {
            // Ronda 7 (Codex): que quede en el registro; sin DAC se
            // limpia la preferencia y Android elige la salida normal.
            ErrorLog.log(this@PlaybackService, "USB", e)
            null
        }
        for (sink in audioSinks) {
            try {
                sink.setPreferredDevice(dac)
            } catch (e: Exception) {
                ErrorLog.log(this@PlaybackService, "USB", e)
            }
        }
    }

    /**
     * EQ por salida: si está activado y la salida actual tiene un EQ
     * guardado, se aplica sobre el EQ global (también al de solape,
     * para que el tono no cambie a mitad del fundido).
     */
    private fun applyRouteEq() {
        // Ronda 3 (Codex): en modo directo el EQ no se toca; sin
        // esta guarda, conectar/quitar auriculares lo reactivaba.
        if (audioOutputDirect) return
        val eq = equalizer ?: return
        serviceScope.launch {
            val store = EqStateStore(this@PlaybackService)
            if (!store.perOutputFlow.first()) return@launch
            val (route, name) = currentOutputRoute(this@PlaybackService)
            // Ronda 16: sin EQ guardado para esta salida, vuelve el EQ
            // global (así lo documenta EqStateStore); antes se
            // retornaba y el EQ de la salida anterior seguía sonando
            // en los nuevos auriculares.
            val saved = store.routeStateFlow(routeKey(route, name)).first()
                ?: store.stateFlow.first()
            eq.setEnabled(saved.enabled)
            eq.applyPreset(
                EqualizerManager.Preset(
                    "Salida",
                    applyTone2d(saved.levelsDb, saved.toneBassDb, saved.toneTrebleDb),
                ),
            )
            eq.setBassBoost(saved.bassBoost.toShort())
            eq.setVirtualizer(saved.virtualizer.toShort())
            overlapEqualizer?.let { overlap ->
                overlap.setEnabled(saved.enabled)
                overlap.applyPreset(
                    EqualizerManager.Preset(
                        "Salida",
                        applyTone2d(saved.levelsDb, saved.toneBassDb, saved.toneTrebleDb),
                    ),
                )
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
            // Ronda 4: reloj monotónico — cada paso espera solo
            // lo que falta hasta su instante previsto, sin acumular
            // la deriva de los delay. Mismos 20 niveles y duración.
            val startMs = SystemClock.elapsedRealtime()
            for (i in 1..steps) {
                delay((startMs + i * stepMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
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
                val sink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(gainB, balanceB))
                    .build()
                audioSinks.add(sink)
                // Slice 5 paso 4: offload Atmos también en el
                // reproductor de solape (mismo modo que el principal).
                applyOffloadModeToSink(sink)
                return sink
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
        measureLufsFor(nextItem, isOverlap = true)
        b.prepare()
        b.volume = 0f
        b.play()
        overlapPlayer = b
        overlapping = true
        // RONDA 127: la rampa duraba la ventana COMPLETA, pero A
        // termina antes: el solape arrancaba cuando quedaban <=
        // ventana ms y la rampa (ventana ms + preparar B) terminaba
        // DESPUÉS del final de A. La transición natural llegaba
        // primero, cancelaba B y la pista siguiente REEMPEZABA desde
        // 0 con fade-in: el traspaso no ganaba nunca en el caso
        // normal (solo cuando la pista siguiente era más corta que la
        // ventana). La rampa se recorta a lo que le queda a A menos
        // 150 ms: el traspaso corre justo antes del final de A.
        val remainingAtRamp = (p.duration - p.currentPosition).coerceAtLeast(0L)
        val rampWindow = minOf(window, (remainingAtRamp - 150L).coerceAtLeast(0L))
        if (rampWindow <= 0L) {
            releaseOverlapPlayer()
            overlapping = false
            return false
        }
        // Rampa: A baja 1→0 y B sube 0→1 en la misma ventana.
        overlapJob = serviceScope.launch {
            val steps = 20
            val stepMs = rampWindow / steps
            // Ronda 4: reloj monotónico, como el fade-in.
            val startMs = SystemClock.elapsedRealtime()
            for (i in 1..steps) {
                delay((startMs + i * stepMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
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
        // Ronda 8 (Codex): NO limpiar overlapHandoff aquí: la
        // transición es asíncrona y onMediaItemTransition lo limpia
        // cuando la ve (ver la rama de traspaso arriba).
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
        overlapLufsDb = null
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
                // RONDA EQ: mismo registro que el EQ principal: si el
                // efecto no se creó o el teléfono lo rechaza, queda
                // escrito en vez de fallar en silencio.
                if (!eq.isAvailable) {
                    ErrorLog.log(this@PlaybackService, "EQ solape no disponible en este dispositivo (el efecto no se creó o no tiene bandas)", null)
                }
                val wantEnabled = if (audioOutputDirect) false else saved.enabled
                if (wantEnabled && eq.isAvailable && !eq.setEnabled(true)) {
                    ErrorLog.log(this@PlaybackService, "EQ solape: el teléfono rechazó activar el ecualizador", null)
                }
                if (!wantEnabled) eq.setEnabled(false)
                if (!eq.applyPreset(
                    EqualizerManager.Preset(
                        "Guardado",
                        applyTone2d(saved.levelsDb, saved.toneBassDb, saved.toneTrebleDb),
                    ),
                )) {
                    ErrorLog.log(this@PlaybackService, "EQ solape: no se pudo aplicar el preset guardado", null)
                }
                eq.setBassBoost(saved.bassBoost.toShort())
                eq.setVirtualizer(saved.virtualizer.toShort())
                if (!audioOutputDirect) applyRouteEq()
                applyUsbRouting()
            }
        }
    }

    private fun applyOverlapReplayGain() {
        val processor = overlapGainProcessor ?: return
        val gainDb = selectReplayGainDb(
            replayGainMode, overlapTrackGainDb, overlapAlbumGainDb,
            replayGainPreAmpDb, overlapLufsDb,
        )
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
        // RONDA 179: la alarma se marca activa ANTES de resolver
        // (la resolución tarda y el reproductor sigue en pausa):
        // si el temporizador venciera en ese hueco, pararía la
        // alarma antes de sonar. Se quita en stopAlarm().
        alarmActive = true
        updateAutoCloseTimer()
        try {
            playAlarmInner()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // A2b-1 (auditoría 2026-11-12): playAlarmInner es suspend
            // (settingsFlow.first() dentro); si el servicio se destruye
            // mientras resuelve, la cancelación se lanzaba aquí y el
            // catch genérico la tragaba (la alarma quedaba como fallo
            // en el registro en vez de cancelarse). Se relanza.
            throw e
        } catch (e: Exception) {
            // Sin esto, un fallo al cargar los ajustes, resolver la
            // música o preparar el reproductor escapaba de la
            // corrutina y cerraba la app al sonar la alarma.
            ErrorLog.log(this, "Sonar alarma", e)
            alarmActive = false
            updateAutoCloseTimer()
        }
    }

    private suspend fun playAlarmInner() {
        val settings = withContext(Dispatchers.IO) {
            AlarmStore(this@PlaybackService).settingsFlow.first()
        }
        if (!settings.enabled) {
            alarmActive = false
            updateAutoCloseTimer()
            return
        }
        val items = withContext(Dispatchers.IO) { resolveAlarmItems(settings) }
        if (items.isEmpty()) {
            ErrorLog.log(this, "Alarma sin música", null)
            alarmActive = false
            updateAutoCloseTimer()
            return
        }
        val p = player
        if (p == null) {
            alarmActive = false
            updateAutoCloseTimer()
            return
        }
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
        // RONDA 179: la alarma terminó: el auto-cierre vuelve a
        // aplicar (si la app sigue oculta, se cierra a los minutos).
        alarmActive = false
        updateAutoCloseTimer()
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

    // MediaLibraryService la declara sin null: la sesión ya existe
    // cuando un controlador (el coche) la pide.
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession =
        mediaSession ?: throw IllegalStateException("La sesión aún no está creada")

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
        try { visibilityReceiver?.let { unregisterReceiver(it) } } catch (_: IllegalArgumentException) { }
        visibilityReceiver = null
        fadeJob?.cancel()
        lufsJob?.cancel()
        overlapJob?.cancel()
        releaseOverlapPlayer()
        // Cada liberación con su propio try/catch (auditoría Codex,
        // ronda 2): si una falla, el resto de la limpieza sigue.
        try { mediaSession?.release() } catch (_: Exception) { }
        try { bitmapLoader?.shutdown() } catch (_: Exception) { }
        bitmapLoader = null
        try { player?.release() } catch (_: Exception) { }
        try { equalizer?.release() } catch (_: Exception) { }
        try { visualizerCapture?.release() } catch (_: Exception) { }
        visualizerCapture = null
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
