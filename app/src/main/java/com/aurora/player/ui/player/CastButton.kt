package com.aurora.player.ui.player

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import com.aurora.player.util.ErrorLog
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Cast (Fase 2 app), Serie B: el botón del Chromecast en el
 * reproductor. Es el MediaRouteButton del framework: descubre los
 * dispositivos de la red y abre su selector al tocar. Sin Play
 * Services o sin dispositivos, el botón simplemente no muestra nada:
 * nunca se finge un dispositivo.
 *
 * En cast-framework 21.4.0 la versión síncrona de
 * setUpMediaRouteButton(context, button) no está; se usa la que
 * devuelve Task con un Executor (el principal).
 *
 * MENÚ ⋮ (2026-11-11): el botón vive en la fila de acciones (24 dp,
 * como los demás iconos), no en la cabecera.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    AndroidView(
        factory = { ctx: Context ->
            // RONDA CAST-CRASH 2026-11-11: el constructor de
            // MediaRouteButton lee android:colorBackground de su tema
            // y el nuestro no lo definía (resolvía a #0 transparente)
            // → IllegalArgumentException al abrir el reproductor.
            // Se crea con el tema propio de MediaRouter (fondo
            // opaco) y, si aun así falla, se devuelve una vista
            // vacía: el botón nunca vuelve a tumbar la app.
            try {
                val themedCtx = ContextThemeWrapper(
                    ctx,
                    androidx.mediarouter.R.style.Theme_MediaRouter,
                )
                MediaRouteButton(themedCtx).also { button ->
                    try {
                        CastButtonFactory.setUpMediaRouteButton(
                            themedCtx,
                            ContextCompat.getMainExecutor(themedCtx),
                            button,
                        )
                    } catch (e: Exception) {
                        // Sin Play Services: el botón queda sin descubrir.
                    }
                }
            } catch (e: Exception) {
                ErrorLog.log(ctx, "Botón Cast", e)
                View(ctx)
            }
        },
        modifier = modifier.size(size),
    )
}
