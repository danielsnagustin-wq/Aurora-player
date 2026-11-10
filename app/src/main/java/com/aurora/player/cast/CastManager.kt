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
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
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
            ctx.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
            ctx.sessionManager.currentCastSession?.let {
                val name = try {
                    it.castDevice?.friendlyName
                } catch (e: Exception) {
                    null
                } ?: "Chromecast"
                _state.value = CastState.Connected(name)
            }
        } catch (e: Exception) {
            castContext = null
        }
    }

    fun stop() {
        try {
            castContext?.sessionManager
                ?.removeSessionManagerListener(listener, CastSession::class.java)
        } catch (e: Exception) {
            // Al cerrar, un fallo al desuscribirse no importa.
        }
        castContext = null
        onSessionStarted = null
        onSessionEnded = null
    }

    /**
     * Envía una pista online al receptor. Devuelve false sin sesión
     * activa, sin cliente remoto o si el envío falla: el llamador
     * decide entonces (seguir en local / avisar).
     */
    fun loadOnlineTrack(
        streamUrl: String,
        title: String,
        artist: String,
        durationMs: Long = 0L,
        coverUrl: String? = null,
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
            client.load(infoBuilder.build(), options)
            true
        } catch (e: Exception) {
            false
        }
    }
}
