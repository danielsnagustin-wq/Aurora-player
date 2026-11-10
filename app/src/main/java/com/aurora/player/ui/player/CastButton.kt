package com.aurora.player.ui.player

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Cast (Fase 2 app), Serie B: el botón del Chromecast en la
 * cabecera del reproductor. Es el MediaRouteButton del framework:
 * descubre los dispositivos de la red y abre su selector al tocar.
 * Sin Play Services o sin dispositivos, el botón simplemente no
 * muestra nada: nunca se finge un dispositivo.
 *
 * En cast-framework 21.4.0 la versión síncrona de
 * setUpMediaRouteButton(context, button) no está; se usa la que
 * devuelve Task con un Executor (el principal).
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx: Context ->
            MediaRouteButton(ctx).also { button ->
                try {
                    CastButtonFactory.setUpMediaRouteButton(
                        ctx,
                        ContextCompat.getMainExecutor(ctx),
                        button,
                    )
                } catch (e: Exception) {
                    // Sin Play Services: el botón queda sin descubrir.
                }
            }
        },
        modifier = modifier.size(48.dp),
    )
}
