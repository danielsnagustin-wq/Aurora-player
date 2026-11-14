package com.aurora.player.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cast (Fase 2 app), Serie B: sesión con el Chromecast / TV y
 * envío de la pista online que suena.
 *
 * - Sin Play Services o sin proveedor, CastContext es null y todo
 *   es no-op: la app nunca se cierra por Cast.
 * - Solo pistas ONLINE: el receptor descarga el audio desde la URL
 *   del stream. Las locales no tienen URL alcanzable (ver CastMedia).
 * - Al conectar, MainActivity transfiere la pista online actual
 *   (onSessionStarted) y pausa el audio local para no sonar dos
 *   veces. Al desconectar no se reanuda nada solo: el usuario
 *   pulsa play y sigue en el teléfono.
 */
sealed interface CastState {
    data object Disconnected : CastState
    data class Connected(val deviceName: String) : CastState
}

class CastManager(context: Context) {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<CastState>(CastState.Disconnected)
    val state: StateFlow<CastState> = _state.asStateFlow()

    val isConnected: Boolean
        get() = _state.value is CastState.Connected

    private var castContext: CastContext? = null

    // RONDA 176: start() no era idempotente — llamarlo dos veces
    // sin stop() suscribía el MISMO listener dos veces (el SDK no
    // deduplica) y onSessionStarted se disparaba doble (doble
    // transferencia al Chromecast); stop() solo lo quitaba una vez
    // y la suscripción que quedaba podía volver a poner "Conectado"
    // tras el stop. Hoy solo lo llama un DisposableEffect (par
    // equilibrado), pero la API no debe depender de eso.
    private var started = false

    private var onSessionStarted: (() -> Unit)? = null
    private var onSessionEnded: (() -> Unit)? = null

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            val name = try {
                session.castDevice?.friendlyName
            } catch (e: Exception) {
                null
            } ?: "Chromecast"
            _state.value = CastState.Connected(name)
            onSessionStarted?.invoke()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            _state.value = CastState.Disconnected
            onSessionEnded?.invoke()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            val name = try {
                session.castDevice?.friendlyName
            } catch (e: Exception) {
                null
            } ?: "Chromecast"
            _state.value = CastState.Connected(name)
            onSessionStarted?.invoke()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            // RONDA 2 (2026-11-13, P2): la sesión suspendida no es
            // una sesión conectada — antes la UI seguía diciendo
            // "Conectado" y el EQ seguía desactivado. Solo cambia
            // el estado (sin onSessionEnded): si se reanuda,
            // onSessionResumed vuelve a poner Conectado y reenvía
            // la pista.
            _state.value = CastState.Disconnected
        }
    }

    fun start(onSessionStarted: () -> Unit, onSessionEnded: () -> Unit = {}) {
        this.onSessionStarted = onSessionStarted
        this.onSessionEnded = onSessionEnded
        val ctx = try {
            CastContext.getSharedInstance(appContext)
        } catch (e: Exception) {
            null
        } ?: return
        castContext = ctx
        try {
            if (!started) {
                ctx.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
                started = true
            }
            notifyCurrentSession()
        } catch (e: Exception) {
            started = false
            castContext = null
        }
    }

    /**
     * RONDA 71: la sesión ya estaba activa cuando se recreó la
     * pantalla (rotación, vuelta a la app): el listener no vuelve
     * a disparar onSessionStarted, así que sin esto la canción no
     * pasaba al Chromecast aunque la app dijera "Conectado".
     */
    private fun notifyCurrentSession() {
        castContext?.sessionManager?.currentCastSession?.let {
            val name = try {
                it.castDevice?.friendlyName
            } catch (e: Exception) {
                null
            } ?: "Chromecast"
            _state.value = CastState.Connected(name)
            onSessionStarted?.invoke()
        }
    }

    fun stop() {
        try {
            castContext?.sessionManager
                ?.removeSessionManagerListener(listener, CastSession::class.java)
        } catch (e: Exception) {
            // Al cerrar, un fallo al desuscribirse no importa.
        }
        started = false
        castContext = null
        onSessionStarted = null
        onSessionEnded = null
    }

    /**
     * Envía una pista online al receptor. Devuelve false sin sesión
     * activa, sin cliente remoto o si el envío no se pudo lanzar: el
     * llamador decide entonces (seguir en local / avisar).
     * RONDA 2 (2026-11-13, P2): client.load() es asíncrono — antes
     * se devolvía true sin mirar el resultado y, si el receptor lo
     * rechazaba, el teléfono ya se había silenciado (el llamador
     * pausa el audio local cuando sent == true). Ahora el
     * PendingResult se comprueba: si el receptor rechaza la carga,
     * se registra y [onLoadFailed] avisa para volver a sonar en local.
     */
    fun loadOnlineTrack(
        streamUrl: String,
        title: String,
        artist: String,
        durationMs: Long = 0L,
        coverUrl: String? = null,
        onLoadFailed: () -> Unit = {},
    ): Boolean {
        val client = try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient
        } catch (e: Exception) {
            null
        } ?: return false
        return try {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, title)
                putString(MediaMetadata.KEY_ARTIST, artist)
                coverUrl?.let { addImage(WebImage(Uri.parse(it))) }
            }
            val infoBuilder = MediaInfo.Builder(streamUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(CastMedia.contentTypeForStreamUrl(streamUrl))
                .setMetadata(metadata)
            if (durationMs > 0L) infoBuilder.setStreamDuration(durationMs)
            val options = MediaLoadOptions.Builder().setAutoplay(true).build()
            client.load(infoBuilder.build(), options).setResultCallback { result ->
                if (!result.status.isSuccess) {
                    com.aurora.player.util.ErrorLog.log(
                        appContext,
                        "CAST-LOAD",
                        RuntimeException(
                            "El receptor rechazó la carga (status=${result.status})",
                        ),
                    )
                    onLoadFailed()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
