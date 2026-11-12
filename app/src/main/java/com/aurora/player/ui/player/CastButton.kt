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
            // CAST (2026-11-12): el constructor de MediaRouteButton
            // re-tematiza su contexto por dentro
            // (createThemedButtonContext) y resuelve el colorPrimary
            // de AppCompat (bytecode mediarouter 1.6.0:
            // getControllerColor → calculateContrast); si resuelve a
            // #0 transparente, tira IllegalArgumentException y la app
            // se cierra al abrir el reproductor (12 FATAL en el log
            // del 11 nov, CastButton.kt:28). Theme.AuroraPlayer ya lo
            // define opaco, pero el botón no depende del tema de la
            // actividad: usa el overlay propio (fondo opaco
            // garantizado) y, si aun así falla, se devuelve una vista
            // vacía: el botón nunca vuelve a tumbar la app.
            try {
                val themedCtx = ContextThemeWrapper(
                    ctx,
                    com.aurora.player.R.style.ThemeOverlay_Aurora_CastButton,
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
